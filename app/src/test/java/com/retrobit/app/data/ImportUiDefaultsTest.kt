package com.retrobit.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportUiDefaultsTest {
    @Test
    fun artworkDefaultsOnForSupporters() {
        assertTrue(SettingsData().retroThemeTest)
        assertTrue(SettingsData(supporterUnlocked = true).retroTheme)
    }

    @Test
    fun shelfDefaultsOnForSupportersAndStaysLockedOtherwise() {
        assertTrue(SettingsData(supporterUnlocked = true).effectiveLibraryShelf)
        assertFalse(SettingsData().effectiveLibraryShelf)
        assertFalse(SettingsData(supporterUnlocked = true, libraryShelf = false).effectiveLibraryShelf)
    }

    @Test
    fun artworkRemainsLockedWithoutSupporter() {
        assertFalse(SettingsData().retroTheme)
    }

    @Test
    fun explicitStandardSelectionIsPreserved() {
        assertFalse(SettingsData(supporterUnlocked = true, retroThemeTest = false).retroTheme)
    }

    @Test
    fun uiSoundsAreEnabledByDefault() {
        assertTrue(SettingsData().uiSounds)
    }

    @Test
    fun explicitSilentSelectionIsPreserved() {
        assertFalse(SettingsData(uiSounds = false).uiSounds)
    }

    @Test
    fun unknownTotalsHaveNoFraction() {
        assertNull(ImportProgress(scanning = true).fraction)
        assertNull(ImportProgress(current = 1, total = -1).fraction)
    }

    @Test
    fun nestedArchiveOpeningHasNoFractionEvenAfterKnownItemsFinish() {
        assertNull(ImportProgress(current = 296, total = 296, note = "game.7z").fraction)
        assertNull(ImportProgress(current = 1, total = 2, scanning = true).fraction)
    }

    @Test
    fun progressReflectsCompletedItems() {
        assertEquals(0f, ImportProgress(current = 0, total = 1).fraction!!, 0f)
        assertEquals(0.5f, ImportProgress(current = 1, total = 2).fraction!!, 0f)
        assertEquals(1f, ImportProgress(current = 2, total = 2).fraction!!, 0f)
    }

    @Test
    fun progressStaysWithinIndicatorBounds() {
        assertEquals(0f, ImportProgress(current = -1, total = 2).fraction!!, 0f)
        assertEquals(1f, ImportProgress(current = 3, total = 2).fraction!!, 0f)
    }
}
