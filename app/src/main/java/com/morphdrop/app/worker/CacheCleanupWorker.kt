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

    companion object {
        // Files are only eligible for cleanup once they are this old;
        // anything fresher might still be in use by pending work.
        private const val MAX_CACHE_AGE_MS = 48L * 60 * 60 * 1000
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d("CacheCleanupWorker", "Starting automated cache cleanup")
            val failedDeletions = mutableListOf<String>()

            val cacheDirs = listOfNotNull(context.cacheDir, context.externalCacheDir)
            for (dir in cacheDirs) {
                val cutoff = System.currentTimeMillis() - MAX_CACHE_AGE_MS
                dir.walkBottomUp().forEach { file ->
                    if (file == dir) return@forEach
                    if (file.lastModified() < cutoff) {
                        try {
                            if (file.isDirectory) {
                                val children = file.list()
                                if (children != null && children.isEmpty()) {
                                    if (!file.delete() && file.exists()) {
                                        failedDeletions.add(file.absolutePath)
                                    }
                                }
                            } else {
                                if (!file.delete() && file.exists()) {
                                    failedDeletions.add(file.absolutePath)
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("CacheCleanupWorker", "Failed to delete ${file.absolutePath}", e)
                            failedDeletions.add(file.absolutePath)
                        }
                    }
                }
            }

            if (failedDeletions.isNotEmpty()) {
                Log.w("CacheCleanupWorker", "${failedDeletions.size} file(s) could not be deleted")
                Result.failure()
            } else {
                Log.d("CacheCleanupWorker", "Automated cache cleanup finished successfully")
                Result.success()
            }
        } catch (e: Exception) {
            Log.e("CacheCleanupWorker", "Error during automated cache cleanup", e)
            Result.failure()
        }
    }
}
