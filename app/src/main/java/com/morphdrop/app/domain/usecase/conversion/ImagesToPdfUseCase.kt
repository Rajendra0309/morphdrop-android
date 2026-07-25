package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class ImagesToPdfUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    enum class PageSize(val rect: PDRectangle) {
        A4(PDRectangle.A4),
        LETTER(PDRectangle.LETTER),
        FIT_IMAGE(PDRectangle.A4) // placeholder, actual size set from image
    }

    enum class Orientation { PORTRAIT, LANDSCAPE }

    class EmptyImageListException : Exception("No images provided")

    suspend operator fun invoke(
        imageUris: List<Uri>,
        pageSize: PageSize = PageSize.A4,
        orientation: Orientation = Orientation.PORTRAIT,
        outputFileName: String = "images_to_pdf_${System.currentTimeMillis()}.pdf"
    ): Uri = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) throw EmptyImageListException()

        PDFBoxResourceLoader.init(context)
        val document = PDDocument()

        try {
            for (uri in imageUris) {
                val bitmap = loadAndDownsampleBitmap(uri) ?: continue

                try {
                    val rect = when {
                        pageSize == PageSize.FIT_IMAGE -> PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat())
                        orientation == Orientation.LANDSCAPE -> PDRectangle(pageSize.rect.height, pageSize.rect.width)
                        else -> pageSize.rect
                    }

                    val page = PDPage(rect)
                    document.addPage(page)

                    val pdImage = if (bitmap.hasAlpha()) {
                        LosslessFactory.createFromImage(document, bitmap)
                    } else {
                        JPEGFactory.createFromImage(document, bitmap, 0.9f)
                    }

                    val contentStream = PDPageContentStream(document, page)

                    // Scale image to fit page while maintaining aspect ratio
                    val pageW = rect.width
                    val pageH = rect.height
                    val imgW = bitmap.width.toFloat()
                    val imgH = bitmap.height.toFloat()
                    val scale = minOf(pageW / imgW, pageH / imgH)
                    val drawW = imgW * scale
                    val drawH = imgH * scale
                    val x = (pageW - drawW) / 2
                    val y = (pageH - drawH) / 2

                    contentStream.drawImage(pdImage, x, y, drawW, drawH)
                    contentStream.close()
                } finally {
                    bitmap.recycle()
                }
            }

            if (document.numberOfPages == 0) throw EmptyImageListException()

            val baos = ByteArrayOutputStream()
            document.save(baos)
            FileHelper.saveToFile(context, settingsRepository, outputFileName, baos.toByteArray())
        } finally {
            document.close()
        }
    }

    private fun loadAndDownsampleBitmap(uri: Uri): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

            var sampleSize = 1
            val maxDim = 2048
            while ((options.outWidth / sampleSize) > maxDim || (options.outHeight / sampleSize) > maxDim) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
        } catch (e: Exception) {
            null
        }
    }
}
