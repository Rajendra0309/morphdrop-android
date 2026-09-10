package com.morphdrop.app.data.updater

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

enum class DownloadStatus {
    IDLE,
    PENDING,
    DOWNLOADING,
    PAUSED,
    SUCCESSFUL,
    FAILED
}

data class DownloadProgress(
    val status: DownloadStatus = DownloadStatus.IDLE,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val percentage: Int = 0,
    val statusMessage: String = ""
)

@Singleton
class UpdateManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun downloadApk(url: String, versionName: String): Long {
        val cleanVersion = if (versionName.startsWith("v", ignoreCase = true)) versionName else "v$versionName"
        val formattedName = "MorphDrop-$cleanVersion"
        val fileName = "$formattedName.apk"

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(formattedName)
            .setDescription("Downloading latest version...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setMimeType("application/vnd.android.package-archive")

        return downloadManager.enqueue(request)
    }

    fun pollDownloadProgress(downloadId: Long): Flow<DownloadProgress> = flow {
        var isDone = false
        while (!isDone) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            if (cursor != null && cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                cursor.close()

                val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                when (status) {
                    DownloadManager.STATUS_RUNNING -> {
                        emit(DownloadProgress(DownloadStatus.DOWNLOADING, downloaded, total, percent, "Downloading update..."))
                    }
                    DownloadManager.STATUS_PAUSED -> {
                        val msg = if (reason == DownloadManager.PAUSED_WAITING_FOR_NETWORK || reason == DownloadManager.PAUSED_WAITING_TO_RETRY) {
                            "Download paused — waiting for network"
                        } else {
                            "Download paused"
                        }
                        emit(DownloadProgress(DownloadStatus.PAUSED, downloaded, total, percent, msg))
                    }
                    DownloadManager.STATUS_PENDING -> {
                        emit(DownloadProgress(DownloadStatus.PENDING, downloaded, total, percent, "Connecting to server..."))
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        emit(DownloadProgress(DownloadStatus.SUCCESSFUL, total, total, 100, "Download complete"))
                        isDone = true
                    }
                    DownloadManager.STATUS_FAILED -> {
                        emit(DownloadProgress(DownloadStatus.FAILED, downloaded, total, percent, "Download failed. Check connection."))
                        isDone = true
                    }
                }
            } else {
                cursor?.close()
                isDone = true
            }
            if (!isDone) delay(400)
        }
    }.flowOn(Dispatchers.IO)
}
