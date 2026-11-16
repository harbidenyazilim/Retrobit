package com.retrobit.app.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.text.Normalizer
import java.util.Locale

class GameRepository(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val catalogText = runCatching {
        context.assets.open("games.json").bufferedReader().use { it.readText() }
    }.getOrDefault("[]")

    val games: List<CatalogGame> = runCatching {
        json.decodeFromString<List<CatalogGame>>(catalogText)
    }.getOrElse { emptyList() }

    // Keep source fields, not CatalogGame's legacy default action/1/4.5 values, for metadata UI.
    private val sourceFields: Map<String, JsonObject> = runCatching {
        json.parseToJsonElement(catalogText).jsonArray.associate { element ->
            val fields = element.jsonObject
            fields.getValue("id").jsonPrimitive.content to fields
        }
    }.getOrDefault(emptyMap())

    fun explicitMetadata(id: String): GameMetadata? {
        val fields = sourceFields[id] ?: return null
        fun text(name: String) = fields[name]?.jsonPrimitive?.contentOrNull
            ?.trim()?.takeIf { it.isNotBlank() && it.lowercase(Locale.ROOT) !in setOf("unknown", "n/a", "—") }
        return GameMetadata(
            year = text("year")?.toIntOrNull()?.takeIf { it in 1950..2100 },
            genre = text("genre"),
            developer = text("developer"),
            players = text("players")?.takeIf { Regex("[1-9]\\d?(?:[-–][1-9]\\d?)?").matches(it) },
            source = "catalog"
        ).takeIf { it.hasDetails }
    }

    private val byId = games.associateBy { it.id }

    fun findById(id: String?): CatalogGame? = id?.let { byId[it] }

    fun matchByTitle(rawTitle: String): CatalogGame? {
        val wanted = normalize(rawTitle)
        if (wanted.isBlank()) return null
        byId.values.firstOrNull { normalize(it.title) == wanted }?.let { return it }
        return byId.values
            .filter { normalize(it.title).length >= 4 }
            .firstOrNull { candidate ->
                val titled = normalize(candidate.title)
                val longer = if (wanted.length >= titled.length) wanted else titled
                val shorter = if (wanted.length >= titled.length) titled else wanted
                if (!longer.startsWith(shorter)) return@firstOrNull false
                // "Golden Axe II" must not inherit the first game's title and cover.
                !SEQUEL_START.containsMatchIn(longer.removePrefix(shorter).trim())
            }
    }

    companion object {
        private val SEQUEL_START = Regex("^(\\d+|ii|iii|iv|v|vi|vii|viii|ix|x)\\b")

        fun normalize(value: String): String {
            val stripped = value
                .replace(Regex("\\s*[\\(\\[].*?[\\)\\]]"), " ")
                .replace(Regex("\\b(the|a|an)\\b", RegexOption.IGNORE_CASE), " ")
            val ascii = Normalizer.normalize(stripped, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
            return ascii.lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")
        }
    }
}
