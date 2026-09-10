package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.graphics.pdf.PdfRenderer
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

import com.morphdrop.app.domain.model.PdfSizeAnalysis

data class CompressResult(
    val outputUri: Uri,
    val originalSize: Long,
    val newSize: Long
)

enum class CompressionLevel(val quality: Float, val scaleFactor: Float) {
    LOW(quality = 0.8f, scaleFactor = 0.9f),
    MEDIUM(quality = 0.5f, scaleFactor = 0.7f),
    HIGH(quality = 0.2f, scaleFactor = 0.4f)
}

class CompressPdfUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    init {
        try {
            PDFBoxResourceLoader.init(context)
        } catch (_: Exception) {}
    }

    suspend fun analyzePdf(pdfUri: Uri): PdfSizeAnalysis = withContext(Dispatchers.IO) {
        val totalSize = FileHelper.getFileSize(context, pdfUri)
        try {
            val inputStream = FileHelper.readFileFromUri(context, pdfUri)
            val document = PDDocument.load(inputStream)
            try {
                var imageBytes = 0L
                var imageCount = 0
                val seenCosObjects = mutableSetOf<COSBase>()

                fun inspectResources(resources: PDResources?) {
                    if (resources == null) return
                    for (name in resources.xObjectNames) {
                        try {
                            val xObj = resources.getXObject(name)
                            if (xObj is PDImageXObject) {
                                val cosObj = xObj.cosObject
                                if (seenCosObjects.add(cosObj)) {
                                    imageCount++
                                    val stream = cosObj as? com.tom_roush.pdfbox.cos.COSStream
                                    val len = stream?.length?.toLong() ?: 0L
                                    imageBytes += if (len > 0) len else (xObj.width * xObj.height * 3L / 10L)
                                }
                            } else if (xObj is PDFormXObject) {
                                inspectResources(xObj.resources)
                            }
                        } catch (_: Exception) {}
                    }
                }

                for (page in document.pages) {
                    inspectResources(page.resources)
                }

                val nonImage = (totalSize - imageBytes).coerceAtLeast(0L)
                val estMin = nonImage + (imageCount * 12 * 1024L)

                PdfSizeAnalysis(
                    totalSizeBytes = totalSize,
                    totalImageBytes = imageBytes,
                    nonImageOverheadBytes = nonImage,
                    imageCount = imageCount,
                    estimatedMinBytes = estMin
                )
            } finally {
                document.close()
                inputStream.close()
            }
        } catch (e: Exception) {
            PdfSizeAnalysis(
                totalSizeBytes = totalSize,
                totalImageBytes = 0L,
                nonImageOverheadBytes = totalSize,
                imageCount = 0,
                estimatedMinBytes = totalSize
            )
        }
    }

    suspend operator fun invoke(
        pdfUri: Uri,
        compressionLevel: CompressionLevel = CompressionLevel.MEDIUM,
        targetSizeKb: Int? = null,
        outputFileName: String = "compressed_${System.currentTimeMillis()}.pdf",
        subFolder: String? = null,
        onProgress: ((iteration: Int, maxIterations: Int, currentSize: Long) -> Unit)? = null
    ): CompressResult = withContext(Dispatchers.IO) {
        val originalSize = FileHelper.getFileSize(context, pdfUri)
        val targetBytes = (targetSizeKb ?: 0) * 1024L

        // If target size mode is requested, use the Heuristic First Jump + Binary Search
        if (targetSizeKb != null && targetBytes > 0) {
            val analysis = analyzePdf(pdfUri)
            val nonImage = analysis.nonImageOverheadBytes
            val imageBytes = analysis.totalImageBytes

            // Heuristic Step: Calculate starting quality & scale factor
            val targetImageBytes = (targetBytes - nonImage).coerceAtLeast(1024L)
            val ratio = if (imageBytes > 0) {
                (targetImageBytes.toFloat() / imageBytes.toFloat()).coerceIn(0.05f, 1.0f)
            } else 0.5f

            var quality = (ratio * 0.85f).coerceIn(0.12f, 0.85f)
            var scale = Math.sqrt(ratio.toDouble()).toFloat().coerceIn(0.28f, 1.0f)

            var bestBytes: ByteArray? = null
            var bestDistance = Long.MAX_VALUE
            val maxIterations = 4

            for (iteration in 1..maxIterations) {
                kotlinx.coroutines.yield()

                val inputStream = FileHelper.readFileFromUri(context, pdfUri)
                val doc = PDDocument.load(inputStream)
                val currentBytes: ByteArray
                try {
                    doc.documentInformation = PDDocumentInformation().apply {
                        producer = "MorphDrop PDF"
                        creator = "MorphDrop"
                    }

                    val imageMap = mutableMapOf<COSBase, PDImageXObject>()
                    for (page in doc.pages) {
                        kotlinx.coroutines.yield()
                        compressResources(doc, page.resources, quality, scale, imageMap)
                    }

                    val baos = ByteArrayOutputStream()
                    doc.save(baos)
                    currentBytes = baos.toByteArray()
                } finally {
                    doc.close()
                    inputStream.close()
                }

                onProgress?.invoke(iteration, maxIterations, currentBytes.size.toLong())

                val distance = Math.abs(currentBytes.size - targetBytes)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestBytes = currentBytes
                }

                // Stop if within 5% of target size
                val tolerance = (targetBytes * 0.05).toLong().coerceAtLeast(20 * 1024L)
                if (distance <= tolerance) {
                    break
                }

                // Binary search adjustments
                if (currentBytes.size > targetBytes) {
                    // Too large -> compress more aggressively
                    quality = (quality * 0.65f).coerceIn(0.10f, 0.90f)
                    scale = (scale * 0.80f).coerceIn(0.25f, 1.0f)
                } else {
                    // Too small -> relax compression slightly to preserve visual quality
                    quality = (quality * 1.25f).coerceIn(0.10f, 0.90f)
                    scale = (scale * 1.15f).coerceIn(0.25f, 1.0f)
                }
            }

            val finalBytes = bestBytes ?: run {
                val s = FileHelper.readFileFromUri(context, pdfUri)
                val b = s.readBytes()
                s.close()
                b
            }

            val sanitizedFileName = if (outputFileName.endsWith(".pdf", ignoreCase = true)) {
                outputFileName
            } else {
                "$outputFileName.pdf"
            }

            val outputUri = if (!subFolder.isNullOrBlank()) {
                FileHelper.saveToDirectory(context, subFolder, sanitizedFileName, finalBytes)
            } else {
                FileHelper.saveToFile(context, settingsRepository, sanitizedFileName, finalBytes)
            }

            return@withContext CompressResult(
                outputUri = outputUri,
                originalSize = originalSize,
                newSize = finalBytes.size.toLong()
            )
        }

        // Standard Quality Level Compression (single pass)
        val inputStream = FileHelper.readFileFromUri(context, pdfUri)
        val document = PDDocument.load(inputStream)

        try {
            document.documentInformation = PDDocumentInformation().apply {
                producer = "MorphDrop PDF"
                creator = "MorphDrop"
            }

            val imageMap = mutableMapOf<COSBase, PDImageXObject>()

            for (page in document.pages) {
                kotlinx.coroutines.yield()
                compressResources(document, page.resources, compressionLevel.quality, compressionLevel.scaleFactor, imageMap)
            }

            val baos = ByteArrayOutputStream()
            document.save(baos)
            val bytes = baos.toByteArray()

            val sanitizedFileName = if (outputFileName.endsWith(".pdf", ignoreCase = true)) {
                outputFileName
            } else {
                "$outputFileName.pdf"
            }

            val outputUri = if (!subFolder.isNullOrBlank()) {
                FileHelper.saveToDirectory(context, subFolder, sanitizedFileName, bytes)
            } else {
                FileHelper.saveToFile(context, settingsRepository, sanitizedFileName, bytes)
            }

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

    private fun compressResources(
        document: PDDocument,
        resources: PDResources?,
        quality: Float,
        scale: Float,
        imageMap: MutableMap<COSBase, PDImageXObject>
    ) {
        if (resources == null) return

        val names = resources.xObjectNames.toList()
        for (name in names) {
            val xObject = resources.getXObject(name)

            if (xObject is PDImageXObject) {
                val cosObject = xObject.cosObject
                
                // If we've already compressed this image, reuse the new object
                if (imageMap.containsKey(cosObject)) {
                    resources.put(name, imageMap[cosObject])
                    continue
                }

                try {
                    val bitmap = xObject.image ?: continue
                    
                    val scaledWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
                    val scaledHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)

                    val scaledBitmap = if (scaledWidth != bitmap.width) {
                        Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                    } else bitmap

                    // JPEG doesn't support alpha transparency, which causes silent failures.
                    // Flatten to a white background before compressing.
                    val noAlphaBitmap = Bitmap.createBitmap(scaledBitmap.width, scaledBitmap.height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(noAlphaBitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    canvas.drawBitmap(scaledBitmap, 0f, 0f, null)

                    val compressedImage = JPEGFactory.createFromImage(document, noAlphaBitmap, quality)
                    
                    // CRITICAL: Check if the new JPEG is actually smaller than the original image!
                    // If the original was a 1-bit B&W scan (CCITTFax/JBIG2), converting to a 32-bit JPEG 
                    // will MASSIVELY inflate the byte size, ruining compression.
                    var shouldReplace = true
                    try {
                        val oldStream = xObject.cosObject as? com.tom_roush.pdfbox.cos.COSStream
                        val newStream = compressedImage.cosObject as? com.tom_roush.pdfbox.cos.COSStream
                        
                        if (oldStream != null && newStream != null) {
                            // Estimate size based on stream length dictionary entry or raw bytes
                            val oldLength = oldStream.length
                            val newLength = newStream.length
                            if (oldLength > 0 && newLength >= oldLength) {
                                shouldReplace = false // The original encoding was more efficient!
                            }
                        }
                    } catch (_: Exception) {}

                    if (shouldReplace) {
                        // Update current resource dictionary
                        resources.put(name, compressedImage)
                        // Map old object to new object for other pages to reuse
                        imageMap[cosObject] = compressedImage
                    }
                    
                    
                    noAlphaBitmap.recycle()
                    if (scaledBitmap != bitmap) scaledBitmap.recycle()
                    bitmap.recycle()
                } catch (_: Exception) {}
            } else if (xObject is PDFormXObject) {
                // Recursively handle nested resources (crucial for complex PDFs)
                compressResources(document, xObject.resources, quality, scale, imageMap)
            }
        }
    }
}
