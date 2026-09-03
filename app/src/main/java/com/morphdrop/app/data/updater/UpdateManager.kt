package com.morphdrop.app.data.updater

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun downloadApk(url: String, versionName: String): Long {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("MorphDrop Update $versionName")
            .setDescription("Downloading latest version...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "morphdrop-$versionName.apk")
            .setMimeType("application/vnd.android.package-archive")

        return downloadManager.enqueue(request)
    }
}
