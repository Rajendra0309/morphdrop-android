package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.morphdrop.app.util.FileHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class ImageConverterUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    class InvalidImageException : Exception("Failed to decode source image")

    suspend operator fun invoke(
        inputUri: Uri,
        outputFormat: String = "jpg",
        quality: Int = 85,
        outputFileName: String = "converted_image_${System.currentTimeMillis()}.$outputFormat"
    ): Uri = withContext(Dispatchers.IO) {
        val inputStream = FileHelper.readFileFromUri(context, inputUri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
            ?: throw InvalidImageException()

        try {
            val format = when (outputFormat.lowercase()) {
                "png" -> Bitmap.CompressFormat.PNG
                "webp" -> Bitmap.CompressFormat.WEBP_LOSSY
                "bmp" -> Bitmap.CompressFormat.PNG
                else -> Bitmap.CompressFormat.JPEG
            }

            val baos = ByteArrayOutputStream()
            bitmap.compress(format, quality, baos)

            FileHelper.saveToCache(context, outputFileName, baos.toByteArray())
        } finally {
            bitmap.recycle()
            inputStream.close()
        }
    }
}
