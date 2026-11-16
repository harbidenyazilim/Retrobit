package com.retrobit.app.data

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** All containers, extraction targets and traversal probes stay under this test's temp root. */
class RomArchivesTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun kindOfDetectsRealContainersRegardlessOfExtension() {
        val members = mapOf("game.bin" to byteArrayOf(1, 2, 3))
        val zip = archive(RomArchives.Kind.Zip, members, "zip-content.7z")
        val sevenZ = archive(RomArchives.Kind.SevenZ, members, "seven-z-content.zip")
        assertEquals(RomArchives.Kind.Zip, RomArchives.kindOf(zip))
        assertEquals(RomArchives.Kind.SevenZ, RomArchives.kindOf(sevenZ))
        assertEquals(listOf(ArchiveEntry("game.bin", 3L)), RomArchives.entries(zip))
        assertEquals(listOf(ArchiveEntry("game.bin", 3L)), RomArchives.entries(sevenZ))
    }

    @Test
    fun kindOfReturnsNullForMissingDirectoryUnknownAndShortFiles() {
        val missing = File(temp.root, "missing.zip")
        val directory = temp.newFolder("directory.zip")
        val empty = temp.newFile("empty.zip")
        val short = temp.newFile("short.zip").apply { writeBytes(byteArrayOf(0x50, 0x4B)) }
        val unknown = temp.newFile("unknown.7z").apply { writeText("not an archive") }
        listOf(missing, directory, empty, short, unknown).forEach { source ->
            assertNull(source.name, RomArchives.kindOf(source))
        }
    }

    @Test
    fun entriesExcludeDirectoriesAndReportUncompressedSizesIncludingEmptyFiles() {
        withArchives(
            linkedMapOf(
                "folder/" to byteArrayOf(),
                "folder/game.bin" to ByteArray(1000) { 7 },
                "empty.bin" to byteArrayOf()
            )
        ) { source ->
            assertEquals(
                listOf(ArchiveEntry("folder/game.bin", 1000L), ArchiveEntry("empty.bin", 0L)),
                RomArchives.entries(source)
            )
        }
    }

    @Test
    fun entriesNormalizeBackslashesButPreserveRawNames() {
        withArchives(mapOf("folder\\game.bin" to byteArrayOf(1, 2))) { source ->
            assertEquals(
                listOf(ArchiveEntry("folder/game.bin", 2L, "folder\\game.bin")),
                RomArchives.entries(source)
            )
        }
    }

    @Test
    fun entriesReturnEmptyForInvalidOrDamagedContainers() {
        invalidArchives().forEach { source ->
            assertTrue(source.name, RomArchives.entries(source).isEmpty())
        }
    }

    @Test
    fun extractEntryResolvesNormalizedNamesAndCreatesTargetParents() {
        val content = byteArrayOf(0, 1, 2, 127, -1)
        withArchives(mapOf("folder\\game.bin" to content)) { source ->
            val target = File(temp.root, "${source.name}-single/nested/game.bin")
            assertFalse(target.parentFile!!.exists())
            assertTrue(source.name, RomArchives.extractEntry(source, "folder/game.bin", target))
            assertArrayEquals(content, target.readBytes())
        }
    }

    @Test
    fun extractEntryAcceptsExactRawZipName() {
        val content = byteArrayOf(9, 8, 7)
        val source = archive(RomArchives.Kind.Zip, mapOf("folder\\game.bin" to content))
        val target = File(temp.root, "raw-target.bin")
        assertTrue(RomArchives.extractEntry(source, "folder\\game.bin", target))
        assertArrayEquals(content, target.readBytes())
    }

    @Test
    fun extractEntryMissingMemberReturnsFalseWithoutCreatingTarget() {
        withArchives(mapOf("game.bin" to byteArrayOf(1))) { source ->
            val target = File(temp.root, "${source.name}-missing/game.bin")
            assertFalse(RomArchives.extractEntry(source, "missing.bin", target))
            assertFalse(target.exists())
            assertFalse(target.parentFile!!.exists())
        }
    }

    @Test
    fun extractEntryInvalidSourceReturnsFalseWithoutCreatingTarget() {
        invalidArchives().forEach { source ->
            val target = File(temp.root, "${source.name}-invalid-single/game.bin")
            assertFalse(source.name, RomArchives.extractEntry(source, "game.bin", target))
            assertFalse(target.exists())
        }
    }

    @Test
    fun extractEntriesSelectsMembersNormalizesRequestsAndSkipsDirectories() {
        val game = byteArrayOf(1, 2, 3)
        val track = byteArrayOf(4, 5)
        withArchives(
            linkedMapOf(
                "folder/" to byteArrayOf(),
                "folder\\game.bin" to game,
                "folder/track.bin" to track,
                "unselected.bin" to byteArrayOf(6)
            )
        ) { source ->
            val root = File(temp.root, "${source.name}-batch")
            val gameTarget = File(root, "nested/game.bin")
            val trackTarget = File(root, "nested/track.bin")
            val missingTarget = File(root, "missing.bin")
            val directoryTarget = File(root, "directory-target")
            val done = RomArchives.extractEntries(
                source,
                listOf(
                    "folder/game.bin" to gameTarget,
                    "folder\\track.bin" to trackTarget,
                    "missing.bin" to missingTarget,
                    "folder/" to directoryTarget
                )
            )
            assertEquals(setOf("folder/game.bin", "folder/track.bin"), done)
            assertArrayEquals(game, gameTarget.readBytes())
            assertArrayEquals(track, trackTarget.readBytes())
            assertFalse(missingTarget.exists())
            assertFalse(directoryTarget.exists())
            assertFalse(File(root, "unselected.bin").exists())
        }
    }

    @Test
    fun extractEntriesEmptyTargetsReturnEmptyEvenForMissingSource() {
        assertTrue(
            RomArchives.extractEntries(File(temp.root, "missing.zip"), emptyList()).isEmpty()
        )
        assertTrue(temp.root.listFiles()!!.isEmpty())
    }

    @Test
    fun extractEntriesInvalidSourceReturnsEmptyWithoutCreatingTargets() {
        invalidArchives().forEach { source ->
            val target = File(temp.root, "${source.name}-invalid-batch/game.bin")
            assertTrue(
                source.name,
                RomArchives.extractEntries(source, listOf("game.bin" to target)).isEmpty()
            )
            assertFalse(target.exists())
        }
    }

    @Test
    fun readEntryTextDecodesUtf8AndResolvesNormalizedNames() {
        val text = "FILE \"Çılgın Oyun.bin\" BINARY\n"
        withArchives(mapOf("folder\\disc.cue" to text.toByteArray(Charsets.UTF_8))) { source ->
            assertEquals(text, RomArchives.readEntryText(source, "folder/disc.cue"))
        }
    }

    @Test
    fun readEntryTextReturnsNullForMissingMembersAndInvalidSources() {
        withArchives(mapOf("disc.cue" to byteArrayOf(65))) { source ->
            assertNull(RomArchives.readEntryText(source, "missing.cue"))
        }
        invalidArchives().forEach { source ->
            assertNull(source.name, RomArchives.readEntryText(source, "disc.cue"))
        }
    }

    @Test
    fun readEntryTextHonorsByteLimitAcrossMultipleReadChunks() {
        val content = ByteArray(40_000) { (65 + it % 26).toByte() }
        withArchives(mapOf("disc.cue" to content)) { source ->
            listOf(1, 16 * 1024, 20_000, content.size, content.size + 1).forEach { limit ->
                assertEquals(
                    "${source.name} limit=$limit",
                    String(content.copyOfRange(0, minOf(limit, content.size)), Charsets.UTF_8),
                    RomArchives.readEntryText(source, "disc.cue", limit)
                )
            }
        }
    }

    @Test
    fun readEntryTextZeroLimitReturnsEmptyTextForExistingMember() {
        withArchives(mapOf("disc.cue" to "FILE track.bin BINARY".toByteArray())) { source ->
            assertEquals("", RomArchives.readEntryText(source, "disc.cue", 0))
        }
    }

    @Test
    fun readEntryTextDefaultLimitIs512KiB() {
        val limit = 512 * 1024
        withArchives(mapOf("disc.cue" to ByteArray(limit + 17) { 65 })) { source ->
            assertEquals("A".repeat(limit), RomArchives.readEntryText(source, "disc.cue"))
        }
    }

    @Test
    fun extractAllWritesNestedAndEmptyFilesAndNormalizesBackslashes() {
        val content = byteArrayOf(3, 2, 1)
        withArchives(
            linkedMapOf(
                "folder/" to byteArrayOf(),
                "folder\\game.bin" to content,
                "empty.bin" to byteArrayOf()
            )
        ) { source ->
            val destination = File(temp.root, "${source.name}-all")
            assertTrue(RomArchives.extractAll(source, destination))
            assertArrayEquals(content, File(destination, "folder/game.bin").readBytes())
            val empty = File(destination, "empty.bin")
            assertTrue(empty.isFile)
            assertEquals(0L, empty.length())
        }
    }

    @Test
    fun extractAllRejectsTraversalIncludingBackslashesAndSiblingPrefix() {
        withArchives(
            linkedMapOf(
                "safe/game.bin" to byteArrayOf(1),
                "../escaped.bin" to byteArrayOf(2),
                "..\\backslash.bin" to byteArrayOf(3),
                "../out-sibling/prefix.bin" to byteArrayOf(4)
            )
        ) { source ->
            val sandbox = temp.newFolder("${source.name}-sandbox")
            val destination = File(sandbox, "out")
            assertTrue(RomArchives.extractAll(source, destination))
            assertArrayEquals(byteArrayOf(1), File(destination, "safe/game.bin").readBytes())
            assertFalse(File(sandbox, "escaped.bin").exists())
            assertFalse(File(sandbox, "backslash.bin").exists())
            assertFalse(File(sandbox, "out-sibling/prefix.bin").exists())
        }
    }

    @Test
    fun launcherPackagesPreserveCompanionFoldersAndBytes() {
        val files = linkedMapOf(
            "scummvm/test/test.scummvm" to "test-game".toByteArray(),
            "scummvm/test/data/resource.000" to byteArrayOf(2, 3, 4),
            "scummvm/test/data/archive.zip" to byteArrayOf(5, 6),
            "amiga/test/test.uae" to "floppy0=disk.adf".toByteArray(),
            "amiga/test/disk.adf" to byteArrayOf(7, 8, 9)
        )
        withArchives(files) { source ->
            val destination = File(temp.root, "${source.name}-launchers")
            assertTrue(RomArchives.extractAll(source, destination))
            files.forEach { (name, content) ->
                assertArrayEquals(content, File(destination, name).readBytes())
            }
        }
    }

    @Test
    fun extractAllReturnsFalseForInvalidSources() {
        invalidArchives().forEach { source ->
            val destination = File(temp.root, "${source.name}-invalid-all")
            assertFalse(source.name, RomArchives.extractAll(source, destination))
            assertTrue(destination.listFiles()?.isEmpty() == true)
        }
    }

    private fun withArchives(members: Map<String, ByteArray>, block: (File) -> Unit) {
        RomArchives.Kind.values().forEach { kind -> block(archive(kind, members)) }
    }

    private fun archive(
        kind: RomArchives.Kind,
        members: Map<String, ByteArray>,
        fileName: String = "fixture-${kind.name}.archive"
    ): File {
        val file = temp.newFile(fileName)
        when (kind) {
            RomArchives.Kind.Zip -> ZipOutputStream(file.outputStream()).use { output ->
                members.forEach { (name, bytes) ->
                    output.putNextEntry(ZipEntry(name))
                    if (!name.endsWith('/')) output.write(bytes)
                    output.closeEntry()
                }
            }
            RomArchives.Kind.SevenZ -> SevenZOutputFile(file).use { output ->
                // COPY keeps tiny fixtures deterministic and needs no native compressor.
                output.setContentCompression(SevenZMethod.COPY)
                members.forEach { (memberName, bytes) ->
                    val entry = SevenZArchiveEntry().apply {
                        name = memberName
                        isDirectory = memberName.endsWith('/')
                        size = bytes.size.toLong()
                    }
                    output.putArchiveEntry(entry)
                    if (!entry.isDirectory) output.write(bytes)
                    output.closeArchiveEntry()
                }
            }
        }
        return file
    }

    private fun invalidArchives(): List<File> = listOf(
        File(temp.root, "missing.archive"),
        temp.newFolder("directory.archive"),
        temp.newFile("empty.archive"),
        temp.newFile("short.archive").apply { writeBytes(byteArrayOf(0x50, 0x4B)) },
        temp.newFile("unknown.archive").apply { writeText("not an archive") },
        temp.newFile("damaged-zip.archive").apply {
            writeBytes(byteArrayOf(0x50, 0x4B, 0, 0, 0, 0))
        },
        temp.newFile("damaged-seven-z.archive").apply {
            writeBytes(byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C))
        }
    )
}
