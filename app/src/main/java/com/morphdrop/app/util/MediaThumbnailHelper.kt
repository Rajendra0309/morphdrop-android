package com.morphdrop.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object MediaThumbnailHelper {

    suspend fun getMediaThumbnailUri(context: Context, uri: Uri): Uri? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)

                // 1. Check for embedded album art (Audio / Video)
                val artBytes = retriever.embeddedPicture
                if (artBytes != null && artBytes.isNotEmpty()) {
                    val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                    if (bitmap != null) {
                        val uriResult = saveBitmapToCache(context, uri, bitmap)
                        bitmap.recycle()
                        return@withContext uriResult
                    }
                }

                // 2. Fetch video frame at 1 second (1,000,000 microseconds)
                val frame = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
                if (frame != null) {
                    val uriResult = saveBitmapToCache(context, uri, frame)
                    frame.recycle()
                    return@withContext uriResult
                }

                null
            }
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun saveBitmapToCache(context: Context, sourceUri: Uri, bitmap: Bitmap): Uri? {
        return try {
            val uniqueId = sourceUri.toString().hashCode().let { if (it < 0) -it else it }
            val cacheFile = File(context.cacheDir, "media_thumb_$uniqueId.jpg")
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            Uri.fromFile(cacheFile)
        } catch (_: Exception) {
            null
        }
    }
}
