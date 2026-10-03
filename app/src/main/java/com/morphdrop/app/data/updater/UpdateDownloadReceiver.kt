package com.morphdrop.app.data.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

class UpdateDownloadReceiver : BroadcastReceiver() {

    // Downloaded APKs we enqueue are named "MorphDrop-vX.Y.Z.apk".
    private val apkFileNamePattern = Regex("^MorphDrop-v[\\d.]+\\.apk$")

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId == -1L) return

            // Security: ignore any completed download that is not the one we
            // enqueued, otherwise this receiver would offer to install
            // arbitrary files other apps download.
            val expectedId = context
                .getSharedPreferences(UpdateManager.UPDATER_PREFS, Context.MODE_PRIVATE)
                .getLong(UpdateManager.KEY_LAST_DOWNLOAD_ID, -1)
            if (downloadId != expectedId) return

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)

            downloadManager.query(query)?.use { cursor ->
                if (!cursor.moveToFirst()) return

                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (statusIndex == -1 || cursor.getInt(statusIndex) != DownloadManager.STATUS_SUCCESSFUL) return

                // Verify the downloaded file actually matches the MorphDrop APK naming pattern.
                val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val localUriString = if (localUriIndex != -1) cursor.getString(localUriIndex) else null
                val fileName = localUriString?.let { Uri.parse(it).lastPathSegment }
                if (fileName == null || !apkFileNamePattern.matches(fileName)) return

                // Security: verify the APK is signed by the same certificate as the
                // installed app before offering to install it. Never install an
                // APK whose signature we cannot verify.
                val apkFile = localUriString?.let { Uri.parse(it) }
                    ?.takeIf { it.scheme == "file" }
                    ?.path
                    ?.let { java.io.File(it) }
                    ?.takeIf { it.isFile }
                if (apkFile == null) return
                if (!UpdateManager(context.applicationContext).verifyApkSignature(apkFile)) {
                    android.util.Log.w("UpdateDownloadReceiver", "APK signature mismatch; deleting $fileName")
                    try { apkFile.delete() } catch (_: Exception) {}
                    return
                }

                val contentUri = downloadManager.getUriForDownloadedFile(downloadId)
                if (contentUri != null) {
                    installApk(context, contentUri)
                }
            }
        }
    }

    private fun installApk(context: Context, contentUri: Uri) {
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(installIntent)
    }
}
