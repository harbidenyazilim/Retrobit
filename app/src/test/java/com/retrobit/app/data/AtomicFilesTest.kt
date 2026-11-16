package com.retrobit.app.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class AtomicFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun replacesCompletedFile() {
        val target = temporary.newFile("slot.state").apply { writeText("old") }
        AtomicFiles.write(target) { it.write("new state".toByteArray()) }
        assertEquals("new state", target.readText())
        assertEquals(listOf("slot.state"), temporary.root.list()!!.toList())
    }

    @Test fun failedWritePreservesExistingFile() {
        val target = temporary.newFile("slot.state").apply { writeText("old state") }
        assertThrows(IOException::class.java) {
            AtomicFiles.write(target) {
                it.write("partial".toByteArray())
                throw IOException("Interrupted")
            }
        }
        assertEquals("old state", target.readText())
        assertEquals(listOf("slot.state"), temporary.root.list()!!.toList())
    }

    @Test fun failedNewWriteLeavesNoFile() {
        val target = File(temporary.root, "new.state")
        assertThrows(IOException::class.java) {
            AtomicFiles.write(target) { throw IOException("No space") }
        }
        assertFalse(target.exists())
        assertTrue(temporary.root.list()!!.isEmpty())
    }

    @Test fun failedPublishPreservesDestinationDirectory() {
        val target = temporary.newFolder("slot.state")
        File(target, "existing").writeText("keep")
        assertThrows(IOException::class.java) {
            AtomicFiles.write(target) { it.write(byteArrayOf(1)) }
        }
        assertEquals("keep", File(target, "existing").readText())
        assertEquals(listOf("slot.state"), temporary.root.list()!!.toList())
    }

    @Test fun createsMissingParent() {
        val target = File(temporary.root, "nested/slot.state")
        AtomicFiles.write(target) { it.write(byteArrayOf(1, 2, 3)) }
        assertArrayEquals(byteArrayOf(1, 2, 3), target.readBytes())
    }
}
