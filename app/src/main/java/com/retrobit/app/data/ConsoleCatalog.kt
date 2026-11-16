package com.retrobit.app.data

import java.util.Locale

object ConsoleCatalog {

    private fun console(
        id: String, name: String, shortName: String, year: Int, maker: String,
        extensions: String, core: String, thumbnailSystem: String,
        descEn: String, descTr: String, glyph: String = "cartridge", color: Long = 0xFF64748B
    ) = ConsoleInfo(
        id = id, name = name, trName = name, shortName = shortName, year = year, maker = maker,
        extensions = extensions.split('|'), cores = listOf(core),
        libretroSystem = thumbnailSystem.replace(' ', '_'),
        descEn = descEn, descTr = descTr, glyph = glyph, color = color
    )

    // Active definitions only; archived ROMs, cores, saves and database ids are left untouched.
    val consoles: List<ConsoleInfo> = listOf(
        ConsoleInfo(
            id = "nes",
            name = "Nintendo Entertainment System",
            trName = "Nintendo Entertainment System",
            shortName = "NES",
            color = 0xFFE4572E,
            glyph = "cartridge",
            extensions = listOf("nes", "unf", "unif"),
            libretroSystem = "Nintendo_-_Nintendo_Entertainment_System",
            cores = listOf("fceumm"),
            descEn = "The 8-bit icon that started it all.",
            descTr = "Her şeyi başlatan 8-bit ikon.",
            year = 1983,
            maker = "Nintendo"
        ),
        ConsoleInfo(
            id = "snes",
            name = "Super Nintendo",
            trName = "Super Nintendo",
            shortName = "SNES",
            color = 0xFF8B7CFF,
            glyph = "cartridge",
            extensions = listOf("sfc", "smc", "fig", "swc"),
            libretroSystem = "Nintendo_-_Super_Nintendo_Entertainment_System",
            cores = listOf("bsnes_mercury_performance"),
            descEn = "16-bit colour, mode-7 magic and timeless adventures.",
            descTr = "16-bit renk, mode-7 büyüsü ve zamansız maceralar.",
            year = 1990,
            maker = "Nintendo"
        ),
        ConsoleInfo(
            id = "n64",
            name = "Nintendo 64",
            trName = "Nintendo 64",
            shortName = "N64",
            color = 0xFF22C55E,
            glyph = "controller",
            extensions = listOf("n64", "z64", "v64"),
            libretroSystem = "Nintendo_-_Nintendo_64",
            cores = listOf("mupen64plus_next_gles3"),
            descEn = "The three-pronged leap into 3D worlds.",
            descTr = "3D dünyalara üç dişli sıçrayış.",
            year = 1996,
            maker = "Nintendo"
        ),
        ConsoleInfo(
            id = "gb",
            name = "Game Boy",
            trName = "Game Boy",
            shortName = "GB",
            color = 0xFF9BBC0F,
            glyph = "handheld",
            extensions = listOf("gb"),
            libretroSystem = "Nintendo_-_Game_Boy",
            cores = listOf("gambatte"),
            descEn = "Pocket-sized adventures in four shades of LCD green.",
            descTr = "LCD yeşilinin dört tonunda cep boyu maceralar.",
            year = 1989,
            maker = "Nintendo"
        ),
        ConsoleInfo(
            id = "gbc",
            name = "Game Boy Color",
            trName = "Game Boy Color",
            shortName = "GBC",
            color = 0xFF2563EB,
            glyph = "handheld",
            extensions = listOf("gbc"),
            libretroSystem = "Nintendo_-_Game_Boy_Color",
            cores = listOf("gambatte"),
            descEn = "A colourful new chapter in handheld adventures.",
            descTr = "El konsolu maceralarında rengârenk yeni bir dönem.",
            year = 1998,
            maker = "Nintendo"
        ),
        ConsoleInfo(
            id = "gba",
            name = "Game Boy Advance",
            trName = "Game Boy Advance",
            shortName = "GBA",
            color = 0xFFA855F7,
            glyph = "handheld",
            extensions = listOf("gba"),
            libretroSystem = "Nintendo_-_Game_Boy_Advance",
            cores = listOf("mgba"),
            descEn = "A 32-bit handheld powerhouse.",
            descTr = "32-bit bir el konsolu gücü.",
            year = 2001,
            maker = "Nintendo"
        ),
        ConsoleInfo(
            id = "nds",
            name = "Nintendo DS",
            trName = "Nintendo DS",
            shortName = "NDS",
            color = 0xFF0EA5E9,
            glyph = "dualscreen",
            extensions = listOf("nds"),
            libretroSystem = "Nintendo_-_Nintendo_DS",
            cores = listOf("melonds"),
            descEn = "Two screens, a stylus and endless ideas.",
            descTr = "İki ekran, bir kalem ve sonsuz fikir.",
            year = 2004,
            maker = "Nintendo"
        ),
        // The id stays "genesis" so existing libraries, saves and settings keep working.
        ConsoleInfo(
            id = "genesis",
            name = "SEGA",
            trName = "SEGA",
            shortName = "SEGA",
            color = 0xFF2563EB,
            glyph = "controller",
            extensions = listOf("md", "sms", "gg", "gen", "bin", "smd", "68k", "sgd"),
            libretroSystem = "Sega_-_Mega_Drive_-_Genesis",
            cores = listOf("picodrive", "gearsystem"),
            descEn = "Mega Drive, Master System and Game Gear together in one place.",
            descTr = "Mega Drive, Master System ve Game Gear tek yerde bir arada.",
            year = 1985,
            maker = "Sega"
        ),
        ConsoleInfo(
            id = "ps1",
            name = "PlayStation",
            trName = "PlayStation",
            shortName = "PS1",
            color = 0xFF94A3B8,
            glyph = "disc",
            extensions = listOf("bin", "cue", "iso", "chd", "pbp", "img", "m3u", "exe"),
            libretroSystem = "Sony_-_PlayStation",
            cores = listOf("pcsx_rearmed"),
            descEn = "The polygon revolution on a disc.",
            descTr = "Diskteki poligon devrimi.",
            year = 1994,
            maker = "Sony"
        ),
        ConsoleInfo(
            id = "psp",
            name = "PlayStation Portable",
            trName = "PlayStation Portable",
            shortName = "PSP",
            color = 0xFF0F766E,
            glyph = "handheld",
            extensions = listOf("iso", "cso", "pbp", "chd"),
            libretroSystem = "Sony_-_PlayStation_Portable",
            cores = listOf("ppsspp"),
            descEn = "Console-scale adventures in your pocket.",
            descTr = "Cebinde konsol ölçeğinde maceralar.",
            year = 2004,
            maker = "Sony"
        ),
        console("atari2600", "Atari 2600", "A26", 1977, "Atari", "a26|bin",
            "stella", "Atari - 2600", "The original VCS cartridge library.", "Orijinal VCS kartuş oyunları.",
            color = 0xFFD58A43)
    )

