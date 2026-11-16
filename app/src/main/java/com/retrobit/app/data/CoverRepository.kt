package com.retrobit.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds box art for the games that live in the user's own ROMS folder.
 *
 * The libretro-thumbnail repositories are keyed by the exact No-Intro file name, so the ROM file
 * name is the best clue available; scene dumps and region-code shorthand need normalising before
 * they hit it. Hits are the only thing written to disk: a miss suppresses re-probing for the rest
 * of the session but stays open to a later version or a calmer network, because a stale negative is
 * how games end up with no artwork for good.
 */
class CoverRepository(
    private val context: Context,
    private val wikiApi: WikiApi,
    private val gameRepository: GameRepository
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile = File(context.filesDir, "data/covers.json")
    private val cache = ConcurrentHashMap<String, String>()
    private val probed = ConcurrentHashMap.newKeySet<String>()
    private val semaphore = Semaphore(4)

    init {
        runCatching {
            if (cacheFile.exists()) {
                val map = json.decodeFromString<Map<String, String>>(cacheFile.readText())
                cache.putAll(map)
            }
        }
    }

    suspend fun coverFor(game: CatalogGame): String? {
        game.cover?.let { return it }
        val key = "c:${game.id}"
        coverOf(key)?.let { return it }
        if (key in probed) return null
        return semaphore.withPermit { catalogCover(game) }
    }

    private suspend fun catalogCover(game: CatalogGame): String? {
        game.cover?.let { return it }
        val key = "c:${game.id}"
        coverOf(key)?.let { return it }
        if (key in probed) return null
        val name = game.wiki ?: game.title
        val system = ConsoleCatalog.get(game.consoleId)?.libretroSystem
        var url = firstReachable(libretroCandidates(system, name))
        if (url == null) url = wikiApi.summary("en", name)?.imageUrl
        if (url == null) url = firstReachable(libretroCandidates(system, game.title))
        if (url == null) url = fallbackArt(game.title)
        store(key, url)
        return url
    }

    suspend fun coverForLibrary(game: LibraryGame): String? {
        game.coverUrl?.let { return it }
        val key = "l:${game.id}"
        coverOf(key)?.let { return it }
        if (key in probed) return null
        return semaphore.withPermit {
            coverOf(key)?.let { return@withPermit it }
            if (key in probed) return@withPermit null
            val system = ConsoleCatalog.thumbnailSystem(game.consoleId, game.pendingEntry ?: game.fileName)
            val stored = game.fileName.substringBeforeLast('.').ifBlank { game.title }
            val resolved = game.title
            var url = firstReachable(libretroCandidates(system, resolved))
            if (url == null && resolved != stored) {
                url = firstReachable(libretroCandidates(system, stored))
            }
            if (url == null) {
                val catalog = gameRepository.findById(game.catalogId)
                    ?: gameRepository.matchByTitle(resolved)
                        ?.takeIf { it.consoleId == game.consoleId }
                if (catalog != null) url = catalogCover(catalog)
            }
            if (url == null) url = fallbackArt(resolved)
            store(key, url)
            url
        }
    }

    /** Every thumbnail URL worth probing for one ROM name, best guess first. */
    fun libretroCandidates(system: String?, name: String): List<String> {
        if (system == null) return emptyList()
        val names = thumbnailNames(name)
        val urls = mutableListOf<String>()
        names.forEachIndexed { index, candidate ->
            val galleries = if (index < 2) GALLERIES else GALLERIES.take(1)
            galleries.forEach { urls += thumbnailUrl(system, it, candidate) }
        }
        // Scene dumps drop the region tag the repository keys on, so probe the plain title with
        // each common region attached.
        names.lastOrNull()?.takeIf { !it.contains('(') }?.let { bare ->
            REGION_NAMES.forEach { urls += thumbnailUrl(system, "Named_Boxarts", "$bare ($it)") }
        }
        return urls
    }

    private fun thumbnailUrl(system: String, gallery: String, name: String): String {
        val encoded = URLEncoder.encode("$name.png", "UTF-8").replace("+", "%20")
        return "https://raw.githubusercontent.com/libretro-thumbnails/$system/master/$gallery/$encoded"
    }

    /**
     * A ROM file name is a No-Intro name plus scene decoration. Each step removes a little more of
     * that decoration, so the exact dump name still wins while `1421 - Game (E)(Rising Sun)` finds
     * the `Game (Europe)` artwork.
     */
    private fun thumbnailNames(raw: String): List<String> {
        val base = raw.trim()
        if (base.isEmpty()) return emptyList()
        val noSceneNumber = base.replace(Regex("^\\d{2,4}\\s+-\\s+"), "")
        val noMarker = noSceneNumber.replace(Regex("\\s*#.*$"), "")
        val noTags = noMarker.replace(Regex("\\s*\\[[^\\]]*]"), "")
        val cleaned = noTags.replace(Regex("\\s+"), " ").trim()
        val expanded = expandRegions(cleaned)
        val regionsOnly = keepKnownParentheticals(expanded)
        val bare = expanded.replace(Regex("\\s*\\([^)]*\\)"), "").replace(Regex("\\s+"), " ").trim()
        val names = buildList {
            addAll(listOf(base, cleaned, expanded, regionsOnly, bare))
            // Scene renaming spells out the ampersand No-Intro names keep.
            addAll(listOf(regionsOnly, bare).map { it.replace(Regex("\\s+and\\s+"), " & ") })
        }
        return names.filter { it.isNotBlank() }.distinct()
    }

    private fun expandRegions(name: String): String =
        REGION_CODES.entries.fold(name) { acc, (code, full) ->
            acc.replace(Regex("\\($code\\)"), "($full)")
        }

    /** Drops release-group and other non-region parentheticals that No-Intro names never carry. */
    private fun keepKnownParentheticals(name: String): String {
        val kept = Regex("\\([^)]*\\)").replace(name) { match ->
            val inner = match.value.removeSurrounding("(", ")")
            val tokens = inner.split(',', '/', '+', ' ').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (tokens.isNotEmpty() && tokens.all { it in REGION_TOKENS }) match.value else ""
        }
        return kept.replace(Regex("\\s+"), " ").trim()
    }

    private fun coverOf(key: String): String? = cache[key]

    private suspend fun store(key: String, url: String?) {
        probed += key
        if (url != null) {
            cache[key] = url
            persist()
        }
    }

    /**
     * The last resort for dumps no thumbnail repository names. Wikipedia answers "Dino" with a
     * dinosaur, so a single word is never worth asking, the page title has to read back as the same
     * game, and only English Wikipedia's own fair-use media counts — its commons gallery is full of
     * franchise logos that pass for cover art until they are on screen. Games whose plain title is
     * a comic, film or franchise page are usually filed under "Title (video game)".
     */
    private suspend fun fallbackArt(title: String): String? {
        val wanted = GameRepository.normalize(title)
        if (!wanted.contains(' ')) return null
        for (candidate in listOf(title, "$title (video game)")) {
            val summary = wikiApi.summary("en", candidate) ?: continue
            val heading = summary.title?.let { GameRepository.normalize(it) } ?: continue
            if (heading != wanted && !heading.startsWith("$wanted ") && !wanted.startsWith("$heading ")) {
                continue
            }
            summary.imageUrl?.takeIf { "/wikipedia/en/" in it }?.let { return it }
        }
        return null
    }

    private suspend fun firstReachable(urls: List<String>): String? = withContext(Dispatchers.IO) {
        for (url in urls) {
            val ok = runCatching {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                connection.instanceFollowRedirects = true
                try {
                    connection.responseCode == 200
                } finally {
                    connection.disconnect()
                }
            }.getOrDefault(false)
            if (ok) return@withContext url
        }
        null
    }

    private suspend fun persist() = withContext(Dispatchers.IO) {
        runCatching {
            AtomicFiles.write(cacheFile) { it.write(json.encodeToString(cache.toMap()).toByteArray()) }
        }
    }

    companion object {
        private val GALLERIES = listOf("Named_Boxarts", "Named_Titles", "Named_Snaps")
        private val REGION_NAMES = listOf("USA", "Europe", "World", "Japan")

        private val REGION_CODES = mapOf(
            "U" to "USA",
            "E" to "Europe",
            "J" to "Japan",
            "W" to "World",
            "K" to "Korea",
            "F" to "France",
            "G" to "Germany",
            "I" to "Italy",
            "S" to "Spain",
            "N" to "Netherlands",
            "P" to "Brazil",
            "B" to "Scandinavia",
            "H" to "Portugal",
            "L" to "Latin",
            "A" to "Australia",
            "C" to "China",
            "R" to "Russia",
            "As" to "Asia"
        )

        private val REGION_TOKENS = setOf(
            "usa", "us", "u", "e", "j", "w", "k", "f", "g", "i", "s", "n", "p", "b", "h", "l",
            "a", "c", "r", "as", "europe", "japan", "world", "australia", "brazil", "canada",
            "france", "germany", "spain", "italy", "netherlands", "scandinavia", "portugal",
            "korea", "china", "russia", "asia", "united", "kingdom", "england", "taiwan",
            "en", "fr", "de", "es", "it", "nl", "sv", "da", "no", "fi", "ja", "pt", "ru", "pl",
            "tr", "zh", "ko", "hu", "cs", "he", "el", "th", "id", "ro", "uk"
        )
    }
}
