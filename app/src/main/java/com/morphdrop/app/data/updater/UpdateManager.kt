package com.morphdrop.app.data.updater

import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
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

    companion object {
        const val UPDATER_PREFS = "updater_prefs"
        const val KEY_LAST_DOWNLOAD_ID = "last_download_id"
    }

    fun downloadApk(url: String, versionName: String): Long {
        // Only HTTPS update URLs are allowed; anything else is rejected.
        val uri = try {
            Uri.parse(url)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid update URL")
        }
        require(uri.scheme.equals("https", ignoreCase = true)) {
            "Update URL must use https"
        }

        val cleanVersion = if (versionName.startsWith("v", ignoreCase = true)) versionName else "v$versionName"
        val formattedName = "MorphDrop-$cleanVersion"
        val fileName = "$formattedName.apk"

        val request = DownloadManager.Request(uri)
            .setTitle(formattedName)
            .setDescription("Downloading latest version...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setMimeType("application/vnd.android.package-archive")

        val downloadId = downloadManager.enqueue(request)
        // Persist the enqueue ID so UpdateDownloadReceiver can verify that a
        // completed download is actually ours before offering an install.
        context.getSharedPreferences(UPDATER_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_DOWNLOAD_ID, downloadId)
            .apply()
        return downloadId
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

    /**
     * Verifies that the downloaded APK is signed by the same certificate as
     * the installed app. Returns false if either side's signature info cannot
     * be read, if the certificates differ, or if the file is not a valid APK.
     */
    fun verifyApkSignature(apkFile: java.io.File): Boolean {
        return try {
            val pm = context.packageManager
            val archiveFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, archiveFlags)
                ?: return false
            val installedInfo = pm.getPackageInfo(context.packageName, archiveFlags)
                ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val archiveSigning = archiveInfo.signingInfo ?: return false
                val installedSigning = installedInfo.signingInfo ?: return false

                if (archiveSigning.hasMultipleSigners() || installedSigning.hasMultipleSigners()) {
                    val archiveSigners = archiveSigning.apkContentsSigners.map { it.toCharsString() }.toSet()
                    val installedSigners = installedSigning.apkContentsSigners.map { it.toCharsString() }.toSet()
                    archiveSigners == installedSigners
                } else {
                    val archiveCurrent = archiveSigning.apkContentsSigners.firstOrNull()?.toCharsString() ?: return false
                    val installedCurrent = installedSigning.apkContentsSigners.firstOrNull()?.toCharsString() ?: return false
                    val archiveHistory = archiveSigning.signingCertificateHistory.map { it.toCharsString() }.toSet()
                    val installedHistory = installedSigning.signingCertificateHistory.map { it.toCharsString() }.toSet()

                    archiveCurrent == installedCurrent ||
                        archiveHistory.contains(installedCurrent) ||
                        installedHistory.contains(archiveCurrent)
                }
            } else {
                @Suppress("DEPRECATION")
                val archiveSigners = archiveInfo.signatures?.map { it.toCharsString() }?.toSet() ?: return false
                @Suppress("DEPRECATION")
                val installedSigners = installedInfo.signatures?.map { it.toCharsString() }?.toSet() ?: return false
                archiveSigners == installedSigners
            }
        } catch (e: Exception) {
            Log.w("UpdateManager", "APK signature verification failed", e)
            false
        }
    }
}
