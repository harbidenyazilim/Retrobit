package com.retrobit.app.ui.player

import com.retrobit.app.data.StartupRestoreGate
import com.retrobit.app.data.scanStartupProgress
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StartupProgressTest {
    @get:Rule val folder = TemporaryFolder()

    private fun save(name: String, timestamp: Long, bytes: ByteArray = byteArrayOf(1, 2, 3)): File =
        File(folder.root, name).apply { writeBytes(bytes); assertTrue(setLastModified(timestamp)) }

    @Test fun newestSnapshotIncludesLegacySlotAndAllManualSlotsWithoutDeletingAnything() {
        save("g_auto.state", 1000)
        save("g_slot1.state", 2000)
        val legacy = save("g.state", 3000)
        save("other_slot5.state", 9000)
        save("g_slot0.state", 9500)
        save("g_slot1.png", 10000)
        val before = folder.root.listFiles()!!.associate { it.name to it.readBytes().toList() }
        assertEquals(legacy.absolutePath, scanStartupProgress(folder.root, "g").snapshot!!.path)
        val manual = save("g_slot5.state", 4000)
        assertEquals(manual.absolutePath, scanStartupProgress(folder.root, "g").snapshot!!.path)
        before.forEach { (name, bytes) -> assertEquals(bytes, File(folder.root, name).readBytes().toList()) }
    }

    @Test fun emptyFilesDoNotEnableResumeButBatteryOnlyDoes() {
        save("g_auto.state", 1000, byteArrayOf())
        save("g.srm", 1000, byteArrayOf())
        assertFalse(scanStartupProgress(folder.root, "g").canResume)
        save("g.srm", 2000)
        val progress = scanStartupProgress(folder.root, "g")
        assertTrue(progress.canResume)
        assertNull(progress.snapshot)
        assertNotNull(progress.sram)
    }

    @Test fun unavailableInventoryIsNotTreatedAsAnEmptySaveDirectory() {
        val notDirectory = save("inventory", 1000)
        assertTrue(runCatching { scanStartupProgress(notDirectory, "g") }.isFailure)
        assertArrayEquals(byteArrayOf(1, 2, 3), notDirectory.readBytes())
        assertFalse(scanStartupProgress(File(folder.root, "missing"), "g").canResume)
    }

    @Test fun recoveryCanBeNewestEvenWithManualSavesPresent() {
        save("g_slot1.state", 1000)
        val auto = save("g_auto.state", 2000)
        assertEquals(auto.absolutePath, scanStartupProgress(folder.root, "g").snapshot!!.path)
    }

    @Test fun candidateIsRevalidatedAfterSelectionInsteadOfSilentlyUsingAnotherFile() {
        val file = save("g_auto.state", 1000)
        val candidate = scanStartupProgress(folder.root, "g").snapshot!!
        assertArrayEquals(byteArrayOf(1, 2, 3), candidate.read())
        file.writeBytes(byteArrayOf(4))
        assertTrue(runCatching { candidate.read() }.isFailure)
        assertTrue(file.delete())
        assertTrue(runCatching { candidate.read() }.isFailure)
    }

    @Test fun startupGateBlocksCancelFailureAndDuplicateReadinessEvents() {
        val gate = StartupRestoreGate()
        assertFalse(gate.writesAllowed) // Cancel before native creation does nothing.
        assertTrue(gate.begin())
        assertFalse(gate.begin()) // SurfaceCreated then FrameRendered must not restore twice.
        gate.complete(false)
        assertFalse(gate.writesAllowed)
        assertFalse(gate.begin()) // Resume / another frame cannot retry a corrupt restore.
        val explicitlyChosenFreshSurface = StartupRestoreGate()
        assertTrue(explicitlyChosenFreshSurface.begin())
        explicitlyChosenFreshSurface.complete(true)
        assertTrue(explicitlyChosenFreshSurface.writesAllowed)
    }
}
