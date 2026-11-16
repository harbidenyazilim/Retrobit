package com.retrobit.app.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.retrobit.app.data.LibraryGame

class RetrobitDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "retrobit.db"
        private const val DATABASE_VERSION = 2

        const val TABLE_GAMES = "library_games"
        const val COL_ID = "id"
        const val COL_TITLE = "title"
        const val COL_CONSOLE_ID = "console_id"
        const val COL_FILE_PATH = "file_path"
        const val COL_FILE_NAME = "file_name"
        const val COL_ADDED_AT = "added_at"
        const val COL_LAST_PLAYED_AT = "last_played_at"
        const val COL_PLAY_COUNT = "play_count"
        const val COL_CATALOG_ID = "catalog_id"
        const val COL_COVER_URL = "cover_url"
        const val COL_PENDING_ENTRY = "pending_entry"
        const val COL_UNPACKED_PATH = "unpacked_path"
        const val COL_PLAY_SECONDS = "play_seconds"

        const val TABLE_FAVORITES = "favorites"
        const val COL_FAV_KEY = "key"
        const val COL_FAV_ADDED_AT = "added_at"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_GAMES (
                $COL_ID TEXT PRIMARY KEY,
                $COL_TITLE TEXT NOT NULL,
                $COL_CONSOLE_ID TEXT NOT NULL,
                $COL_FILE_PATH TEXT NOT NULL UNIQUE,
                $COL_FILE_NAME TEXT NOT NULL,
                $COL_ADDED_AT INTEGER NOT NULL,
                $COL_LAST_PLAYED_AT INTEGER NOT NULL DEFAULT 0,
                $COL_PLAY_COUNT INTEGER NOT NULL DEFAULT 0,
                $COL_CATALOG_ID TEXT,
                $COL_COVER_URL TEXT,
                $COL_PENDING_ENTRY TEXT,
                $COL_UNPACKED_PATH TEXT,
                $COL_PLAY_SECONDS INTEGER NOT NULL DEFAULT 0
            );
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_games_console ON $TABLE_GAMES ($COL_CONSOLE_ID);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_games_filepath ON $TABLE_GAMES ($COL_FILE_PATH);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_games_last_played ON $TABLE_GAMES ($COL_LAST_PLAYED_AT);")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_FAVORITES (
                $COL_FAV_KEY TEXT PRIMARY KEY,
                $COL_FAV_ADDED_AT INTEGER NOT NULL
            );
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                "ALTER TABLE $TABLE_GAMES ADD COLUMN $COL_PLAY_SECONDS INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    fun getAllGames(): List<LibraryGame> {
        val list = mutableListOf<LibraryGame>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_GAMES,
            null, null, null, null, null,
            "$COL_ADDED_AT DESC"
        )
        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(COL_ID)
            val titleIdx = c.getColumnIndexOrThrow(COL_TITLE)
            val consoleIdx = c.getColumnIndexOrThrow(COL_CONSOLE_ID)
            val pathIdx = c.getColumnIndexOrThrow(COL_FILE_PATH)
            val nameIdx = c.getColumnIndexOrThrow(COL_FILE_NAME)
            val addedIdx = c.getColumnIndexOrThrow(COL_ADDED_AT)
            val playedIdx = c.getColumnIndexOrThrow(COL_LAST_PLAYED_AT)
            val countIdx = c.getColumnIndexOrThrow(COL_PLAY_COUNT)
            val catIdx = c.getColumnIndexOrThrow(COL_CATALOG_ID)
            val coverIdx = c.getColumnIndexOrThrow(COL_COVER_URL)
            val entryIdx = c.getColumnIndexOrThrow(COL_PENDING_ENTRY)
            val unpackedIdx = c.getColumnIndexOrThrow(COL_UNPACKED_PATH)
            val secondsIdx = c.getColumnIndexOrThrow(COL_PLAY_SECONDS)

            while (c.moveToNext()) {
                list += LibraryGame(
                    id = c.getString(idIdx),
                    title = c.getString(titleIdx),
                    consoleId = c.getString(consoleIdx),
                    filePath = c.getString(pathIdx),
                    fileName = c.getString(nameIdx),
                    addedAt = c.getLong(addedIdx),
                    lastPlayedAt = c.getLong(playedIdx),
                    playCount = c.getInt(countIdx),
                    catalogId = c.getString(catIdx),
                    coverUrl = c.getString(coverIdx),
                    pendingEntry = c.getString(entryIdx),
                    unpackedPath = c.getString(unpackedIdx),
                    playSeconds = c.getLong(secondsIdx)
                )
            }
        }
        return list
    }

    fun insertGames(games: List<LibraryGame>): List<LibraryGame> {
        if (games.isEmpty()) return emptyList()
        val db = writableDatabase
        val inserted = mutableListOf<LibraryGame>()
        db.beginTransaction()
        try {
            for (game in games) {
                val cv = ContentValues().apply {
                    put(COL_ID, game.id)
                    put(COL_TITLE, game.title)
                    put(COL_CONSOLE_ID, game.consoleId)
                    put(COL_FILE_PATH, game.filePath)
                    put(COL_FILE_NAME, game.fileName)
                    put(COL_ADDED_AT, game.addedAt)
                    put(COL_LAST_PLAYED_AT, game.lastPlayedAt)
                    put(COL_PLAY_COUNT, game.playCount)
                    put(COL_CATALOG_ID, game.catalogId)
                    put(COL_COVER_URL, game.coverUrl)
                    put(COL_PENDING_ENTRY, game.pendingEntry)
                    put(COL_UNPACKED_PATH, game.unpackedPath)
                    put(COL_PLAY_SECONDS, game.playSeconds)
                }
                val rowId = db.insertWithOnConflict(
                    TABLE_GAMES,
                    null,
                    cv,
                    SQLiteDatabase.CONFLICT_IGNORE
                )
                if (rowId != -1L) {
                    inserted += game
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return inserted
    }

    fun deleteGame(id: String): Boolean {
        val db = writableDatabase
        return db.delete(TABLE_GAMES, "$COL_ID = ?", arrayOf(id)) > 0
    }

    fun clearAllGames() {
        val db = writableDatabase
        db.delete(TABLE_GAMES, null, null)
    }

    /** Drops a retired console's rows and their favorites together; returns the removed row count. */
    fun purgeConsole(consoleId: String): Int {
        val db = writableDatabase
        val ids = mutableListOf<String>()
        val catalogIds = mutableSetOf<String>()
        db.query(TABLE_GAMES, arrayOf(COL_ID, COL_CATALOG_ID), "$COL_CONSOLE_ID = ?", arrayOf(consoleId), null, null, null)
            .use { c ->
                while (c.moveToNext()) {
                    ids += c.getString(0)
                    if (!c.isNull(1)) catalogIds += c.getString(1)
                }
            }
        if (ids.isEmpty()) return 0
        db.beginTransaction()
        try {
            ids.forEach { db.delete(TABLE_FAVORITES, "$COL_FAV_KEY = ?", arrayOf("lib:$it")) }
            catalogIds.forEach { catalogId ->
                val shared = db.query(
                    TABLE_GAMES, arrayOf(COL_ID), "$COL_CATALOG_ID = ? AND $COL_CONSOLE_ID != ?",
                    arrayOf(catalogId, consoleId), null, null, null, "1"
                ).use { it.moveToFirst() }
                if (!shared) db.delete(TABLE_FAVORITES, "$COL_FAV_KEY = ?", arrayOf("catalog:$catalogId"))
            }
            val removed = db.delete(TABLE_GAMES, "$COL_CONSOLE_ID = ?", arrayOf(consoleId))
            db.setTransactionSuccessful()
            return removed
        } finally {
            db.endTransaction()
        }
    }

    fun markPlayed(id: String, timestamp: Long) {
        val db = writableDatabase
        db.execSQL(
            "UPDATE $TABLE_GAMES SET $COL_LAST_PLAYED_AT = ?, $COL_PLAY_COUNT = $COL_PLAY_COUNT + 1 WHERE $COL_ID = ?",
            arrayOf(timestamp, id)
        )
    }

    fun addPlayTime(id: String, seconds: Long) {
        if (seconds <= 0L) return
        val db = writableDatabase
        db.execSQL(
            "UPDATE $TABLE_GAMES SET $COL_PLAY_SECONDS = $COL_PLAY_SECONDS + ? WHERE $COL_ID = ?",
            arrayOf(seconds, id)
        )
    }

    fun markUnpacked(id: String, unpackedPath: String) {
        val db = writableDatabase
        val cv = ContentValues().apply { put(COL_UNPACKED_PATH, unpackedPath) }
        db.update(TABLE_GAMES, cv, "$COL_ID = ?", arrayOf(id))
    }

    fun updateTitles(titles: Map<String, String>) {
        if (titles.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            titles.forEach { (id, title) ->
                val cv = ContentValues().apply { put(COL_TITLE, title) }
                db.update(TABLE_GAMES, cv, "$COL_ID = ?", arrayOf(id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getAllFavorites(): Set<String> {
        val set = mutableSetOf<String>()
        val db = readableDatabase
        val cursor = db.query(TABLE_FAVORITES, arrayOf(COL_FAV_KEY), null, null, null, null, null)
        cursor.use { c ->
            val idx = c.getColumnIndexOrThrow(COL_FAV_KEY)
            while (c.moveToNext()) {
                set += c.getString(idx)
            }
        }
        return set
    }

    fun toggleFavorite(key: String): Boolean {
        val db = writableDatabase
        val exists = isFavorite(key)
        if (exists) {
            db.delete(TABLE_FAVORITES, "$COL_FAV_KEY = ?", arrayOf(key))
            return false
        } else {
            val cv = ContentValues().apply {
                put(COL_FAV_KEY, key)
                put(COL_FAV_ADDED_AT, System.currentTimeMillis())
            }
            db.insertWithOnConflict(TABLE_FAVORITES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            return true
        }
    }

    fun insertFavorites(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            for (k in keys) {
                val cv = ContentValues().apply {
                    put(COL_FAV_KEY, k)
                    put(COL_FAV_ADDED_AT, now)
                }
                db.insertWithOnConflict(TABLE_FAVORITES, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun isFavorite(key: String): Boolean {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_FAVORITES,
            arrayOf(COL_FAV_KEY),
            "$COL_FAV_KEY = ?",
            arrayOf(key),
            null, null, null
        )
        return cursor.use { it.moveToFirst() }
    }
}
