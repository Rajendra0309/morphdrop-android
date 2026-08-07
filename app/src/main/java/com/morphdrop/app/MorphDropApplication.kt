package com.morphdrop.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MorphDropApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(this)
        
        setupCacheCleanupWorker()
    }

    private fun setupCacheCleanupWorker() {
        val cacheCleanupRequest = androidx.work.PeriodicWorkRequestBuilder<com.morphdrop.app.worker.CacheCleanupWorker>(
            7, java.util.concurrent.TimeUnit.DAYS
        ).build()

        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CacheCleanupWork",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            cacheCleanupRequest
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}