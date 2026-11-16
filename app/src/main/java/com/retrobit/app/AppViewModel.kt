package com.retrobit.app

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.retrobit.app.BuildConfig
import com.retrobit.app.data.ConsoleCatalog
import com.retrobit.app.data.ConsoleInfo
import com.retrobit.app.data.EmulatorInfo
import com.retrobit.app.data.EmulatorManager
import com.retrobit.app.data.ImportProgress
import com.retrobit.app.data.ImportResult
import com.retrobit.app.data.LibraryGame
import com.retrobit.app.data.LibretroCore
import com.retrobit.app.data.SaveBackup
import com.retrobit.app.data.SettingsData
import com.retrobit.app.data.StorageUsage
import com.retrobit.app.data.SupporterKeys
import com.retrobit.app.billing.SupporterPurchase
import com.retrobit.app.billing.SupporterPurchaseGateway
import com.retrobit.app.billing.SupporterPurchaseResult
import com.retrobit.app.ui.player.RetroPlayerActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class UiMessage(@StringRes val resId: Int, val args: List<Any> = emptyList())

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val container = (application as RetrobitApplication).container
    private val settingsRepository = container.settingsRepository
    private val libraryRepository = container.libraryRepository
    private val coverRepository = container.coverRepository
    private val importer = container.romImporter
    private val coreManager = container.coreManager

    val consoles: List<ConsoleInfo> = ConsoleCatalog.consoles

    val settings: StateFlow<SettingsData> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsData())

    val settingsLoaded: StateFlow<Boolean> = settingsRepository.settings
        .map { true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isTr: StateFlow<Boolean> = settings
        .map { settings ->
            when (settings.language) {
                SettingsData.LANGUAGE_TR -> true
                SettingsData.LANGUAGE_EN -> false
                else -> Locale.getDefault().language == "tr"
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            Locale.getDefault().language == "tr"
        )

    val favorites: StateFlow<Set<String>> = libraryRepository.favorites
    val libraryLoaded: StateFlow<Boolean> = libraryRepository.loaded
    val libraryError: StateFlow<Boolean> = libraryRepository.loadError

    fun retryLibraryLoad() = libraryRepository.retryLoad()

    private val _coverUrls = MutableStateFlow<Map<String, String?>>(emptyMap())
    val coverUrls: StateFlow<Map<String, String?>> = _coverUrls.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    val library: StateFlow<List<LibraryGame>> = combine(libraryRepository.library, importing) { games, busy ->
        if (busy) emptyList() else games.filter { ConsoleCatalog.get(it.consoleId) != null }
    }.distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _importProgress.asStateFlow()

    private val _installedCores = MutableStateFlow(coreManager.installedConsoleIds())
    val installedCores: StateFlow<Set<String>> = _installedCores.asStateFlow()

    private val _legacyCoresBytes = MutableStateFlow(coreManager.legacyDownloadsSize())
    val legacyCoresBytes: StateFlow<Long> = _legacyCoresBytes.asStateFlow()

    private val _storageBytes = MutableStateFlow(0L)
    val storageBytes: StateFlow<Long> = _storageBytes.asStateFlow()

    private val _storageUsage = MutableStateFlow(StorageUsage())
    val storageUsage: StateFlow<StorageUsage> = _storageUsage.asStateFlow()

    private val _emulators = MutableStateFlow<List<EmulatorInfo>>(emptyList())
    val emulators: StateFlow<List<EmulatorInfo>> = _emulators.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private val _supporterPrice = MutableStateFlow<String?>(null)
    val supporterPrice: StateFlow<String?> = _supporterPrice.asStateFlow()

    private val billingGateway: SupporterPurchaseGateway =
        SupporterPurchase.create(app) { result -> handlePurchaseResult(result) }

    private val coverLoading = mutableSetOf<String>()

    private val _gameMetadata = MutableStateFlow<Map<String, com.retrobit.app.data.GameMetadata>>(emptyMap())
    val gameMetadata: StateFlow<Map<String, com.retrobit.app.data.GameMetadata>> = _gameMetadata.asStateFlow()
    fun metadataFor(game: LibraryGame): com.retrobit.app.data.GameMetadata? =
        _gameMetadata.value[game.id] ?: container.gameMetadataRepository.localFor(game)

    /** Only the selected preview/detail requests remote metadata, never every row in a ROM set. */
    suspend fun ensureMetadata(game: LibraryGame) {
        if (_importing.value || _gameMetadata.value.containsKey(game.id)) return
        // Called from the selection's LaunchedEffect: scrolling away cancels queued work.
        kotlinx.coroutines.delay(180)
        val metadata = container.gameMetadataRepository.metadataForLibrary(game)
        if (metadata != null) _gameMetadata.update { it + (game.id to metadata) }
    }

    init {
        refreshEmulators()
        refreshStorage()
        runCatching { billingGateway.start() }
        viewModelScope.launch(Dispatchers.IO) { runCatching { importer.clearStaleStaging() } }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { container.saveBackupManager.recoverInterruptedRestore() }
        }
    }

    // region library

    fun consoleGameCount(consoleId: String): Int = library.value.count { it.consoleId == consoleId }

    fun ensureCover(game: LibraryGame) {
        if (_importing.value) return
        val key = "lib:${game.id}"
        val custom = settings.value.customCovers[game.id]
        if (custom != null) {
            if (_coverUrls.value[key] != custom) _coverUrls.update { it + (key to custom) }
            return
        }
        if (game.coverUrl != null) return
        if (_coverUrls.value.containsKey(key) || !coverLoading.add(key)) return
        viewModelScope.launch {
            val url = coverRepository.coverForLibrary(game)
            _coverUrls.update { it + (key to url) }
            coverLoading.remove(key)
        }
    }

    fun setCustomCover(gameId: String, uri: Uri) {
        viewModelScope.launch {
            val target = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(app.filesDir, "covers").apply { mkdirs() }
                    val file = File(dir, "custom_$gameId.jpg")
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    } ?: return@runCatching null
                    file.absolutePath
                }.getOrNull()
            }
            if (target == null) {
                emit(R.string.supporter_cover_failed)
                return@launch
            }
            settingsRepository.setCustomCover(gameId, target)
            _coverUrls.update { it + ("lib:$gameId" to target) }
            emit(R.string.supporter_cover_saved)
        }
    }

    fun clearCustomCover(gameId: String) {
        viewModelScope.launch {
            val old = settings.value.customCovers[gameId]
            settingsRepository.setCustomCover(gameId, null)
            _coverUrls.update { it - "lib:$gameId" }
            if (old != null) withContext(Dispatchers.IO) { runCatching { File(old).delete() } }
            emit(R.string.supporter_cover_cleared)
        }
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty() || _importing.value) return
        _importing.value = true
        _importProgress.value = ImportProgress(scanning = true)
        viewModelScope.launch {
            try {
                val result = importer.importUris(uris) { _importProgress.value = it }
                finishImport(result)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emit(R.string.import_error)
            } finally {
                _importing.value = false
                _importProgress.value = null
            }
        }
    }

    fun importRomZip(uri: Uri) {
        if (_importing.value) return
        // The main UI opens as soon as a ZIP is picked; the import keeps running in the
        // background with a non-blocking status pill instead of holding the user hostage.
        completeOnboarding()
        _importing.value = true
        _importProgress.value = ImportProgress(scanning = true)
        viewModelScope.launch {
            try {
                val result = importer.importCollection(uri) { _importProgress.value = it }
                finishZipImport(result)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emit(R.string.import_error)
            } finally {
                _importing.value = false
                _importProgress.value = null
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingDone(true) }
    }

    private fun emitImported(result: ImportResult, rejected: Int) {
        val singleConsole = result.consoles.size == 1
        val label = result.consoles.entries.joinToString(", ") { (id, count) ->
            val console = ConsoleCatalog.get(id)!!
            if (singleConsole) console.name else "${console.shortName} ($count)"
        }
        when {
            rejected > 0 -> emit(R.string.import_summary_partial, result.imported, rejected, label)
            result.imported == 1 -> emit(R.string.import_success, label)
            else -> emit(R.string.import_summary, result.imported, label)
        }
    }

    private fun finishImport(result: ImportResult) {
        refreshStorage()
        val rejected = result.skipped + result.failed
        when {
            result.imported > 0 -> emitImported(result, rejected)
            result.skipped > 0 && result.failed == 0 -> emit(R.string.zip_import_already)
            result.failed > 0 -> emit(R.string.import_error)
            result.total == 0 -> emit(R.string.import_empty)
            else -> emit(R.string.import_error)
        }
    }

    private fun finishZipImport(result: ImportResult) {
        refreshStorage()
        val rejected = result.skipped + result.failed
        when {
            result.invalid -> emit(R.string.zip_import_invalid)
            result.imported > 0 -> emitImported(result, rejected)
            result.skipped > 0 && result.failed == 0 -> emit(R.string.zip_import_already)
            result.total == 0 -> emit(R.string.zip_import_empty)
            else -> emit(R.string.import_error)
        }
    }

    fun removeLibraryGame(game: LibraryGame) {
        viewModelScope.launch { libraryRepository.removeLibraryGame(game.id) }
    }

    fun clearLibrary() {
        viewModelScope.launch {
            libraryRepository.clearLibrary()
            refreshStorage()
        }
    }

    fun playLibrary(game: LibraryGame) {
        if (_importing.value) {
            emit(R.string.import_library_locked)
            return
        }
        if (ConsoleCatalog.get(game.consoleId) == null) {
            emit(R.string.launch_failed)
            return
        }
        if (!File(game.filePath).exists()) {
            emit(R.string.file_missing)
            return
        }
        if (useInternalPlayer()) {
            launchInternalPlayer(game.id)
            return
        }
        viewModelScope.launch {
            val preferred = settings.value.defaultEmulator.ifBlank { null }
            val result = withContext(Dispatchers.IO) {
                EmulatorManager.launchFile(app, File(game.filePath), game.consoleId, preferred)
            }
            when (result) {
                EmulatorManager.LaunchResult.STARTED -> libraryRepository.markPlayed(game.id)
                EmulatorManager.LaunchResult.MISSING_FILE -> emit(R.string.file_missing)
                EmulatorManager.LaunchResult.NO_TARGET -> emit(R.string.no_emulator_title)
                EmulatorManager.LaunchResult.FAILED -> emit(R.string.launch_failed)
            }
        }
    }

    private fun useInternalPlayer(): Boolean =
        settings.value.playerMode == SettingsData.PLAYER_INTERNAL

    private fun launchInternalPlayer(libraryId: String) {
        val intent = Intent(app, RetroPlayerActivity::class.java)
            .putExtra(RetroPlayerActivity.EXTRA_LIBRARY_ID, libraryId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(intent) }
            .onFailure { emit(R.string.launch_failed) }
    }

    val builtinCores: List<LibretroCore> = com.retrobit.app.data.CoreManager.CORES

    fun refreshCores() {
        _installedCores.value = coreManager.installedConsoleIds()
        _legacyCoresBytes.value = coreManager.legacyDownloadsSize()
    }

    fun removeLegacyCores() {
        viewModelScope.launch {
            coreManager.deleteLegacyDownloads()
            refreshCores()
            refreshStorage()
            emit(R.string.cores_removed)
        }
    }

    // endregion

    // region favorites

    fun toggleLibraryFavorite(game: LibraryGame) {
        viewModelScope.launch {
            val now = libraryRepository.toggleLibraryFavorite(game.id, game.catalogId)
            emit(if (now) R.string.favorite_added else R.string.favorite_removed)
        }
    }

    // endregion


    // region settings and system

    fun setLanguage(language: String) {
        viewModelScope.launch {
            settingsRepository.setLanguage(language)
            val locales = when (language) {
                SettingsData.LANGUAGE_EN -> LocaleListCompat.forLanguageTags("en")
                SettingsData.LANGUAGE_TR -> LocaleListCompat.forLanguageTags("tr")
                else -> LocaleListCompat.getEmptyLocaleList()
            }
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    fun setTheme(themeId: String) {
        viewModelScope.launch { settingsRepository.setTheme(themeId) }
    }

    fun setDefaultEmulator(packageName: String) {
        viewModelScope.launch { settingsRepository.setDefaultEmulator(packageName) }
    }

    fun setPlayerMode(mode: String) {
        viewModelScope.launch {
            settingsRepository.setPlayerMode(mode)
            emit(R.string.saved)
        }
    }

    fun setLayoutMode(mode: String) {
        viewModelScope.launch { settingsRepository.setLayoutMode(mode) }
    }

    fun setDefaultShader(shader: String) {
        if (shader in SettingsData.SUPPORTER_SHADERS && !settings.value.supporterUnlocked) return
        viewModelScope.launch { settingsRepository.setDefaultShader(shader) }
    }

    // region supporter

    fun purchaseSupporter(activity: Activity) {
        runCatching { billingGateway.purchase(activity) }
            .onFailure { emit(R.string.supporter_purchase_failed) }
    }

    fun restoreSupporter() {
        runCatching { billingGateway.restore() }
    }

    /** Validates offline on a worker thread; the KDF takes long enough to stall the UI thread. */
    fun redeemSupporterKey(key: String, onResult: (Boolean) -> Unit) {
        if (!BuildConfig.CD_KEY_ENTRY) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val valid = withContext(Dispatchers.Default) { SupporterKeys.isValid(key) }
            if (valid) {
                settingsRepository.setSupporter(true, SettingsData.SUPPORTER_SOURCE_KEY)
                emit(R.string.supporter_unlocked_msg)
            }
            onResult(valid)
        }
    }

    fun setSupporterName(name: String, listed: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSupporterName(name, listed)
            emit(R.string.saved)
        }
    }

    fun setLibraryShelf(enabled: Boolean) {
        if (!settings.value.supporterUnlocked) return
        viewModelScope.launch { settingsRepository.setLibraryShelf(enabled) }
    }

    fun setCoverSize(size: String) {
        if (!settings.value.supporterUnlocked) return
        viewModelScope.launch { settingsRepository.setCoverSize(size) }
    }

    fun setAttractMode(enabled: Boolean) {
        if (!settings.value.supporterUnlocked) return
        viewModelScope.launch { settingsRepository.setAttractMode(enabled) }
    }

    fun setUiScanlines(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUiScanlines(enabled) }
    }

    fun setRetroThemeTest(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setRetroThemeTest(enabled) }
    }

    fun setCoolAnimations(enabled: Boolean) {
        if (!settings.value.supporterUnlocked) return
        viewModelScope.launch { settingsRepository.setCoolAnimations(enabled) }
    }

    fun setHideEmptyConsoles(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHideEmptyConsoles(enabled) }
    }

    fun setUiSounds(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUiSounds(enabled) }
    }

    private fun handlePurchaseResult(result: SupporterPurchaseResult) {
        when (result) {
            is SupporterPurchaseResult.Price -> _supporterPrice.value = result.label
            is SupporterPurchaseResult.Purchased -> viewModelScope.launch {
                settingsRepository.setSupporter(true, result.source)
                emit(R.string.supporter_unlocked_msg)
            }
            SupporterPurchaseResult.Unavailable -> _supporterPrice.value = null
            is SupporterPurchaseResult.Failed -> emit(R.string.supporter_purchase_failed)
        }
    }

    // endregion

    val saveBackupManager = container.saveBackupManager

    private val _saveBackups = MutableStateFlow<List<SaveBackup>>(emptyList())
    val saveBackups: StateFlow<List<SaveBackup>> = _saveBackups.asStateFlow()

    fun refreshSaveBackups() {
        viewModelScope.launch {
            _saveBackups.value = saveBackupManager.listBackups()
        }
    }

    fun backupSaves() {
        viewModelScope.launch {
            val result = saveBackupManager.createBackup()
            if (result != null) {
                refreshSaveBackups()
                emit(R.string.backup_success, result.backup.name)
            } else {
                emit(R.string.backup_failed)
            }
        }
    }

    fun restoreSaves(backup: SaveBackup) {
        viewModelScope.launch {
            val hasSaves = withContext(Dispatchers.IO) {
                container.coreManager.savesDirectory.walkTopDown().any { it.isFile }
            }
            if (hasSaves && saveBackupManager.createBackup() == null) {
                emit(R.string.backup_failed)
                return@launch
            }
            val count = saveBackupManager.restoreBackup(backup)
            if (count > 0) {
                emit(R.string.restore_success, count)
            } else {
                emit(R.string.restore_failed)
            }
        }
    }

    fun setGamepadLayout(layout: String) {
        viewModelScope.launch { settingsRepository.setGamepadLayout(layout) }
    }

    fun setGamepadCustomMapping(mapping: Map<Int, Int>) {
        viewModelScope.launch {
            settingsRepository.setGamepadCustomMapping(mapping)
            emit(R.string.gamepad_remap_saved)
        }
    }

    fun refreshEmulators() {
        _emulators.value = EmulatorManager.installedEmulators(app)
    }

    fun refreshStorage() {
        viewModelScope.launch {
            val usage = withContext(Dispatchers.IO) {
                val roots = listOfNotNull(app.getExternalFilesDir("roms"), File(app.filesDir, "roms"))
                StorageUsage(
                    coresBytes = coreManager.bundledSize() + coreManager.legacyDownloadsSize(),
                    savesBytes = folderBytes(coreManager.savesDirectory),
                    coversBytes = folderBytes(File(app.cacheDir, "image_cache")),
                    romsBytes = roots.sumOf { folderBytes(it) }
                )
            }
            _storageBytes.value = usage.romsBytes
            _storageUsage.value = usage
        }
    }

    /** Only the app's own cover cache is cleared; saves, cores and ROM files are left untouched. */
    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearCoverCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val loader = coil.Coil.imageLoader(app)
                loader.memoryCache?.clear()
                val cache = loader.diskCache
                if (cache != null) cache.clear() else File(app.cacheDir, "image_cache").deleteRecursively()
            }
            refreshStorage()
            emit(R.string.storage_covers_cleared)
        }
    }

    fun setStartupBehavior(value: String) {
        viewModelScope.launch { container.settingsRepository.setStartupBehavior(value) }
    }

    fun setAutoSaveState(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoSaveState(enabled) }
    }

    fun setLibrarySort(sort: String) {
        viewModelScope.launch { settingsRepository.setLibrarySort(sort) }
    }

    fun setConsoleSort(sort: String) {
        viewModelScope.launch { settingsRepository.setConsoleSort(sort) }
    }

    fun setConsoleCustomOrder(ids: List<String>) {
        viewModelScope.launch { settingsRepository.setConsoleCustomOrder(ids) }
    }

    private fun folderBytes(dir: File?): Long =
        if (dir == null || !dir.exists()) 0L
        else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun emit(@StringRes resId: Int, vararg args: Any) {
        _messages.tryEmit(UiMessage(resId, args.toList()))
    }

    // endregion
}
