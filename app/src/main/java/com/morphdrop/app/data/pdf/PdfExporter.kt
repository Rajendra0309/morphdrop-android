package com.morphdrop.app.data.pdf

import android.content.Context
import android.net.Uri
import com.morphdrop.app.domain.model.PdfAnnotation
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfExporter {
    suspend fun exportAnnotatedPdf(
        context: Context,
        sourceFile: File,
        targetUri: Uri,
        annotations: List<PdfAnnotation>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val document = PDDocument.load(sourceFile)
            
            // Group annotations by page
            val annotationsByPage = annotations.groupBy { it.pageIndex }

            for ((pageIndex, pageAnnots) in annotationsByPage) {
                if (pageIndex < 0 || pageIndex >= document.numberOfPages) continue

                val page = document.getPage(pageIndex)
                val mediaBox = page.mediaBox
                val pWidth = mediaBox.width
                val pHeight = mediaBox.height

                // Open content stream in append mode
                val contentStream = PDPageContentStream(
                    document, 
                    page, 
                    PDPageContentStream.AppendMode.APPEND, 
                    true, 
                    true
                )

                // Shared graphics state for transparency
                val highlightState = PDExtendedGraphicsState().apply {
                    blendMode = BlendMode.MULTIPLY
                    nonStrokingAlphaConstant = 0.4f
                }
                
                for (ann in pageAnnots) {
                    when (ann) {
                        is PdfAnnotation.Highlight -> {
                            contentStream.saveGraphicsState()
                            contentStream.setGraphicsStateParameters(highlightState)
                            
                            val color = ann.color
                            contentStream.setNonStrokingColor(
                                (color.red * 255).toInt(),
                                (color.green * 255).toInt(),
                                (color.blue * 255).toInt()
                            )

                            for (rect in ann.boundingBoxes) {
                                // rect coordinates are normalized [0..1]
                                // PDF Origin is Bottom-Left, Android Origin is Top-Left
                                val x = rect.left * pWidth
                                val topY = (1f - rect.top) * pHeight
                                val bottomY = (1f - rect.bottom) * pHeight
                                val w = rect.width * pWidth
                                val h = topY - bottomY

                                contentStream.addRect(x, bottomY, w, h)
                                contentStream.fill()
                            }
                            
                            contentStream.restoreGraphicsState()
                        }
                        is PdfAnnotation.Drawing -> {
                            if (ann.pathPoints.isEmpty()) continue
                            
                            contentStream.saveGraphicsState()
                            
                            // Check if it's a highlighter stroke (alpha < 1)
                            val isHighlighter = ann.color.alpha < 1f
                            if (isHighlighter) {
                                val drawState = PDExtendedGraphicsState().apply {
                                    blendMode = BlendMode.MULTIPLY
                                    strokingAlphaConstant = ann.color.alpha
                                }
                                contentStream.setGraphicsStateParameters(drawState)
                            } else {
                                val drawState = PDExtendedGraphicsState().apply {
                                    strokingAlphaConstant = 1f
                                }
                                contentStream.setGraphicsStateParameters(drawState)
                            }
                            
                            val color = ann.color
                            contentStream.setStrokingColor(
                                (color.red * 255).toInt(),
                                (color.green * 255).toInt(),
                                (color.blue * 255).toInt()
                            )
                            
                            // PDF line width must match scale
                            contentStream.setLineWidth(ann.strokeWidth)
                            contentStream.setLineCapStyle(1) // Round
                            contentStream.setLineJoinStyle(1) // Round

                            val p0 = ann.pathPoints[0]
                            contentStream.moveTo(p0.x * pWidth, (1f - p0.y) * pHeight)

                            for (i in 1 until ann.pathPoints.size) {
                                val p = ann.pathPoints[i]
                                contentStream.lineTo(p.x * pWidth, (1f - p.y) * pHeight)
                            }

                            contentStream.stroke()
                            contentStream.restoreGraphicsState()
                        }
                    }
                }
                
                contentStream.close()
            }

            // Save to a temporary local file first to prevent 0kb corruption over ContentResolver streams
            val tempOutFile = File(context.cacheDir, "export_temp_${System.currentTimeMillis()}.pdf")
            document.save(tempOutFile)
            document.close()

            // Copy the fully written temporary file to the final destination URI
            context.contentResolver.openOutputStream(targetUri)?.use { outStream ->
                tempOutFile.inputStream().use { inStream ->
                    inStream.copyTo(outStream)
                }
            }
            
            // Clean up temporary file
            if (tempOutFile.exists()) {
                tempOutFile.delete()
            }
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
