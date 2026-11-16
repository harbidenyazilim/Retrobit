package com.retrobit.app.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.retrobit.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class SaveBackup(
    val name: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val file: File? = null,
    val uri: Uri? = null
)

data class BackupResult(val backup: SaveBackup, val fileCount: Int)

class SaveBackupManager(private val context: Context, private val savesDir: File) {

    /**
     * The store build cannot write to public Downloads with a plain File, so it creates the
     * same visible folder through MediaStore instead.
     */
    private val useMediaStore: Boolean =
        !BuildConfig.ALL_FILES_ACCESS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private val relativePath: String =
        "${Environment.DIRECTORY_DOWNLOADS}/Retrobit_Backups/"

    val defaultBackupFolder: File
        get() {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dir = File(downloads, "Retrobit_Backups")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    suspend fun listBackups(): List<SaveBackup> = withContext(Dispatchers.IO) {
        runCatching {
            if (useMediaStore) mediaStoreBackups() else fileBackups()
        }.getOrDefault(emptyList())
    }

    suspend fun createBackup(): BackupResult? = withContext(Dispatchers.IO) {
        runCatching {
            if (!savesDir.exists()) return@runCatching null
            val files = savesDir.walkTopDown()
                .filter { it.isFile && !it.name.startsWith(".write-") }.toList()
            if (files.isEmpty()) return@runCatching null
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val name = "retrobit_saves_backup_${timestamp}_${UUID.randomUUID()}.zip"
            if (useMediaStore) createInDownloads(name, files) else createInFolder(name, files)
        }.getOrNull()
    }

    suspend fun restoreBackup(backup: SaveBackup): Int = withContext(Dispatchers.IO) {
        runCatching {
            val input = openInput(backup) ?: return@runCatching 0
            SaveRestoreTransaction.restore(savesDir, input)
        }.getOrDefault(0)
    }

    /** Startup hook: finishes or rolls back a restore killed mid-commit. */
    suspend fun recoverInterruptedRestore(): Int = withContext(Dispatchers.IO) {
        runCatching { SaveRestoreTransaction.recoverInterruptedRestore(savesDir) }.getOrDefault(0)
    }

    private fun createInFolder(name: String, files: List<File>): BackupResult? {
        val folder = defaultBackupFolder
        if (!folder.isDirectory && !folder.mkdirs()) return null
        val target = File(folder, name)
        return try {
            val count = writeZip(FileOutputStream(target), files)
            BackupResult(
                SaveBackup(
                    name = name,
                    sizeBytes = target.length(),
                    modifiedAt = target.lastModified(),
                    file = target
                ),
                count
            )
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createInDownloads(name: String, files: List<File>): BackupResult? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            val stream = resolver.openOutputStream(uri)
                ?: throw IOException("Cannot open pending backup output")
            val count = writeZip(stream, files)
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null
            )
            val meta = readMeta(uri)
            return BackupResult(
                SaveBackup(
                    name = meta?.first ?: name,
                    sizeBytes = meta?.second ?: 0L,
                    modifiedAt = meta?.third ?: System.currentTimeMillis(),
                    uri = uri
                ),
                count
            )
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }

    private fun writeZip(output: OutputStream, files: List<File>): Int {
        var fileCount = 0
        ZipOutputStream(BufferedOutputStream(output)).use { zos ->
            for (file in files) {
                val relPath = file.relativeTo(savesDir).path.replace('\\', '/')
                zos.putNextEntry(ZipEntry(relPath))
                file.inputStream().use { input -> input.copyTo(zos) }
                zos.closeEntry()
                fileCount++
            }
        }
        return fileCount
    }

    private fun fileBackups(): List<SaveBackup> {
        val folder = defaultBackupFolder
        if (!folder.exists() || !folder.isDirectory) return emptyList()
        return folder.listFiles { file -> file.isFile && file.name.endsWith(".zip", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { SaveBackup(it.name, it.length(), it.lastModified(), file = it) }
            ?: emptyList()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaStoreBackups(): List<SaveBackup> {
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.DATE_MODIFIED
        )
        val selection =
            "${MediaStore.Downloads.RELATIVE_PATH}=? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
        val args = arrayOf(relativePath, "%.zip")
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val result = mutableListOf<SaveBackup>()
        context.contentResolver.query(
            collection,
            projection,
            selection,
            args,
            "${MediaStore.Downloads.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)
            while (cursor.moveToNext()) {
                result += SaveBackup(
                    name = cursor.getString(nameIndex) ?: continue,
                    sizeBytes = cursor.getLong(sizeIndex),
                    modifiedAt = cursor.getLong(dateIndex) * 1000L,
                    uri = ContentUris.withAppendedId(collection, cursor.getLong(idIndex))
                )
            }
        }
        return result
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun readMeta(uri: Uri): Triple<String, Long, Long>? {
        val projection = arrayOf(
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.DATE_MODIFIED
        )
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            Triple(
                cursor.getString(0) ?: return@use null,
                cursor.getLong(1),
                cursor.getLong(2) * 1000L
            )
        }
    }

    private fun openInput(backup: SaveBackup): InputStream? {
        backup.uri?.let { uri ->
            return runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
        }
        val file = backup.file ?: return null
        if (!file.isFile) return null
        return FileInputStream(file)
    }
}
