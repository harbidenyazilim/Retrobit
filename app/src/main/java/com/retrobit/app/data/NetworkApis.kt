package com.retrobit.app.data

import android.net.Uri
import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.io.File
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

private const val USER_AGENT = "Retrobit/1.0 (Android retrospective game library; contact: support@retrobit.app)"

private suspend fun httpGet(
    client: OkHttpClient,
    url: String,
    headers: Map<String, String> = emptyMap()
): String? = withContext(Dispatchers.IO) {
    runCatching {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
        headers.forEach { (key, value) -> builder.header(key, value) }
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }.getOrNull()
}

@Serializable
data class WikiImage(val source: String? = null)

@Serializable
data class WikiSummary(
    val title: String? = null,
    val extract: String? = null,
    val thumbnail: WikiImage? = null,
    val originalimage: WikiImage? = null
) {
    val imageUrl: String? get() = originalimage?.source ?: thumbnail?.source
}

class WikiApi(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, WikiSummary>()

    suspend fun summary(language: String, title: String): WikiSummary? {
        val key = "$language|$title"
        cache[key]?.let { return it }
        val encoded = Uri.encode(title)
        val body = httpGet(
            client,
            "https://$language.wikipedia.org/api/rest_v1/page/summary/$encoded?redirect=true"
        ) ?: return null
        val summary = runCatching { json.decodeFromString<WikiSummary>(body) }.getOrNull() ?: return null
        if (!summary.extract.isNullOrBlank() || summary.imageUrl != null) {
            cache[key] = summary
        }
        return summary
    }

    /** Structured claims only. Summaries, search snippets and descriptions are never metadata. */
    suspend fun gameMetadata(title: String, consoleId: String): GameMetadata? {
        val platformNames = MetadataIdentity.platformNames[consoleId]
            ?: ConsoleCatalog.get(consoleId)?.name?.let { setOf(MetadataIdentity.name(it)) }
            ?: return null
        val cleanTitle = MetadataIdentity.cleanTitle(title)
        val wanted = MetadataIdentity.name(cleanTitle)
        if (wanted.isBlank() || cleanTitle.length > 180 || '|' in cleanTitle) return null
        val ids = linkedSetOf<String>()
        val candidates = listOf(cleanTitle, "$cleanTitle (video game)")
        val pages = requestJson(
            "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2" +
                "&prop=pageprops&ppprop=wikibase_item&redirects=1&titles=${Uri.encode(candidates.joinToString("|"))}"
        )?.obj("query")?.array("pages").orEmpty()
        pages.forEach { page ->
            val objectPage = page as? JsonObject ?: return@forEach
            if (MetadataIdentity.name(objectPage.text("title").orEmpty()) == wanted) {
                objectPage.obj("pageprops")?.text("wikibase_item")?.takeIf(::isEntityId)?.let(ids::add)
            }
        }
        // Search supplies candidates, never a match. Each result still needs exact title, game type
        // and a matching P400 platform. This finds disambiguated older releases, not just remakes.
        val search = requestJson(
            "https://www.wikidata.org/w/api.php?action=wbsearchentities&format=json&language=en" +
                "&type=item&limit=5&search=${Uri.encode(cleanTitle)}"
        )?.array("search").orEmpty()
        search.forEach { hit ->
            (hit as? JsonObject)?.text("id")?.takeIf(::isEntityId)?.let(ids::add)
        }
        if (ids.isEmpty()) return null
        val entities = entities(ids, "claims|labels|aliases|sitelinks") ?: return null
        val sameGames = ids.mapNotNull { id -> entities[id] as? JsonObject }.filter { entity ->
            val names = listOfNotNull(
                entity.obj("labels")?.obj("en")?.text("value"),
                entity.obj("labels")?.obj("mul")?.text("value"),
                entity.obj("sitelinks")?.obj("enwiki")?.text("title")
            ) + listOf("en", "mul").flatMap { language ->
                entity.obj("aliases")?.array(language).orEmpty().mapNotNull { (it as? JsonObject)?.text("value") }
            }
            names.any { MetadataIdentity.name(it) == wanted } &&
                entity.claims("P31").any { it.entityId() == "Q7889" }
        }
        if (sameGames.isEmpty()) return null
        val relatedIds = sameGames.flatMap { entity ->
            listOf("P400", "P136", "P178").flatMap { property -> entity.claims(property).mapNotNull { it.entityId() } } +
                listOf("P577", "P136", "P178", "P1873").flatMap { property ->
                    entity.claims(property).flatMap { it.platformQualifiers() }
                }
        }.distinct()
        if (relatedIds.isEmpty() || relatedIds.size > 50) return null
        val labels = entities(relatedIds, "labels") ?: return null
        fun label(id: String): String? {
            val names = (labels[id] as? JsonObject)?.obj("labels")
            return names?.obj("en")?.text("value") ?: names?.obj("mul")?.text("value")
        }
        fun matchesPlatform(id: String) = label(id)?.let { MetadataIdentity.name(it) in platformNames } == true
        val matched = sameGames.filter { entity ->
            entity.claims("P400").mapNotNull { it.entityId() }.any(::matchesPlatform)
        }
        // Two distinct games with this name on this platform are ambiguous, not a reason to take
        // the search service's first result. No sequel, prefix or fuzzy matching is allowed.
        val entity = matched.singleOrNull() ?: return null
        val platforms = entity.claims("P400").mapNotNull { it.entityId() }
        val onlyThisPlatform = platforms.isNotEmpty() &&
            platforms.size == entity.claims("P400").size && platforms.all(::matchesPlatform)
        fun applies(statement: JsonObject, allowUnqualified: Boolean): Boolean {
            val qualifiers = statement.obj("qualifiers")
            if (qualifiers?.containsKey("P518") == true) return false // edition/part-specific claim
            val qualified = statement.platformQualifiers()
            return if (qualifiers?.containsKey("P400") == true) {
                qualified.isNotEmpty() && qualified.size == qualifiers?.array("P400").orEmpty().size &&
                    qualified.all(::matchesPlatform)
            } else allowUnqualified
        }
        val years = entity.claims("P577").filter { applies(it, onlyThisPlatform) }.mapNotNull { statement ->
            val date = statement.value() as? JsonObject ?: return@mapNotNull null
            if ((date["precision"] as? JsonPrimitive)?.intOrNull?.let { it >= 9 } != true) return@mapNotNull null
            if (listOf("before", "after").any { (date[it] as? JsonPrimitive)?.intOrNull != 0 }) return@mapNotNull null
            if (date.text("calendarmodel")?.substringAfterLast('/') != "Q1985727") return@mapNotNull null
            // P577's structured ISO time is the only year source, never a year found in prose.
            Regex("^\\+(\\d{4})-").find(date.text("time").orEmpty())?.groupValues?.get(1)
                ?.toIntOrNull()?.takeIf { it in 1950..2100 }
        }
        fun claimLabels(property: String, allowUnqualified: Boolean): String? = entity.claims(property)
            .filter { applies(it, allowUnqualified) }.mapNotNull { it.entityId()?.let(::label) }
            .filter { it.isNotBlank() }.distinct().takeIf { it.isNotEmpty() }?.joinToString(" / ")
        val playerCounts = entity.claims("P1873").filter { applies(it, onlyThisPlatform) }.mapNotNull { statement ->
            val quantity = statement.value() as? JsonObject ?: return@mapNotNull null
            if (quantity.text("unit") != "1") return@mapNotNull null
            val amount = quantity.text("amount")?.toBigDecimalOrNull() ?: return@mapNotNull null
            if (listOf("lowerBound", "upperBound").any { bound ->
                    quantity.text(bound)?.toBigDecimalOrNull()?.let { it.compareTo(amount) != 0 } == true
                }) return@mapNotNull null
            runCatching { amount.intValueExact() }.getOrNull()?.takeIf { it in 1..99 }
        }.distinct().singleOrNull()
        val id = entity.text("id")?.takeIf(::isEntityId) ?: return null
        return GameMetadata(
            year = years.minOrNull(),
            genre = claimLabels("P136", true),
            developer = claimLabels("P178", onlyThisPlatform),
            players = playerCounts?.toString(),
            source = "wikidata",
            sourceUrl = "https://www.wikidata.org/wiki/$id"
        ).takeIf { it.hasDetails }
    }

    private suspend fun requestJson(url: String): JsonObject? = httpGet(client, url)?.let { body ->
        runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
    }

    private suspend fun entities(ids: Collection<String>, props: String): JsonObject? = requestJson(
        "https://www.wikidata.org/w/api.php?action=wbgetentities&format=json&languages=en%7Cmul" +
            "&props=${Uri.encode(props)}&ids=${Uri.encode(ids.joinToString("|"))}"
    )?.obj("entities")
}

