package com.retrobit.app

import android.app.ActivityManager
import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.retrobit.app.data.AppContainer
import okhttp3.OkHttpClient

class RetrobitApplication : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader {
        // TV boxes often ship 1-2 GB of RAM. Crossfade keeps two extra bitmaps alive per card
        // and the default cache can eat a fourth of the heap, which shows up as launcher jank.
        val activityManager = getSystemService(ActivityManager::class.java)
        val lowRam = activityManager?.isLowRamDevice == true ||
            (activityManager?.memoryClass ?: 256) <= 128
        return ImageLoader.Builder(this)
            .crossfade(!lowRam)
            .apply {
                if (lowRam) {
                    memoryCache {
                        MemoryCache.Builder(this@RetrobitApplication)
                            .maxSizePercent(0.12)
                            .build()
                    }
                }
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .header(
                                    "User-Agent",
                                    "Retrobit/1.0 (Android; retro game library; +https://retrobit.app)"
                                )
                                .build()
                        )
                    }
                    .build()
            }
            .build()
    }
}
