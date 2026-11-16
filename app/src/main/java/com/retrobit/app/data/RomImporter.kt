package com.retrobit.app.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

data class ImportResult(
    val imported: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val total: Int = 0,
    val invalid: Boolean = false,
    val consoles: Map<String, Int> = emptyMap()
)

data class ImportProgress(
    val current: Int = 0,
    val total: Int = 0,
    val fileName: String = "",
    val scanning: Boolean = false,
    /** Secondary line for work that runs inside the current step, e.g. an opening nested archive. */
    val note: String? = null
) {
    val fraction: Float?
        get() = if (!scanning && note == null && total > 0) {
            (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        } else null
}

class RomImporter(
    private val context: Context,
    private val libraryRepository: LibraryRepository,
    private val gameRepository: GameRepository
) {

    private sealed interface Outcome {
        data class Imported(val game: LibraryGame) : Outcome
        data object Skipped : Outcome
        data object Failed : Outcome
    }

    private val cartridgeExtensions = ConsoleCatalog.cartridgeExtensions
    private val discExtensions = ConsoleCatalog.discExtensions
    private val archiveExtensions = ConsoleCatalog.archiveExtensions
    private val importableExtensions = ConsoleCatalog.allExtensions + archiveExtensions
    private val skippedFolders = setOf(
        "bios", "saves", "save", "savestates", "states", "system", "cheats",
        "screenshots", "covers", "artwork", "media",
        // Arcade romsets have no core; their chip dumps must never pass as cartridges.
        "mame", "mame2003", "mame2003-plus", "arcade", "fbneo", "neogeo"
    )
    private val operationMutex = Mutex()

    private suspend fun <T> withOperationLock(block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                libraryRepository.loaded.first { it }
                block()
            }
        }

    private companion object {
        const val EXTRACT_CHUNK = 6
        const val MAX_NESTED_ARCHIVE_DEPTH = 2
        val CUE_FILE = Regex("(?im)^[\\t ]*FILE[\\t ]+(?:\"([^\"\\r\\n]+)\"|(\\S+))")
        val GDI_TRACK = Regex("(?m)^[\\t ]*\\d+[\\t ]+\\d+[\\t ]+\\d+[\\t ]+\\d+[\\t ]+([^\\r\\n]+?)[\\t ]+\\d+[\\t ]*$")
    }

    suspend fun importUris(
        uris: List<Uri>,
        onProgress: (ImportProgress) -> Unit = {}
    ): ImportResult = withOperationLock {
        if (uris.isEmpty()) return@withOperationLock ImportResult()
        val batch = Batch(libraryRepository.library.value)
        uris.forEachIndexed { index, uri ->
            val name = runCatching { queryDisplayName(uri) }.getOrNull().orEmpty()
            onProgress(ImportProgress(index, uris.size, name, false))
            runCatching { takePermission(uri) }
            val openStream = { runCatching { context.contentResolver.openInputStream(uri) }.getOrNull() }
            val mime = { runCatching { context.contentResolver.getType(uri) }.getOrNull() }
            when (
                val outcome = importOne(openStream, mime, name.takeIf { it.isNotBlank() }, null, batch)
            ) {
                is Outcome.Imported -> batch.pending += outcome.game
                Outcome.Skipped -> batch.skipped++
                Outcome.Failed -> batch.failed++
            }
            if (batch.shouldFlush()) commit(batch)
            onProgress(ImportProgress(index + 1, uris.size, name, false))
        }
        commit(batch)
        onProgress(ImportProgress(uris.size, uris.size, "", false))
        batch.result(uris.size)
    }

    /** A collection import stages a copy of the ZIP; an interrupted run must not fill storage. */
    fun clearStaleStaging(maxAgeMs: Long = 6 * 60 * 60 * 1000L) {
        runCatching {
            val dir = File(romsRoot(), ".import")
            val cutoff = System.currentTimeMillis() - maxAgeMs
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoff) file.delete()
            }
        }
    }

    /**
     * Play builds cannot read shared storage directly, so the user hands over one ROMS.zip.
     * Every playable member is unpacked into app storage; nested archives and cue/gdi sets
     * are followed as well so disc collections are not reduced to a single game.
     */
    suspend fun importCollection(
        uri: Uri,
        onProgress: (ImportProgress) -> Unit = {}
    ): ImportResult = withOperationLock {
        onProgress(ImportProgress(scanning = true))
        val displayName = runCatching { queryDisplayName(uri) }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "roms.zip"
        runCatching { takePermission(uri) }
        if (!looksLikeArchive(uri)) return@withOperationLock ImportResult(invalid = true)
        var source = openArchive(uri, displayName)
            ?: return@withOperationLock ImportResult(failed = 1, total = 1)
        try {
            // Some TV providers hand over a descriptor the archive readers cannot use; a
            // staged copy is the slow but universally readable fallback.
            if (!source.staged && RomArchives.entries(source.file).isEmpty()) {
                source.close()
                source = stageSource(uri, displayName)
                    ?: return@withOperationLock ImportResult(failed = 1, total = 1)
            }
            val batch = Batch(libraryRepository.library.value)
            val progress = CollectionProgress(onProgress)
            progress.scanning()
            val total = importArchiveCollection(source.file, batch, batch.seen, progress, 0)
            commit(batch)
            var result = batch.result(total)
            var grandTotal = total
            if (result.failed > 0 && !source.staged) {
                source.close()
                val staged = stageSource(uri, displayName)
                if (staged != null) {
                    source = staged
                    val retryProgress = CollectionProgress(onProgress)
                    retryProgress.scanning()
                    val retry = Batch(libraryRepository.library.value)
                    val retryTotal =
                        importArchiveCollection(source.file, retry, retry.seen, retryProgress, 0)
                    commit(retry)
                    val retryResult = retry.result(retryTotal)
                    grandTotal = maxOf(grandTotal, retryTotal)
                    val consoles = LinkedHashMap(result.consoles)
                    retryResult.consoles.forEach { (id, count) -> consoles.merge(id, count, Int::plus) }
                    result = ImportResult(
                        imported = result.imported + retryResult.imported,
                        skipped = result.skipped + retryResult.skipped,
                        failed = retryResult.failed,
                        total = grandTotal,
                        consoles = consoles
                    )
                }
            }
            onProgress(ImportProgress(grandTotal, grandTotal, "", false))
            result
        } finally {
            source.close()
        }
    }

    private fun stageSource(uri: Uri, displayName: String): ArchiveSource? {
        val staged = stageFile(
            { runCatching { context.contentResolver.openInputStream(uri) }.getOrNull() },
            displayName
        ) ?: return null
        return ArchiveSource(staged, descriptor = null, staged = true)
    }

    private class ArchiveSource(
        val file: File,
        private val descriptor: ParcelFileDescriptor?,
        val staged: Boolean
    ) {
        fun close() {
            runCatching { descriptor?.close() }
            if (staged) runCatching { file.delete() }
        }
    }

    /**
     * File Manager Plus and the Downloads provider hand over a descriptor for the real file, so the
     * archive is read in place instead of being copied into app storage first. Copying an 800 MB
     * ZIP doubled the disk traffic and made collection imports look frozen; providers that only
     * expose a stream still fall back to the staged copy.
     */
    private fun openArchive(uri: Uri, displayName: String): ArchiveSource? {
        val descriptor = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }
            .getOrNull()
        if (descriptor != null) {
            val file = File("/proc/self/fd/${descriptor.fd}")
            if (runCatching { file.isFile && file.length() > 0L }.getOrDefault(false)) {
                return ArchiveSource(file, descriptor, staged = false)
            }
            runCatching { descriptor.close() }
        }
        val staged = stageFile(
            { runCatching { context.contentResolver.openInputStream(uri) }.getOrNull() },
            displayName
        ) ?: return null
        return ArchiveSource(staged, descriptor = null, staged = true)
    }

    private class Batch(games: List<LibraryGame>) {
        val seen = games.flatMapTo(mutableSetOf()) { ImportTitle.gameIdentities(it) }
        val pending = mutableListOf<LibraryGame>()
        var committed = 0
        val consoles = LinkedHashMap<String, Int>()
        var skipped = 0
        var failed = 0
        var deadline = System.currentTimeMillis() + FLUSH_INTERVAL_MS

        fun shouldFlush(): Boolean =
            pending.size >= FLUSH_SIZE || (pending.isNotEmpty() && System.currentTimeMillis() >= deadline)

        fun result(total: Int) = ImportResult(
            imported = committed,
            skipped = skipped,
            failed = failed,
            total = total,
            consoles = LinkedHashMap(consoles)
        )

        companion object {
            const val FLUSH_SIZE = 6
            const val FLUSH_INTERVAL_MS = 900L
        }
    }

    private suspend fun commit(batch: Batch) {
        if (batch.pending.isEmpty()) return
        val games = batch.pending.toList()
        batch.pending.clear()
        batch.deadline = System.currentTimeMillis() + Batch.FLUSH_INTERVAL_MS
        val added = libraryRepository.addLibraryGames(games)
        batch.committed += added.size
        added.forEach { batch.consoles.merge(it.consoleId, 1, Int::plus) }
    }

    private data class CollectionSet(
        val main: ArchiveEntry,
        val members: List<ArchiveEntry>
    )

    private data class CollectionItem(
        val console: ConsoleInfo,
        val fileName: String,
        val title: String,
        val mainEntry: String,
        val mainTarget: File,
        val targets: List<Pair<String, File>>,
        val folder: File?
    )

    /**
     * One counter for the whole collection. Nested archives extend the same total instead of
     * starting their own 1/N, so the bar never jumps back to a meaningless fraction mid-import.
     */
    private class CollectionProgress(private val emit: (ImportProgress) -> Unit) {
        private var current = 0
        private var total = 0
        private var note: String? = null

        fun scanning() = emit(ImportProgress(scanning = true, note = note))

        fun expect(count: Int) {
            if (count > 0) total += count
        }

        fun opening(fileName: String) {
            note = fileName
            emit(ImportProgress(current, total, "", false, note))
        }

        fun advance(fileName: String) {
            current++
            note = null
            emit(ImportProgress(current, total, fileName, false))
        }

        fun finish() = emit(ImportProgress(total, total, "", false))
    }

    private suspend fun importArchiveCollection(
        archive: File,
        batch: Batch,
        seen: MutableSet<String>,
        progress: CollectionProgress,
        depth: Int,
        parentHint: String? = null
    ): Int {
        val entries = RomArchives.entries(archive)
        if (entries.isEmpty()) return 0
        val valid = entries
            .map { it.copy(name = normalizeArchivePath(it.name)) }
            .filter { it.size > 0L && !skippedArchivePath(it.name) }
        if (valid.isEmpty()) return 0
        val byLower = valid.associateBy { it.name.lowercase() }

        val sets = mutableListOf<CollectionSet>()
        val setMembers = mutableSetOf<String>()
        valid.filter { it.extension in setOf("scummvm", "uae") }
            .groupBy { it.name.substringBeforeLast('/', "") }.forEach { (directory, launchers) ->
                val main = launchers.singleOrNull() ?: return@forEach
                val prefix = if (directory.isBlank()) "" else "$directory/"
                val members = valid.filter { it.name.startsWith(prefix) }
                if (members.size < 2 || members.count { it.extension in setOf("scummvm", "uae") } != 1) return@forEach
                sets += CollectionSet(main, members)
                members.forEach { setMembers += it.name.lowercase() }
            }
        val discSheetFolders = valid.filter { it.extension == "cue" || it.extension == "gdi" }
            .map { it.name.substringBeforeLast('/', "").lowercase() }.toSet()
        valid.filter {
            (it.extension == "cue" || it.extension == "gdi") && it.name.lowercase() !in setMembers
        }.forEach { main ->
            val members = resolveDiscSet(main, valid, byLower, archive) ?: return@forEach
            sets += CollectionSet(main, members)
            members.forEach { setMembers += it.name.lowercase() }
        }

        val items = mutableListOf<CollectionItem>()
        valid.sortedBy { it.name.lowercase() }.forEach { entry ->
            if (entry.name.lowercase() in setMembers) return@forEach
            if (entry.extension in archiveExtensions) return@forEach
            if (entry.extension == "cue" || entry.extension == "gdi") return@forEach
            // An incomplete disc set must not turn its surviving tracks into standalone games.
            if (entry.extension in setOf("bin", "img") &&
                entry.name.substringBeforeLast('/', "").lowercase() in discSheetFolders &&
                (consoleHint(entry.name) ?: parentHint) in ConsoleCatalog.discConsoleIds
            ) return@forEach
            val console = collectionConsole(entry, parentHint) ?: return@forEach
            val entryName = entry.name.substringAfterLast('/')
            val fileName = sanitizeFileName(entryName)
            if (!claim(console.id, fileName, seen)) {
                batch.skipped++
                return@forEach
            }
            val destination = uniqueFile(consoleDirectory(console.id), fileName)
            items += CollectionItem(
                console = console,
                fileName = fileName,
                title = cleanTitle(entryName.substringBeforeLast('.')),
                mainEntry = entry.name,
                mainTarget = destination,
                targets = listOf(entry.name to destination),
                folder = null
            )
        }

        sets.forEach { set ->
            val hint = consoleHint(set.main.name) ?: parentHint
            val console = if (set.main.extension in setOf("scummvm", "uae")) {
                consoleForFile(set.main.extension, hint)
            } else {
                consoleForDiscEntry(set.main, hint)
            } ?: return@forEach
            val mainName = set.main.name.substringAfterLast('/')
            val fileName = sanitizeFileName(mainName)
            if (!claim(console.id, fileName, seen)) {
                batch.skipped++
                return@forEach
            }
            val folder = uniqueDirectory(
                consoleDirectory(console.id),
                sanitizeFileName(mainName.substringBeforeLast('.'))
            )
            val root = folder.canonicalFile
            val prefix = set.main.name.substringBeforeLast('/', "").let {
                if (it.isBlank()) "" else "$it/"
            }
            val targets = set.members.mapNotNull { member ->
                val relative = member.name.removePrefix(prefix).trimStart('/')
                val target = runCatching { File(root, relative).canonicalFile }.getOrNull()
                    ?: return@mapNotNull null
                if (!target.path.startsWith(root.path + File.separator)) return@mapNotNull null
                member.name to target
            }
            val mainTarget = targets.firstOrNull { it.first == set.main.name }?.second
            if (targets.size != set.members.size || mainTarget == null) {
                runCatching { folder.deleteRecursively() }
                batch.failed++
                return@forEach
            }
            items += CollectionItem(
                console = console,
                fileName = fileName,
                title = cleanTitle(mainName.substringBeforeLast('.')),
                mainEntry = set.main.name,
                mainTarget = mainTarget,
                targets = targets,
                folder = folder
            )
        }

        progress.expect(items.size)
        items.chunked(EXTRACT_CHUNK).forEach { chunk ->
            val extracted = RomArchives.extractEntries(archive, chunk.flatMap { it.targets })
            chunk.forEach { item ->
                progress.advance(item.fileName)
                val complete = item.mainEntry in extracted &&
                    item.mainTarget.isFile && item.mainTarget.length() > 0L &&
                    (item.folder == null || item.targets.all {
                        it.first in extracted && it.second.isFile && it.second.length() > 0L
                    })
                if (!complete) {
                    if (item.folder != null) {
                        runCatching { item.folder.deleteRecursively() }
                    } else {
                        runCatching { item.targets.forEach { target -> target.second.delete() } }
                    }
                    batch.failed++
                    return@forEach
                }
                batch.pending += buildGame(item.title, item.console, item.mainTarget, null, null)
                if (batch.shouldFlush()) commit(batch)
            }
        }

        var nestedTotal = 0
        if (depth < MAX_NESTED_ARCHIVE_DEPTH) {
            val staging = File(romsRoot(), ".import").apply { mkdirs() }
            valid.filter {
                it.extension in archiveExtensions && it.name.lowercase() !in setMembers
            }.forEach { entry ->
                val nested = File(staging, "nested-${UUID.randomUUID()}.${entry.extension}")
                progress.opening(entry.name.substringAfterLast('/'))
                val unpacked = RomArchives.extractEntry(archive, entry.name, nested)
                if (!unpacked) {
                    batch.failed++
                    return@forEach
                }
                try {
                    nestedTotal += importArchiveCollection(
                        nested, batch, seen, progress, depth + 1, consoleHint(entry.name) ?: parentHint
                    )
                } finally {
                    runCatching { nested.delete() }
                }
            }
        }
        return items.size + nestedTotal
    }

    private fun collectionConsole(entry: ArchiveEntry, parentHint: String?): ConsoleInfo? {
        val hint = consoleHint(entry.name) ?: parentHint
        return when (entry.extension) {
            in ConsoleCatalog.genericExtensions -> hint?.let { consoleForFile(entry.extension, it) }
            "m3u", "cue", "gdi" -> null // Sheets and companions are resolved as a set.
            "prg", "p00", "abs", "cof", "o" -> consoleForFile(entry.extension, hint)
            in ConsoleCatalog.programExtensions -> null
            in cartridgeExtensions -> consoleForFile(entry.extension, hint)
            in discExtensions -> consoleForDiscEntry(entry, hint)
            else -> null
        }
    }

    /**
     * A cue/gdi is only useful with its track files. The references are read from the small
     * text member, so a collection import never guesses wrong and never drops a track.
     */
    private fun resolveDiscSet(
        main: ArchiveEntry,
        entries: List<ArchiveEntry>,
        byLower: Map<String, ArchiveEntry>,
        archive: File
    ): List<ArchiveEntry>? {
        val folder = main.name.substringBeforeLast('/', "")
        val refs = mutableListOf<String>()
        val text = RomArchives.readEntryText(archive, main.name)
        if (!text.isNullOrBlank()) {
            if (main.extension == "cue") {
                CUE_FILE.findAll(text).forEach { match ->
                    refs += match.groupValues[1].ifBlank { match.groupValues[2] }
                }
            } else {
                GDI_TRACK.findAll(text).forEach { match ->
                    refs += match.groupValues[1].trim().trim('"')
                }
            }
        }
        val members = mutableListOf(main)
        if (refs.isEmpty()) {
            if (main.extension != "cue") return null
            val base = main.name.substringAfterLast('/').substringBeforeLast('.').lowercase()
            entries.filter { entry ->
                entry !== main &&
                    entry.name.substringBeforeLast('/') == folder &&
                    entry.extension == "bin" &&
                    entry.name.substringAfterLast('/').substringBeforeLast('.')
                        .lowercase() == base
            }.forEach { members += it }
            return members.takeIf { it.size > 1 }
        }
        refs.forEach { ref ->
            val resolved = resolveArchivePath(folder, ref) ?: return null
            members += byLower[resolved.lowercase()] ?: return null
        }
        return members.distinctBy { it.name }
    }

    private fun normalizeArchivePath(name: String): String =
        name.replace('\\', '/').trimStart('/')

    private fun skippedArchivePath(path: String): Boolean {
        val segments = path.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return true
        if (segments.any { it.startsWith(".") || it.equals("__MACOSX", true) }) return true
        return segments.dropLast(1).any { it.lowercase() in skippedFolders }
    }

    private fun resolveArchivePath(folder: String, ref: String): String? {
        val clean = normalizeArchivePath(ref).trim()
        if (clean.isBlank() || clean.contains("..")) return null
        return if (folder.isBlank()) clean else "$folder/$clean"
    }

    private fun looksLikeArchive(uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val head = ByteArray(6)
            if (input.read(head) < 6) return@runCatching false
            when {
                head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() -> true
                head[0] == 0x37.toByte() && head[1] == 0x7A.toByte() -> true
                else -> false
            }
        } ?: false
    }.getOrDefault(false)

    private fun importOne(
        openStream: () -> java.io.InputStream?,
        mime: () -> String?,
        displayNameHint: String?,
        relativePath: String?,
        batch: Batch
    ): Outcome {
        val displayName = displayNameHint ?: return Outcome.Failed
        val rawExtension = displayName.substringAfterLast('.', "").lowercase()
        val extension = rawExtension.ifBlank { extensionFromMime(mime()) }
        val hint = consoleHint(relativePath ?: displayName)

        if (extension in archiveExtensions) {
            val staged = stageFile(openStream, displayName) ?: return Outcome.Failed
            val outcome = try {
                handleArchive(staged, displayName, hint, batch)
            } finally {
                runCatching { staged.delete() }
            }
            return outcome
        }
        if (extension !in importableExtensions) return Outcome.Failed
        // Launchers and disc sheets must travel with their companion files.
        if (extension in setOf("scummvm", "uae", "cue", "gdi", "m3u")) return Outcome.Failed

        val console = consoleForFile(extension, hint)
            ?: if (extension == "iso" && hint == null) detectIsoConsole(openStream) else null
        if (console == null) return Outcome.Failed
        val safeName = sanitizeFileName(displayName)
        if (!claim(console.id, safeName, batch.seen)) return Outcome.Skipped

        val destination = uniqueFile(consoleDirectory(console.id), safeName)
        val copied = runCatching {
            openStream()?.use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            } ?: error("Cannot open ROM")
        }.isSuccess
        if (!copied || destination.length() == 0L) {
            runCatching { destination.delete() }
            return Outcome.Failed
        }

        val title = cleanTitle(displayName.substringBeforeLast('.'))
        return Outcome.Imported(buildGame(title, console, destination, null, null))
    }

    /**
     * Decides what an archive really holds and turns it into a library entry.
     * Cartridge ROMs are unpacked individually; disc archives are extracted with their companions.
     */
    private fun handleArchive(
        archive: File,
        displayName: String,
        hint: String?,
        batch: Batch
    ): Outcome {
        val entries = RomArchives.entries(archive)
        if (entries.isEmpty()) return Outcome.Failed

        val launchers = entries.filter { it.extension in setOf("scummvm", "uae") }
        if (launchers.isNotEmpty()) {
            val launcher = launchers.singleOrNull() ?: return Outcome.Failed
            val console = consoleForFile(launcher.extension, consoleHint(launcher.name) ?: hint)
                ?: return Outcome.Failed
            val prefix = launcher.name.substringBeforeLast('/', "").let {
                if (it.isBlank()) "" else "$it/"
            }
            if (entries.none { it != launcher && it.size > 0L && it.name.startsWith(prefix) }) {
                return Outcome.Failed
            }
            val mainName = sanitizeFileName(launcher.name.substringAfterLast('/'))
            if (!claim(console.id, mainName, batch.seen)) return Outcome.Skipped
            val folder = uniqueDirectory(
                consoleDirectory(console.id), sanitizeFileName(displayName.substringBeforeLast('.'))
            )
            val main = File(folder, launcher.name)
            if (!RomArchives.extractAll(archive, folder) || !insideFolder(main, folder) || main.length() == 0L) {
                runCatching { folder.deleteRecursively() }
                return Outcome.Failed
            }
            return Outcome.Imported(
                buildGame(cleanTitle(mainName.substringBeforeLast('.')), console, main.canonicalFile, null, mainName)
            )
        }

        val cartridge = entries
            .filter {
                val entryHint = consoleHint(it.name) ?: hint
                it.extension in cartridgeExtensions ||
                    (it.extension in setOf("prg", "p00", "abs", "cof", "o") &&
                        consoleForFile(it.extension, entryHint) != null) ||
                    (it.extension in ConsoleCatalog.genericExtensions && entryHint != null &&
                        entryHint !in ConsoleCatalog.discConsoleIds &&
                        ConsoleCatalog.acceptsExtension(entryHint, it.extension))
            }
            .maxByOrNull { it.size }
        if (cartridge != null) {
            val console = consoleForFile(cartridge.extension, consoleHint(cartridge.name) ?: hint)
                ?: return Outcome.Failed
            val entryName = cartridge.name.substringAfterLast('/')
            if (!claim(console.id, entryName, batch.seen)) return Outcome.Skipped
            val destination = uniqueFile(consoleDirectory(console.id), sanitizeFileName(entryName))
            val unpacked = RomArchives.extractEntry(archive, cartridge.name, destination) &&
                destination.length() > 0L
            if (!unpacked) {
                runCatching { destination.delete() }
                return Outcome.Failed
            }
            return Outcome.Imported(
                buildGame(cleanTitle(entryName.substringBeforeLast('.')), console, destination, null, null)
            )
        }

        val disc = chooseDiscEntry(entries, hint)
        if (disc != null) {
            val console = consoleForDiscEntry(disc, consoleHint(disc.name) ?: hint) ?: return Outcome.Failed
            val mainName = disc.name.substringAfterLast('/')
            if (!claim(console.id, mainName, batch.seen)) {
                return Outcome.Skipped
            }
            val folder = uniqueDirectory(
                consoleDirectory(console.id),
                sanitizeFileName(displayName.substringBeforeLast('.'))
            )
            val extracted = RomArchives.extractAll(archive, folder) &&
                insideFolder(File(folder, disc.name), folder)
            if (!extracted) {
                runCatching { folder.deleteRecursively() }
                return Outcome.Failed
            }
            return Outcome.Imported(
                buildGame(
                    title = cleanTitle(mainName.substringBeforeLast('.')),
                    console = console,
                    file = File(folder, disc.name).canonicalFile,
                    pendingEntry = null,
                    fileName = mainName
                )
            )
        }

        return Outcome.Failed
    }

    /** Prepares legacy archive-backed games while preserving their existing file paths. */
    suspend fun prepareRom(game: LibraryGame): File? = withOperationLock {
        game.unpackedPath
            ?.let { File(it).takeIf { file -> file.isFile && file.canRead() && file.length() > 0L } }
            ?.let { return@withOperationLock it }
        val source = File(game.filePath)
        val pending = game.pendingEntry
            ?: return@withOperationLock source.takeIf { it.isFile && it.canRead() && it.length() > 0L }
        val directory = consoleDirectory(game.consoleId)
        val folderName = "unpacked-${sanitizeFileName(game.id)}"
        var folder = File(directory, folderName)
        val existingMain = runCatching { File(folder, pending).canonicalFile }.getOrNull()
        if (existingMain != null && insideFolder(existingMain, folder) &&
            existingMain.isFile && existingMain.canRead() && existingMain.length() > 0L
        ) {
            libraryRepository.markUnpacked(game.id, existingMain.absolutePath)
            return@withOperationLock existingMain
        }
        if (!source.isFile || !source.canRead() || source.length() == 0L) {
            return@withOperationLock null
        }
        val staging = File(directory, ".prepare-${UUID.randomUUID()}")
        if (!staging.mkdir()) return@withOperationLock null
        try {
            currentCoroutineContext().ensureActive()
            val main = runCatching { File(staging, pending).canonicalFile }.getOrNull()
                ?: return@withOperationLock null
            val unpacked = RomArchives.extractAll(source, staging)
            currentCoroutineContext().ensureActive()
            if (!unpacked || !insideFolder(main, staging) || !main.isFile ||
                !main.canRead() || main.length() == 0L
            ) {
                return@withOperationLock null
            }
            if (folder.exists()) {
                do {
                    folder = File(directory, "$folderName-${UUID.randomUUID()}")
                } while (folder.exists())
            }
            val finalizedMain = File(folder, main.relativeTo(staging.canonicalFile).path)
            if (folder.exists() || !staging.renameTo(folder)) return@withOperationLock null
            libraryRepository.markUnpacked(game.id, finalizedMain.absolutePath)
            finalizedMain
        } finally {
            runCatching { staging.deleteRecursively() }
        }
    }

    private fun chooseDiscEntry(entries: List<ArchiveEntry>, hint: String?): ArchiveEntry? {
        val preferred = when (hint) {
            "psp" -> listOf("iso", "cso", "pbp", "chd")
            else -> listOf("cue", "chd", "pbp", "iso", "cso")
        }
        return (preferred + listOf("bin", "img")).firstNotNullOfOrNull { extension ->
            entries.firstOrNull {
                it.extension == extension && consoleForDiscEntry(it, consoleHint(it.name) ?: hint) != null
            }
        }
    }

    private fun consoleForDiscEntry(entry: ArchiveEntry, hint: String?): ConsoleInfo? =
        ConsoleCatalog.consoleForExtension(entry.extension, hint)
            ?.takeIf { it.id in ConsoleCatalog.discConsoleIds }

    private fun stageFile(openStream: () -> java.io.InputStream?, displayName: String): File? {
        val stagingRoot = File(romsRoot(), ".import").apply { mkdirs() }
        val extension = displayName.substringAfterLast('.', "").takeIf { it.length <= 4 } ?: "bin"
        val staged = File(stagingRoot, "import-${UUID.randomUUID()}.$extension")
        return runCatching {
            openStream()?.use { input ->
                FileOutputStream(staged).use { output -> input.copyTo(output) }
            } ?: error("Cannot open archive")
            staged.takeIf { it.length() > 0L }
        }.getOrElse {
            runCatching { staged.delete() }
            null
        }
    }

    private fun insideFolder(file: File, folder: File): Boolean = runCatching {
        val root = folder.canonicalPath
        val target = file.canonicalFile
        target.path.startsWith(root + File.separator) && target.isFile
    }.getOrDefault(false)

    private fun claim(consoleId: String, fileName: String, seen: MutableSet<String>): Boolean =
        ImportTitle.claim(consoleId, fileName, seen)

    private fun buildGame(
        title: String,
        console: ConsoleInfo,
        file: File,
        pendingEntry: String?,
        fileName: String?
    ): LibraryGame {
        val catalog = gameRepository.matchByTitle(title)
            ?.takeIf {
                it.consoleId == console.id &&
                    ImportTitle.gameIdentity(console.id, it.title) ==
                    ImportTitle.gameIdentity(console.id, title)
            }
        return libraryRepository.createLibraryGame(
            title = catalog?.title ?: title,
            consoleId = console.id,
            filePath = file.absolutePath,
            fileName = fileName ?: file.name,
            catalogId = catalog?.id,
            coverUrl = catalog?.cover,
            pendingEntry = pendingEntry
        )
    }

    private fun detectIsoConsole(openStream: () -> java.io.InputStream?): ConsoleInfo? =
        runCatching {
            openStream()?.use { stream ->
                val input = java.io.DataInputStream(stream)
                if (input.skipBytes(16 * 2048) != 16 * 2048) return@use null
                val header = ByteArray(40)
                input.readFully(header)
                ConsoleCatalog.consoleForIsoHeader(header)
            }
        }.getOrNull()

    private fun consoleForFile(extension: String, hint: String?): ConsoleInfo? =
        ConsoleCatalog.consoleForExtension(extension, hint)

    private fun consoleHint(path: String?): String? = ConsoleCatalog.consoleHint(path)

    private fun romsRoot(): File =
        context.getExternalFilesDir("roms") ?: File(context.filesDir, "roms").apply { mkdirs() }

    private fun consoleDirectory(consoleId: String): File =
        File(romsRoot(), consoleId).apply { mkdirs() }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.lastPathSegment?.substringAfterLast('/')
        }
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index) else null
                    } else null
                }
        }.getOrNull()?.let { return it }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun takePermission(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }

    private fun extensionFromMime(mime: String?): String = when (mime) {
        "application/zip" -> "zip"
        "application/x-7z-compressed" -> "7z"
        "application/x-nes-rom" -> "nes"
        "application/x-snes-rom" -> "sfc"
        "application/x-n64-rom" -> "n64"
        "application/x-gameboy-rom" -> "gb"
        "application/x-gameboy-color-rom" -> "gbc"
        "application/x-gba-rom" -> "gba"
        "application/x-nintendo-ds-rom" -> "nds"
        "application/x-genesis-rom" -> "md"
        "application/x-psx-rom" -> "chd"
        "application/x-pc-engine-rom" -> "pce"
        "application/x-wonderswan-rom" -> "ws"
        else -> ""
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(120).ifBlank { "game" }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var index = 2
        while (candidate.exists()) {
            candidate = File(dir, if (ext.isBlank()) "$base ($index)" else "$base ($index).$ext")
            index++
        }
        return candidate
    }

    private fun uniqueDirectory(dir: File, name: String): File {
        var candidate = File(dir, name)
        var index = 2
        while (candidate.exists()) {
            candidate = File(dir, "$name (${index++})")
        }
        candidate.mkdirs()
        return candidate
    }

    private fun cleanTitle(value: String): String = ImportTitle.clean(value)

    private val ArchiveEntry.extension: String
        get() = name.substringAfterLast('.', "").lowercase()
}

