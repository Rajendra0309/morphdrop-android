package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Converts HEIC/HEIF photos (the default format on many phones) to JPG or
 * PNG using the platform ImageDecoder, which applies the photo's EXIF
 * orientation automatically. HEIC decoding needs Android 9 (API 28)+;
 * older devices get a clear unsupported message instead of a decode error.
 */
class HeicToImageUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    class HeicUnsupportedException :
        Exception("HEIC photos can only be opened on Android 9 or newer devices.")

    class InvalidHeicException :
        Exception("This file could not be read as a HEIC photo.")

    suspend operator fun invoke(
        inputUri: Uri,
        outputFormat: String = "jpg",
        quality: Int = 90,
        outputFolderName: String? = null,
        outputFileName: String = "heic_photo_${System.currentTimeMillis()}.jpg"
    ): Uri = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            throw HeicUnsupportedException()
        }
        val format = outputFormat.lowercase()
        val compressFormat = when (format) {
            "png" -> Bitmap.CompressFormat.PNG
            "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
            else -> throw IllegalArgumentException(
                "Unsupported output format \"$outputFormat\". HEIC converts to JPG or PNG."
            )
        }
        val extension = if (compressFormat == Bitmap.CompressFormat.PNG) "png" else "jpg"
        val baseName = outputFileName.substringBeforeLast(".", outputFileName)
        val finalName = "$baseName.$extension"

        val source = ImageDecoder.createSource(context.contentResolver, inputUri)
        val decoded = try {
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                // Software allocation: the bitmap must stay mutable-free but
                // re-encodable on every device, including ones whose GPU
                // decoder produces hardware bitmaps that cannot be read back.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (e: Exception) {
            throw InvalidHeicException()
        }

        try {
            // JPEG has no alpha: flatten transparent pixels onto white
            // instead of letting them turn black.
            val toEncode = if (compressFormat == Bitmap.CompressFormat.JPEG && decoded.hasAlpha()) {
                val flat = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(flat)
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(decoded, 0f, 0f, null)
                flat
            } else {
                decoded
            }
            try {
                val stream = ByteArrayOutputStream()
                if (!toEncode.compress(compressFormat, quality, stream)) {
                    throw InvalidHeicException()
                }
                if (outputFolderName != null) {
                    FileHelper.saveToFile(context, outputFolderName, finalName, stream.toByteArray())
                } else {
                    FileHelper.saveToFile(context, settingsRepository, finalName, stream.toByteArray())
                }
            } finally {
                if (toEncode != decoded) toEncode.recycle()
            }
        } finally {
            decoded.recycle()
        }
    }
}
