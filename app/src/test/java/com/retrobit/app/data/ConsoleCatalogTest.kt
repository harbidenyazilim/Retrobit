package com.retrobit.app.data

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleCatalogTest {
    private val activeIds = setOf(
        "nes", "genesis", "snes", "ps1", "n64", "gba", "nds", "atari2600", "psp", "gb", "gbc"
    )
    private val removedIds = setOf(
        "arcade", "mame", "pce", "wonderswan", "dos", "sg1000",
        "segacd", "sega32x", "saturn", "atari5200", "atari7800", "lynx", "jaguar", "fds",
        "virtualboy", "pokemonmini", "satellaview", "sufamiturbo", "ngp", "ngpc", "neogeocd",
        "pcecd", "supergrafx", "colecovision", "intellivision", "vectrex", "odyssey2", "channelf",
        "amiga", "c64", "vic20", "plus4", "zxspectrum", "amstradcpc", "msx", "msx2", "x68000",
        "pc98", "scummvm", "3do", "cdi", "dreamcast"
    )

    @Test
    fun activeCatalogContainsExactlyElevenSystems() {
        val ids = ConsoleCatalog.consoles.map { it.id }
        assertEquals(11, ids.size)
        assertEquals(activeIds, ids.toSet())
        assertEquals(ids.size, ids.toSet().size)
        assertNull(ConsoleCatalog.get(null))
        for (id in removedIds + setOf("ps2", "ps3", "pcsx2", "rpcs3", "kronos")) {
            assertNull(id, ConsoleCatalog.get(id))
        }
        ConsoleCatalog.consoles.forEach {
            assertTrue(it.name.isNotBlank())
            assertTrue(it.shortName.isNotBlank())
            assertTrue(it.year > 0)
            assertTrue(it.descEn.isNotBlank())
            assertTrue(it.descTr.isNotBlank())
            assertTrue(it.libretroSystem.orEmpty().isNotBlank())
            assertEquals(it.id, if (it.id == "genesis") 2 else 1, it.cores.size)
        }
    }

    @Test
    fun knownSuffixesRemainDeterministic() {
        val expected = mapOf(
            "nes" to "nes", "unf" to "nes", "unif" to "nes", "sfc" to "snes", "smc" to "snes",
            "fig" to "snes", "swc" to "snes", "n64" to "n64", "z64" to "n64", "v64" to "n64",
            "gb" to "gb", "gbc" to "gbc", "gba" to "gba", "nds" to "nds", "a26" to "atari2600", "md" to "genesis",
            "gen" to "genesis", "smd" to "genesis", "68k" to "genesis",
            "sgd" to "genesis", "sms" to "genesis", "gg" to "genesis", "cso" to "psp"
        )
        expected.forEach { (extension, id) ->
            assertEquals(extension, id, ConsoleCatalog.consoleForExtension(extension)?.id)
        }
        assertEquals("gba", ConsoleCatalog.consoleForExtension(".GBA")?.id)
    }

    @Test
    fun isoVolumeHeadersIdentifyOnlySupportedSonyDiscs() {
        fun header(system: String): ByteArray = ByteArray(40).apply {
            this[0] = 1
            "CD001".toByteArray(Charsets.US_ASCII).copyInto(this, 1)
            this[6] = 1
            system.padEnd(32).toByteArray(Charsets.US_ASCII).copyInto(this, 8)
        }
        assertEquals("psp", ConsoleCatalog.consoleForIsoHeader(header("PSP GAME"))?.id)
        assertEquals("ps1", ConsoleCatalog.consoleForIsoHeader(header("PLAYSTATION"))?.id)
        assertNull(ConsoleCatalog.consoleForIsoHeader(header("PLAYSTATION2")))
        assertNull(ConsoleCatalog.consoleForIsoHeader(header("")))
        assertNull(ConsoleCatalog.consoleForIsoHeader(ByteArray(39)))
        assertNull(ConsoleCatalog.consoleForIsoHeader(header("PSP GAME").apply { this[0] = 2 }))
        assertNull(ConsoleCatalog.consoleForIsoHeader(header("PSP GAME").apply { this[1] = 0 }))
        assertNull(ConsoleCatalog.consoleForIsoHeader(header("PSP GAME").apply { this[6] = 0 }))
    }

    @Test
    fun sharedAndGenericSuffixesDoNotGuessAGamePlatform() {
        for (extension in listOf("rom", "bin", "img", "chd", "iso", "pbp", "zip", "dsk", "d64", "tap", "sna", "hdf", "exe")) {
            assertNull(extension, ConsoleCatalog.consoleForExtension(extension))
        }
        // Playlist/queue suffixes belonged to Dreamcast too; with it gone PS1 owns them alone.
        assertEquals("ps1", ConsoleCatalog.consoleForExtension("cue")?.id)
        assertEquals("ps1", ConsoleCatalog.consoleForExtension("m3u")?.id)
        assertNull(ConsoleCatalog.consoleForExtension("rom", "arcade"))
        assertNull(ConsoleCatalog.consoleForExtension("zip", "mame"))
        assertNull(ConsoleCatalog.consoleForExtension("img", "dos"))
        assertNull(ConsoleCatalog.consoleForExtension("unknown", "nes"))
    }

    @Test
    fun validHintsWinAndUnsupportedHintsNeverFallThrough() {
        ConsoleCatalog.consoles.forEach { console ->
            console.extensions.forEach { extension ->
                assertTrue("${console.id}:$extension", ConsoleCatalog.acceptsExtension(console.id, extension))
                assertEquals(console.id, ConsoleCatalog.consoleForExtension(extension, console.id)?.id)
            }
        }
        for (id in removedIds + setOf("ps2", "unknown")) {
            for (extension in ConsoleCatalog.allExtensions) {
                assertFalse("$id:$extension", ConsoleCatalog.acceptsExtension(id, extension))
                assertNull("$id:$extension", ConsoleCatalog.consoleForExtension(extension, id))
            }
        }
        assertEquals("gba", ConsoleCatalog.consoleForExtension("gba", "ps1")?.id)
        assertNull(ConsoleCatalog.consoleForExtension("iso", "nes"))
        assertNull(ConsoleCatalog.consoleForExtension("iso", "dreamcast"))
        assertNull(ConsoleCatalog.consoleForExtension("rom", "ps1"))
        assertFalse(ConsoleCatalog.acceptsExtension("genesis", "32x"))
    }

    @Test
    fun atariBinRequiresAnExplicitValidHint() {
        assertEquals(listOf("a26", "bin"), ConsoleCatalog.get("atari2600")?.extensions)
        assertEquals(listOf("stella"), ConsoleCatalog.get("atari2600")?.cores)
        assertTrue(ConsoleCatalog.acceptsExtension("atari2600", " .BIN "))
        assertEquals("atari2600", ConsoleCatalog.consoleForExtension("bin", "atari2600")?.id)
        assertNull(ConsoleCatalog.consoleForExtension("bin"))
        assertNull(ConsoleCatalog.consoleForExtension("bin", "atari5200"))
        assertFalse(ConsoleCatalog.acceptsExtension("atari2600", "a52"))
        assertFalse(ConsoleCatalog.acceptsExtension("atari2600", "rom"))
        assertEquals("genesis", ConsoleCatalog.consoleForExtension("bin", "genesis")?.id)
    }

    @Test
    fun activeDiscsUseValidatedHints() {
        assertEquals(setOf("ps1", "psp"), ConsoleCatalog.discConsoleIds)
        for (id in listOf("ps1")) {
            assertEquals(id, id, ConsoleCatalog.consoleForExtension("cue", id)?.id)
            assertEquals(id, id, ConsoleCatalog.consoleForExtension("chd", id)?.id)
        }
        assertEquals("psp", ConsoleCatalog.consoleForExtension("chd", "psp")?.id)
        assertEquals("psp", ConsoleCatalog.consoleForExtension("pbp", "psp")?.id)
        assertEquals("ps1", ConsoleCatalog.consoleForExtension("pbp", "ps1")?.id)
        assertNull(ConsoleCatalog.consoleForExtension("cue", "sms"))
        assertNull(ConsoleCatalog.consoleForExtension("gdi", "psp"))
        assertNull(ConsoleCatalog.consoleForExtension("zip"))
        assertNull(ConsoleCatalog.consoleForExtension("zip", "arcade"))
    }

    @Test
    fun activeAliasesAndSimpleFolderDecorationsAreRecognized() {
        val expected = mapOf(
            "ROMS/Famicom/game.nes" to "nes",
            "ROMS/Super Famicom/game.sfc" to "snes",
            "ROMS/Nintendo - Nintendo 64/game.z64" to "n64",
            "ROMS/Nintendo - Game Boy Advance/game.gba" to "gba",
            "ROMS/Nintendo Game Boy/game.gb" to "gb",
            "ROMS/Nintendo Game Boy Color/game.gbc" to "gbc",
            "ROMS/Gameboy Color/game.gbc" to "gbc",
            "ROMS/Nintendo DS/game.nds" to "nds",
            "ROMS/Sony - PlayStation Portable/game.iso" to "psp",
            "ROMS/Sony - PlayStation/game.cue" to "ps1",
            "ROMS/Sega - Mega Drive - Genesis/game.md" to "genesis",
            "ROMS/Sega Mega Drive/game.bin" to "genesis",
            "ROMS/Sega - Master System - Mark III/game.sms" to "genesis",
            "ROMS/Master System/game.bin" to "genesis",
            "ROMS/Sega Game Gear/game.gg" to "genesis",
            "ROMS/GameGear/game.bin" to "genesis",
            "ROMS/Atari - 2600/game.bin" to "atari2600",
            "ROMS/VCS/game.bin" to "atari2600",
            "ROMS/GBA Games/game.gba" to "gba",
            "ROMS/Games PS1/game.cue" to "ps1",
            "ROMS/ROMs Atari 2600 Games/game.bin" to "atari2600"
        )
        expected.forEach { (path, id) -> assertEquals(path, id, ConsoleCatalog.consoleHint(path)) }
        ConsoleCatalog.consoles.forEach { console ->
            assertEquals(console.id, ConsoleCatalog.consoleHint("ROMS/${console.id}/game.bin"))
            assertEquals(console.id, ConsoleCatalog.consoleHint("ROMS/${console.libretroSystem}/game.bin"))
        }
        assertEquals("atari2600", ConsoleCatalog.consoleHint("C:\\ROMS\\ATARI_2600\\game.bin"))
        assertEquals("gba", ConsoleCatalog.consoleHint("ROMS/GB/GBA/game.gba"))
        assertEquals("gb", ConsoleCatalog.consoleHint("ROMS/GBA/Game Boy Games/game.gba"))
        assertEquals("gba", ConsoleCatalog.consoleHint("ROMS/NES/GBA Games/game.gba"))
    }

    @Test
    fun filenamesBroadManufacturersAndUnsupportedSiblingsAreNotHints() {
        for (path in listOf(
            "Sega/game.bin", "Sega Games/game.bin", "Sony/game.img", "Atari/game.rom",
            "gameboy.bin", "Sega Saturn.chd", "ROMS/mypspbackup/game.iso",
            "ROMS/PlayStation 2/game.iso", "ROMS/Sony PlayStation 3 Games/game.iso",
            "ROMS/PS Vita/game.bin", "ROMS/unknown/game.rom",
            "ROMS/Sega Mega Drive Sega CD Games/game.bin",
            "ROMS/Genesis/Sega CD/game.bin", "ROMS/PS1/PlayStation 2/game.iso",
            "ROMS/Genesis/segacd/game.bin", "ROMS/PS1/playstation2/game.iso",
            "ROMS/Sega - Dreamcast/game.gdi", "ROMS/PS1/Dreamcast/game.gdi",
            "ROMS/NES/Famicom Disk System/game.bin", "ROMS/SNES/Satellaview/game.sfc",
            "ROMS/SNES/sufamiturbo/game.sfc", "ROMS/Atari2600/atari7800/game.bin",
            "ROMS/Atari2600/Atari 7800/game.bin",
            "ROMS/Arcade Games/game.zip", "ROMS/MAME 2003 Plus/game.zip", "ROMS/SEGA/game.bin",
            "ROMS/Sega - SG-1000/game.bin",
            "ROMS/FinalBurn Neo/game.zip", "ROMS/Philips CD-i/game.chd"
        )) assertNull(path, ConsoleCatalog.consoleHint(path))
        removedIds.forEach { id ->
            assertNull(id, ConsoleCatalog.consoleHint("ROMS/$id/game.bin"))
        }
        assertNull(ConsoleCatalog.consoleHint(null))
        assertNull(ConsoleCatalog.consoleHint(""))
    }

    @Test
    fun aliasAndExtensionMatchingDoesNotDependOnTurkishLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("genesis", ConsoleCatalog.consoleHint("ROMS/SEGA MEGA DRIVE/game.MD"))
            assertEquals("atari2600", ConsoleCatalog.consoleHint("ROMS/ATARI 2600/game.BIN"))
            assertEquals("atari2600", ConsoleCatalog.consoleForExtension("A26")?.id)
            assertNull(ConsoleCatalog.consoleHint("ROMS/PHILIPS CD-I/game.CHD"))
            assertNull(ConsoleCatalog.consoleForExtension("MIN"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun cartridgeSetIsDerivedAndExcludesTracksArchivesAndPrograms() {
        val excluded = ConsoleCatalog.discExtensions + ConsoleCatalog.archiveExtensions +
            ConsoleCatalog.programExtensions + ConsoleCatalog.genericExtensions
        assertEquals(ConsoleCatalog.allExtensions - excluded, ConsoleCatalog.cartridgeExtensions)
        assertEquals(setOf(
            "nes", "unf", "unif", "sfc", "smc", "fig", "swc", "n64", "z64", "v64",
            "gb", "gbc", "gba", "nds", "md", "sms", "gg", "gen", "smd", "68k", "sgd", "a26"
        ), ConsoleCatalog.cartridgeExtensions)
        assertTrue(ConsoleCatalog.cartridgeExtensions.intersect(excluded).isEmpty())
        for (extension in listOf("adz", "hdz", "lha", "gz", "d6z", "rp9")) {
            assertFalse(extension, extension in ConsoleCatalog.allExtensions)
        }
    }

    @Test
    fun everySystemRoutesToItsBundledCore() {
        val expected = mapOf(
            "nes" to "fceumm", "snes" to "bsnes_mercury_performance", "n64" to "mupen64plus_next_gles3",
            "gb" to "gambatte", "gbc" to "gambatte", "gba" to "mgba", "nds" to "melonds",
            "genesis" to "picodrive", "ps1" to "pcsx_rearmed", "psp" to "ppsspp", "atari2600" to "stella"
        )
        assertEquals(activeIds, CoreManager.CORES.map { it.consoleId }.toSet())
        ConsoleCatalog.consoles.forEach { console ->
            assertEquals(console.id, expected[console.id], CoreManager.coreFor(console.id)?.coreName)
            assertEquals(console.id, console.cores.toSet(),
                CoreManager.CORES.filter { it.consoleId == console.id }.map { it.coreName }.toSet())
        }
        CoreManager.CORES.forEach {
            assertTrue(it.displayName.isNotBlank())
            assertTrue(it.author.isNotBlank())
        }
        for (id in removedIds + setOf("ps2", "ps3", "unknown")) assertNull(id, CoreManager.coreFor(id))
        assertNull(CoreManager.coreFor(null))
    }

    @Test
    fun segaCardPicksCoreByRomExtension() {
        for (name in listOf("Alex Kidd.sms", "Shinobi.GG", "/data/roms/Columns.gg")) {
            assertEquals(name, "gearsystem", CoreManager.coreFor("genesis", name)?.coreName)
        }
        for (name in listOf("Sonic.md", "Sonic.bin", "Sonic.gen", "Sonic.smd", "Sonic", null)) {
            assertEquals(name.toString(), "picodrive", CoreManager.coreFor("genesis", name)?.coreName)
        }
        assertEquals("fceumm", CoreManager.coreFor("nes", "game.gg")?.coreName)
    }

    @Test
    fun bsnesRunsWithHighLevelChipEmulation() {
        val snes = CoreManager.coreFor("snes", "Game.smc")!!
        assertEquals("enabled", snes.variables["bsnes_violate_accuracy"])
        assertEquals("HLE", snes.variables["bsnes_chip_hle"])
        CoreManager.CORES.filter { it.consoleId != "snes" }.forEach { assertTrue(it.coreName, it.variables.isEmpty()) }
    }

    @Test
    fun segaCardPicksThumbnailRepositoryByExtension() {
        assertEquals("Sega_-_Mega_Drive_-_Genesis", ConsoleCatalog.thumbnailSystem("genesis", "Sonic.md"))
        assertEquals("Sega_-_Mega_Drive_-_Genesis", ConsoleCatalog.thumbnailSystem("genesis", "Sonic.bin"))
        assertEquals("Sega_-_Master_System_-_Mark_III", ConsoleCatalog.thumbnailSystem("genesis", "Alex Kidd.SMS"))
        assertEquals("Sega_-_Game_Gear", ConsoleCatalog.thumbnailSystem("genesis", "Shinobi.gg"))
        assertEquals(ConsoleCatalog.get("nes")?.libretroSystem, ConsoleCatalog.thumbnailSystem("nes", "game.gg"))
        assertNull(ConsoleCatalog.thumbnailSystem("mame", "dino.zip"))
    }

    @Test
    fun removedOnlySuffixesCannotBecomeImportCandidates() {
        for (extension in listOf(
            "32x", "fds", "sg", "a52", "a78", "lnx", "j64",
            "vb", "min", "ngp", "ngc", "pce", "sgx", "col", "int", "vec", "chf", "adf",
            "tzx", "cpr", "mx1", "mx2", "dim", "hdi", "bs", "st", "d64", "dsk", "tap",
            "com", "prg", "uae", "scummvm", "adz", "hdz", "lha", "gz", "d6z", "rp9",
            "gdi", "cdi", "mdx"
        )) {
            assertFalse(extension, extension in ConsoleCatalog.allExtensions)
            assertNull(extension, ConsoleCatalog.consoleForExtension(extension))
            activeIds.forEach { id -> assertFalse("$id:$extension", ConsoleCatalog.acceptsExtension(id, extension)) }
        }
    }
}