internal object ImportTitle {
    // "01 - Game" / "01.Game" are track numbers; "10-Yard Fight" and "1200-in-1" are names.
    private val trackPrefix = Regex("^\\d+\\s*(?:\\s-|-\\s|\\.)\\s*")
    private val legacyTrackPrefix = Regex("^\\d+\\s*[-.]\\s*")

    private val identityPunctuation = Regex("[^\\p{L}\\p{N}]+")
    private val mediaPart = Regex(
        "\\b(disc|disk|cd|side|part|track|tape|episode|chapter|volume)\\s*[-. ]*([0-9]+|[ivx]+|[a-z])\\b(?:\\s*(?:of|/)\\s*\\d+)?",
        RegexOption.IGNORE_CASE
    )

    fun clean(value: String): String = tidy(value, trackPrefix)

    /** Exact, console-scoped title key; disc/side/part numbers survive metadata cleaning. */
    fun gameIdentity(consoleId: String, stem: String, mediaStem: String = stem): String {
        val title = clean(stem)
        val normalized = (if (title == "Unknown game") stem else title)
            .replace(mediaPart, " ").lowercase(Locale.ROOT)
            .replace(identityPunctuation, " ").trim()
        val mediaSource = mediaStem.replace('_', ' ')
            .takeIf { mediaPart.containsMatchIn(it) } ?: stem.replace('_', ' ')
        val parts = mediaPart.findAll(mediaSource).map { match ->
            val kind = when (val name = match.groupValues[1].lowercase(Locale.ROOT)) {
                "disk", "cd" -> "disc"
                else -> name
            }
            val number = match.groupValues[2].lowercase(Locale.ROOT)
            "$kind:${number.toIntOrNull()?.toString() ?: number}"
        }.distinct().sorted().joinToString(",")
        return "$consoleId|$normalized|$parts"
    }