    private val byId = consoles.associateBy { it.id }

    fun get(id: String?): ConsoleInfo? = byId[id]

    /** One SEGA card spans three libretro thumbnail repositories, chosen by the ROM extension. */
    fun thumbnailSystem(consoleId: String, fileName: String): String? {
        val console = get(consoleId) ?: return null
        if (console.id != "genesis") return console.libretroSystem
        return when (fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "sms" -> "Sega_-_Master_System_-_Mark_III"
            "gg" -> "Sega_-_Game_Gear"
            else -> console.libretroSystem
        }
    }

    /** Applies the player's carousel order; custom ids keep their saved rank, unknowns follow. */
    fun sortSystems(
        systems: List<ConsoleInfo>,
        mode: String,
        customOrder: List<String>,
        counts: Map<String, Int>,
        isTr: Boolean
    ): List<ConsoleInfo> {
        val originalRank = systems.withIndex().associate { it.value.id to it.index }
        fun nameOf(system: ConsoleInfo) = system.localizedName(isTr).lowercase()
        return when (mode) {
            SettingsData.CONSOLE_SORT_COUNT ->
                systems.sortedWith(compareByDescending<ConsoleInfo> { counts[it.id] ?: 0 }.thenBy(::nameOf))
            SettingsData.CONSOLE_SORT_NAME -> systems.sortedBy(::nameOf)
            SettingsData.CONSOLE_SORT_CUSTOM -> {
                if (customOrder.isEmpty()) systems
                else {
                    val rank = customOrder.withIndex().associate { it.value to it.index }
                    systems.sortedWith(
                        compareBy<ConsoleInfo> { rank[it.id] ?: Int.MAX_VALUE }
                            .thenBy { originalRank[it.id] ?: 0 }
                    )
                }
            }
            else -> systems.sortedWith(
                compareBy<ConsoleInfo> { if (it.year > 0) it.year else Int.MAX_VALUE }.thenBy(::nameOf)
            )
        }
    }

