package com.retrobit.app.data

import kotlinx.serialization.Serializable

@Serializable
data class CatalogGame(
    val id: String,
    val title: String,
    val titleTr: String? = null,
    val consoleId: String,
    val year: Int = 0,
    val developer: String = "",
    val genre: String = "action",
    val players: String = "1",
    val rating: Double = 4.5,
    val descEn: String = "",
    val descTr: String = "",
    val wiki: String? = null,
    val cover: String? = null,
    val featured: Boolean = false
) {
    fun localizedDescription(isTr: Boolean): String =
        if (isTr && descTr.isNotBlank()) descTr else descEn
}

@Serializable
data class LibraryGame(
    val id: String,
    val title: String,
    val consoleId: String,
    val filePath: String,
    val addedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = 0L,
    val playCount: Int = 0,
    val catalogId: String? = null,
    val coverUrl: String? = null,
    val fileName: String = "",
    /** Zip entry that has to be unpacked before the core can load it. Null when the ROM loads directly. */
    val pendingEntry: String? = null,
    /** Where pendingEntry was last unpacked to, so a repeat launch skips the extraction. */
    val unpackedPath: String? = null,
    /** Total seconds this ROM has actually spent on screen, stored on the device only. */
    val playSeconds: Long = 0L
) {
    fun isFavorite(favorites: Set<String>): Boolean =
        (catalogId != null && favorites.contains("catalog:$catalogId")) ||
            favorites.contains("lib:$id")

    /** Only the explicit "(TR)" release tag counts; language guesses would mislabel games. */
    val isTurkishRelease: Boolean
        get() = listOf(title, fileName, pendingEntry.orEmpty()).any { it.contains("(TR)", ignoreCase = true) }
}

data class ConsoleInfo(
    val id: String,
    val name: String,
    val trName: String,
    val shortName: String,
    val color: Long,
    val glyph: String,
    val extensions: List<String>,
    val libretroSystem: String?,
    val cores: List<String>,
    val descEn: String,
    val descTr: String,
    val year: Int = 0,
    val maker: String = ""
) {
    fun localizedName(isTr: Boolean) = if (isTr) trName else name
    fun localizedDescription(isTr: Boolean) = if (isTr) descTr else descEn
}

@Serializable
data class GameDisplayPrefs(
    val shader: String? = null
)

@Serializable
data class GamepadPrefs(
    val layout: String = SettingsData.GAMEPAD_XBOX,
    val mapping: Map<Int, Int> = emptyMap()
)

/** Console application clears only the supplied console's stable library ids. */
fun gamepadOverridesAfterConsoleApply(
    overrides: Map<String, GamepadPrefs>,
    gameIds: Collection<String>
): Map<String, GamepadPrefs> = overrides - gameIds.toSet()

/** Metadata is captured on IO; revalidation rejects replaced/disappearing files, not just empty ones. */
data class StartupSnapshot(val path: String, val modified: Long, val size: Long) {
    fun read(): ByteArray {
        val file = java.io.File(path)
        check(file.isFile && file.length() == size && file.lastModified() == modified && size > 0)
        val bytes = file.readBytes()
        check(bytes.size.toLong() == size && file.lastModified() == modified)
        return bytes
    }
}

data class StartupProgress(val snapshot: StartupSnapshot?, val sram: StartupSnapshot?) {
    val canResume: Boolean get() = snapshot != null || sram != null
}

