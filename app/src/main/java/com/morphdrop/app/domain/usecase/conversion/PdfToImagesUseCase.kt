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
        pageRange: IntRange? = null
    ): List<Uri> = withContext(Dispatchers.IO) {
        val baseFolder = settingsRepository.outputFolderName.first()
        val rawFileName = FileHelper.getFileName(context, pdfUri)
        val nameWithoutExt = if (rawFileName.contains(".")) rawFileName.substringBeforeLast(".") else rawFileName
        val folderName = if (nameWithoutExt.isBlank() || nameWithoutExt == "Input File") {
            "pdf_to_images_${System.currentTimeMillis()}"
        } else {
            "${nameWithoutExt}_images"
        }
        val outputDir = "$baseFolder/$folderName"
        FileHelper.createOutputDirectory(context, outputDir)
        val results = mutableListOf<Uri>()

        val fileDescriptor = try {
            context.contentResolver.openFileDescriptor(pdfUri, "r")
                ?: throw PdfException.CorruptPdf()
        } catch (e: Exception) {
            val cacheFile = java.io.File(context.cacheDir, "temp_pdf_${System.currentTimeMillis()}.pdf")
            try {
                FileHelper.readFileFromUri(context, pdfUri).use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
                android.os.ParcelFileDescriptor.open(cacheFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (ex: Exception) {
                throw PdfException.CorruptPdf()
            }
        }

        val renderer = try {
            PdfRenderer(fileDescriptor)
        } catch (e: Exception) {
            fileDescriptor.close()
            throw PdfException.CorruptPdf()
        }

        try {
            val pageCount = renderer.pageCount
            if (pageCount == 0) throw PdfException.EmptyPdf()

            val range = pageRange?.let {
                val start = (it.first - 1).coerceAtLeast(0)
                val end = (it.last - 1).coerceAtMost(pageCount - 1)
                start..end
            } ?: (0 until pageCount)

            for (i in range) {
                val page = renderer.openPage(i)
                // Render at 2x for good quality
                val width = page.width * 2
                val height = page.height * 2
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val baos = ByteArrayOutputStream()
                val compressFormat = if (outputFormat.equals("jpg", ignoreCase = true) ||
                    outputFormat.equals("jpeg", ignoreCase = true)
                ) {
                    Bitmap.CompressFormat.JPEG
                } else {
                    Bitmap.CompressFormat.PNG
                }
                bitmap.compress(compressFormat, quality, baos)
                bitmap.recycle()

                val ext = if (compressFormat == Bitmap.CompressFormat.JPEG) "jpg" else "png"
                val fileName = "page_${i + 1}.$ext"
                val savedUri = FileHelper.saveToDirectory(context, outputDir, fileName, baos.toByteArray())
                results.add(savedUri)
                baos.close()
            }
        } finally {
            renderer.close()
            fileDescriptor.close()
        }

        results
    }
}
