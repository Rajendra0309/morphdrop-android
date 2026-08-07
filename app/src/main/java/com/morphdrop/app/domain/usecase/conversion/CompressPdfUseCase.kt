package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

data class CompressResult(
    val outputUri: Uri,
    val originalSize: Long,
    val newSize: Long
)

enum class CompressionLevel(val quality: Float, val scaleFactor: Float) {
    LOW(quality = 0.8f, scaleFactor = 0.9f),
    MEDIUM(quality = 0.6f, scaleFactor = 0.75f),
    HIGH(quality = 0.35f, scaleFactor = 0.5f)
}

class CompressPdfUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(
        pdfUri: Uri,
        compressionLevel: CompressionLevel = CompressionLevel.MEDIUM,
        outputFileName: String = "compressed_${System.currentTimeMillis()}.pdf"
    ): CompressResult = withContext(Dispatchers.IO) {
        val originalSize = FileHelper.getFileSize(context, pdfUri)
        val inputStream = FileHelper.readFileFromUri(context, pdfUri)
        val document = PDDocument.load(inputStream)

        try {
            for (page in document.pages) {
                kotlinx.coroutines.yield()
                val resources = page.resources ?: continue
                for (name in resources.xObjectNames) {
                    val xObject = resources.getXObject(name)
                    if (xObject is PDImageXObject) {
                        try {
                            val bitmap = xObject.image ?: continue
                            val scaledWidth = (bitmap.width * compressionLevel.scaleFactor).toInt().coerceAtLeast(1)
                            val scaledHeight = (bitmap.height * compressionLevel.scaleFactor).toInt().coerceAtLeast(1)

                            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                            val compressedImage = JPEGFactory.createFromImage(document, scaledBitmap, compressionLevel.quality)

                            resources.put(name, compressedImage)
                            if (scaledBitmap != bitmap) scaledBitmap.recycle()
                            bitmap.recycle()
                        } catch (_: Exception) { }
                    }
                }
            }

            val baos = ByteArrayOutputStream()
            document.save(baos)
            val bytes = baos.toByteArray()
            val outputUri = FileHelper.saveToFile(context, settingsRepository, outputFileName, bytes)

            CompressResult(
                outputUri = outputUri,
                originalSize = originalSize,
                newSize = bytes.size.toLong()
            )
        } finally {
            document.close()
            inputStream.close()
        }
    }
}