/** Call on IO. Include legacy slot 1 even when the modern slot also exists. Never delete saves. */
fun scanStartupProgress(directory: java.io.File, gameId: String): StartupProgress {
    fun metadata(file: java.io.File): StartupSnapshot? =
        file.takeIf { it.isFile && it.length() > 0 }?.let {
            StartupSnapshot(it.absolutePath, it.lastModified(), it.length())
        }
    val files = if (!directory.exists()) emptyArray() else directory.listFiles()
        ?: throw java.io.IOException("Cannot list save directory")
    val candidates = files.filter {
        it.name == "${gameId}_auto.state" || it.name == "$gameId.state" ||
            (it.name.startsWith("${gameId}_slot") && it.name.endsWith(".state") &&
                it.name.removePrefix("${gameId}_slot").removeSuffix(".state")
                    .toIntOrNull()?.let { slot -> slot > 0 } == true)
    }.mapNotNull(::metadata)
    return StartupProgress(
        candidates.sortedWith(compareByDescending<StartupSnapshot> { it.modified }.thenBy { it.path })
            .firstOrNull(),
        metadata(java.io.File(directory, "$gameId.srm"))
    )
}

/** One restore attempt per surface; failed restores never authorize recovery/SRAM writes. */
class StartupRestoreGate {
    var attempted: Boolean = false
        private set
    var writesAllowed: Boolean = false
        private set
    fun begin(): Boolean {
        if (attempted) return false
        attempted = true
        return true
    }
    fun complete(success: Boolean) { writesAllowed = success }
}

