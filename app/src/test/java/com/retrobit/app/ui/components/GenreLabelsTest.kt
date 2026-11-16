package com.retrobit.app.ui.components

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenreLabelsTest {
    @Test
    fun preservesAllCanonicalCodes() {
        val codes = listOf(
            "platform", "adventure", "rpg", "racing", "fighting", "shooter", "puzzle", "strategy",
            "sim", "run", "beat", "action", "horror", "rhythm", "arcade", "sports",
            "action_adventure", "jrpg", "shoot_em_up"
        )
        assertEquals(codes, GenreLabels.resolve(codes.joinToString(" / ")))
    }

    @Test
    fun resolvesCachedAndWikidataLabelsExactly() {
        val cases = mapOf(
            "run and gun" to "run",
            "platform game" to "platform",
            "Platformer" to "platform",
            "action game" to "action",
            "role-playing video game" to "rpg",
            "Japanese role-playing video game" to "jrpg",
            "JRPG" to "jrpg",
            "action-adventure game" to "action_adventure",
            "simulation video game" to "sim",
            "survival horror" to "horror",
            "real-time strategy" to "strategy",
            "fighting game" to "fighting",
            "racing video game" to "racing",
            "sports video game" to "sports",
            "puzzle video game" to "puzzle",
            "rhythm game" to "rhythm",
            "arcade game" to "arcade",
            "adventure game" to "adventure",
            "first-person shooter" to "shooter",
            "beat 'em up" to "beat",
            "shoot 'em up" to "shoot_em_up"
        )
        cases.forEach { (label, code) ->
            assertEquals(label, listOf(code), GenreLabels.resolve(label))
        }
    }

    @Test
    fun splitsCompositeSourceLabelsWithoutInventingGenres() {
        assertEquals(listOf("platform", "action"), GenreLabels.resolve("platform game / action game"))
        assertEquals(listOf("rpg", "jrpg", "action_adventure"),
            GenreLabels.resolve("RPG / Japanese RPG / action-adventure"))
        assertEquals(listOf("run", "action"), GenreLabels.resolve("run and gun / action game"))
        assertEquals(listOf("action", "puzzle", "racing", "sports"),
            GenreLabels.resolve("action;puzzle, racing | sports"))
    }

    @Test
    fun deduplicatesAliasesAndPreservesFirstSourceOrder() {
        assertEquals(listOf("action", "platform", "run"),
            GenreLabels.resolve("action game / Platformer / action / platform game / run / run and gun"))
    }

    @Test
    fun handlesSourcePunctuationAndWhitespace() {
        assertEquals(listOf("beat", "shoot_em_up", "action_adventure", "run"),
            GenreLabels.resolve("  Beat ’em up. / Shoot ‘em up / Action–adventure / RUN‑AND‑GUN  "))
        assertEquals(listOf("run", "platform"),
            GenreLabels.resolve(" Run\u00a0and\u00a0gun /  platform   game.  "))
        assertEquals(listOf("beat"), GenreLabels.resolve("beat ʼem up"))
    }

    @Test
    fun nullBlankAndUnknownHaveNoCanonicalGenre() {
        listOf(null, "", "  ", " / ", "unknown", "Q12345", "experimental narrative genre").forEach {
            assertTrue("label=$it", GenreLabels.resolve(it).isEmpty())
        }
    }

    @Test
    fun dropsUnknownCompositePartsInsteadOfDisplayingExternalText() {
        assertEquals(listOf("platform", "action"),
            GenreLabels.resolve("uncatalogued genre / platform game / mystery / action game"))
        assertTrue(GenreLabels.resolve("unknown / uncatalogued genre").isEmpty())
    }

    @Test
    fun doesNotGuessFromKeywordsTitlesOrPartialMatches() {
        listOf("action-packed story", "Super Mario platform game", "not a puzzle game",
            "sports management story", "rpg-like", "platform game (unverified)").forEach {
            assertTrue("label=$it", GenreLabels.resolve(it).isEmpty())
        }
    }

    @Test
    fun aliasMatchingDoesNotDependOnTurkishDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertEquals(listOf("fighting", "racing", "sim", "jrpg"),
                GenreLabels.resolve("FIGHTING GAME / RACING / SIMULATION / JRPG"))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
