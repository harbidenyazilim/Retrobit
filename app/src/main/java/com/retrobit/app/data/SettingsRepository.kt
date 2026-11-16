package com.retrobit.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "retrobit_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME = stringPreferencesKey("theme")
        val DEFAULT_EMULATOR = stringPreferencesKey("default_emulator")
        val PLAYER_MODE = stringPreferencesKey("player_mode")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val LAYOUT_MODE = stringPreferencesKey("layout_mode")
        val DEFAULT_SHADER = stringPreferencesKey("default_shader")
        val GAMEPAD_LAYOUT = stringPreferencesKey("gamepad_layout")
        val GAMEPAD_MAPPING = stringPreferencesKey("gamepad_mapping")
        val GAMEPAD_GAMES = stringPreferencesKey("gamepad_game_overrides")
        val GAMEPAD_CONSOLES = stringPreferencesKey("gamepad_console_overrides")
        val GAME_DISPLAY = stringPreferencesKey("game_display_overrides")
        val SUPPORTER_UNLOCKED = booleanPreferencesKey("supporter_unlocked")
        val SUPPORTER_SOURCE = stringPreferencesKey("supporter_source")
        val SUPPORTER_NAME = stringPreferencesKey("supporter_name")
        val SUPPORTER_LISTED = booleanPreferencesKey("supporter_listed")
        val LIBRARY_SHELF = booleanPreferencesKey("library_shelf")
        val COVER_SIZE = stringPreferencesKey("cover_size")
        val CUSTOM_COVERS = stringPreferencesKey("custom_covers")
        val ATTRACT_MODE = booleanPreferencesKey("attract_mode")
        val UI_SCANLINES = booleanPreferencesKey("ui_scanlines")
        val RETRO_THEME_TEST = booleanPreferencesKey("retro_theme_test")
        // Renamed so motion now follows the exclusive interface instead of an older opt-in value.
        val COOL_ANIMATIONS = booleanPreferencesKey("theme_animations")
        val UI_SOUNDS = booleanPreferencesKey("ui_sounds")
        val AUTO_SAVE_STATE = booleanPreferencesKey("auto_save_state")
        val STARTUP_BEHAVIOR = stringPreferencesKey("startup_behavior")
        val LIBRARY_SORT = stringPreferencesKey("library_sort")
        val CONSOLE_SORT = stringPreferencesKey("console_sort")
        val CONSOLE_CUSTOM_ORDER = stringPreferencesKey("console_custom_order")
        val HIDE_EMPTY_CONSOLES = booleanPreferencesKey("hide_empty_consoles")
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val displaySerializer = MapSerializer(String.serializer(), GameDisplayPrefs.serializer())
    private val gamepadSerializer = MapSerializer(String.serializer(), GamepadPrefs.serializer())
    private val stringMapSerializer = MapSerializer(String.serializer(), String.serializer())
    private val stringListSerializer = ListSerializer(String.serializer())

    val settings: Flow<SettingsData> = context.settingsDataStore.data.map { prefs ->
        val mappingStr = prefs[Keys.GAMEPAD_MAPPING].orEmpty()
        val customMapping = parseMapping(mappingStr)
        SettingsData(
            language = prefs[Keys.LANGUAGE] ?: SettingsData.LANGUAGE_SYSTEM,
            theme = prefs[Keys.THEME] ?: SettingsData.THEME_DARK,
            defaultEmulator = prefs[Keys.DEFAULT_EMULATOR] ?: "",
            playerMode = prefs[Keys.PLAYER_MODE] ?: SettingsData.PLAYER_INTERNAL,
            onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: false,
            layoutMode = prefs[Keys.LAYOUT_MODE] ?: SettingsData.LAYOUT_AUTO,
            defaultShader = prefs[Keys.DEFAULT_SHADER] ?: SettingsData.SHADER_DEFAULT,
            gamepadLayout = prefs[Keys.GAMEPAD_LAYOUT] ?: SettingsData.GAMEPAD_XBOX,
            gamepadCustomMapping = customMapping,
            gamepadGameOverrides = parseGamepad(prefs[Keys.GAMEPAD_GAMES].orEmpty()),
            gamepadConsoleOverrides = parseGamepad(prefs[Keys.GAMEPAD_CONSOLES].orEmpty()),
            gameDisplayOverrides = parseDisplay(prefs[Keys.GAME_DISPLAY].orEmpty()),
            supporterUnlocked = com.retrobit.app.BuildConfig.SUPPORTER_TEST_UNLOCK ||
                (prefs[Keys.SUPPORTER_UNLOCKED] ?: false),
            supporterSource = prefs[Keys.SUPPORTER_SOURCE] ?: "",
            supporterName = prefs[Keys.SUPPORTER_NAME] ?: "",
            supporterListed = prefs[Keys.SUPPORTER_LISTED] ?: false,
            libraryShelf = prefs[Keys.LIBRARY_SHELF] ?: (prefs[Keys.RETRO_THEME_TEST] ?: true),
            coverSize = prefs[Keys.COVER_SIZE] ?: SettingsData.COVER_MEDIUM,
            customCovers = parseStringMap(prefs[Keys.CUSTOM_COVERS].orEmpty()),
            attractMode = prefs[Keys.ATTRACT_MODE] ?: false,
            uiScanlines = prefs[Keys.UI_SCANLINES] ?: true,
            retroThemeTest = prefs[Keys.RETRO_THEME_TEST] ?: true,
            coolAnimations = prefs[Keys.COOL_ANIMATIONS] ?: prefs[Keys.RETRO_THEME_TEST] ?: true,
            uiSounds = prefs[Keys.UI_SOUNDS] ?: true,
            autoSaveState = prefs[Keys.AUTO_SAVE_STATE] ?: true,
            startupBehavior = prefs[Keys.STARTUP_BEHAVIOR] ?: SettingsData.STARTUP_ASK,
            librarySort = prefs[Keys.LIBRARY_SORT] ?: SettingsData.SORT_TITLE,
            consoleSort = prefs[Keys.CONSOLE_SORT] ?: SettingsData.CONSOLE_SORT_YEAR,
            consoleCustomOrder = parseStringList(prefs[Keys.CONSOLE_CUSTOM_ORDER].orEmpty()),
            hideEmptyConsoles = prefs[Keys.HIDE_EMPTY_CONSOLES] ?: false
        )
    }

    suspend fun setLayoutMode(mode: String) {
        context.settingsDataStore.edit {
            it[Keys.LAYOUT_MODE] = when (mode) {
                SettingsData.LAYOUT_PHONE, SettingsData.LAYOUT_TV -> mode
                else -> SettingsData.LAYOUT_AUTO
            }
        }
    }

    suspend fun setLanguage(language: String) {
        context.settingsDataStore.edit { it[Keys.LANGUAGE] = language }
    }

    suspend fun setTheme(themeId: String) {
        context.settingsDataStore.edit { it[Keys.THEME] = themeId }
    }

    suspend fun setDefaultEmulator(pkg: String) {
        context.settingsDataStore.edit { it[Keys.DEFAULT_EMULATOR] = pkg }
    }

    suspend fun setPlayerMode(mode: String) {
        context.settingsDataStore.edit {
            it[Keys.PLAYER_MODE] = if (mode == SettingsData.PLAYER_EXTERNAL) {
                SettingsData.PLAYER_EXTERNAL
            } else {
                SettingsData.PLAYER_INTERNAL
            }
        }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.settingsDataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun setDefaultShader(shader: String) {
        context.settingsDataStore.edit { it[Keys.DEFAULT_SHADER] = shader }
    }

    suspend fun setGameDisplay(gameId: String, shader: String) {
        context.settingsDataStore.edit { prefs ->
            val current = parseDisplay(prefs[Keys.GAME_DISPLAY].orEmpty()).toMutableMap()
            current[gameId] = GameDisplayPrefs(shader = shader)
            prefs[Keys.GAME_DISPLAY] = json.encodeToString(displaySerializer, current)
        }
    }

    suspend fun applyDisplayToAll(shader: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_SHADER] = shader
            prefs.remove(Keys.GAME_DISPLAY)
        }
    }

    private fun parseDisplay(str: String): Map<String, GameDisplayPrefs> {
        if (str.isBlank()) return emptyMap()
        return runCatching { json.decodeFromString(displaySerializer, str) }
            .getOrDefault(emptyMap())
    }

    private fun parseGamepad(value: String): Map<String, GamepadPrefs> =
        runCatching { json.decodeFromString(gamepadSerializer, value) }.getOrDefault(emptyMap())

    suspend fun setGamepadForGame(gameId: String, prefs: GamepadPrefs) {
        context.settingsDataStore.edit { stored ->
            val games = parseGamepad(stored[Keys.GAMEPAD_GAMES].orEmpty()) + (gameId to prefs)
            stored[Keys.GAMEPAD_GAMES] = json.encodeToString(gamepadSerializer, games)
        }
    }

    /** Both map changes belong to one DataStore transaction; global settings are untouched. */
    suspend fun applyGamepadToConsole(consoleId: String, prefs: GamepadPrefs, gameIds: Collection<String>) {
        context.settingsDataStore.edit { stored ->
            val consoles = parseGamepad(stored[Keys.GAMEPAD_CONSOLES].orEmpty()) + (consoleId to prefs)
            val games = gamepadOverridesAfterConsoleApply(
                parseGamepad(stored[Keys.GAMEPAD_GAMES].orEmpty()), gameIds
            )
            stored[Keys.GAMEPAD_CONSOLES] = json.encodeToString(gamepadSerializer, consoles)
            stored[Keys.GAMEPAD_GAMES] = json.encodeToString(gamepadSerializer, games)
        }
    }

    suspend fun setGamepadLayout(layout: String) {
        context.settingsDataStore.edit { it[Keys.GAMEPAD_LAYOUT] = layout }
    }

    suspend fun setGamepadCustomMapping(mapping: Map<Int, Int>) {
        context.settingsDataStore.edit { it[Keys.GAMEPAD_MAPPING] = serializeMapping(mapping) }
    }

    suspend fun setSupporter(unlocked: Boolean, source: String) {
        context.settingsDataStore.edit {
            it[Keys.SUPPORTER_UNLOCKED] = unlocked
            if (unlocked) it[Keys.SUPPORTER_SOURCE] = source else it.remove(Keys.SUPPORTER_SOURCE)
        }
    }

    suspend fun setSupporterName(name: String, listed: Boolean) {
        context.settingsDataStore.edit {
            it[Keys.SUPPORTER_NAME] = name.trim().take(28)
            it[Keys.SUPPORTER_LISTED] = listed
        }
    }

    suspend fun setLibraryShelf(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.LIBRARY_SHELF] = enabled }
    }

    suspend fun setCoverSize(size: String) {
        context.settingsDataStore.edit {
            it[Keys.COVER_SIZE] = when (size) {
                SettingsData.COVER_SMALL, SettingsData.COVER_LARGE -> size
                else -> SettingsData.COVER_MEDIUM
            }
        }
    }

    suspend fun setCustomCover(gameId: String, path: String?) {
        context.settingsDataStore.edit { prefs ->
            val current = parseStringMap(prefs[Keys.CUSTOM_COVERS].orEmpty()).toMutableMap()
            if (path == null) current.remove(gameId) else current[gameId] = path
            prefs[Keys.CUSTOM_COVERS] = json.encodeToString(stringMapSerializer, current)
        }
    }

    suspend fun setAttractMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.ATTRACT_MODE] = enabled }
    }

    suspend fun setUiScanlines(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.UI_SCANLINES] = enabled }
    }

    suspend fun setRetroThemeTest(enabled: Boolean) {
        context.settingsDataStore.edit {
            it[Keys.RETRO_THEME_TEST] = enabled
            it[Keys.COOL_ANIMATIONS] = enabled
            if (enabled) it[Keys.LIBRARY_SHELF] = true
        }
    }

    suspend fun setHideEmptyConsoles(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.HIDE_EMPTY_CONSOLES] = enabled }
    }

    suspend fun setCoolAnimations(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.COOL_ANIMATIONS] = enabled }
    }

    suspend fun setUiSounds(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.UI_SOUNDS] = enabled }
    }

    suspend fun setAutoSaveState(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_SAVE_STATE] = enabled }
    }

    suspend fun setStartupBehavior(behavior: String) {
        context.settingsDataStore.edit {
            it[Keys.STARTUP_BEHAVIOR] = when (behavior) {
                SettingsData.STARTUP_RESUME, SettingsData.STARTUP_FRESH -> behavior
                else -> SettingsData.STARTUP_ASK
            }
        }
    }

    suspend fun setLibrarySort(sort: String) {
        context.settingsDataStore.edit {
            it[Keys.LIBRARY_SORT] = when (sort) {
                SettingsData.SORT_RECENT, SettingsData.SORT_PLAYTIME -> sort
                else -> SettingsData.SORT_TITLE
            }
        }
    }

    suspend fun setConsoleSort(sort: String) {
        context.settingsDataStore.edit {
            it[Keys.CONSOLE_SORT] = sort.takeIf { id -> id in SettingsData.CONSOLE_SORTS }
                ?: SettingsData.CONSOLE_SORT_YEAR
        }
    }

    suspend fun setConsoleCustomOrder(ids: List<String>) {
        val clean = ids.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        context.settingsDataStore.edit {
            if (clean.isEmpty()) it.remove(Keys.CONSOLE_CUSTOM_ORDER)
            else it[Keys.CONSOLE_CUSTOM_ORDER] = json.encodeToString(stringListSerializer, clean)
        }
    }

    private fun parseStringMap(str: String): Map<String, String> {
        if (str.isBlank()) return emptyMap()
        return runCatching { json.decodeFromString(stringMapSerializer, str) }
            .getOrDefault(emptyMap())
    }

    private fun parseStringList(str: String): List<String> {
        if (str.isBlank()) return emptyList()
        return runCatching { json.decodeFromString(stringListSerializer, str) }
            .getOrDefault(emptyList())
    }

    private fun parseMapping(str: String): Map<Int, Int> {
        if (str.isBlank()) return emptyMap()
        return str.split(',').mapNotNull { part ->
            val sub = part.split(':')
            if (sub.size == 2) {
                val k = sub[0].toIntOrNull()
                val v = sub[1].toIntOrNull()
                if (k != null && v != null) k to v else null
            } else null
        }.toMap()
    }

    private fun serializeMapping(m: Map<Int, Int>): String =
        m.entries.joinToString(",") { "${it.key}:${it.value}" }
}