    private fun stem(fileName: String): String =
        fileName.replace('\\', '/').substringAfterLast('/').substringBeforeLast('.')

    fun fileIdentity(consoleId: String, fileName: String): String =
        gameIdentity(consoleId, stem(fileName))

    fun gameIdentities(game: LibraryGame): Set<String> {
        val fileStem = stem(game.pendingEntry ?: game.fileName)
        val mediaStem = fileStem.ifBlank { game.title }
        return buildSet {
            if (fileStem.isNotBlank()) add(gameIdentity(game.consoleId, fileStem, mediaStem))
            if (game.title.isNotBlank() && game.title != "Unknown game") {
                add(gameIdentity(game.consoleId, game.title, mediaStem))
            }
        }
    }

    /** Claims still remain reserved after a failed copy, just as filename claims did. */
    fun claim(consoleId: String, fileName: String, seen: MutableSet<String>): Boolean =
        seen.add(fileIdentity(consoleId, fileName))

    /** Only filters incoming inserts; existing entries (including old duplicates) are untouched. */
    fun newGames(existing: List<LibraryGame>, incoming: List<LibraryGame>): List<LibraryGame> {
        val seen = existing.flatMapTo(mutableSetOf()) { gameIdentities(it) }
        return incoming.filter { game ->
            val keys = gameIdentities(game)
            if (keys.any { it in seen }) {
                false
            } else {
                seen.addAll(keys)
                true
            }
        }
    }

