package com.retrobit.app.ui.components

import android.view.KeyEvent
import com.retrobit.app.data.SettingsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses compile-time key constants only; never constructs Android events or navigation. */
class GamepadInputTest {
    private val faceKeys = listOf(
        KeyEvent.KEYCODE_BUTTON_1 to KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_2 to KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_3 to KeyEvent.KEYCODE_BUTTON_X,
        KeyEvent.KEYCODE_BUTTON_4 to KeyEvent.KEYCODE_BUTTON_Y
    )

    private val otherKeys = listOf(
        KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_BUTTON_START,
        KeyEvent.KEYCODE_BUTTON_L1,
        KeyEvent.KEYCODE_BUTTON_R1,
        KeyEvent.KEYCODE_BUTTON_L2,
        KeyEvent.KEYCODE_BUTTON_R2,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_UNKNOWN,
        -99
    )

    @Test
    fun heldConfirmConsumesOnlyItsMatchingRelease() {
        assertFalse(GamepadKeyGuard.consumeCenterUp())
        GamepadKeyGuard.armCenterUp()
        assertTrue(GamepadKeyGuard.consumeCenterUp())
        assertFalse(GamepadKeyGuard.consumeCenterUp())
    }

    @Test
    fun canonicalKeyMapsNumberedFaceButtons() {
        faceKeys.forEach { (numbered, canonical) ->
            assertEquals("key=$numbered", canonical, GamepadInput.canonicalKey(numbered))
        }
    }

    @Test
    fun canonicalKeyPreservesFaceAndOtherKeys() {
        (faceKeys.map { it.second } + otherKeys).forEach { key ->
            assertEquals("key=$key", key, GamepadInput.canonicalKey(key))
        }
    }

    @Test
    fun xboxUsesCanonicalIdentityForAllKeys() {
        faceKeys.forEach { (numbered, canonical) ->
            assertEquals(canonical, mapped(numbered, SettingsData.GAMEPAD_XBOX))
            assertEquals(canonical, mapped(canonical, SettingsData.GAMEPAD_XBOX))
        }
        otherKeys.forEach { key ->
            assertEquals(key, mapped(key, SettingsData.GAMEPAD_XBOX))
        }
    }