    val discExtensions: Set<String> = setOf("iso", "chd", "pbp", "cue", "gdi", "cdi", "cso", "m3u")
    val archiveExtensions: Set<String> = setOf("zip", "7z")
    val programExtensions: Set<String> = setOf("exe")
    val genericExtensions: Set<String> = setOf("rom", "bin", "img")
    val discConsoleIds: Set<String> = setOf("ps1", "psp")
    val allExtensions: Set<String> = consoles.flatMap { it.extensions }.toSet()
    val cartridgeExtensions: Set<String> =
        allExtensions - discExtensions - archiveExtensions - programExtensions - genericExtensions

    fun acceptsExtension(consoleId: String, ext: String): Boolean {
        val normalized = ext.trim().removePrefix(".").lowercase(Locale.ROOT)
        val console = get(consoleId) ?: return false
        return normalized in console.extensions
    }

    /** A valid folder hint wins; an unsupported id never falls through to an active family. */
    fun consoleForExtension(ext: String, hint: String? = null): ConsoleInfo? {
        val normalized = ext.trim().removePrefix(".").lowercase(Locale.ROOT)
        if (hint != null) {
            if (get(hint) == null) return null
            if (acceptsExtension(hint, normalized)) return get(hint)
        }
        // ZIP is an archive, never a game by itself; BIN remains ambiguous, including Atari.
        if (normalized in genericExtensions || normalized in archiveExtensions ||
            normalized in programExtensions) return null
        return consoles.singleOrNull { normalized in it.extensions }
    }

    fun consoleForIsoHeader(header: ByteArray): ConsoleInfo? {
        if (header.size < 40 || header[0] != 1.toByte() || header[6] != 1.toByte() ||
            String(header, 1, 5, Charsets.US_ASCII) != "CD001") return null
        return when (String(header, 8, 32, Charsets.US_ASCII).trim(' ', '\u0000')) {
            "PSP GAME" -> get("psp")
            "PLAYSTATION" -> get("ps1")
            else -> null
        }
    }

    private fun normalizeAlias(value: String): String = value.lowercase(Locale.ROOT)
        .replace('_', ' ').replace(Regex("[^a-z0-9]+"), " ").trim()

    private val pathAliases: Map<String, String> = buildMap {
        fun aliases(id: String, vararg values: String) {
            values.forEach { put(normalizeAlias(it), id) }
        }
        consoles.forEach { system ->
            aliases(system.id, system.id, system.name, system.trName)
            system.libretroSystem?.let { aliases(system.id, it) }
        }
        aliases("nes", "famicom")
        aliases("snes", "sfc", "super famicom")
        aliases("gb", "gb", "gameboy", "nintendo game boy")
        aliases("gbc", "gbc", "gameboy color", "nintendo game boy color")
        aliases("gba", "gba", "gameboy advance", "nintendo game boy advance")
        aliases("nds", "nds", "ds")
        aliases("genesis", "mega drive", "megadrive", "md", "sega mega drive", "sega genesis",
            "master system", "mastersystem", "sms", "sega master system", "mark iii",
            "sega master system mark iii", "game gear", "gamegear", "gg", "sega game gear")
        // The merged card is named "SEGA", but a bare Sega folder may hold Saturn or CD .bin files.
        remove("sega")
        aliases("ps1", "ps1", "psx", "sony playstation")
        aliases("psp", "psp", "sony psp")
        aliases("atari2600", "atari 2600", "2600", "a26", "vcs")
    }

    private val leadingFolderDecoration = Regex("^(?:roms?|games?) ")
    private val trailingFolderDecoration = Regex(" (?:roms?|games?)$")
    // Rejection only, never aliases: a sibling/expansion must not inherit a parent system hint.
    private val unsupportedFamily = Regex(
        "(?:^| )(?:dreamcast|dc|playstation ?(?:[2-5]|vita)|ps ?[2-5]|ps ?vita|" +
            "sega ?cd|mega ?cd|sega ?32x|32x|saturn|sg ?1000|" +
            "famicom disk system|family computer disk system|fds|satellaview|sufami ?turbo|" +
            "atari ?(?:5200|7800|jaguar|lynx))(?: |$)"
    )

    /** Nearest directory alias wins; ROM/Games decorations are allowed, not family substrings. */
    fun consoleHint(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val directories = path.replace('\\', '/').split('/').dropLast(1)
            .filter { it.isNotBlank() }.asReversed().map(::normalizeAlias)
        for (directory in directories) {
            pathAliases[directory]?.let { return it }
            if (unsupportedFamily.containsMatchIn(directory)) return null
            val trailingTrimmed = directory.replace(trailingFolderDecoration, "").trim()
            pathAliases[trailingTrimmed]?.let { return it }
            val decorated = trailingTrimmed.replace(leadingFolderDecoration, "").trim()
            pathAliases[decorated]?.let { return it }
        }
        return null
    }
}