@Serializable
data class GameMetadata(
    val year: Int? = null,
    val genre: String? = null,
    val developer: String? = null,
    val players: String? = null,
    val source: String = "catalog",
    val sourceUrl: String? = null
) {
    val hasDetails: Boolean get() = year != null || !genre.isNullOrBlank() ||
        !developer.isNullOrBlank() || !players.isNullOrBlank()
}

@Serializable
private data class CachedGameMetadata(val metadata: GameMetadata, val savedAt: Long)

/** Local exact console/title matches first; two remote workers, coalesced requests, success-only disk cache. */
class GameMetadataRepository(
    context: Context,
    private val wikiApi: WikiApi,
    private val games: GameRepository
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile = File(context.filesDir, "data/game_metadata_v1.json")
    private val cache = ConcurrentHashMap<String, CachedGameMetadata>()
    private val misses = ConcurrentHashMap<String, Long>()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<GameMetadata?>>()
    private val workers = Semaphore(2)
    private val diskLock = Mutex()
    private var loaded = false
    private val localIndex = buildMap<String, List<CatalogGame>> {
        games.games.forEach { game ->
            listOfNotNull(game.title, game.wiki).map(MetadataIdentity::name).distinct().forEach { name ->
                val key = "${game.consoleId}|$name"
                put(key, get(key).orEmpty() + game)
            }
        }
    }

    fun localFor(game: LibraryGame): GameMetadata? {
        val candidates = localIndex[key(game)].orEmpty().distinctBy { it.id }
        // An importer's old fuzzy catalogId is not sufficient evidence on its own.
        val catalog = candidates.singleOrNull() ?: return null
        return games.explicitMetadata(catalog.id)
    }

    suspend fun metadataForLibrary(game: LibraryGame): GameMetadata? {
        localFor(game)?.let { return it }
        loadCache()
        val key = key(game)
        cached(key)?.let { return it }
        val now = SystemClock.elapsedRealtime()
        if (misses[key]?.let { now - it < MISS_TTL } == true) return null
        val result = CompletableDeferred<GameMetadata?>()
        pending.putIfAbsent(key, result)?.let { existing ->
            try {
                return existing.await()
            } catch (_: CancellationException) {
                // The preview that owned the request can disappear while the detail screen waits.
                // A live waiter may retry; a cancelled selection must not start more work.
                currentCoroutineContext().ensureActive()
                pending.remove(key, existing)
                return metadataForLibrary(game)
            }
        }
        try {
            val metadata = workers.withPermit {
                cached(key) ?: wikiApi.gameMetadata(lookupTitle(game), game.consoleId)?.also { metadata ->
                    diskLock.withLock {
                        cache[key] = CachedGameMetadata(metadata, System.currentTimeMillis())
                        trimCache()
                        withContext(Dispatchers.IO) {
                            runCatching {
                                AtomicFiles.write(cacheFile) { output ->
                                    output.write(json.encodeToString(cache.toMap()).toByteArray())
                                }
                            }
                        }
                    }
                }
            }
            if (metadata == null) {
                if (misses.size >= MAX_CACHE) misses.clear()
                misses[key] = SystemClock.elapsedRealtime()
            }
            result.complete(metadata)
            return metadata
        } catch (cancelled: CancellationException) {
            result.cancel(cancelled)
            throw cancelled
        } catch (_: Exception) {
            if (misses.size >= MAX_CACHE) misses.clear()
            misses[key] = SystemClock.elapsedRealtime()
            result.complete(null)
            return null
        } finally {
            pending.remove(key, result)
        }
    }

    private fun lookupTitle(game: LibraryGame): String {
        val storedName = game.pendingEntry ?: game.fileName.takeIf { it.isNotBlank() }
        // The importer may have replaced a title using its legacy fuzzy matcher. Its display title
        // or catalogId cannot override the original ROM name for metadata identity.
        val raw = storedName?.substringAfterLast('/')?.substringAfterLast('\\')?.substringBeforeLast('.')
            ?: game.title
        return MetadataIdentity.cleanTitle(raw)
    }

    private fun key(game: LibraryGame) = "${game.consoleId}|${MetadataIdentity.name(lookupTitle(game))}"

    private fun cached(key: String): GameMetadata? = cache[key]?.takeIf {
        System.currentTimeMillis() - it.savedAt in 0..SUCCESS_TTL &&
            it.metadata.source == "wikidata" && it.metadata.hasDetails
    }?.metadata

    private suspend fun loadCache() = diskLock.withLock {
        if (loaded) return@withLock
        val entries = withContext(Dispatchers.IO) {
            runCatching {
                if (cacheFile.isFile) json.decodeFromString<Map<String, CachedGameMetadata>>(cacheFile.readText())
                else emptyMap()
            }.getOrDefault(emptyMap())
        }
        cache.putAll(entries)
        trimCache()
        loaded = true
    }

    private fun trimCache() {
        val now = System.currentTimeMillis()
        cache.entries.removeAll { now - it.value.savedAt !in 0..SUCCESS_TTL }
        cache.entries.sortedByDescending { it.value.savedAt }.drop(MAX_CACHE).forEach { cache.remove(it.key) }
    }

    companion object {
        private const val MAX_CACHE = 512
        private const val SUCCESS_TTL = 30L * 24 * 60 * 60 * 1000
        private const val MISS_TTL = 10L * 60 * 1000
    }
}

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.array(key: String): List<JsonElement> = (this[key] as? JsonArray)?.toList().orEmpty()
private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
private fun isEntityId(id: String) = Regex("Q[1-9]\\d*").matches(id)
private fun JsonObject.claims(property: String): List<JsonObject> = obj("claims")?.array(property).orEmpty()
    .mapNotNull { it as? JsonObject }.filter { it.text("rank") in setOf("normal", "preferred") }
