package com.retrobit.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ImportTitleTest {
    @Test
    fun stripsTrackNumbers() {
        assertEquals("Contra", ImportTitle.clean("01 - Contra (U) [!]"))
        assertEquals("Contra", ImportTitle.clean("01- Contra"))
        assertEquals("Contra", ImportTitle.clean("01.Contra"))
        assertEquals("Super Mario Bros", ImportTitle.clean("Super_Mario_Bros (World)"))
    }

    @Test
    fun keepsNumbersThatArePartOfTheName() {
        assertEquals("1200-in-1", ImportTitle.clean("1200-in-1 (J) [p1]"))
        assertEquals("10-Yard Fight", ImportTitle.clean("10-Yard Fight (USA)"))
        assertEquals("3-D WorldRunner", ImportTitle.clean("3-D WorldRunner (USA)"))
        assertEquals("1942", ImportTitle.clean("1942 (Japan, USA)"))
    }

    @Test
    fun legacyRuleReproducesDamagedTitles() {
        assertEquals("in-1", ImportTitle.legacy("1200-in-1 (J) [p1]"))
        assertEquals("Yard Fight", ImportTitle.legacy("10-Yard Fight (USA)"))
        assertEquals("Contra", ImportTitle.legacy("01 - Contra"))
    }

    @Test
    fun identityIgnoresRegionRevisionContainerAndCase() {
        val seen = mutableSetOf<String>()
        assertTrue(ImportTitle.claim("nes", "01 - Super_Mario_Bros. (USA) (Rev 1) [!].zip", seen))
        assertFalse(ImportTitle.claim("nes", "super mario bros (Europe).nes", seen))
        assertFalse(ImportTitle.claim("nes", "Super Mario Bros. [b1].7z", seen))
        assertTrue(ImportTitle.claim("snes", "Super Mario Bros. (USA).sfc", seen))
        assertEquals(2, seen.size)
    }

    @Test
    fun identityIsIndependentOfDeviceLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(ImportTitle.fileIdentity("genesis", "Sonic.md"), ImportTitle.fileIdentity("genesis", "SONIC (USA).ZIP"))
            assertEquals(ImportTitle.fileIdentity("nes", "shinobi.nes"), ImportTitle.fileIdentity("nes", "SHINOBI (USA).zip"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun identityKeepsSequelsAndSeparateArcadeEditions() {
        val seen = mutableSetOf<String>()
        listOf("sf2", "sf2ce", "sf2hf", "ssf2", "ssf2t", "sfiii", "sfiii2", "sfiii3", "sfa", "sfa2", "sfa3", "mslug", "mslug2", "mslugx", "samsho", "samsho2", "samsho5", "samsh5sp").forEach { name ->
            assertTrue(name, ImportTitle.claim("arcade", "$name.zip", seen))
        }
        assertTrue(ImportTitle.claim("nes", "10-Yard Fight.nes", seen))
        assertTrue(ImportTitle.claim("nes", "1200-in-1.zip", seen))
        assertTrue(ImportTitle.claim("nes", "Contra.nes", seen))
        assertTrue(ImportTitle.claim("nes", "Contra II.nes", seen))
        assertFalse(ImportTitle.claim("arcade", "SFIII3 (Japan).zip", seen))
    }

    @Test
    fun identityRetainsDiscSidePartAndTrackNumbers() {
        val seen = mutableSetOf<String>()
        assertTrue(ImportTitle.claim("psx", "Final Fantasy VII (USA) (Disc 1).cue", seen))
        assertFalse(ImportTitle.claim("psx", "Final_Fantasy_VII (Europe) [Disk 01 of 3].chd", seen))
        assertFalse(ImportTitle.claim("psx", "Final Fantasy VII Disc 1.zip", seen))
        assertTrue(ImportTitle.claim("psx", "Final Fantasy VII (USA) (Disc 2).cue", seen))
        assertTrue(ImportTitle.claim("psx", "Final Fantasy VII (Disc 3).pbp", seen))
        assertTrue(ImportTitle.claim("psx", "Final Fantasy VII.m3u", seen))
        listOf("Side A", "Side B", "Part I", "Part II", "Track 1", "Track 2", "Tape 1", "Tape 2", "Episode I", "Episode II", "Chapter 1", "Chapter 2", "Volume 1", "Volume 2").forEach { part ->
            assertTrue(part, ImportTitle.claim("c64", "Game ($part) (Europe).zip", seen))
            assertFalse(part, ImportTitle.claim("c64", "Game [$part] (USA).d64", seen))
        }
    }

    @Test
    fun existingFilenameAndStoredTitleBothSeedClaims() {
        val existing = listOf(
            game("old", "Legacy Internal Name.nes", "Super Mario Bros.", "nes"),
            game("sega", "sonic1.md", "Sonic the Hedgehog", "genesis")
        )
        val seen = existing.flatMapTo(mutableSetOf()) { ImportTitle.gameIdentities(it) }
        assertFalse(ImportTitle.claim("nes", "Super_Mario_Bros (Japan).zip", seen))
        assertFalse(ImportTitle.claim("nes", "Legacy Internal Name (Rev 2).nes", seen))
        assertFalse(ImportTitle.claim("genesis", "SONIC1 (Rev 1).zip", seen))
        assertFalse(ImportTitle.claim("genesis", "Sonic The Hedgehog (USA, Europe).md", seen))
        assertTrue(ImportTitle.claim("snes", "sonic1.sfc", seen))
    }

    @Test
    fun claimsRemainReservedUntilANewBatchEvenIfCopyFails() {
        val seen = mutableSetOf<String>()
        assertTrue(ImportTitle.claim("snes", "Final Fight (USA).sfc", seen))
        // No successful game was added after the claim (e.g. extraction failed).
        assertFalse(ImportTitle.claim("snes", "Final Fight (Europe).sfc", seen))
        assertTrue(ImportTitle.claim("snes", "Final Fight (Europe).sfc", mutableSetOf()))
    }

    @Test
    fun repositoryInsertFilterChecksExistingAndTheIncomingBatch() {
        val old = game("old", "ffight.zip", "Final Fight")
        val oldDuplicate = game("old-duplicate", "ffightub.zip", "Final Fight")
        val existing = listOf(old, oldDuplicate)
        val incoming = listOf(
            game("duplicate", "ffightu.zip", "Final Fight"),
            game("new", "sfiii3n.zip", "Street Fighter III 3rd Strike: Fight for the Future"),
            game("batch-duplicate", "sfiii3n (Japan).zip", "Street Fighter III 3rd Strike: Fight for the Future"),
            game("other-console", "ffightub.zip", "Final Fight", "snes"),
            game("sequel", "sfiii2.zip", "Street Fighter III 2nd Impact: Giant Attack")
        )
        assertEquals(listOf("new", "other-console", "sequel"), ImportTitle.newGames(existing, incoming).map { it.id })
        assertEquals(listOf(old, oldDuplicate), existing)
        assertEquals(5, incoming.size)
        assertTrue(ImportTitle.newGames(existing, emptyList()).isEmpty())
    }

    @Test
    fun repositoryInsertFilterAlsoChecksStoredTitles() {
        val existing = listOf(game("old", "Internal.nes", "Contra", "nes"))
        val incoming = listOf(
            game("duplicate-title", "Different Internal.nes", "Contra (USA)", "nes"),
            game("sequel", "Contra II (Europe).nes", "Contra II", "nes"),
            game("region", "Contra II (USA).zip", "Contra II", "nes"),
            game("other-console", "Contra.zip", "Contra", "snes")
        )
        assertEquals(listOf("sequel", "other-console"), ImportTitle.newGames(existing, incoming).map { it.id })
    }

    @Test
    fun storedCleanTitlesDoNotCollapseMultidiscGames() {
        val first = game("disc1", "Final Fantasy VII (USA) (Disc 1).cue", "Final Fantasy VII", "psx")
        val second = game("disc2", "Final Fantasy VII (Europe) (Disc 2).chd", "Final Fantasy VII", "psx")
        val duplicate = game("disc1-copy", "Final Fantasy VII [Disk 01].chd", "Final Fantasy VII", "psx")
        assertEquals(listOf(second), ImportTitle.newGames(listOf(first), listOf(second, duplicate)))
        val seen = ImportTitle.gameIdentities(first).toMutableSet()
        assertFalse(ImportTitle.claim("psx", duplicate.fileName, seen))
        assertTrue(ImportTitle.claim("psx", second.fileName, seen))
    }

    @Test
    fun legacyEntriesWithoutFilenamesStillDeduplicateByTitle() {
        val existing = listOf(game("old", "", "Final Fight"))
        assertTrue(ImportTitle.newGames(existing, listOf(game("duplicate", "ffightub.zip", "Final Fight"))).isEmpty())
        val unknown = listOf(
            game("unknown1", "[a].zip", "Unknown game"),
            game("unknown2", "[b].zip", "Unknown game")
        )
        assertEquals(unknown, ImportTitle.newGames(emptyList(), unknown))
    }

    @Test
    fun pendingArchiveEntrySuppliesTheActualGameAndDiscIdentity() {
        val old = game("old", "Collection.zip", "Final Fantasy VII", "psx")
            .copy(pendingEntry = "PSX/Final Fantasy VII (Disc 1).cue")
        val seen = ImportTitle.gameIdentities(old).toMutableSet()
        assertFalse(ImportTitle.claim("psx", "Final Fantasy VII [CD 1].chd", seen))
        assertTrue(ImportTitle.claim("psx", "Final Fantasy VII (Disc 2).cue", seen))
    }

    private fun game(id: String, fileName: String, title: String, consoleId: String = "genesis") =
        LibraryGame(id = id, title = title, consoleId = consoleId, filePath = "/roms/$consoleId/$fileName", fileName = fileName)
}
