package com.retrobit.app.data

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Calls only the top-level JVM file/regex helper, never the Android RomImporter class. */
class RomSizeOnDiskTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun missingFileReturnsMinusOne() {
        assertEquals(-1L, romSizeOnDisk(File(temp.root, "missing.bin").path))
    }

    @Test
    fun directoryReturnsMinusOneEvenWithSheetExtension() {
        val directory = temp.newFolder("directory.cue")
        assertEquals(-1L, romSizeOnDisk(directory.path))
    }

    @Test
    fun regularAndEmptyFilesReturnTheirOwnLength() {
        val regular = binary("game.bin", 37)
        val empty = binary("empty.chd", 0)
        assertEquals(37L, romSizeOnDisk(regular.path))
        assertEquals(0L, romSizeOnDisk(empty.path))
    }

    @Test
    fun archiveSizeIsContainerLengthNotUncompressedEntrySize() {
        val archive = temp.newFile("game.zip")
        ZipOutputStream(archive.outputStream()).use { output ->
            output.putNextEntry(ZipEntry("game.bin"))
            output.write(ByteArray(4096) { 7 })
            output.closeEntry()
        }
        assertEquals(archive.length(), romSizeOnDisk(archive.path))
    }

    @Test
    fun cueAddsQuotedAndUnquotedTracksWithCaseInsensitiveFileKeywordAndExtension() {
        val first = binary("track one.bin", 11)
        val second = binary("track02.bin", 23)
        val cue = sheet(
            "disc.CUE",
            "REM fixture\n  file \"track one.bin\" BINARY\n    TRACK 01 MODE1/2352\n" +
                "FiLe track02.bin BINARY\n    TRACK 02 AUDIO\n"
        )
        assertEquals(cue.length() + first.length() + second.length(), romSizeOnDisk(cue.path))
    }

    @Test
    fun cueCountsRepeatedTrackReferenceOnlyOnce() {
        val track = binary("track.bin", 31)
        val cue = sheet(
            "disc.cue",
            "FILE \"track.bin\" BINARY\nTRACK 01 AUDIO\n" +
                "FILE track.bin BINARY\nTRACK 02 AUDIO\n"
        )
        assertEquals(cue.length() + track.length(), romSizeOnDisk(cue.path))
    }

    @Test
    fun cueIgnoresMissingReferencesAndDirectories() {
        val track = binary("present.bin", 7)
        temp.newFolder("not-a-track.bin")
        val cue = sheet(
            "disc.cue",
            "FILE present.bin BINARY\nFILE missing.bin BINARY\nFILE not-a-track.bin BINARY\n"
        )
        assertEquals(cue.length() + track.length(), romSizeOnDisk(cue.path))
    }

    @Test
    fun cueResolvesRelativeSubdirectoriesAndBackslashes() {
        val track = binary("tracks/track one.bin", 19)
        val cue = sheet("disc.cue", "FILE \"tracks\\track one.bin\" BINARY\n")
        assertEquals(cue.length() + track.length(), romSizeOnDisk(cue.path))
    }

    @Test
    fun sheetsWithoutReferencesReturnTheirOwnLength() {
        listOf(
            "empty.cue" to "",
            "no-refs.cue" to "REM no tracks\nTRACK 01 AUDIO\n",
            "no-refs.gdi" to "0\nnot a track row\n",
            "no-refs.m3u" to "# playlist\n\n  # no discs\n"
        ).forEach { (name, text) ->
            val file = sheet(name, text)
            assertEquals(name, file.length(), romSizeOnDisk(file.path))
        }
    }

    @Test
    fun gdiAddsQuotedAndUnquotedTracksButNotTheCountOrOffsets() {
        val first = binary("track 01.bin", 17)
        val second = binary("track02.raw", 29)
        val gdi = sheet(
            "disc.GDI",
            "2\n  1 0 4 2352 \"track 01.bin\" 0\n2 45000 0 2352 track02.raw 1234\n"
        )
        assertEquals(gdi.length() + first.length() + second.length(), romSizeOnDisk(gdi.path))
    }

    @Test
    fun gdiCountsDuplicateTracksOnceAndIgnoresMissingTracks() {
        val track = binary("track.bin", 13)
        val gdi = sheet(
            "disc.gdi",
            "3\n1 0 4 2352 track.bin 0\n2 10 4 2352 \"track.bin\" 0\n" +
                "3 20 0 2352 missing.raw 0\n"
        )
        assertEquals(gdi.length() + track.length(), romSizeOnDisk(gdi.path))
    }

    @Test
    fun m3uTrimsLinesSkipsCommentsAndBlanksAndDeduplicatesDiscs() {
        val first = binary("disc one.chd", 41)
        val second = binary("disc02.chd", 43)
        val playlist = sheet(
            "collection.M3U",
            "#EXTM3U\n\n  disc one.chd  \n  # comment\n disc02.chd\n" +
                "disc one.chd\nmissing.chd\n"
        )
        assertEquals(
            playlist.length() + first.length() + second.length(),
            romSizeOnDisk(playlist.path)
        )
    }

    @Test
    fun m3uExpandsCueDiscsRelativeToEachDiscDirectory() {
        val firstTrack = binary("disc1/track.bin", 47)
        val secondTrack = binary("disc2/track.bin", 53)
        val firstCue = sheet("disc1/disc.cue", "FILE track.bin BINARY\n")
        val secondCue = sheet("disc2/disc.cue", "FILE track.bin BINARY\n")
        val playlist = sheet("collection.m3u", "disc1/disc.cue\ndisc2\\disc.cue\n")
        assertEquals(
            playlist.length() + firstCue.length() + secondCue.length() +
                firstTrack.length() + secondTrack.length(),
            romSizeOnDisk(playlist.path)
        )
    }

    @Test
    fun nestedSheetsStopExpandingAfterDepthOne() {
        binary("track.bin", 101)
        val cue = sheet("disc.cue", "FILE track.bin BINARY\n")
        val inner = sheet("inner.m3u", "disc.cue\n")
        val outer = sheet("outer.m3u", "inner.m3u\n")
        assertEquals(outer.length() + inner.length() + cue.length(), romSizeOnDisk(outer.path))
    }

    @Test
    fun explicitDepthOneExpandsReferencesButDepthTwoUsesOnlyFileLength() {
        val track = binary("track.bin", 59)
        val cue = sheet("disc.cue", "FILE track.bin BINARY\n")
        assertEquals(cue.length() + track.length(), romSizeOnDisk(cue.path, depth = 1))
        assertEquals(cue.length(), romSizeOnDisk(cue.path, depth = 2))
        assertEquals(cue.length(), romSizeOnDisk(cue.path, depth = 10))
        assertEquals(-1L, romSizeOnDisk(File(temp.root, "missing.cue").path, depth = 2))
    }

    @Test
    fun cyclicPlaylistsTerminateAtTheDepthLimit() {
        val first = sheet("first.m3u", "second.m3u\n")
        val second = sheet("second.m3u", "first.m3u\n")
        assertEquals(first.length() * 2 + second.length(), romSizeOnDisk(first.path))
    }

    @Test
    fun cueSkipsReferencesOutsideItsDirectoryWithANonMatchingPrefix() {
        val local = binary("disc/local.bin", 61)
        binary("outside/track.bin", 67)
        val cue = sheet(
            "disc/game.cue",
            "FILE local.bin BINARY\nFILE \"../outside/track.bin\" BINARY\n"
        )
        assertEquals(cue.length() + local.length(), romSizeOnDisk(cue.path))
    }

    @Test
    fun cueRejectsSiblingDirectorySharingTheParentPrefix() {
        val local = binary("disc/local.bin", 61)
        binary("disc-other/track.bin", 67)
        val cue = sheet("disc/game.cue", "FILE local.bin BINARY\nFILE \"../disc-other/track.bin\" BINARY\n")
        assertEquals(cue.length() + local.length(), romSizeOnDisk(cue.path))
    }

    @Test
    fun sharedTracksAreCountedPerDistinctDiscRatherThanGloballyDeduplicated() {
        val track = binary("track.bin", 71)
        val firstCue = sheet("first.cue", "FILE track.bin BINARY\n")
        val secondCue = sheet("second.cue", "FILE track.bin BINARY\n")
        val playlist = sheet("collection.m3u", "first.cue\nsecond.cue\nfirst.cue\n")
        assertEquals(
            playlist.length() + firstCue.length() + secondCue.length() + track.length() * 2,
            romSizeOnDisk(playlist.path)
        )
    }

    private fun binary(name: String, size: Int): File = fixture(name).apply {
        writeBytes(ByteArray(size) { (it % 256).toByte() })
    }

    private fun sheet(name: String, text: String): File = fixture(name).apply {
        writeText(text, Charsets.UTF_8)
    }

    private fun fixture(name: String): File = File(temp.root, name).apply {
        parentFile!!.mkdirs()
    }
}
