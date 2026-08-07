package com.morphdrop.app.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class CacheCleanupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d("CacheCleanupWorker", "Starting automated cache cleanup")
            
            // Delete cache directory contents
            context.cacheDir?.listFiles()?.forEach { file ->
                file.deleteRecursively()
            }
            
            // Delete external cache directory contents
            context.externalCacheDir?.listFiles()?.forEach { file ->
                file.deleteRecursively()
            }
            
            Log.d("CacheCleanupWorker", "Automated cache cleanup finished successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e("CacheCleanupWorker", "Error during automated cache cleanup", e)
            Result.failure()
        }
    }
}
