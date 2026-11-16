package com.retrobit.app.data

import android.content.Context
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val wikiApi = WikiApi(httpClient)

    val gameRepository = GameRepository(appContext)
    val gameMetadataRepository = GameMetadataRepository(appContext, wikiApi, gameRepository)
    val settingsRepository = SettingsRepository(appContext)
    val libraryRepository = LibraryRepository(appContext)
    val coverRepository = CoverRepository(appContext, wikiApi, gameRepository)
    val romImporter = RomImporter(appContext, libraryRepository, gameRepository)
    val coreManager = CoreManager(appContext)
    val saveBackupManager = SaveBackupManager(appContext, coreManager.savesDirectory)
}
