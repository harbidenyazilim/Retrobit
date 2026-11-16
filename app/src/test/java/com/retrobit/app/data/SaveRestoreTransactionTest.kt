package com.retrobit.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SaveRestoreTransactionTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun restoresOnlySaveFilesAndPreservesUnlistedAndLegacyContents() {
        val live = liveSaves()
        val untouched = listOf("legacy.state", "game.srm", "game_slot1.png", "nested/old.state", "legacy.dat")
            .associateWith { File(live, it).readBytes() }
        val input = zip(
            "game.state" to "restored".toByteArray(),
            "nested/new.state" to byteArrayOf(1, 2, 3),
            "legacy..name.state" to byteArrayOf(4),
            "game_auto.STATE" to byteArrayOf(5),
            "game_slot2.png" to byteArrayOf(6),
            "new.srm" to byteArrayOf(7),
            "ROM.nes" to byteArrayOf(8),
            "ROMS.zip" to byteArrayOf(9),
            "cores/core.so" to byteArrayOf(10)
        )

        assertEquals(6, SaveRestoreTransaction.restore(live, input.inputStream()))

        assertEquals("restored", File(live, "game.state").readText())
        untouched.forEach { (name, bytes) -> assertArrayEquals(name, bytes, File(live, name).readBytes()) }
        assertArrayEquals(byteArrayOf(1, 2, 3), File(live, "nested/new.state").readBytes())
        assertTrue(File(live, "legacy..name.state").isFile)
        assertTrue(File(live, "empty-directory").isDirectory)
        assertFalse(File(live, "ROM.nes").exists())
        assertFalse(File(live, "ROMS.zip").exists())
        assertFalse(File(live, "cores").exists())
        assertNoWorkspace()
    }

    @Test
    fun restoresNativeMemoryCardsAndArcadeSaves() {
        val live = liveSaves()
        val names = listOf("pcsx-card2.mcd", "mame/hi/dino.hi", "mame/nvram/dino.nv", "mame/cfg/dino.cfg")
        val bytes = zip(*names.map { it to byteArrayOf(1, 2, 3) }.toTypedArray())
        assertEquals(names.size, SaveRestoreTransaction.restore(live, bytes.inputStream()))
        names.forEach { assertArrayEquals(byteArrayOf(1, 2, 3), File(live, it).readBytes()) }
        assertNoWorkspace()
    }

    @Test
    fun acceptsEmptySaveFilesAndNormalizesBackslashes() {
        val live = liveSaves()
        assertEquals(
            2,
            SaveRestoreTransaction.restore(
                live,
                zip("nested\\new.state" to byteArrayOf(), "empty.png" to byteArrayOf()).inputStream()
            )
        )
        assertTrue(File(live, "nested/new.state").isFile)
        assertEquals(0L, File(live, "nested/new.state").length())
        assertTrue(File(live, "empty.png").isFile)
        assertNoWorkspace()
    }

    @Test
    fun restoresToMissingLiveDirectoryOnlyAfterValidation() {
        val live = File(temp.root, "saves")
        assertEquals(0, SaveRestoreTransaction.restore(live, byteArrayOf().inputStream()))
        assertFalse(live.exists())
        assertNoWorkspace()
        assertEquals(1, SaveRestoreTransaction.restore(live, zip("legacy.state" to byteArrayOf(1)).inputStream()))
        assertArrayEquals(byteArrayOf(1), File(live, "legacy.state").readBytes())
        assertNoWorkspace()
    }

    @Test
    fun emptyDirectoryOnlyAndNonSaveArchivesDoNotChangeLiveTree() {
        val live = liveSaves()
        val before = snapshot(live)
        listOf(
            zip(),
            zip("folder/" to byteArrayOf()),
            zip("ROM.nes" to byteArrayOf(1), "ROMS.zip" to byteArrayOf(2), "cores/core.so" to byteArrayOf(3))
        ).forEach { input ->
            assertEquals(0, SaveRestoreTransaction.restore(live, input.inputStream()))
            assertEquals(before, snapshot(live))
            assertNoWorkspace()
        }
    }

    @Test
    fun invalidAndTruncatedZipNeverTruncateLiveFiles() {
        val live = liveSaves()
        val before = snapshot(live)
        val valid = zip("game.state" to "replacement".toByteArray(), "second.srm" to byteArrayOf(2))
        listOf(
            byteArrayOf(),
            byteArrayOf(0x50, 0x4b),
            "not a zip".toByteArray(),
            valid.copyOf(valid.size - 1),
            valid.copyOf(valid.size - 22),
            valid.copyOf(valid.size / 2)
        ).forEach { input ->
            assertEquals(0, SaveRestoreTransaction.restore(live, input.inputStream()))
            assertEquals(before, snapshot(live))
            assertNoWorkspace()
        }
    }

    @Test
    fun laterEntryCrcFailureDiscardsAllStagedReplacements() {
        val live = liveSaves()
        val before = snapshot(live)
        val payload = "unique corrupt SRAM payload".toByteArray()
        val damaged = corruptPayload(zip("game.state" to byteArrayOf(1), "second.srm" to payload), payload)

        assertEquals(0, SaveRestoreTransaction.restore(live, damaged.inputStream()))

        assertEquals(before, snapshot(live))
        assertNoWorkspace()
    }

    @Test
    fun corruptExcludedEntryStillRejectsWholeArchive() {
        val live = liveSaves()
        val before = snapshot(live)
        val payload = "unique non-save payload".toByteArray()
        val damaged = corruptPayload(zip("game.state" to byteArrayOf(1), "ROM.nes" to payload), payload)
        assertEquals(0, SaveRestoreTransaction.restore(live, damaged.inputStream()))
        assertEquals(before, snapshot(live))
        assertNoWorkspace()
    }

    @Test
    fun damagedLocalCrcIsRejectedEvenWhenCentralCrcAndDataAreValid() {
        val live = liveSaves()
        val before = snapshot(live)
        val bytes = zip("game.state" to byteArrayOf(1), "second.srm" to byteArrayOf(2))
        val name = "second.srm".toByteArray()
        val nameOffset = (0..bytes.size - name.size).first { offset ->
            name.indices.all { bytes[offset + it] == name[it] }
        }
        val crcOffset = nameOffset - 30 + 14
        bytes[crcOffset] = (bytes[crcOffset].toInt() xor 0x01).toByte()

        assertEquals(0, SaveRestoreTransaction.restore(live, bytes.inputStream()))

        assertEquals(before, snapshot(live))
        assertNoWorkspace()
    }

    @Test
    fun deflatedBackupsRestoreCompletely() {
        val live = liveSaves()
        val content = ByteArray(40_000) { (it % 251).toByte() }
        val input = zip("game.state" to content, "second.srm" to byteArrayOf(2), deflated = true)
        assertEquals(2, SaveRestoreTransaction.restore(live, input.inputStream()))
        assertArrayEquals(content, File(live, "game.state").readBytes())
        assertArrayEquals(byteArrayOf(2), File(live, "second.srm").readBytes())
        assertNoWorkspace()
    }

    @Test
    fun laterDeflateReadErrorDoesNotCommitEarlierValidEntry() {
        val live = liveSaves()
        val before = snapshot(live)
        val bytes = zip("game.state" to byteArrayOf(1), "second.srm" to ByteArray(1000) { 3 }, deflated = true)
        val name = "second.srm".toByteArray()
        val nameOffset = (0..bytes.size - name.size).first { offset ->
            name.indices.all { bytes[offset + it] == name[it] }
        }
        val localHeader = nameOffset - 30
        val extraLength = (bytes[localHeader + 28].toInt() and 0xff) or
            ((bytes[localHeader + 29].toInt() and 0xff) shl 8)
        bytes[nameOffset + name.size + extraLength] = 0x07

        assertEquals(0, SaveRestoreTransaction.restore(live, bytes.inputStream()))

        assertEquals(before, snapshot(live))
        assertNoWorkspace()
    }

    @Test
    fun inputReadFailureAndCloseFailureOccurBeforeLiveChanges() {
        val live = liveSaves()
        val before = snapshot(live)
        val bytes = zip("game.state" to "replacement".toByteArray())
        var closed = false
        val failingRead = object : InputStream() {
            private var position = 0

            override fun read(): Int {
                if (position >= bytes.size - 5) throw IOException("Injected input read failure")
                return bytes[position++].toInt() and 0xff
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (length == 0) return 0
                if (position >= bytes.size - 5) throw IOException("Injected input read failure")
                val count = minOf(length, bytes.size - 5 - position)
                bytes.copyInto(buffer, offset, position, position + count)
                position += count
                return count
            }

            override fun close() {
                closed = true
            }
        }
        assertEquals(0, SaveRestoreTransaction.restore(live, failingRead))
        assertTrue(closed)
        assertEquals(before, snapshot(live))
        assertNoWorkspace()

        val failingClose = object : ByteArrayInputStream(bytes) {
            override fun close() {
                throw IOException("Injected input close failure")
            }
        }
        assertEquals(0, SaveRestoreTransaction.restore(live, failingClose))
        assertEquals(before, snapshot(live))
        assertNoWorkspace()
    }

    @Test
    fun unsafePathsRejectEntireArchiveInsteadOfRestoringSafePrefix() {
        val live = liveSaves()
        val before = snapshot(live)
        val sibling = temp.newFolder("saves-sibling")
        File(sibling, "prefix.state").writeText("outside sentinel")
        val outsideBefore = snapshot(sibling)
        listOf(
            "../escape.state", "..\\escape.state", "../saves-sibling/prefix.state",
            "folder/../../escape.state", "/absolute.state", "\\absolute.state",
            "C:\\escape.state", "C:escape.state", "\u0000bad.state", " ", "."
        ).forEach { name ->
            val input = zip("game.state" to byteArrayOf(1), name to byteArrayOf(2))
            assertEquals(name, 0, SaveRestoreTransaction.restore(live, input.inputStream()))
            assertEquals(name, before, snapshot(live))
            assertEquals(outsideBefore, snapshot(sibling))
            assertFalse(File(temp.root, "escape.state").exists())
            assertNoWorkspace()
        }
    }

    @Test
    fun normalizedDuplicatePathsAndFileDirectoryDuplicatesRejectWholeArchive() {
        val live = liveSaves()
        val before = snapshot(live)
        listOf("folder//game.state", "folder/./game.state", "folder\\game.state", "folder/game.state/")
            .forEach { alias ->
                val input = zip("folder/game.state" to byteArrayOf(1), alias to byteArrayOf(2))
                assertEquals(alias, 0, SaveRestoreTransaction.restore(live, input.inputStream()))
                assertEquals(before, snapshot(live))
                assertNoWorkspace()
            }
    }

    @Test
    fun exactDuplicateZipNamesAreRejected() {
        val live = liveSaves()
        val before = snapshot(live)
        val bytes = zip("first.state" to byteArrayOf(1), "other.state" to byteArrayOf(2))
        replaceBytes(bytes, "other.state".toByteArray(), "first.state".toByteArray())
        assertEquals(0, SaveRestoreTransaction.restore(live, bytes.inputStream()))
        assertEquals(before, snapshot(live))
        assertNoWorkspace()
    }

    @Test
    fun liveFileDirectoryConflictsDoNotRemoveExistingContents() {
        val live = liveSaves()
        val before = snapshot(live)
        listOf(
            zip("game.state/new.state" to byteArrayOf(1)),
            zip("nested/old.state/child.state" to byteArrayOf(2))
        ).forEach { bytes ->
            assertEquals(0, SaveRestoreTransaction.restore(live, bytes.inputStream()))
            assertEquals(before, snapshot(live))
            assertNoWorkspace()
        }
        val directory = File(live, "occupied.state").apply { mkdir() }
        File(directory, "child.srm").writeText("keep")
        val withDirectory = snapshot(live)
        assertEquals(0, SaveRestoreTransaction.restore(live, zip("occupied.state" to byteArrayOf(3)).inputStream()))
        assertEquals(withDirectory, snapshot(live))
        assertNoWorkspace()
    }

    @Test
    fun existingSymlinkCannotReadOrOverwriteOutsideSaves() {
        val live = liveSaves()
        val outside = temp.newFolder("outside")
        val sentinel = File(outside, "game.state").apply { writeText("outside sentinel") }
        val link = File(live, "linked")
        runCatching { Files.createSymbolicLink(link.toPath(), outside.toPath()) }
            .exceptionOrNull()?.let { assumeNoException(it) }
        val original = File(live, "game.state").readBytes()

        assertEquals(0, SaveRestoreTransaction.restore(live, zip("linked/game.state" to byteArrayOf(1)).inputStream()))

        assertEquals("outside sentinel", sentinel.readText())
        assertArrayEquals(original, File(live, "game.state").readBytes())
        assertTrue(Files.isSymbolicLink(link.toPath()))
        assertNoWorkspace()
    }

    @Test
    fun completeMergedTreePrecedesFirstMoveAndOldTreeSurvivesUntilCommit() {
        val live = liveSaves()
        val before = snapshot(live)
        var moves = 0
        val workspaces = HashSet<File>()
        repeat(2) {
            val bytes = zip("game.state" to byteArrayOf(1), "new.srm" to byteArrayOf(2))
            assertEquals(2, SaveRestoreTransaction.restore(live, bytes.inputStream()) { source, target ->
                moves++
                val work = if (target.name == "previous") target.parentFile else source.parentFile
                assertEquals(temp.root.canonicalFile, work.parentFile.canonicalFile)
                assertTrue(work.name.startsWith(".saves-restore-"))
                if (target.name == "previous") {
                    assertTrue(workspaces.add(work))
                    if (moves == 1) assertEquals(before, snapshot(live))
                    assertArrayEquals(byteArrayOf(1), File(work, "staged/game.state").readBytes())
                    assertArrayEquals(byteArrayOf(2), File(work, "staged/new.srm").readBytes())
                    assertEquals("legacy", File(work, "staged/legacy.state").readText())
                } else {
                    assertTrue(File(work, "previous").isDirectory)
                    assertFalse(live.exists())
                }
                atomicMove(source, target)
                if (target == live) assertTrue(File(work, "previous").isDirectory)
            })
            assertNoWorkspace()
        }
        assertEquals(4, moves)
        assertEquals(2, workspaces.size)
    }

    @Test
    fun firstMoveFailureLeavesOriginalAndRemovesOnlyOwnStaging() {
        val live = liveSaves()
        val before = snapshot(live)
        val unrelated = temp.newFolder(".saves-restore-unrelated")
        File(unrelated, "keep.state").writeText("keep")
        assertEquals(0, SaveRestoreTransaction.restore(live, zip("game.state" to byteArrayOf(1)).inputStream()) { _, _ ->
            throw IOException("Injected first move failure")
        })
        assertEquals(before, snapshot(live))
        assertEquals("keep", File(unrelated, "keep.state").readText())
        assertEquals(listOf(unrelated.name), workspaceDirs().map { it.name })
    }

    @Test
    fun secondMoveFailureRollsBackEntireOriginalTree() {
        val live = liveSaves()
        val before = snapshot(live)
        var moves = 0
        assertEquals(0, SaveRestoreTransaction.restore(live, zip("game.state" to byteArrayOf(1)).inputStream()) { source, target ->
            moves++
            if (moves == 2) throw IOException("Injected install failure")
            atomicMove(source, target)
        })
        assertEquals(3, moves)
        assertEquals(before, snapshot(live))
        assertNoWorkspace()
    }

    @Test
    fun failureAfterInstallMovesNewTreeAsideBeforeRollingBack() {
        val live = liveSaves()
        val before = snapshot(live)
        var moves = 0
        assertEquals(0, SaveRestoreTransaction.restore(live, zip("game.state" to byteArrayOf(1)).inputStream()) { source, target ->
            moves++
            atomicMove(source, target)
            if (moves == 2) throw IOException("Injected post-install failure")
        })
        assertEquals(4, moves)
        assertEquals(before, snapshot(live))
        assertNoWorkspace()
    }

    @Test
    fun unconfirmedInstallRollsBackBeforeDeletingOriginal() {
        val live = liveSaves()
        val before = snapshot(live)
        var moves = 0
        assertEquals(0, SaveRestoreTransaction.restore(live, zip("game.state" to byteArrayOf(1)).inputStream()) { source, target ->
            moves++
            if (moves != 2) atomicMove(source, target)
        })
        assertEquals(3, moves)
        assertEquals(before, snapshot(live))
        assertNoWorkspace()
    }

    @Test
    fun rollbackFailureRetainsOriginalTreeAndLaterRestoreDoesNotCleanIt() {
        val live = liveSaves()
        val before = snapshot(live)
        var moves = 0
        val input = zip("game.state" to byteArrayOf(1))
        assertEquals(0, SaveRestoreTransaction.restore(live, input.inputStream()) { source, target ->
            moves++
            if (moves >= 2) throw IOException("Injected install and rollback failure")
            atomicMove(source, target)
        })
        assertEquals(3, moves)
        assertFalse(live.exists())
        val retained = workspaceDirs().single()
        assertEquals(before, snapshot(File(retained, "previous")))
        assertEquals(1, SaveRestoreTransaction.restore(live, input.inputStream()))
        assertEquals(before, snapshot(File(retained, "previous")))
        assertEquals(listOf(retained), workspaceDirs())
    }

    @Test
    fun failedCommitWithoutOriginalRestoresDirectoryAbsence() {
        val live = File(temp.root, "saves")
        var moves = 0
        assertEquals(0, SaveRestoreTransaction.restore(live, zip("game.state" to byteArrayOf(1)).inputStream()) { source, target ->
            moves++
            atomicMove(source, target)
            if (moves == 1) throw IOException("Injected post-install failure")
        })
        assertEquals(2, moves)
        assertFalse(live.exists())
        assertNoWorkspace()
    }

    @Test
    fun interruptedCommitRollsBackThePreviousTreeAtStartup() {
        val before = mapOf("game.state" to "original", "nested/old.state" to "old nested")
        val work = workspace(phase = "committing", target = "saves")
        before.forEach { (name, content) ->
            File(work, "previous/$name").apply { parentFile!!.mkdirs(); writeText(content) }
        }
        File(work, "staged/new.state").apply { parentFile!!.mkdirs(); writeText("staged") }

        assertEquals(1, SaveRestoreTransaction.recoverInterruptedRestore(File(temp.root, "saves")))

        val live = File(temp.root, "saves")
        assertEquals("original", File(live, "game.state").readText())
        assertEquals("old nested", File(live, "nested/old.state").readText())
        assertNoWorkspace()
    }

    @Test
    fun finishedCommitClearsLeftoverWorkspaceWithoutTouchingLive() {
        val live = File(temp.root, "saves").apply { mkdirs() }
        File(live, "game.state").writeText("restored")
        val work = workspace(phase = "committing", target = "saves")
        File(work, "previous/game.state").apply { parentFile!!.mkdirs(); writeText("old") }

        assertEquals(0, SaveRestoreTransaction.recoverInterruptedRestore(live))

        assertEquals("restored", File(live, "game.state").readText())
        assertNoWorkspace()
    }

    @Test
    fun ambiguousWorkspaceKeepsPreviousInsteadOfGuessing() {
        val live = File(temp.root, "saves").apply { mkdirs() }
        File(live, "game.state").writeText("live")
        val work = workspace(phase = "committing", target = "saves")
        File(work, "previous/game.state").apply { parentFile!!.mkdirs(); writeText("old") }
        File(work, "staged/game.state").apply { parentFile!!.mkdirs(); writeText("staged") }

        assertEquals(0, SaveRestoreTransaction.recoverInterruptedRestore(live))

        assertEquals("live", File(live, "game.state").readText())
        assertEquals("old", File(work, "previous/game.state").readText())
        assertTrue(work.isDirectory)
    }

    @Test
    fun workspaceForAnotherTargetAndStagingOnlyWorkspaceAreHandledSafely() {
        val live = File(temp.root, "saves").apply { mkdirs() }
        val foreign = workspace(phase = "committing", target = "other_saves")
        File(foreign, "previous/game.state").apply { parentFile!!.mkdirs(); writeText("old") }
        val stagingOnly = workspace(phase = "staging", target = "saves")
        File(stagingOnly, "staged/game.state").apply { parentFile!!.mkdirs(); writeText("staged") }

        assertEquals(0, SaveRestoreTransaction.recoverInterruptedRestore(live))

        assertTrue(foreign.isDirectory)
        assertEquals("old", File(foreign, "previous/game.state").readText())
        assertFalse(stagingOnly.exists())
    }

    private fun workspace(phase: String, target: String): File {
        val dir = File(temp.root, ".saves-restore-${System.nanoTime()}")
        assertTrue(dir.mkdir())
        File(dir, "manifest.txt").writeText("version=1\ntarget=$target\nphase=$phase\n")
        return dir
    }

    private fun liveSaves(): File = temp.newFolder("saves").apply {
        mapOf(
            "game.state" to "original", "legacy.state" to "legacy", "game.srm" to "SRAM",
            "game_slot1.png" to "thumbnail", "nested/old.state" to "old nested", "legacy.dat" to "keep legacy data"
        ).forEach { (name, content) ->
            File(this, name).apply {
                parentFile!!.mkdirs()
                writeText(content)
            }
        }
        File(this, "empty-directory").mkdir()
    }

    private fun zip(vararg members: Pair<String, ByteArray>, deflated: Boolean = false): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { output ->
            members.forEach { (name, data) ->
                val content = if (name.endsWith('/')) byteArrayOf() else data
                val entry = ZipEntry(name).apply {
                    method = if (deflated) ZipEntry.DEFLATED else ZipEntry.STORED
                    size = content.size.toLong()
                    if (!deflated) compressedSize = size
                    crc = CRC32().apply { update(content) }.value
                }
                output.putNextEntry(entry)
                output.write(content)
                output.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun corruptPayload(bytes: ByteArray, payload: ByteArray): ByteArray {
        for (offset in 0..bytes.size - payload.size) {
            if (payload.indices.all { bytes[offset + it] == payload[it] }) {
                bytes[offset] = (bytes[offset].toInt() xor 0x01).toByte()
                return bytes
            }
        }
        throw AssertionError("Missing fixture payload")
    }

    private fun replaceBytes(bytes: ByteArray, from: ByteArray, to: ByteArray) {
        assertEquals(from.size, to.size)
        var replacements = 0
        for (offset in 0..bytes.size - from.size) {
            if (from.indices.all { bytes[offset + it] == from[it] }) {
                to.copyInto(bytes, offset)
                replacements++
            }
        }
        assertEquals(2, replacements)
    }

    private fun snapshot(root: File): Map<String, List<Byte>?> = root.walkTopDown().associate {
        it.relativeTo(root).path.replace('\\', '/') to if (it.isFile) it.readBytes().toList() else null
    }

    private fun workspaceDirs(): List<File> = temp.root.listFiles()!!
        .filter { it.name.startsWith(".saves-restore-") }.sortedBy { it.name }

    private fun assertNoWorkspace() {
        assertTrue(workspaceDirs().isEmpty())
    }

    private fun atomicMove(source: File, target: File) {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }
}
