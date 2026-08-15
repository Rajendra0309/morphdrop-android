package com.morphdrop.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfThumbnailHelper {

    private val thumbnailCache = LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt())

    suspend fun getThumbnail(context: Context, uri: Uri, pageIndex: Int = 0): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${uri}_$pageIndex"
        thumbnailCache.get(cacheKey)?.let { return@withContext it }

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { fileDescriptor ->
                PdfRenderer(fileDescriptor).use { renderer ->
                    if (pageIndex >= renderer.pageCount) return@withContext null
                    
                    renderer.openPage(pageIndex).use { page ->
                        // Render at a reasonable scale to prevent OOM
                        val scale = 1.5f
                        val width = (page.width * scale).toInt()
                        val height = (page.height * scale).toInt()
                        
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        
                        thumbnailCache.put(cacheKey, bitmap)
                        return@withContext bitmap
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getThumbnailUri(context: Context, uri: Uri, pageIndex: Int = 0): Uri? = withContext(Dispatchers.IO) {
        val bitmap = getThumbnail(context, uri, pageIndex) ?: return@withContext null
        
        try {
            val cacheFile = File(context.cacheDir, "pdf_thumb_${uri.hashCode()}_$pageIndex.png")
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Uri.fromFile(cacheFile)
        } catch (e: Exception) {
            null
        }
    }

    fun clearCache() {
        thumbnailCache.evictAll()
    }
}
