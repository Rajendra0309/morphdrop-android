package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.morphdrop.app.domain.model.WatermarkConfig
import com.morphdrop.app.domain.model.WatermarkPosition
import com.morphdrop.app.domain.model.WatermarkType
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class WatermarkPdfUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    init {
        try {
            if (!PDFBoxResourceLoader.isReady()) {
                PDFBoxResourceLoader.init(context)
            }
        } catch (_: Exception) {}
    }

    suspend operator fun invoke(
        pdfUri: Uri,
        config: WatermarkConfig,
        outputFileName: String = "watermarked_${System.currentTimeMillis()}.pdf",
        subFolder: String? = null
    ): Uri = withContext(Dispatchers.IO) {
        val inputStream = FileHelper.readFileFromUri(context, pdfUri)
        val document = PDDocument.load(inputStream)

        try {
            val totalPages = document.numberOfPages
            val font = PDType1Font.HELVETICA_BOLD
            val fontSize = config.fontSizeSp.coerceIn(10f, 120f)
            val opacity = config.opacity.coerceIn(0.05f, 1.0f)

            // Parse font colors
            val red = ((config.fontColor shr 16) and 0xFF).toInt()
            val green = ((config.fontColor shr 8) and 0xFF).toInt()
            val blue = (config.fontColor and 0xFF).toInt()

            val sanitizedText = FileHelper.sanitizeForPdfBox(
                if (config.text.isNotBlank()) config.text else "CONFIDENTIAL"
            )

            // Cache image bitmap if image watermark
            val imageBitmap = if (config.type == WatermarkType.IMAGE && config.imageUri != null) {
                try {
                    val imgStream = FileHelper.readFileFromUri(context, Uri.parse(config.imageUri))
                    BitmapFactory.decodeStream(imgStream)?.also { imgStream.close() }
                } catch (_: Exception) {
                    null
                }
            } else null

            val pdImage = if (imageBitmap != null) {
                try {
                    if (imageBitmap.hasAlpha()) {
                        LosslessFactory.createFromImage(document, imageBitmap)
                    } else {
                        JPEGFactory.createFromImage(document, imageBitmap, 0.85f)
                    }
                } catch (_: Exception) {
                    null
                }
            } else null

            for (pageIndex in 0 until totalPages) {
                kotlinx.coroutines.yield()

                if (config.skipFirstPage && pageIndex == 0) {
                    continue
                }

                if (config.targetPages != null && !config.targetPages.contains(pageIndex + 1)) {
                    continue
                }

                val page = document.getPage(pageIndex)
                val mediaBox = page.mediaBox
                val pageWidth = mediaBox.width
                val pageHeight = mediaBox.height

                val contentStream = PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                )

                try {
                    val graphicsState = PDExtendedGraphicsState().apply {
                        nonStrokingAlphaConstant = opacity
                        strokingAlphaConstant = opacity
                    }
                    contentStream.setGraphicsStateParameters(graphicsState)

                    if (config.type == WatermarkType.TEXT) {
                        val textWidth = (font.getStringWidth(sanitizedText) / 1000f) * fontSize
                        val textHeight = fontSize * 0.75f

                        val (targetX, targetY) = calculatePosition(
                            config.position,
                            pageWidth,
                            pageHeight,
                            textWidth,
                            textHeight
                        )

                        contentStream.beginText()
                        contentStream.setFont(font, fontSize)
                        contentStream.setNonStrokingColor(red / 255f, green / 255f, blue / 255f)

                        val rotation = if (config.position == WatermarkPosition.DIAGONAL) {
                            config.rotationDegrees
                        } else {
                            config.rotationDegrees
                        }

                        if (rotation != 0f) {
                            val rad = Math.toRadians(rotation.toDouble())
                            val matrix = Matrix.getRotateInstance(rad, targetX + textWidth / 2f, targetY + textHeight / 2f)
                            matrix.translate(-textWidth / 2f, -textHeight / 2f)
                            contentStream.setTextMatrix(matrix)
                        } else {
                            contentStream.newLineAtOffset(targetX, targetY)
                        }

                        contentStream.showText(sanitizedText)
                        contentStream.endText()
                    } else if (pdImage != null) {
                        val scale = config.imageScale.coerceIn(0.1f, 1.0f)
                        val targetWidth = pageWidth * scale
                        val aspectRatio = pdImage.height.toFloat() / pdImage.width.toFloat()
                        val targetHeight = targetWidth * aspectRatio

                        val (targetX, targetY) = calculatePosition(
                            config.position,
                            pageWidth,
                            pageHeight,
                            targetWidth,
                            targetHeight
                        )

                        if (config.rotationDegrees != 0f) {
                            contentStream.saveGraphicsState()
                            val rad = Math.toRadians(config.rotationDegrees.toDouble())
                            val matrix = Matrix.getRotateInstance(
                                rad,
                                targetX + targetWidth / 2f,
                                targetY + targetHeight / 2f
                            )
                            contentStream.transform(matrix)
                            contentStream.drawImage(pdImage, -targetWidth / 2f, -targetHeight / 2f, targetWidth, targetHeight)
                            contentStream.restoreGraphicsState()
                        } else {
                            contentStream.drawImage(pdImage, targetX, targetY, targetWidth, targetHeight)
                        }
                    }
                } finally {
                    contentStream.close()
                }
            }

            imageBitmap?.recycle()

            val baos = ByteArrayOutputStream()
            document.save(baos)
            val bytes = baos.toByteArray()

            val sanitizedFileName = if (outputFileName.endsWith(".pdf", ignoreCase = true)) {
                outputFileName
            } else {
                "$outputFileName.pdf"
            }

            if (!subFolder.isNullOrBlank()) {
                FileHelper.saveToDirectory(context, subFolder, sanitizedFileName, bytes)
            } else {
                FileHelper.saveToFile(context, settingsRepository, sanitizedFileName, bytes)
            }
        } finally {
            document.close()
            inputStream.close()
        }
    }

    private fun calculatePosition(
        position: WatermarkPosition,
        pageWidth: Float,
        pageHeight: Float,
        elementWidth: Float,
        elementHeight: Float
    ): Pair<Float, Float> {
        val margin = 36f
        return when (position) {
            WatermarkPosition.CENTER, WatermarkPosition.DIAGONAL -> {
                val x = (pageWidth - elementWidth) / 2f
                val y = (pageHeight - elementHeight) / 2f
                x to y
            }
            WatermarkPosition.TOP_LEFT -> {
                val x = margin
                val y = pageHeight - margin - elementHeight
                x to y
            }
            WatermarkPosition.TOP_RIGHT -> {
                val x = pageWidth - margin - elementWidth
                val y = pageHeight - margin - elementHeight
                x to y
            }
            WatermarkPosition.BOTTOM_LEFT -> {
                val x = margin
                val y = margin
                x to y
            }
            WatermarkPosition.BOTTOM_RIGHT -> {
                val x = pageWidth - margin - elementWidth
                val y = margin
                x to y
            }
        }
    }
}