data class SettingsData(
    val language: String = LANGUAGE_SYSTEM,
    val theme: String = THEME_DARK,
    val defaultEmulator: String = "",
    val playerMode: String = PLAYER_INTERNAL,
    val onboardingDone: Boolean = false,
    val layoutMode: String = LAYOUT_AUTO,
    val defaultShader: String = SHADER_DEFAULT,
    val gamepadLayout: String = GAMEPAD_XBOX,
    val gamepadCustomMapping: Map<Int, Int> = emptyMap(),
    val gamepadGameOverrides: Map<String, GamepadPrefs> = emptyMap(),
    val gamepadConsoleOverrides: Map<String, GamepadPrefs> = emptyMap(),
    val gameDisplayOverrides: Map<String, GameDisplayPrefs> = emptyMap(),
    val supporterUnlocked: Boolean = false,
    val supporterSource: String = "",
    val supporterName: String = "",
    val supporterListed: Boolean = false,
    val libraryShelf: Boolean = true,
    val coverSize: String = COVER_MEDIUM,
    val customCovers: Map<String, String> = emptyMap(),
    val attractMode: Boolean = false,
    val uiScanlines: Boolean = true,
    val retroThemeTest: Boolean = true,
    val coolAnimations: Boolean = false,
    val uiSounds: Boolean = true,
    val autoSaveState: Boolean = true,
    val startupBehavior: String = STARTUP_ASK,
    val librarySort: String = SORT_TITLE,
    val consoleSort: String = CONSOLE_SORT_YEAR,
    val consoleCustomOrder: List<String> = emptyList(),
    val hideEmptyConsoles: Boolean = false
) {
    fun effectiveGamepad(gameId: String, consoleId: String): GamepadPrefs =
        gamepadGameOverrides[gameId] ?: gamepadConsoleOverrides[consoleId]
        ?: GamepadPrefs(gamepadLayout, gamepadCustomMapping)

    /** Older builds stored the ThemeMode enum name; map it to the new theme ids. */
    val themeId: String
        get() = when (theme) {
            "System" -> THEME_SYSTEM
            "Dark" -> THEME_DARK
            "Light" -> THEME_LIGHT
            else -> theme
        }

    val effectiveShader: String
        get() = if (!supporterUnlocked && defaultShader in SUPPORTER_SHADERS) SHADER_DEFAULT else defaultShader

    val saveSlots: Int
        get() = if (supporterUnlocked) SUPPORTER_SLOTS else FREE_SLOTS

    val effectiveCoverSize: String
        get() = if (supporterUnlocked) coverSize else COVER_MEDIUM

    val effectiveLibraryShelf: Boolean
        get() = supporterUnlocked && libraryShelf

    val effectiveTheme: String
        get() = if (!supporterUnlocked && themeId in SUPPORTER_THEMES) THEME_DARK else themeId

    /**
     * The console-themed frontend look is a supporter perk; the test flag previews it pre-release.
     * Supporters can still choose the simple look, so the stored preference decides once unlocked.
     */
    val retroTheme: Boolean
        get() = supporterUnlocked && retroThemeTest

    /** Motion is opt-in for supporters; everyone else gets the calm interface by default. */
    val reducedMotion: Boolean
        get() = !(supporterUnlocked && coolAnimations)

    companion object {
        const val LANGUAGE_SYSTEM = "system"
        const val LANGUAGE_EN = "en"
        const val LANGUAGE_TR = "tr"
        const val PLAYER_INTERNAL = "internal"
        const val PLAYER_EXTERNAL = "external"
        const val STARTUP_RESUME = "resume"
        const val STARTUP_FRESH = "fresh"
        const val STARTUP_ASK = "ask"
        const val LAYOUT_AUTO = "auto"
        const val LAYOUT_PHONE = "phone"
        const val LAYOUT_TV = "tv"

        const val SHADER_DEFAULT = "default"
        const val SHADER_CRT = "crt"
        const val SHADER_LCD = "lcd"
        const val SHADER_SHARP = "sharp"
        const val SHADER_APERTURE = "aperture"
        const val SHADER_SCANLINE = "scanline"
        const val SHADER_DOTMATRIX = "dotmatrix"
        const val SHADER_GHOST = "ghost"

        val FREE_SHADERS = listOf(SHADER_DEFAULT, SHADER_CRT, SHADER_LCD, SHADER_SHARP)
        val SUPPORTER_SHADERS = listOf(
            SHADER_APERTURE, SHADER_SCANLINE, SHADER_DOTMATRIX, SHADER_GHOST
        )

        const val GAMEPAD_NINTENDO = "nintendo"
        const val GAMEPAD_XBOX = "xbox"
        const val GAMEPAD_CUSTOM = "custom"

        const val THEME_SYSTEM = "system"
        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"
        const val THEME_OLED = "oled"
        const val THEME_AMBER = "amber"
        const val THEME_GREEN = "green"
        const val THEME_NINTENDO = "theme_nintendo"
        const val THEME_SEGA = "theme_sega"
        const val THEME_PSX = "theme_psx"

        val FREE_THEMES = listOf(THEME_SYSTEM, THEME_DARK, THEME_LIGHT)
        val SUPPORTER_THEMES = listOf(
            THEME_OLED, THEME_AMBER, THEME_GREEN,
            THEME_NINTENDO, THEME_SEGA, THEME_PSX
        )

        const val COVER_SMALL = "s"
        const val COVER_MEDIUM = "m"
        const val COVER_LARGE = "l"

        const val FREE_SLOTS = 2
        const val SUPPORTER_SLOTS = 5

        const val SORT_RECENT = "recent"
        const val SORT_TITLE = "title"
        const val SORT_PLAYTIME = "playtime"

        const val CONSOLE_SORT_COUNT = "count"
        const val CONSOLE_SORT_YEAR = "year"
        const val CONSOLE_SORT_NAME = "name"
        const val CONSOLE_SORT_CUSTOM = "custom"
        val CONSOLE_SORTS = listOf(
            CONSOLE_SORT_COUNT, CONSOLE_SORT_YEAR, CONSOLE_SORT_NAME, CONSOLE_SORT_CUSTOM
        )

        const val SUPPORTER_SOURCE_KEY = "cdkey"
        const val SUPPORTER_SOURCE_PLAY = "play"
    }
}

data class EmulatorInfo(
    val packageName: String,
    val label: String,
    val supportsRomIntents: Boolean = true
)

/** App-owned storage categories. User ROM folders outside these paths are never counted or deleted. */
data class StorageUsage(
    val coresBytes: Long = 0L,
    val savesBytes: Long = 0L,
    val coversBytes: Long = 0L,
    val romsBytes: Long = 0L
) {
    val totalBytes: Long get() = coresBytes + savesBytes + coversBytes + romsBytes
}