    @Test
    fun xboxIgnoresCustomMappings() {
        val custom = mapOf(
            KeyEvent.KEYCODE_BUTTON_1 to KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_A to KeyEvent.KEYCODE_BUTTON_X
        )
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_A,
            mapped(KeyEvent.KEYCODE_BUTTON_1, SettingsData.GAMEPAD_XBOX, custom)
        )
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_A,
            mapped(KeyEvent.KEYCODE_BUTTON_A, SettingsData.GAMEPAD_XBOX, custom)
        )
    }

    @Test
    fun nintendoSwapsCanonicalAndNumberedFaceButtons() {
        val expected = listOf(
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_X
        )
        faceKeys.zip(expected).forEach { (keys, target) ->
            assertEquals(target, mapped(keys.first, SettingsData.GAMEPAD_NINTENDO))
            assertEquals(target, mapped(keys.second, SettingsData.GAMEPAD_NINTENDO))
        }
    }

    @Test
    fun nintendoPreservesOtherKeysAndIgnoresCustomMappings() {
        otherKeys.forEach { key ->
            assertEquals(
                key,
                mapped(key, SettingsData.GAMEPAD_NINTENDO, mapOf(key to KeyEvent.KEYCODE_BUTTON_A))
            )
        }
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_B,
            mapped(
                KeyEvent.KEYCODE_BUTTON_1,
                SettingsData.GAMEPAD_NINTENDO,
                mapOf(KeyEvent.KEYCODE_BUTTON_1 to KeyEvent.KEYCODE_BUTTON_Y)
            )
        )
    }

    @Test
    fun unknownLayoutFallsBackToCanonicalIdentity() {
        listOf("", "unknown").forEach { layout ->
            faceKeys.forEach { (numbered, canonical) ->
                assertEquals(canonical, mapped(numbered, layout, mapOf(numbered to 0)))
            }
        }
    }

    @Test
    fun customUnmappedKeysUseCanonicalIdentityRatherThanNintendoSwaps() {
        listOf(emptyMap(), mapOf(KeyEvent.KEYCODE_BUTTON_START to KeyEvent.KEYCODE_BUTTON_SELECT))
            .forEach { custom ->
                faceKeys.forEach { (numbered, canonical) ->
                    assertEquals(canonical, mapped(numbered, SettingsData.GAMEPAD_CUSTOM, custom))
                    assertEquals(canonical, mapped(canonical, SettingsData.GAMEPAD_CUSTOM, custom))
                }
                assertEquals(
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    mapped(KeyEvent.KEYCODE_DPAD_LEFT, SettingsData.GAMEPAD_CUSTOM, custom)
                )
            }
    }

    @Test
    fun customExactPhysicalKeyTakesPriorityOverCanonicalFallback() {
        faceKeys.forEach { (numbered, canonical) ->
            val custom = mapOf(
                numbered to KeyEvent.KEYCODE_DPAD_LEFT,
                canonical to KeyEvent.KEYCODE_DPAD_RIGHT
            )
            assertEquals(
                KeyEvent.KEYCODE_DPAD_LEFT,
                mapped(numbered, SettingsData.GAMEPAD_CUSTOM, custom)
            )
            assertEquals(
                KeyEvent.KEYCODE_DPAD_RIGHT,
                mapped(canonical, SettingsData.GAMEPAD_CUSTOM, custom)
            )
        }
    }

    @Test
    fun customNumberedKeyFallsBackToItsCanonicalMapping() {
        faceKeys.forEach { (numbered, canonical) ->
            assertEquals(
                KeyEvent.KEYCODE_BUTTON_START,
                mapped(
                    numbered,
                    SettingsData.GAMEPAD_CUSTOM,
                    mapOf(canonical to KeyEvent.KEYCODE_BUTTON_START)
                )
            )
        }
    }

    @Test
    fun customMappingIsOnePassAndPreservesTheConfiguredTarget() {
        val custom = mapOf(
            KeyEvent.KEYCODE_BUTTON_A to KeyEvent.KEYCODE_BUTTON_2,
            KeyEvent.KEYCODE_BUTTON_2 to KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_START to KeyEvent.KEYCODE_UNKNOWN
        )
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_2,
            mapped(KeyEvent.KEYCODE_BUTTON_1, SettingsData.GAMEPAD_CUSTOM, custom)
        )
        assertEquals(
            KeyEvent.KEYCODE_UNKNOWN,
            mapped(KeyEvent.KEYCODE_BUTTON_START, SettingsData.GAMEPAD_CUSTOM, custom)
        )
        // A physical-key-only mapping does not apply backwards to its canonical key.
        assertEquals(
            KeyEvent.KEYCODE_BUTTON_A,
            mapped(
                KeyEvent.KEYCODE_BUTTON_A,
                SettingsData.GAMEPAD_CUSTOM,
                mapOf(KeyEvent.KEYCODE_BUTTON_1 to KeyEvent.KEYCODE_BUTTON_Y)
            )
        )
    }

    @Test
    fun menuPairAcceptsSelectAndStart() {
        assertTrue(
            GamepadInput.menuPairHeld(setOf(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START))
        )
    }

    @Test
    fun menuPairAcceptsL2AndR2() {
        assertTrue(
            GamepadInput.menuPairHeld(setOf(KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2))
        )
    }

    @Test
    fun menuPairRejectsEmptySingleAndAllUnrelatedPairs() {
        assertFalse(GamepadInput.menuPairHeld(emptySet()))
        val candidates = otherKeys + faceKeys.flatMap { listOf(it.first, it.second) }
        val accepted = setOf(
            setOf(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START),
            setOf(KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2)
        )
        candidates.forEachIndexed { index, first ->
            assertFalse("single=$first", GamepadInput.menuPairHeld(setOf(first)))
            candidates.drop(index + 1).forEach { second ->
                val pair = setOf(first, second)
                assertEquals("pair=$pair", pair in accepted, GamepadInput.menuPairHeld(pair))
            }
        }
    }

    @Test
    fun menuPairAllowsAdditionalHeldKeys() {
        assertTrue(
            GamepadInput.menuPairHeld(
                setOf(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_A)
            )
        )
        assertTrue(
            GamepadInput.menuPairHeld(
                setOf(KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_L1)
            )
        )
        assertTrue(
            GamepadInput.menuPairHeld(
                setOf(
                    KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START,
                    KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2
                )
            )
        )
    }

    @Test
    fun directionIsNeutralStrictlyInsideHalfAxisDeadzone() {
        listOf(-0.4999f, -0.12f, 0f, 0.12f, 0.4999f).forEach { x ->
            listOf(-0.4999f, 0f, 0.4999f).forEach { y ->
                assertEquals("x=$x y=$y", 0, GamepadInput.direction(x, y))
            }
        }
    }

    @Test
    fun directionActivatesAtExactlyHalfAxis() {
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, GamepadInput.direction(-0.5f, 0f))
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, GamepadInput.direction(0.5f, 0f))
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, GamepadInput.direction(0f, -0.5f))
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, GamepadInput.direction(0f, 0.5f))
    }

    @Test
    fun directionUsesDominantHorizontalAxisInEveryQuadrant() {
        listOf(-0.8f, 0.8f).forEach { y ->
            assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, GamepadInput.direction(-0.9f, y))
            assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, GamepadInput.direction(0.9f, y))
        }
    }

    @Test
    fun directionUsesDominantVerticalAxisInEveryQuadrant() {
        listOf(-0.8f, 0.8f).forEach { x ->
            assertEquals(KeyEvent.KEYCODE_DPAD_UP, GamepadInput.direction(x, -0.9f))
            assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, GamepadInput.direction(x, 0.9f))
        }
    }

    @Test
    fun directionBreaksEqualMagnitudeTiesUsingVerticalAxis() {
        listOf(0.5f, 0.9f).forEach { magnitude ->
            listOf(-magnitude, magnitude).forEach { x ->
                assertEquals(KeyEvent.KEYCODE_DPAD_UP, GamepadInput.direction(x, -magnitude))
                assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, GamepadInput.direction(x, magnitude))
            }
        }
    }

    @Test
    fun deadzoneDefaultIncludesBothThresholdEndpoints() {
        listOf(-0.12f, -0.1f, 0f, 0.1f, 0.12f).forEach { value ->
            assertEquals("value=$value", 0f, GamepadInput.deadzone(value), 0f)
        }
    }

    @Test
    fun deadzoneNeverUsesAFlatBelowTheMinimum() {
        listOf(-0.5f, 0f, 0.05f, 0.12f).forEach { flat ->
            listOf(-0.12f, 0f, 0.12f).forEach { value ->
                assertEquals("value=$value flat=$flat", 0f, GamepadInput.deadzone(value, flat), 0f)
            }
            assertEquals(0.1201f, GamepadInput.deadzone(0.1201f, flat), 0f)
            assertEquals(-0.1201f, GamepadInput.deadzone(-0.1201f, flat), 0f)
        }
    }

    @Test
    fun deadzoneUsesLargerDeviceFlatInclusively() {
        listOf(-0.4f, -0.2f, 0f, 0.2f, 0.4f).forEach { value ->
            assertEquals("value=$value", 0f, GamepadInput.deadzone(value, 0.4f), 0f)
        }
    }

    @Test
    fun deadzonePreservesValuesOutsideThresholdWithoutRescaling() {
        listOf(-1f, -0.6f, -0.1201f, 0.1201f, 0.6f, 1f).forEach { value ->
            assertEquals(value, GamepadInput.deadzone(value), 0f)
        }
        assertEquals(0.4001f, GamepadInput.deadzone(0.4001f, 0.4f), 0f)
        assertEquals(-0.4001f, GamepadInput.deadzone(-0.4001f, 0.4f), 0f)
    }

    @Test
    fun deadzoneClampsOutputToUnitRange() {
        listOf(1.01f, 2f, Float.POSITIVE_INFINITY).forEach { value ->
            assertEquals(1f, GamepadInput.deadzone(value), 0f)
        }
        listOf(-1.01f, -2f, Float.NEGATIVE_INFINITY).forEach { value ->
            assertEquals(-1f, GamepadInput.deadzone(value), 0f)
        }
    }

    @Test
    fun shoulderAliasesAndSwapsAreLayoutIndependent() {
        val aliases = listOf(
            KeyEvent.KEYCODE_BUTTON_5 to KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_L to KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_6 to KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_R to KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_7 to KeyEvent.KEYCODE_BUTTON_L2,
            KeyEvent.KEYCODE_BUTTON_8 to KeyEvent.KEYCODE_BUTTON_R2
        )
        var mapping = emptyMap<Int, Int>()
        GamepadInput.swapPairs.drop(2).forEach { (first, second) ->
            mapping = GamepadInput.togglePair(mapping, first, second)
        }
        listOf(SettingsData.GAMEPAD_XBOX, SettingsData.GAMEPAD_NINTENDO, SettingsData.GAMEPAD_CUSTOM).forEach { layout ->
            aliases.forEach { (alias, canonical) ->
                assertEquals(canonical, GamepadInput.canonicalKey(alias))
                assertEquals(mapping[canonical], mapped(alias, layout, mapping))
                assertEquals(mapping[canonical], mapped(canonical, layout, mapping))
            }
        }
    }

    @Test
    fun pairTogglePreservesOtherPairsAndClearsAliases() {
        val first = KeyEvent.KEYCODE_BUTTON_L2
        val second = KeyEvent.KEYCODE_BUTTON_R2
        val original = mapOf(KeyEvent.KEYCODE_BUTTON_A to KeyEvent.KEYCODE_BUTTON_B)
        val swapped = GamepadInput.togglePair(original + (KeyEvent.KEYCODE_BUTTON_7 to second), first, second)
        assertTrue(GamepadInput.pairSwapped(swapped, first, second))
        assertFalse(swapped.containsKey(KeyEvent.KEYCODE_BUTTON_7))
        assertEquals(original, GamepadInput.togglePair(swapped, first, second))
    }

    @Test
    fun physicalTriggerMergerDoesNotCreateASwappedComboOrPrematureRelease() {
        val sources = PhysicalTriggerSources()
        val left = KeyEvent.KEYCODE_BUTTON_L2
        val right = KeyEvent.KEYCODE_BUTTON_R2
        val mapping = GamepadInput.togglePair(emptyMap(), left, right)
        assertTrue(sources.update(KeyEvent.KEYCODE_BUTTON_7, "digital", true))
        assertTrue(sources.update(left, "axis", true))
        assertEquals(right, mapped(left, SettingsData.GAMEPAD_XBOX, mapping))
        assertFalse(sources.held(right))
        fun combo() = GamepadInput.menuPairHeld(setOf(left, right).filter { sources.held(it) }.toSet())
        assertFalse(combo())
        assertTrue(sources.update(left, "digital", false)) // Axis still owns this physical trigger.
        assertFalse(combo())
        sources.update(right, "axis", true)
        assertTrue(combo())
        assertFalse(sources.update(left, "axis", false))
        assertFalse(combo())
        sources.clear()
        assertFalse(sources.held(left))
        assertFalse(sources.held(right))
    }

    @Test
    fun digitalTriggerAliasesAndDevicesMergeWithoutStuckKeys() {
        val sources = PhysicalTriggerSources()
        val left = KeyEvent.KEYCODE_BUTTON_L2
        sources.update(left, "digital:device1:L2", true)
        sources.update(KeyEvent.KEYCODE_BUTTON_7, "digital:device1:7", true)
        sources.update(left, "digital:device2:L2", true)
        assertTrue(sources.update(left, "digital:device1:L2", false))
        assertTrue(sources.update(left, "digital:device1:7", false))
        assertFalse(sources.update(left, "digital:device2:L2", false))
    }

    private fun mapped(key: Int, layout: String, custom: Map<Int, Int> = emptyMap()): Int =
        GamepadInput.mappedKey(key, layout, custom)
}