    /** What imports before 1.11.0 stored, so damaged titles can be recognised and repaired. */
    fun legacy(value: String): String = tidy(value, legacyTrackPrefix)

    private fun tidy(value: String, prefix: Regex): String =
        value.replace('_', ' ')
            .replace(prefix, " ")
            .replace(Regex("\\s*[\\(\\[].*?[\\)\\]]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Unknown game" }
}

private val SHEET_CUE_FILE = Regex("(?im)^[\\t ]*FILE[\\t ]+(?:\"([^\"\\r\\n]+)\"|(\\S+))")
private val SHEET_GDI_TRACK = Regex("(?m)^[\\t ]*\\d+[\\t ]+\\d+[\\t ]+\\d+[\\t ]+\\d+[\\t ]+([^\\r\\n]+?)[\\t ]+\\d+[\\t ]*$")

/** Bytes on disk for a game; cue/gdi/m3u sheets add up the tracks or discs they reference. */
fun romSizeOnDisk(path: String, depth: Int = 0): Long {
    val file = java.io.File(path)
    if (!file.isFile) return -1L
    val ext = file.extension.lowercase()
    if (ext !in setOf("cue", "gdi", "m3u") || depth > 1) return file.length()
    val text = runCatching { file.readText() }.getOrNull() ?: return file.length()
    val refs = when (ext) {
        "cue" -> SHEET_CUE_FILE.findAll(text).map { it.groupValues[1].ifBlank { it.groupValues[2] } }
        "gdi" -> SHEET_GDI_TRACK.findAll(text).map { it.groupValues[1].trim('"') }
        else -> text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    }.distinct().toList()
    val dir = file.parentFile ?: return file.length()
    val prefix = dir.canonicalPath + java.io.File.separator
    val total = refs.sumOf { ref ->
        val target = java.io.File(dir, ref.replace('\\', '/'))
        if (target.isFile && target.canonicalPath.startsWith(prefix)) {
            romSizeOnDisk(target.path, depth + 1).coerceAtLeast(0L)
        } else {
            0L
        }
    }
    return file.length() + total
}
