package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.morphdrop.app.domain.model.OcrScript
import com.morphdrop.app.util.FileHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OcrUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    suspend fun extractFromBatch(
        uris: List<Uri>,
        script: OcrScript,
        onProgress: (current: Int, total: Int, currentImageUri: Uri) -> Unit
    ): Result<List<Pair<Uri, String>>> = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext Result.success(emptyList())

        val recognizer = getRecognizerForScript(script)
        val results = mutableListOf<Pair<Uri, String>>()

        try {
            for ((index, uri) in uris.withIndex()) {
                ensureActive()
                onProgress(index + 1, uris.size, uri)

                try {
                    val mimeType = context.contentResolver.getType(uri)
                    val text = if (mimeType == "application/pdf" || uri.toString().endsWith(".pdf", ignoreCase = true)) {
                        // Extract from first page of PDF for batch (or should it extract all? The user prompt said extract from images. Let's do all pages if it's a PDF)
                        val pageCount = getPdfPageCount(uri)
                        val pdfText = StringBuilder()
                        for (i in 0 until pageCount) {
                            ensureActive()
                            var pageBitmap: Bitmap? = null
                            try {
                                pageBitmap = renderPdfPage(context, uri, i)
                                val pageInput = InputImage.fromBitmap(pageBitmap, 0)
                                val result = recognizer.process(pageInput).await()
                                pdfText.append(result.text).append("\n")
                            } finally {
                                pageBitmap?.recycle()
                            }
                        }
                        pdfText.toString()
                    } else {
                        // Image
                        try {
                            val inputImage = InputImage.fromFilePath(context, uri)
                            val result = recognizer.process(inputImage).await()
                            result.text
                        } catch (e: Exception) {
                            Log.e("OcrUseCase", "fromFilePath failed for $uri, falling back to bitmap", e)
                            var bitmap: Bitmap? = null
                            try {
                                bitmap = loadUniversalScaledBitmap(context, uri)
                                val inputImage = InputImage.fromBitmap(bitmap, 0)
                                val result = recognizer.process(inputImage).await()
                                result.text
                            } finally {
                                bitmap?.recycle()
                            }
                        }
                    }

                    if (text.isNotBlank()) {
                        results.add(uri to text.trim())
                    }
                } catch (e: Exception) {
                    Log.e("OcrUseCase", "Failed to extract text from uri: $uri", e)
                    // Skip failed image and continue
                }
            }

            if (results.isEmpty()) {
                Result.failure(OcrException.NoTextFoundException())
            } else {
                Result.success(results)
            }
        } finally {
            recognizer.close()
        }
    }

    private fun getRecognizerForScript(script: OcrScript): TextRecognizer {
        return when (script) {
            OcrScript.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            OcrScript.DEVANAGARI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            OcrScript.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            OcrScript.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            OcrScript.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        }
    }

    suspend fun processBitmapForScript(bitmap: Bitmap, script: OcrScript): Result<String> = withContext(Dispatchers.Default) {
        val recognizer = getRecognizerForScript(script)
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(inputImage).await()
            val text = result.text.trim()
            if (text.isEmpty()) {
                Result.failure(OcrException.NoTextFoundException())
            } else {
                Result.success(text)
            }
        } catch (_: OutOfMemoryError) {
            Result.failure(OcrException.MemoryException())
        } catch (e: Exception) {
            Log.e("OcrUseCase", "Failed to process bitmap", e)
            Result.failure(OcrException.ExtractionFailedException(e.message))
        } finally {
            recognizer.close()
        }
    }

    suspend fun extractFromImageUri(uri: Uri, script: OcrScript): Result<String> = withContext(Dispatchers.IO) {
        val recognizer = getRecognizerForScript(script)
        try {
            val inputImage = InputImage.fromFilePath(context, uri)
            val result = recognizer.process(inputImage).await()
            val text = result.text.trim()
            if (text.isEmpty()) {
                Result.failure(OcrException.NoTextFoundException())
            } else {
                Result.success(text)
            }
        } catch (e: Exception) {
            Log.e("OcrUseCase", "fromFilePath failed for $uri, falling back to bitmap", e)
            var bitmap: Bitmap? = null
            try {
                bitmap = loadUniversalScaledBitmap(context, uri)
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val result = recognizer.process(inputImage).await()
                val text = result.text.trim()
                if (text.isEmpty()) {
                    Result.failure(OcrException.NoTextFoundException())
                } else {
                    Result.success(text)
                }
            } catch (_: OutOfMemoryError) {
                Result.failure(OcrException.MemoryException())
            } catch (e: Exception) {
                Log.e("OcrUseCase", "Bitmap fallback failed for $uri", e)
                Result.failure(OcrException.ExtractionFailedException(e.message))
            } finally {
                bitmap?.recycle()
            }
        } finally {
            recognizer.close()
        }
    }

    suspend fun getPdfPageCount(uri: Uri): Int = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = getParcelFileDescriptor(context, uri)
            renderer = PdfRenderer(pfd)
            renderer.pageCount
        } catch (_: Exception) {
            0
        } finally {
            renderer?.close()
            pfd?.close()
        }
    }

    suspend fun extractFromPdfPage(uri: Uri, pageIndex: Int, script: OcrScript): Result<String> = withContext(Dispatchers.IO) {
        var bitmap: Bitmap? = null
        try {
            bitmap = renderPdfPage(context, uri, pageIndex)
            processBitmapForScript(bitmap, script)
        } catch (_: OutOfMemoryError) {
            Result.failure(OcrException.MemoryException())
        } catch (e: Exception) {
            Log.e("OcrUseCase", "Failed to extract from PDF page", e)
            Result.failure(OcrException.ExtractionFailedException(e.message))
        } finally {
            bitmap?.recycle()
        }
    }

    suspend fun extractFromPdfAllPages(
        uri: Uri,
        script: OcrScript,
        onProgress: (currentPage: Int, totalPages: Int) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val totalPages = getPdfPageCount(uri)
        if (totalPages <= 0) {
            return@withContext Result.failure(OcrException.ExtractionFailedException("Invalid or empty PDF file"))
        }

        val recognizer = getRecognizerForScript(script)
        try {
            val fullTextBuilder = StringBuilder()
            var hasFoundAnyText = false

            for (pageIndex in 0 until totalPages) {
                ensureActive()
                onProgress(pageIndex + 1, totalPages)
                var pageBitmap: Bitmap? = null
                try {
                    pageBitmap = renderPdfPage(context, uri, pageIndex)
                    val pageInput = InputImage.fromBitmap(pageBitmap, 0)
                    val result = recognizer.process(pageInput).await()
                    val text = result.text.trim()
                    
                    if (text.isNotBlank()) {
                        hasFoundAnyText = true
                        if (totalPages > 1) {
                            if (fullTextBuilder.isNotEmpty()) fullTextBuilder.append("\n\n")
                            fullTextBuilder.append("--- Page ").append(pageIndex + 1).append(" ---\n")
                        }
                        fullTextBuilder.append(text)
                    }
                } catch (_: OutOfMemoryError) {
                    // Continue
                } catch (e: Exception) {
                    Log.e("OcrUseCase", "Failed to extract text from page $pageIndex", e)
                    // Continue
                } finally {
                    pageBitmap?.recycle()
                }
            }

            if (!hasFoundAnyText || fullTextBuilder.isBlank()) {
                Result.failure(OcrException.NoTextFoundException())
            } else {
                Result.success(fullTextBuilder.toString().trim())
            }
        } finally {
            recognizer.close()
        }
    }

    fun loadUniversalScaledBitmap(context: Context, uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val maxDim = maxOf(info.size.width, info.size.height)
                    if (maxDim > 4096) {
                        val sample = maxDim / 4096 + 1
                        decoder.setTargetSampleSize(sample)
                    }
                }
                return fixOrientationIfNeeded(context, uri, bitmap)
            } catch (_: Exception) {
                // Fallback
            }
        }

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        FileHelper.readFileFromUri(context, uri).use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        val origWidth = options.outWidth
        val origHeight = options.outHeight

        if (origWidth <= 0 || origHeight <= 0) {
            throw IllegalArgumentException("Could not decode image bounds")
        }

        val maxDimension = 4096
        var inSampleSize = 1
        if (origWidth > maxDimension || origHeight > maxDimension) {
            val halfWidth = origWidth / 2
            val halfHeight = origHeight / 2
            while ((halfWidth / inSampleSize) >= maxDimension || (halfHeight / inSampleSize) >= maxDimension) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            this.inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decodedBitmap = FileHelper.readFileFromUri(context, uri).use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IllegalArgumentException("Failed to decode bitmap")

        return fixOrientationIfNeeded(context, uri, decodedBitmap)
    }

    private fun fixOrientationIfNeeded(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            FileHelper.readFileFromUri(context, uri).use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }

        return if (!matrix.isIdentity) {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } else {
            bitmap
        }
    }

    private fun renderPdfPage(context: Context, uri: Uri, pageIndex: Int): Bitmap {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        try {
            pfd = getParcelFileDescriptor(context, uri)
            renderer = PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                throw IllegalArgumentException("Invalid page index: $pageIndex")
            }
            page = renderer.openPage(pageIndex)

            val scale = 300f / 72f
            val targetWidth = (page.width * scale).toInt().coerceAtMost(3200)
            val targetHeight = (page.height * scale).toInt().coerceAtMost(3200)

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        } finally {
            page?.close()
            renderer?.close()
            pfd?.close()
        }
    }

    private fun getParcelFileDescriptor(context: Context, uri: Uri): ParcelFileDescriptor {
        if (uri.scheme == "file" && uri.path != null) {
            val file = File(uri.path!!)
            if (file.exists()) {
                return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
        }
        val contentPfd = context.contentResolver.openFileDescriptor(uri, "r")
        if (contentPfd != null) {
            return contentPfd
        }

        val tempFile = File(context.cacheDir, "ocr_temp_${System.currentTimeMillis()}.pdf")
        FileHelper.readFileFromUri(context, uri).use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        return ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }
}
sealed class OcrException(message: String) : Exception(message) {
    class NoTextFoundException : OcrException("No readable text detected. Try a clearer, well-lit image or a different page.")
    class MemoryException : OcrException("Image is too large for text extraction. Try a smaller image.")
    class ModelDownloadException(detail: String? = null) : OcrException("Internet required for first-time model download. ${detail ?: ""}".trim())
    class ExtractionFailedException(detail: String? = null) : OcrException("Text extraction failed. The image may be too blurry or at an angle. ${detail ?: ""}".trim())
}