private fun JsonObject.value(): JsonElement? = obj("mainsnak")?.takeIf { it.text("snaktype") == "value" }
    ?.obj("datavalue")?.get("value")
private fun JsonObject.entityId(): String? = (value() as? JsonObject)?.text("id")?.takeIf(::isEntityId)
private fun JsonObject.platformQualifiers(): List<String> = obj("qualifiers")?.array("P400").orEmpty()
    .mapNotNull { qualifier ->
        (qualifier as? JsonObject)?.takeIf { it.text("snaktype") == "value" }
            ?.obj("datavalue")?.obj("value")?.text("id")?.takeIf(::isEntityId)
    }

private object MetadataIdentity {
    // Exact English labels of real platforms, not emulators, platform families or search snippets.
    val platformNames: Map<String, Set<String>> = mapOf(
        "nes" to setOf("Nintendo Entertainment System", "Famicom"),
        "snes" to setOf("Super Nintendo Entertainment System", "Super Famicom"),
        "n64" to setOf("Nintendo 64"),
        "gb" to setOf("Game Boy"), "gbc" to setOf("Game Boy Color"),
        "gba" to setOf("Game Boy Advance"), "nds" to setOf("Nintendo DS"),
        "genesis" to setOf(
            "Sega Genesis", "Sega Mega Drive", "Mega Drive", "Sega Genesis/Mega Drive",
            "Master System", "Sega Master System", "Game Gear", "Sega Game Gear"
        ),
        "ps1" to setOf("PlayStation"),
        "psp" to setOf("PlayStation Portable"),
        "arcade" to setOf("arcade video game", "arcade machine", "arcade video game machine"),
        "pce" to setOf("PC Engine", "TurboGrafx-16"),
        "wonderswan" to setOf("WonderSwan", "WonderSwan Color"),
        "dos" to setOf("DOS", "MS-DOS")
    ).mapValues { (_, names) -> names.map(::name).toSet() }

