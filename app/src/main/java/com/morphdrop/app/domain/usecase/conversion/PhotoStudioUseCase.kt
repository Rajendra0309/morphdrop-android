package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/**
 * Builds an exam-ready photo or signature image at exact pixel dimensions,
 * optionally under an exact file-size ceiling.
 *
 * Unlike the general image converter, the pixel size here is a hard
 * requirement (application portals reject wrong dimensions), so the size
 * ceiling is met by lowering JPEG quality only — the output is never
 * rescaled below the requested width and height.
 */
class PhotoStudioUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    data class StudioResult(
        val outputUri: Uri,
        val sizeBytes: Long,
        val width: Int,
        val height: Int
    )

    class InvalidImageException : Exception("Could not read the selected image")
    class ImageTooLargeException : Exception("Image is too large for device memory. Try a smaller image.")

    suspend operator fun invoke(
        inputUri: Uri,
        targetWidth: Int,
        targetHeight: Int,
        fillFrame: Boolean,
        backgroundColor: Int,
        targetSizeKb: Int?,
        outputFileName: String
    ): StudioResult = withContext(Dispatchers.IO) {
        require(targetWidth > 0 && targetHeight > 0) { "Target size must be positive" }

        val sanitizedFileName = if (outputFileName.endsWith(".jpg", ignoreCase = true)) {
            outputFileName
        } else {
            "$outputFileName.jpg"
        }

        var sourceBitmap: Bitmap? = null
        var framedBitmap: Bitmap? = null
        try {
            sourceBitmap = decodeForTarget(inputUri, targetWidth, targetHeight)
            framedBitmap = frameToTarget(sourceBitmap, targetWidth, targetHeight, fillFrame, backgroundColor)
            val bytes = encodeJpegWithinLimit(framedBitmap, targetSizeKb)
            val outputUri = FileHelper.saveToFile(context, settingsRepository, sanitizedFileName, bytes)
            StudioResult(
                outputUri = outputUri,
                sizeBytes = bytes.size.toLong(),
                width = targetWidth,
                height = targetHeight
            )
        } finally {
            if (framedBitmap != null && framedBitmap != sourceBitmap) framedBitmap.recycle()
            sourceBitmap?.recycle()
        }
    }

    /**
     * Decodes the source at roughly twice the target resolution. A phone
     * camera photo can be 8000px wide; decoding all of it to produce a
     * 413px-wide passport photo wastes memory and time for no quality gain.
     */
    private fun decodeForTarget(uri: Uri, targetWidth: Int, targetHeight: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw InvalidImageException()
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw InvalidImageException()

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= targetWidth * 2 &&
            bounds.outHeight / (sampleSize * 2) >= targetHeight * 2
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = try {
            openStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: throw InvalidImageException()
        } catch (e: OutOfMemoryError) {
            throw ImageTooLargeException()
        }
        return rotateByExif(decoded, uri)
    }

    private fun frameToTarget(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        fillFrame: Boolean,
        backgroundColor: Int
    ): Bitmap {
        return try {
            if (fillFrame) {
                // Scale so the frame is fully covered, then center-crop the overflow.
                val scale = max(
                    targetWidth.toFloat() / source.width,
                    targetHeight.toFloat() / source.height
                )
                val scaledWidth = (source.width * scale).roundAtLeast(targetWidth)
                val scaledHeight = (source.height * scale).roundAtLeast(targetHeight)
                val scaled = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)
                val left = (scaledWidth - targetWidth) / 2
                val top = (scaledHeight - targetHeight) / 2
                val cropped = Bitmap.createBitmap(scaled, left, top, targetWidth, targetHeight)
                if (cropped != scaled && scaled != source) scaled.recycle()
                cropped
            } else {
                // Fit the whole image inside the frame on a solid background.
                val canvasBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(canvasBitmap)
                canvas.drawColor(backgroundColor)
                val scale = min(
                    targetWidth.toFloat() / source.width,
                    targetHeight.toFloat() / source.height
                )
                val drawWidth = (source.width * scale).toInt().coerceAtLeast(1)
                val drawHeight = (source.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(source, drawWidth, drawHeight, true)
                try {
                    val left = (targetWidth - drawWidth) / 2f
                    val top = (targetHeight - drawHeight) / 2f
                    canvas.drawBitmap(scaled, left, top, Paint(Paint.FILTER_BITMAP_FLAG))
                } finally {
                    if (scaled != source) scaled.recycle()
                }
                canvasBitmap
            }
        } catch (e: OutOfMemoryError) {
            throw ImageTooLargeException()
        }
    }

    /**
     * Encodes JPEG, stepping quality down from 95 until the byte size fits
     * [targetSizeKb]. When no limit is set, encodes once at quality 95.
     * If even the quality floor misses the limit, the smallest encode is
     * returned as-is — dimensions are never sacrificed to hit the limit.
     */
    private fun encodeJpegWithinLimit(bitmap: Bitmap, targetSizeKb: Int?): ByteArray {
        if (targetSizeKb == null || targetSizeKb <= 0) {
            return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
        }
        val targetBytes = targetSizeKb * 1024L
        var quality = 95
        var smallest: ByteArray? = null
        while (quality >= 30) {
            val bytes = ByteArrayOutputStream()
                .also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
                .toByteArray()
            if (smallest == null || bytes.size < smallest.size) smallest = bytes
            if (bytes.size <= targetBytes) return bytes
            quality -= 5
        }
        return smallest ?: ByteArray(0)
    }

    private fun Float.roundAtLeast(minimum: Int): Int =
        max(minimum, Math.round(this))

    private fun openStream(uri: Uri) = try {
        FileHelper.readFileFromUri(context, uri)
    } catch (e: Exception) {
        null
    }

    private fun getOrientation(uri: Uri): Int {
        return try {
            openStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun rotateByExif(bitmap: Bitmap, uri: Uri): Bitmap {
        val matrix = Matrix()
        when (getOrientation(uri)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } catch (e: OutOfMemoryError) {
            throw ImageTooLargeException()
        }
    }
}
