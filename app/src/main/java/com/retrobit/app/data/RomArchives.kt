package com.retrobit.app.data

import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/** One unpackable member of a ROM archive, with the path stored inside the archive. */
data class ArchiveEntry(val name: String, val size: Long, val rawName: String = name)

/**
 * Reads the two archive containers retro collections actually ship in. Scene packs label 7z
 * files as .zip, so the container is decided by its magic bytes instead of the extension.
 */
object RomArchives {

    enum class Kind { Zip, SevenZ }

    private val buffer = 128 * 1024

    fun kindOf(file: File): Kind? = runCatching {
        file.inputStream().use { input ->
            val head = ByteArray(6)
            if (input.read(head) < 6) return null
            when {
                head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() -> Kind.Zip
                head[0] == 0x37.toByte() && head[1] == 0x7A.toByte() -> Kind.SevenZ
                else -> null
            }
        }
    }.getOrNull()

    fun entries(file: File): List<ArchiveEntry> = when (kindOf(file)) {
        Kind.Zip -> runCatching {
            ZipFile(file).use { zip ->
                zip.entries().asSequence()
                    .filterNot { it.isDirectory }
                    .map {
                        ArchiveEntry(
                            it.name.replace('\\', '/'),
                            it.size.coerceAtLeast(0L),
                            it.name
                        )
                    }
                    .toList()
            }
        }.getOrDefault(emptyList())

        Kind.SevenZ -> runCatching {
            SevenZFile(file).use { archive ->
                archive.entries.asSequence()
                    .filterNot { it.isDirectory }
                    .map {
                        ArchiveEntry(
                            it.name.replace('\\', '/'),
                            it.size.coerceAtLeast(0L),
                            it.name
                        )
                    }
                    .toList()
            }
        }.getOrDefault(emptyList())

        null -> emptyList()
    }

    fun extractEntry(source: File, entryName: String, target: File): Boolean =
        when (kindOf(source)) {
            Kind.Zip -> runCatching {
                ZipFile(source).use { zip ->
                    val entry = zip.getEntry(entryName)
                        ?: zip.entries().asSequence().firstOrNull {
                            !it.isDirectory && it.name.replace('\\', '/') == entryName
                        }
                        ?: return@runCatching false
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(target).use { output -> input.copyTo(output, buffer) }
                    }
                }
                true
            }.getOrDefault(false)

            Kind.SevenZ -> runCatching {
                SevenZFile(source).use { archive ->
                    var entry = archive.nextEntry
                    while (entry != null) {
                        if (entry.name.replace('\\', '/') == entryName) {
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { output -> archive.copyTo(output) }
                            return@runCatching true
                        }
                        entry = archive.nextEntry
                    }
                    false
                }
            }.getOrDefault(false)

            null -> false
        }

    /**
     * Unpacks several members in one pass. Collection imports need this because reopening a
     * multi-gigabyte zip once per member makes a ROMS.zip import crawl on TV boxes.
     */
    fun extractEntries(source: File, targets: List<Pair<String, File>>): Set<String> {
        if (targets.isEmpty()) return emptySet()
        val wanted = targets.associate { it.first.replace('\\', '/') to it.second }
        val done = mutableSetOf<String>()
        runCatching {
            when (kindOf(source)) {
                Kind.Zip -> ZipFile(source).use { zip ->
                    zip.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
                        val name = entry.name.replace('\\', '/')
                        val target = wanted[name] ?: return@forEach
                        target.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(target).use { output -> input.copyTo(output, buffer) }
                        }
                        done += name
                    }
                }

                Kind.SevenZ -> SevenZFile(source).use { archive ->
                    var entry = archive.nextEntry
                    while (entry != null) {
                        val name = entry.name.replace('\\', '/')
                        val target = wanted[name]
                        if (target != null && !entry.isDirectory) {
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { output -> archive.copyTo(output) }
                            done += name
                        }
                        entry = archive.nextEntry
                    }
                }

                null -> Unit
            }
        }
        return done
    }

    /** Reads a small text member (cue/gdi) without unpacking the whole archive. */
    fun readEntryText(source: File, entryName: String, maxBytes: Int = 512 * 1024): String? =
        runCatching {
            val bytes = when (kindOf(source)) {
                Kind.Zip -> ZipFile(source).use { zip ->
                    val entry = zip.getEntry(entryName)
                        ?: zip.entries().asSequence().firstOrNull {
                            !it.isDirectory && it.name.replace('\\', '/') == entryName
                        }
                        ?: return null
                    zip.getInputStream(entry).use { it.readAtMost(maxBytes) }
                }

                Kind.SevenZ -> SevenZFile(source).use { archive ->
                    var entry = archive.nextEntry
                    while (entry != null && entry.name.replace('\\', '/') != entryName) {
                        entry = archive.nextEntry
                    }
                    if (entry == null) return null
                    archive.readAtMost(maxBytes)
                }

                null -> return null
            }
            String(bytes, Charsets.UTF_8)
        }.getOrNull()

    private fun java.io.InputStream.readAtMost(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        var remaining = limit
        while (remaining > 0) {
            val read = read(chunk, 0, minOf(chunk.size, remaining))
            if (read <= 0) break
            output.write(chunk, 0, read)
            remaining -= read
        }
        return output.toByteArray()
    }

    private fun SevenZFile.readAtMost(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        var remaining = limit
        while (remaining > 0) {
            val read = read(chunk, 0, minOf(chunk.size, remaining))
            if (read <= 0) break
            output.write(chunk, 0, read)
            remaining -= read
        }
        return output.toByteArray()
    }

    fun extractAll(source: File, destination: File): Boolean = runCatching {
        destination.mkdirs()
        val root = destination.canonicalFile
        when (kindOf(source)) {
            Kind.Zip -> ZipFile(source).use { zip ->
                zip.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
                    val target = File(root, entry.name.replace('\\', '/')).canonicalFile
                    if (!inside(target.path, root.path + File.separator)) return@forEach
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(target).use { output -> input.copyTo(output, buffer) }
                    }
                }
            }

            Kind.SevenZ -> SevenZFile(source).use { archive ->
                var entry = archive.nextEntry
                while (entry != null) {
                    val current = entry
                    val target = File(root, current.name.replace('\\', '/')).canonicalFile
                    if (!current.isDirectory &&
                        inside(target.path, root.path + File.separator)
                    ) {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { output -> archive.copyTo(output) }
                    }
                    entry = archive.nextEntry
                }
            }

            null -> return false
        }
        true
    }.getOrDefault(false)

    private fun SevenZFile.copyTo(output: FileOutputStream) {
        val chunk = ByteArray(buffer)
        var read = read(chunk)
        while (read > 0) {
            output.write(chunk, 0, read)
            read = read(chunk)
        }
    }

    private fun inside(path: String, prefix: String): Boolean = path.startsWith(prefix)
}
