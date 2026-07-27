package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
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
        FIT_IMAGE(PDRectangle.A4)
    }

    enum class Orientation { PORTRAIT, LANDSCAPE }

    class EmptyImageListException : Exception("No valid images provided for PDF creation")

    suspend operator fun invoke(
        imageUris: List<Uri>,
        pageSize: PageSize = PageSize.A4,
        orientation: Orientation = Orientation.PORTRAIT,
        outputFileName: String = "images_to_pdf_${System.currentTimeMillis()}.pdf"
    ): Uri = withContext(Dispatchers.IO) {
        if (!PDFBoxResourceLoader.isReady()) {
            PDFBoxResourceLoader.init(context)
        }

        if (imageUris.isEmpty()) throw EmptyImageListException()

        val document = PDDocument()
        val sanitizedFileName = if (outputFileName.endsWith(".pdf", ignoreCase = true)) {
            outputFileName
        } else {
            "$outputFileName.pdf"
        }

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
            FileHelper.saveToFile(context, settingsRepository, sanitizedFileName, baos.toByteArray())
        } finally {
            try {
                document.close()
            } catch (_: Exception) {}
        }
    }

    private fun loadAndDownsampleBitmap(uri: Uri): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            FileHelper.readFileFromUri(context, uri).use { BitmapFactory.decodeStream(it, null, options) }

            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            var sampleSize = 1
            val maxDim = 2048
            while ((options.outWidth / sampleSize) > maxDim || (options.outHeight / sampleSize) > maxDim) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBitmap = FileHelper.readFileFromUri(context, uri).use { BitmapFactory.decodeStream(it, null, decodeOptions) }
                ?: return null

            val rotatedBitmap = rotateBitmapIfRequired(uri, rawBitmap)
            rotatedBitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun rotateBitmapIfRequired(uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            FileHelper.readFileFromUri(context, uri).use { inputStream ->
                val exif = ExifInterface(inputStream)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    else -> return bitmap
                }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) {
                    bitmap.recycle()
                }
                rotated
            }
        } catch (_: Exception) {
            bitmap
        }
    }
}
