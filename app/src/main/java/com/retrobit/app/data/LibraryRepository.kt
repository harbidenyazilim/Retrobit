package com.retrobit.app.data

import android.content.Context
import com.retrobit.app.data.db.RetrobitDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class LibraryRepository(private val context: Context) {

    private val db = RetrobitDatabase(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val root = File(context.filesDir, "data").apply { mkdirs() }
    private val libraryFile = File(root, "library.json")
    private val favoritesFile = File(root, "favorites.json")

    private val _library = MutableStateFlow<List<LibraryGame>>(emptyList())
    val library: StateFlow<List<LibraryGame>> = _library.asStateFlow()

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _loadError = MutableStateFlow(false)
    val loadError: StateFlow<Boolean> = _loadError.asStateFlow()
    private val mutationLock = Any()

    init {
        // The JSON migration and first DB read used to block Application.onCreate. On a 2 GB
        // box a large library delays the first frame, so load it on IO and let the UI wait.
        load()
    }

    fun retryLoad() {
        if (_loaded.value) return
        _loadError.value = false
        load()
    }

    private fun load() {
        scope.launch {
            synchronized(mutationLock) {
                runCatching {
                    migrateFromJsonIfNeeded()
                    purgeRetiredMame()
                    _library.value = reconcileTitles(db.getAllGames())
                    _favorites.value = db.getAllFavorites()
                    _loaded.value = true
                }.onFailure {
                    _loadError.value = true
                }
            }
        }
    }

    private suspend fun <T> mutate(block: () -> T): T {
        loaded.first { it }
        return withContext(Dispatchers.IO) { synchronized(mutationLock, block) }
    }

    /**
     * MAME left in 1.16.0. Its rows, favorites, imported romsets and orphaned core go; saves stay.
     * Idempotent, so a crash halfway simply finishes on the next launch.
     */
    private fun purgeRetiredMame() {
        db.purgeConsole("mame")
        runCatching {
            listOfNotNull(context.getExternalFilesDir("roms"), File(context.filesDir, "roms"))
                .map { File(it, "mame") }
                .filter { it.exists() }
                .forEach { it.deleteRecursively() }
            File(context.filesDir, "cores/mame2003_plus_libretro_android.so").delete()
        }
    }

    /** Repairs numeric names cut by the old track-number rule. */
    private fun reconcileTitles(games: List<LibraryGame>): List<LibraryGame> {
        val updates = mutableMapOf<String, String>()
        games.forEach { game ->
            val base = game.fileName.substringBeforeLast('.')
            if (game.title == ImportTitle.legacy(base)) {
                val repaired = ImportTitle.clean(base)
                if (repaired != game.title) updates[game.id] = repaired
            }
        }
        if (updates.isEmpty()) return games
        db.updateTitles(updates)
        return games.map { game -> updates[game.id]?.let { game.copy(title = it) } ?: game }
    }

    private fun migrateFromJsonIfNeeded() {
        runCatching {
            if (libraryFile.exists() && libraryFile.length() > 0) {
                val legacyGames: List<LibraryGame> = json.decodeFromString(libraryFile.readText())
                if (legacyGames.isNotEmpty()) {
                    db.insertGames(legacyGames)
                }
                libraryFile.renameTo(File(root, "library.json.bak"))
            }
        }
        runCatching {
            if (favoritesFile.exists() && favoritesFile.length() > 0) {
                val legacyFavs: Set<String> = json.decodeFromString(favoritesFile.readText())
                if (legacyFavs.isNotEmpty()) {
                    db.insertFavorites(legacyFavs)
                }
                favoritesFile.renameTo(File(root, "favorites.json.bak"))
            }
        }
    }

    // region library

    suspend fun addLibraryGames(games: List<LibraryGame>): List<LibraryGame> = mutate {
        val inserted = db.insertGames(ImportTitle.newGames(_library.value, games))
        if (inserted.isNotEmpty()) {
            _library.update { current -> (inserted + current).sortedByDescending { it.addedAt } }
        }
        inserted
    }

    suspend fun removeLibraryGame(id: String) = mutate {
        db.deleteGame(id)
        _library.value = db.getAllGames()
    }

    suspend fun clearLibrary() = mutate {
        db.clearAllGames()
        _library.value = emptyList()
    }

    suspend fun markPlayed(id: String) = mutate {
        db.markPlayed(id, System.currentTimeMillis())
        _library.update { games ->
            games.map {
                if (it.id == id) it.copy(lastPlayedAt = System.currentTimeMillis(), playCount = it.playCount + 1) else it
            }
        }
    }

    suspend fun addPlayTime(id: String, seconds: Long) = mutate {
        db.addPlayTime(id, seconds)
        _library.update { games ->
            games.map { if (it.id == id) it.copy(playSeconds = it.playSeconds + seconds) else it }
        }
    }

    /** Fire-and-forget flush for a closing player, where the caller's scope is already dying. */
    fun recordPlayTime(id: String, seconds: Long) {
        if (seconds <= 0L) return
        scope.launch { addPlayTime(id, seconds) }
    }

    /** Remembers where a lazily unpacked archive entry was written, keeping the archive as the source. */
    suspend fun markUnpacked(id: String, unpackedPath: String) = mutate {
        db.markUnpacked(id, unpackedPath)
        _library.update { games ->
            games.map { if (it.id == id) it.copy(unpackedPath = unpackedPath) else it }
        }
    }

    fun createLibraryGame(
        title: String,
        consoleId: String,
        filePath: String,
        fileName: String,
        catalogId: String? = null,
        coverUrl: String? = null,
        pendingEntry: String? = null
    ): LibraryGame = LibraryGame(
        id = UUID.randomUUID().toString(),
        title = title,
        consoleId = consoleId,
        filePath = filePath,
        fileName = fileName,
        catalogId = catalogId,
        coverUrl = coverUrl,
        pendingEntry = pendingEntry
    )

    // endregion

    // region favorites

    suspend fun toggleLibraryFavorite(libraryId: String, catalogId: String?): Boolean = mutate {
        val key = if (catalogId != null) favoriteKey(catalogId) else "lib:$libraryId"
        val nowFavorite = db.toggleFavorite(key)
        if (catalogId != null) {
            if (nowFavorite) db.insertFavorites(listOf("lib:$libraryId"))
            else {
                if (db.isFavorite("lib:$libraryId")) db.toggleFavorite("lib:$libraryId")
            }
        }
        _favorites.value = db.getAllFavorites()
        nowFavorite
    }

    fun favoriteKey(catalogId: String) = "catalog:$catalogId"

    // endregion
}
