package com.retrobit.app.ui.components

import java.util.Locale

/** Exact sourced-label aliases only; no title matching, substring guesses or external text fallback. */
object GenreLabels {
    private val whitespace = Regex("\\s+")
    private val separators = Regex("\\s*/\\s*|\\s*[;,|]\\s*")

    private fun normalize(label: String): String = label
        .lowercase(Locale.ROOT)
        .replace('\u00a0', ' ')
        .replace('\u2018', '\'')
        .replace('\u2019', '\'')
        .replace('\u02bc', '\'')
        .replace('\u2010', '-')
        .replace('\u2011', '-')
        .replace('\u2013', '-')
        .replace('\u2014', '-')
        .trim()
        .trimEnd('.')
        .replace(whitespace, " ")

    private val aliases: Map<String, String> = buildMap {
        fun register(code: String, vararg labels: String) {
            put(code, code)
            labels.forEach { put(normalize(it), code) }
        }
        register("platform", "platformer", "platform game", "platform video game", "platforming")
        register("adventure", "adventure game", "adventure video game")
        register("rpg", "role-playing game", "role-playing video game", "role playing game",
            "role playing video game", "roleplay game")
        register("racing", "racing game", "racing video game")
        register("fighting", "fighting game", "fighting video game", "versus fighting")
        register("shooter", "shooter game", "shooter video game", "shooting game",
            "first-person shooter", "third-person shooter", "fps", "tps")
        register("puzzle", "puzzle game", "puzzle video game")
        register("strategy", "strategy game", "strategy video game", "real-time strategy",
            "turn-based strategy", "rts")
        register("sim", "simulation", "simulation game", "simulation video game", "simulator")
        register("run", "run and gun", "run-and-gun", "run & gun", "run 'n' gun",
            "run and gun game", "run and gun video game", "run-and-gun video game")
        register("beat", "beat 'em up", "beat em up", "beat'em up", "beat-em-up",
            "beat 'em up game", "beat 'em up video game", "brawler")
        register("action", "action game", "action video game")
        register("horror", "horror game", "horror video game", "survival horror")
        register("rhythm", "rhythm game", "rhythm video game", "music game", "music video game")
        register("arcade", "arcade game", "arcade video game")
        register("sports", "sport", "sports game", "sports video game", "sport game")
        register("action_adventure", "action-adventure", "action adventure", "action-adventure game",
            "action-adventure video game", "action adventure game", "action adventure video game")
        register("jrpg", "japanese role-playing game", "japanese role-playing video game",
            "japanese role playing game", "japanese role playing video game", "japanese rpg")
        register("shoot_em_up", "shoot 'em up", "shoot em up", "shoot'em up", "shoot-em-up",
            "shoot 'em up game", "shoot 'em up video game", "shmup")
    }

    /** Canonical codes in source order, deduplicated after alias resolution. Unknown parts disappear. */
    fun resolve(genre: String?): List<String> = genre
        ?.split(separators)
        ?.mapNotNull { aliases[normalize(it)] }
        ?.distinct()
        .orEmpty()
}
