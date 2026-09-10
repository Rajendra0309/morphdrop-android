package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.net.Uri
import com.morphdrop.app.domain.model.PageNumberConfig
import com.morphdrop.app.domain.model.PageNumberFormat
import com.morphdrop.app.domain.model.PageNumberPosition
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class AddPageNumbersUseCase @Inject constructor(
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
        config: PageNumberConfig,
        outputFileName: String = "numbered_${System.currentTimeMillis()}.pdf",
        subFolder: String? = null
    ): Uri = withContext(Dispatchers.IO) {
        val inputStream = FileHelper.readFileFromUri(context, pdfUri)
        val document = PDDocument.load(inputStream)

        try {
            val totalPages = document.numberOfPages
            val font = PDType1Font.HELVETICA
            val fontSize = config.fontSizeSp.coerceIn(8f, 32f)

            // Extract RGB components
            val red = ((config.fontColor shr 16) and 0xFF).toInt()
            val green = ((config.fontColor shr 8) and 0xFF).toInt()
            val blue = (config.fontColor and 0xFF).toInt()

            val margin = config.marginDp.coerceIn(10f, 60f)

            for (pageIndex in 0 until totalPages) {
                kotlinx.coroutines.yield()

                if (config.skipFirstPage && pageIndex == 0) {
                    continue
                }
                if (config.skipLastPage && pageIndex == totalPages - 1) {
                    continue
                }
                if (config.targetPages != null && !config.targetPages.contains(pageIndex + 1)) {
                    continue
                }

                val page = document.getPage(pageIndex)
                val mediaBox = page.mediaBox
                val pageWidth = mediaBox.width
                val pageHeight = mediaBox.height

                val currentNum = pageIndex + config.startNumber

                val pageText = when (config.format) {
                    PageNumberFormat.PAGE_X -> "Page $currentNum"
                    PageNumberFormat.NUMBER_ONLY -> "$currentNum"
                    PageNumberFormat.PAGE_X_OF_Y -> "Page $currentNum of $totalPages"
                    PageNumberFormat.X_OF_Y -> "$currentNum/$totalPages"
                    PageNumberFormat.CUSTOM -> {
                        val template = if (config.customTemplate.isNotBlank() && config.customTemplate.contains("{page}")) {
                            config.customTemplate
                        } else {
                            "- {page} -"
                        }
                        template
                            .replace("{page}", "$currentNum")
                            .replace("{total}", "$totalPages")
                    }
                }

                val sanitizedText = FileHelper.sanitizeForPdfBox(pageText)
                val textWidth = (font.getStringWidth(sanitizedText) / 1000f) * fontSize

                val (x, y) = calculateCoordinates(
                    position = config.position,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                    textWidth = textWidth,
                    fontSize = fontSize,
                    margin = margin
                )

                val contentStream = PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                )

                try {
                    contentStream.beginText()
                    contentStream.setFont(font, fontSize)
                    contentStream.setNonStrokingColor(red / 255f, green / 255f, blue / 255f)
                    contentStream.newLineAtOffset(x, y)
                    contentStream.showText(sanitizedText)
                    contentStream.endText()
                } finally {
                    contentStream.close()
                }
            }

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

    private fun calculateCoordinates(
        position: PageNumberPosition,
        pageWidth: Float,
        pageHeight: Float,
        textWidth: Float,
        fontSize: Float,
        margin: Float
    ): Pair<Float, Float> {
        val yBottom = margin
        val yTop = pageHeight - margin - fontSize

        return when (position) {
            PageNumberPosition.TOP_LEFT -> margin to yTop
            PageNumberPosition.TOP_CENTER -> ((pageWidth - textWidth) / 2f) to yTop
            PageNumberPosition.TOP_RIGHT -> (pageWidth - margin - textWidth) to yTop
            PageNumberPosition.BOTTOM_LEFT -> margin to yBottom
            PageNumberPosition.BOTTOM_CENTER -> ((pageWidth - textWidth) / 2f) to yBottom
            PageNumberPosition.BOTTOM_RIGHT -> (pageWidth - margin - textWidth) to yBottom
        }
    }
}
