package com.retrobit.app.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GamepadPrefsTest {
    private val global = GamepadPrefs(SettingsData.GAMEPAD_XBOX, mapOf(104 to 105, 105 to 104))
    private val console = GamepadPrefs(SettingsData.GAMEPAD_NINTENDO)
    private val game = GamepadPrefs(SettingsData.GAMEPAD_CUSTOM, mapOf(96 to 97, 97 to 96))

    @Test fun gameThenConsoleThenLegacyGlobalPrecedence() {
        val settings = SettingsData(
            gamepadLayout = global.layout, gamepadCustomMapping = global.mapping,
            gamepadGameOverrides = mapOf("nes-a" to game),
            gamepadConsoleOverrides = mapOf("nes" to console)
        )
        assertEquals(game, settings.effectiveGamepad("nes-a", "nes"))
        assertEquals(console, settings.effectiveGamepad("later-import", "nes"))
        assertEquals(global, settings.effectiveGamepad("snes-a", "snes"))
    }

    @Test fun applyingConsoleClearsOnlySuppliedStableGameIdsAndLaterGamesInherit() {
        val original = mapOf("nes-a" to game, "nes-b" to game, "snes-a" to global)
        val remaining = gamepadOverridesAfterConsoleApply(original, listOf("nes-a", "nes-b", "missing"))
        assertEquals(mapOf("snes-a" to global), remaining)
        assertEquals(3, original.size)
        val settings = SettingsData(gamepadGameOverrides = remaining, gamepadConsoleOverrides = mapOf("nes" to console))
        assertEquals(console, settings.effectiveGamepad("nes-a", "nes"))
        assertEquals(console, settings.effectiveGamepad("new-nes", "nes"))
        assertEquals(global, settings.effectiveGamepad("snes-a", "snes"))
        assertFalse(settings.gamepadConsoleOverrides.containsKey("snes"))
    }

    @Test fun emptyGameOverrideStillOverridesInheritedMappings() {
        val settings = SettingsData(gamepadGameOverrides = mapOf("a" to GamepadPrefs()), gamepadConsoleOverrides = mapOf("nes" to console))
        assertEquals(GamepadPrefs(), settings.effectiveGamepad("a", "nes"))
    }

    @Test fun prefsRoundTripAndMissingFieldsRemainBackwardsCompatible() {
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(game, json.decodeFromString(GamepadPrefs.serializer(), json.encodeToString(GamepadPrefs.serializer(), game)))
        assertEquals(GamepadPrefs(), json.decodeFromString(GamepadPrefs.serializer(), "{}"))
    }
}
