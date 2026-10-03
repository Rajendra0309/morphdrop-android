package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class PdfToImagesUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    sealed class PdfException(message: String) : Exception(message) {
        class EmptyPdf : PdfException("PDF has no pages")
        class CorruptPdf : PdfException("PDF file is corrupt or unreadable")
        class PasswordProtected : PdfException("PDF is password-protected")
    }

    suspend operator fun invoke(
        pdfUri: Uri,
        outputFormat: String = "png",
        quality: Int = 100,
        pageRange: IntRange? = null,
        outputFolderName: String? = null
    ): List<Uri> = withContext(Dispatchers.IO) {
        val baseFolder = settingsRepository.outputFolderName.first()
        val rawFileName = FileHelper.getFileName(context, pdfUri)
        val nameWithoutExt = if (rawFileName.contains(".")) rawFileName.substringBeforeLast(".") else rawFileName
        
        val chosenFolder = if (!outputFolderName.isNullOrBlank()) {
            outputFolderName
        } else {
            if (nameWithoutExt.isBlank() || nameWithoutExt == "Input File") {
                "pdf_to_images_${System.currentTimeMillis()}"
            } else {
                "${nameWithoutExt}_images"
            }
        }
        
        val outputDir = "$baseFolder/$chosenFolder"
        FileHelper.createOutputDirectory(outputDir)
        val results = mutableListOf<Uri>()

        // Tracks the fallback cache copy (only created when the content resolver
        // cannot hand us a descriptor directly) so it is deleted in the finally.
        var cacheFile: java.io.File? = null
        val fileDescriptor = try {
            context.contentResolver.openFileDescriptor(pdfUri, "r")
                ?: throw PdfException.CorruptPdf()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val tmp = java.io.File.createTempFile("pdf_to_images_", ".pdf", context.cacheDir)
            cacheFile = tmp
            try {
                FileHelper.readFileFromUri(context, pdfUri).use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                android.os.ParcelFileDescriptor.open(tmp, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (ex: Exception) {
                runCatching { tmp.delete() }
                cacheFile = null
                throw PdfException.CorruptPdf()
            }
        }

        val renderer = try {
            PdfRenderer(fileDescriptor)
        } catch (e: kotlinx.coroutines.CancellationException) {
            fileDescriptor.close()
            cacheFile?.let { runCatching { it.delete() } }
            cacheFile = null
            throw e
        } catch (e: Exception) {
            fileDescriptor.close()
            cacheFile?.let { runCatching { it.delete() } }
            cacheFile = null
            throw PdfException.CorruptPdf()
        }

        try {
            val pageCount = renderer.pageCount
            if (pageCount == 0) throw PdfException.EmptyPdf()

            val range = pageRange?.let {
                require(it.first <= it.last) { "Invalid page range: start must be <= end" }
                val start = (it.first - 1).coerceAtLeast(0)
                val end = (it.last - 1).coerceAtMost(pageCount - 1)
                start..end
            } ?: (0 until pageCount)

            val isSinglePage = range.count() == 1

            for (i in range) {
                kotlinx.coroutines.yield()
                val page = renderer.openPage(i)
                try {
                    // Cap the render scale by total pixels so a huge page cannot
                    // OOM the device: scale = min(2, sqrt(24MP / (w*h))).
                    val rawW = page.width.toFloat()
                    val rawH = page.height.toFloat()
                    val pixelBudget = 24_000_000f
                    val cappedScale = minOf(2f, kotlin.math.sqrt(pixelBudget / (rawW * rawH).coerceAtLeast(1f)))
                    val width = (rawW * cappedScale).toInt().coerceAtLeast(1)
                    val height = (rawH * cappedScale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        val canvas = android.graphics.Canvas(bitmap)
                        canvas.drawColor(android.graphics.Color.WHITE)

                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val baos = ByteArrayOutputStream()
                        val compressFormat = if (outputFormat.equals("jpg", ignoreCase = true) ||
                            outputFormat.equals("jpeg", ignoreCase = true)
                        ) {
                            Bitmap.CompressFormat.JPEG
                        } else {
                            Bitmap.CompressFormat.PNG
                        }
                        bitmap.compress(compressFormat, quality, baos)

                        val ext = if (compressFormat == Bitmap.CompressFormat.JPEG) "jpg" else "png"

                        val savedUri = if (isSinglePage) {
                            // Save as a single file instead of folder if it's 1 page and a folder name was given
                            val finalFileName = if (chosenFolder.isNotBlank()) {
                                if (chosenFolder.lowercase().endsWith(".$ext")) chosenFolder else "$chosenFolder.$ext"
                            } else {
                                "page_${i + 1}.$ext"
                            }
                            FileHelper.saveToFile(context, baseFolder, finalFileName, baos.toByteArray())
                        } else {
                            val fileName = "page_${i + 1}.$ext"
                            FileHelper.saveToDirectory(context, outputDir, fileName, baos.toByteArray())
                        }

                        results.add(savedUri)
                        baos.close()
                    } finally {
                        bitmap.recycle()
                    }
                } finally {
                    page.close()
                }
            }
        } finally {
            renderer.close()
            fileDescriptor.close()
            cacheFile?.let { runCatching { it.delete() } }
        }

        results
    }
}