    fun cleanTitle(value: String): String = value.replace('_', ' ')
        .replace(Regex("^\\d{4}\\s+-\\s+"), "")
        .replace(Regex("\\([^)]*\\)|\\[[^]]*]")) { match ->
        val tag = match.value.substring(1, match.value.length - 1).trim()
        val wikiDisambiguation = Regex(
            "(?:(?:\\d{4}|Game Boy|Nintendo DS|arcade|NES|SNES|PlayStation) )?(?:video game|arcade game)|NES|SNES",
            RegexOption.IGNORE_CASE
        ).matches(tag)
        val romDecoration = tag.split(',', '+').all { token ->
            token.trim().lowercase(Locale.ROOT) in setOf(
                "usa", "europe", "japan", "world", "u", "e", "j", "w", "en", "fr", "de", "es", "it", "ja"
            )
        } || Regex("(?:Rev|Version) [0-9.]+", RegexOption.IGNORE_CASE).matches(tag) ||
            Regex("(?:Disc|Disk) [1-9]\\d?(?: of [1-9]\\d?)?", RegexOption.IGNORE_CASE).matches(tag) ||
            (match.value.startsWith('[') && Regex("[!abfhopt][0-9]?", RegexOption.IGNORE_CASE).matches(tag))
        if (wikiDisambiguation || romDecoration) " " else match.value
    }.replace(Regex("\\s+"), " ").trim()
        .replace(Regex("^(.+),\\s*(The|A|An)$", RegexOption.IGNORE_CASE), "$2 $1")

    fun name(value: String): String = Normalizer.normalize(cleanTitle(value), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT)
        .replace("&", " and ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")
}
