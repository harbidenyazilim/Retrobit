package com.retrobit.app.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

internal object SaveRestoreTransaction {
    private const val WORK_PREFIX = ".saves-restore-"
    private const val MANIFEST = "manifest.txt"

    fun isSaveFile(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf(
            "state", "srm", "png", "sav", "rtc", "mcd", "hi", "nv", "nvram", "cfg", "dat",
            "sra", "fla", "eep", "dsv"
        )

    fun restore(
        savesDir: File,
        input: InputStream,
        moveTree: (File, File) -> Unit = { source, target ->
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            Unit
        }
    ): Int = synchronized(AtomicFiles) {
        var workspace: File? = null
        var committed = false
        return try {
            val live = input.use { source ->
                val parent = savesDir.absoluteFile.parentFile?.canonicalFile
                    ?: throw IOException("Missing saves parent")
                check(parent.isDirectory) { "Missing saves parent" }
                val root = File(parent, savesDir.name)
                check(!Files.isSymbolicLink(root.toPath())) { "Linked saves directory" }
                check(!root.exists() || root.isDirectory) { "Invalid saves directory" }
                val work = Files.createTempDirectory(parent.toPath(), WORK_PREFIX).toFile()
                workspace = work
                writeManifest(work, root.name, "staging")
                FileOutputStream(File(work, "input.zip")).use { output ->
                    source.copyTo(output)
                    output.fd.sync()
                }
                root
            }
            val work = checkNotNull(workspace)
            val staged = File(work, "staged")
            check(staged.mkdir()) { "Cannot create staged saves" }
            val count = extract(File(work, "input.zip"), staged.canonicalFile)
            if (count == 0) return 0
            if (live.exists()) copyMissing(live, staged, live.canonicalFile)
            writeManifest(work, live.name, "committing")
            commit(live, staged, File(work, "previous"), File(work, "failed"), moveTree)
            writeManifest(work, live.name, "committed")
            committed = true
            count
        } catch (_: Throwable) {
            0
        } finally {
            workspace?.let { work ->
                // A failed rollback must retain the only intact original tree.
                if (committed || !exists(File(work, "previous"))) discard(work)
            }
        }
    }

    /**
     * A restore renames the live tree to `previous` and then the staged tree into place. If the
     * process dies between those two renames, the saves folder is missing and the only intact
     * tree sits in the workspace. Startup runs this before anything else touches saves:
     *  - live missing and `previous` present: the commit never finished, move `previous` back;
     *  - live present and `staged` gone: the commit finished, the workspace is leftovers;
     *  - anything ambiguous keeps `previous` exactly where it is. Nothing is deleted blindly.
     */
    fun recoverInterruptedRestore(savesDir: File): Int = synchronized(AtomicFiles) {
        val parent = savesDir.absoluteFile.parentFile?.canonicalFile ?: return 0
        val live = File(parent, savesDir.name)
        val workspaces = parent.listFiles { file ->
            file.isDirectory && file.name.startsWith(WORK_PREFIX)
        } ?: return 0
        var recovered = 0
        for (work in workspaces) {
            runCatching {
                if (Files.isSymbolicLink(work.toPath()) || Files.isSymbolicLink(live.toPath())) {
                    return@runCatching
                }
                val manifest = readManifest(work)
                val target = manifest["target"]
                if (target != null && target != live.name) return@runCatching
                val previous = File(work, "previous")
                if (!exists(previous)) {
                    // Only staged/temp files; no user tree can be lost here.
                    discard(work)
                    return@runCatching
                }
                if (exists(live)) {
                    if (!exists(File(work, "staged"))) discard(work)
                    return@runCatching
                }
                Files.move(previous.toPath(), live.toPath(), StandardCopyOption.ATOMIC_MOVE)
                if (!live.isDirectory) return@runCatching
                recovered++
                discard(work)
            }
        }
        recovered
    }

    private fun writeManifest(work: File, liveName: String, phase: String) {
        FileOutputStream(File(work, MANIFEST)).use { output ->
            output.write("version=1\ntarget=$liveName\nphase=$phase\n".toByteArray())
            output.fd.sync()
        }
    }

    private fun readManifest(work: File): Map<String, String> = runCatching {
        File(work, MANIFEST).readLines().mapNotNull { line ->
            val split = line.indexOf('=')
            if (split > 0) line.substring(0, split) to line.substring(split + 1) else null
        }.toMap()
    }.getOrDefault(emptyMap())

    private data class StagedEntry(val metadata: ZipEntry, val target: File, val selected: Boolean)

    private fun extract(archive: File, root: File): Int {
        val paths = HashSet<File>()
        val expected = HashMap<String, StagedEntry>()
        // ZipInputStream alone can accept a ZIP whose central directory was truncated.
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name.replace('\\', '/')
                check(name.isNotBlank() && !name.startsWith('/') && '\u0000' !in name) {
                    "Invalid save path"
                }
                check(!Regex("^[A-Za-z]:").containsMatchIn(name) && !File(name).isAbsolute) {
                    "Absolute save path"
                }
                check(name.split('/').none { it == ".." }) { "Save path traversal" }
                val target = File(root, name).canonicalFile
                check(target.path.startsWith(root.path + File.separator)) { "Save path escapes staging" }
                check(paths.add(target)) { "Duplicate save path" }
                check(!entry.isDirectory || entry.size == 0L) { "Invalid ZIP directory" }
                expected[entry.name] = StagedEntry(entry, target, !entry.isDirectory && isSaveFile(target.name))
            }
        }
        var count = 0
        ZipInputStream(archive.inputStream().buffered()).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                val staged = expected.remove(entry.name) ?: throw IOException("Unindexed or duplicate ZIP entry")
                check(entry.method == staged.metadata.method) { "Inconsistent ZIP method" }
                if (staged.selected) {
                    val parent = checkNotNull(staged.target.parentFile)
                    check(parent.isDirectory || parent.mkdirs()) { "Cannot create save parent" }
                    check(!staged.target.exists()) { "Conflicting save path" }
                }
                readEntry(input, staged.metadata, if (staged.selected) staged.target else null)
                check(entry.size == staged.metadata.size && entry.crc == staged.metadata.crc) {
                    "Inconsistent ZIP entry metadata"
                }
                input.closeEntry()
                if (staged.selected) count++
                entry = input.nextEntry
            }
        }
        check(expected.isEmpty()) { "Missing ZIP entries" }
        return count
    }

    private fun readEntry(input: InputStream, entry: ZipEntry, target: File?) {
        val crc = CRC32()
        var size = 0L
        target?.let { FileOutputStream(it) }.use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                crc.update(buffer, 0, read)
                size += read
                output?.write(buffer, 0, read)
            }
            check(size == entry.size && crc.value == entry.crc) { "Invalid ZIP size or CRC" }
            output?.fd?.sync()
        }
    }

    private fun copyMissing(source: File, target: File, root: File) {
        check(!Files.isSymbolicLink(source.toPath())) { "Linked existing save" }
        val canonical = source.canonicalFile
        check(canonical == root || canonical.path.startsWith(root.path + File.separator)) {
            "Existing save escapes root"
        }
        if (source.isDirectory) {
            check(target.isDirectory || (!target.exists() && target.mkdir())) { "Save directory conflict" }
            val children = source.listFiles() ?: throw IOException("Cannot read existing saves")
            for (child in children) copyMissing(child, File(target, child.name), root)
        } else {
            check(source.isFile) { "Invalid existing save" }
            if (target.exists()) {
                check(target.isFile) { "Save file conflict" }
            } else {
                source.inputStream().use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                }
                check(target.setLastModified(source.lastModified())) { "Cannot preserve save timestamp" }
            }
        }
    }

    private fun commit(
        live: File,
        staged: File,
        previous: File,
        failed: File,
        moveTree: (File, File) -> Unit
    ) {
        val hadLive = live.exists()
        try {
            if (hadLive) moveTree(live, previous)
            moveTree(staged, live)
            check(live.isDirectory && !exists(staged)) { "Save commit not confirmed" }
        } catch (failure: Throwable) {
            try {
                if (exists(previous)) {
                    if (exists(live)) moveTree(live, failed)
                    moveTree(previous, live)
                } else if (!hadLive && exists(live) && !exists(staged)) {
                    moveTree(live, staged)
                }
            } catch (rollbackFailure: Throwable) {
                failure.addSuppressed(rollbackFailure)
            }
            throw failure
        }
    }

    private fun exists(file: File): Boolean = Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)

    private fun discard(workspace: File) {
        runCatching {
            Files.walkFileTree(workspace.toPath(), object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    if (exc != null) throw exc
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                }
            })
        }
    }
}
