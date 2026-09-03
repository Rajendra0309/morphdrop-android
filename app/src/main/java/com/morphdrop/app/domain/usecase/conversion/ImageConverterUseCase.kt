package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class ImageConverterUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    class InvalidImageException : Exception("Failed to decode source image")
    class ImageTooLargeException : Exception("Image is too large for device memory. Try reducing quality or size.")

    suspend operator fun invoke(
        inputUri: Uri,
        outputFormat: String = "jpg",
        quality: Int = 85,
        targetWidth: Int? = null,
        targetHeight: Int? = null,
        resizeScale: Float? = null, // e.g., 0.5f for 50%, 0.25f for 25%
        paddingColor: Int? = null,
        cropRect: Rect? = null,
        rotationDegrees: Int = 0,
        targetSizeKb: Int? = null,
        stripMetadata: Boolean = false,
        outputFolderName: String? = null,
        outputFileName: String = "converted_image_${System.currentTimeMillis()}.$outputFormat"
    ): Uri = withContext(Dispatchers.IO) {
        var originalBitmap: Bitmap? = null
        var processedBitmap: Bitmap? = null
        
        try {
            // 1. Check dimensions and orientation without loading full pixels
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            openInputStream(inputUri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
            if (options.outWidth == -1 || options.outHeight == -1) {
                throw InvalidImageException()
            }

            val orientation = getOrientation(inputUri)
            val isSwappedByExif = (orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270)
            val isSwappedByManual = (rotationDegrees % 180 != 0)
            val isSwappedTotal = isSwappedByExif xor isSwappedByManual
            
            val originalWidth = if (isSwappedTotal) options.outHeight else options.outWidth
            val originalHeight = if (isSwappedTotal) options.outWidth else options.outHeight

            // 2. Load image at FULL resolution (no automatic downscaling as per user requirement)
            options.inSampleSize = 1
            options.inJustDecodeBounds = false
            
            originalBitmap = try {
                openInputStream(inputUri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, options)
                } ?: throw InvalidImageException()
            } catch (e: OutOfMemoryError) {
                throw ImageTooLargeException()
            }

            // 3. Exif Orientation Support & Manual Rotation
            var currentBitmap = try {
                rotateBitmapIfNecessary(originalBitmap, orientation)
            } catch (e: OutOfMemoryError) {
                throw ImageTooLargeException()
            }

            if (rotationDegrees % 360 != 0) {
                try {
                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    val manuallyRotated = Bitmap.createBitmap(currentBitmap, 0, 0, currentBitmap.width, currentBitmap.height, matrix, true)
                    if (manuallyRotated != currentBitmap) {
                        if (currentBitmap != originalBitmap) {
                            currentBitmap.recycle()
                        }
                        currentBitmap = manuallyRotated
                    }
                } catch (e: OutOfMemoryError) {
                    throw ImageTooLargeException()
                }
            }

            // 4. Cropping if cropRect is specified
            if (cropRect != null) {
                val scaleX = currentBitmap.width.toFloat() / originalWidth.coerceAtLeast(1)
                val scaleY = currentBitmap.height.toFloat() / originalHeight.coerceAtLeast(1)
                
                val safeLeft = max(0, (cropRect.left * scaleX).toInt())
                val safeTop = max(0, (cropRect.top * scaleY).toInt())
                val safeRight = min(currentBitmap.width, (cropRect.right * scaleX).toInt())
                val safeBottom = min(currentBitmap.height, (cropRect.bottom * scaleY).toInt())
                
                if (safeLeft < safeRight && safeTop < safeBottom) {
                    try {
                        val cropped = Bitmap.createBitmap(currentBitmap, safeLeft, safeTop, safeRight - safeLeft, safeBottom - safeTop)
                        if (cropped != currentBitmap) {
                            if (currentBitmap != originalBitmap) {
                                currentBitmap.recycle()
                            }
                            currentBitmap = cropped
                        }
                    } catch (e: OutOfMemoryError) {
                        throw ImageTooLargeException()
                    }
                }
            }

            // 5. User-Selected Resizing (Explicit Percentage or Width/Height)
            if (resizeScale != null && resizeScale > 0f && resizeScale < 1f) {
                val newWidth = (currentBitmap.width * resizeScale).toInt().coerceAtLeast(1)
                val newHeight = (currentBitmap.height * resizeScale).toInt().coerceAtLeast(1)
                try {
                    val scaled = Bitmap.createScaledBitmap(currentBitmap, newWidth, newHeight, true)
                    if (scaled != currentBitmap) {
                        if (currentBitmap != originalBitmap) {
                            currentBitmap.recycle()
                        }
                        currentBitmap = scaled
                    }
                } catch (e: OutOfMemoryError) {
                    throw ImageTooLargeException()
                }
            } else if (targetWidth != null && targetHeight != null && targetWidth > 0 && targetHeight > 0) {
                if (currentBitmap.width != targetWidth || currentBitmap.height != targetHeight) {
                    try {
                        val paddedBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(paddedBitmap)
                        
                        paddingColor?.let { canvas.drawColor(it) }

                        val scale = min(
                            targetWidth.toFloat() / currentBitmap.width,
                            targetHeight.toFloat() / currentBitmap.height
                        )

                        val scaledWidth = (currentBitmap.width * scale).toInt()
                        val scaledHeight = (currentBitmap.height * scale).toInt()
                        
                        val left = (targetWidth - scaledWidth) / 2f
                        val top = (targetHeight - scaledHeight) / 2f
                        
                        val destRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
                        canvas.drawBitmap(currentBitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
                        
                        if (currentBitmap != originalBitmap) {
                            currentBitmap.recycle()
                        }
                        currentBitmap = paddedBitmap
                    } catch (e: OutOfMemoryError) {
                        throw ImageTooLargeException()
                    }
                }
            }
            
            processedBitmap = currentBitmap

            // 6. Output Compression
            val format = when (outputFormat.lowercase()) {
                "png", "bmp" -> Bitmap.CompressFormat.PNG
                "webp" -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                else -> Bitmap.CompressFormat.JPEG
            }

            var baos = ByteArrayOutputStream()
            val finalProcessed = processedBitmap!!
            
            if (targetSizeKb != null && targetSizeKb > 0) {
                val targetBytes = targetSizeKb * 1024L
                var currentQuality = quality.coerceIn(5, 100)
                var loopBitmap = finalProcessed
                var loopCount = 0
                val maxLoops = 20
                
                while (loopCount < maxLoops) {
                    baos = ByteArrayOutputStream()
                    loopBitmap.compress(format, currentQuality, baos)
                    
                    if (baos.size() <= targetBytes) {
                        break
                    } else {
                        if (format == Bitmap.CompressFormat.PNG || currentQuality <= 30) {
                            val nextBitmap = try {
                                Bitmap.createScaledBitmap(
                                    loopBitmap, 
                                    (loopBitmap.width * 0.9).toInt().coerceAtLeast(1), 
                                    (loopBitmap.height * 0.9).toInt().coerceAtLeast(1), 
                                    true
                                )
                            } catch (e: OutOfMemoryError) {
                                throw ImageTooLargeException()
                            }
                            if (loopBitmap != finalProcessed) {
                                loopBitmap.recycle()
                            }
                            loopBitmap = nextBitmap
                            if (format != Bitmap.CompressFormat.PNG) {
                                currentQuality = min(quality + 10, 90)
                            }
                        } else {
                            currentQuality = (currentQuality - 10).coerceAtLeast(5)
                        }
                    }
                    loopCount++
                }
                
                if (loopBitmap != finalProcessed) {
                    processedBitmap = loopBitmap
                    if (finalProcessed != originalBitmap) {
                        finalProcessed.recycle()
                    }
                }
            } else {
                finalProcessed.compress(format, quality, baos)
            }

            val fileData = baos.toByteArray()
            baos.close()

            if (outputFolderName != null) {
                FileHelper.saveToFile(context, outputFolderName, outputFileName, fileData)
            } else {
                FileHelper.saveToFile(context, settingsRepository, outputFileName, fileData)
            }
        } finally {
            originalBitmap?.recycle()
            if (processedBitmap != originalBitmap) {
                processedBitmap?.recycle()
            }
        }
    }

    private fun openInputStream(uri: Uri): InputStream? {
        return try {
            FileHelper.readFileFromUri(context, uri)
        } catch (e: Exception) {
            null
        }
    }

    private fun getOrientation(uri: Uri): Int {
        return try {
            openInputStream(uri)?.use { inputStream ->
                val exifInterface = ExifInterface(inputStream)
                exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun rotateBitmapIfNecessary(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }
}