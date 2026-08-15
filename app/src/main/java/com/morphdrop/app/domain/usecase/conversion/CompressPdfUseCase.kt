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

    suspend operator fun invoke(
        pdfUri: Uri,
        compressionLevel: CompressionLevel = CompressionLevel.MEDIUM,
        targetSizeKb: Int? = null,
        outputFileName: String = "compressed_${System.currentTimeMillis()}.pdf"
    ): CompressResult = withContext(Dispatchers.IO) {
        val originalSize = FileHelper.getFileSize(context, pdfUri)
        val inputStream = FileHelper.readFileFromUri(context, pdfUri)
        
        // 1. Load the document
        val document = PDDocument.load(inputStream)

        try {
            val targetBytes = (targetSizeKb ?: 0) * 1024L
            
            // Wipe metadata to save extra space
            document.documentInformation = PDDocumentInformation().apply {
                producer = "MorphDrop PDF"
                creator = "MorphDrop"
            }

            // Mappings to handle shared images correctly
            val imageMap = mutableMapOf<COSBase, PDImageXObject>()

            // First deep pass
            for (page in document.pages) {
                kotlinx.coroutines.yield()
                compressResources(document, page.resources, compressionLevel.quality, compressionLevel.scaleFactor, imageMap)
            }

            // Save and verify
            val baos = ByteArrayOutputStream()
            document.save(baos)
            var bytes = baos.toByteArray()

            // 2. Optimization Loop (If Target Size is set)
            if (targetSizeKb != null && bytes.size > targetBytes) {
                // If still over, do one super-aggressive pass
                imageMap.clear()
                val pass2Quality = 0.15f
                val pass2Scale = 0.4f
                
                for (page in document.pages) {
                    kotlinx.coroutines.yield()
                    compressResources(document, page.resources, pass2Quality, pass2Scale, imageMap)
                }
                
                val baos2 = ByteArrayOutputStream()
                document.save(baos2)
                bytes = baos2.toByteArray()
            }

            // We do NOT use PdfRenderer rasterization fallback here. 
            // Rasterizing text/vector pages or B&W scans into JPEGs causes extreme blurriness 
            // and actually INCREASES the file size. Offline compression must strictly rely on 
            // internal image optimization.

            // 3. Final Size Check
            // If our optimization attempts made the file larger (e.g. converting 1-bit JBIG2 to 32-bit JPEG),
            // or if the PDF had no images (text-only), we output the original file to prevent size inflation.
            if (bytes.size >= originalSize && originalSize > 0) {
                return@withContext CompressResult(
                    outputUri = pdfUri, // Return original
                    originalSize = originalSize,
                    newSize = originalSize
                )
            }

            val outputUri = FileHelper.saveToFile(context, settingsRepository, outputFileName, bytes)

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
