package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.net.Uri
import com.morphdrop.app.util.FileHelper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextShape
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class PowerPointToPdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val MARGIN = 50f
        private const val LINE_SPACING = 1.3f
    }

    suspend operator fun invoke(
        pptxUri: Uri,
        outputFileName: String = "ppt_to_pdf_${System.currentTimeMillis()}.pdf"
    ): Uri = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context)

        val inputStream = FileHelper.readFileFromUri(context, pptxUri)
        val pptxDoc = XMLSlideShow(inputStream)
        val pdfDoc = PDDocument()

        try {
            val landscapeA4 = PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width)

            for ((slideIndex, slide) in pptxDoc.slides.withIndex()) {
                val page = PDPage(landscapeA4)
                pdfDoc.addPage(page)
                val contentStream = PDPageContentStream(pdfDoc, page)

                var yPos = landscapeA4.height - MARGIN
                val usableWidth = landscapeA4.width - (2 * MARGIN)

                contentStream.beginText()
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 10f)
                contentStream.newLineAtOffset(MARGIN, yPos)
                contentStream.showText("Slide ${slideIndex + 1}")
                contentStream.endText()
                yPos -= 25f

                for (shape in slide.shapes) {
                    if (shape is XSLFTextShape) {
                        val text = shape.text.trim()
                        if (text.isEmpty()) continue

                        val fontSize = 12f
                        val font = PDType1Font.HELVETICA
                        val lineHeight = fontSize * LINE_SPACING

                        val lines = wrapText(text, font, fontSize, usableWidth)

                        for (line in lines) {
                            if (yPos - lineHeight < MARGIN) break

                            contentStream.beginText()
                            contentStream.setFont(font, fontSize)
                            contentStream.newLineAtOffset(MARGIN, yPos)
                            contentStream.showText(line)
                            contentStream.endText()
                            yPos -= lineHeight
                        }

                        yPos -= 10f
                    }
                }

                contentStream.close()
            }

            if (pdfDoc.numberOfPages == 0) {
                val emptyPage = PDPage(landscapeA4)
                pdfDoc.addPage(emptyPage)
            }

            val baos = ByteArrayOutputStream()
            pdfDoc.save(baos)
            FileHelper.saveToCache(context, outputFileName, baos.toByteArray())
        } finally {
            pdfDoc.close()
            pptxDoc.close()
            inputStream.close()
        }
    }

    private fun wrapText(text: String, font: PDType1Font, fontSize: Float, maxWidth: Float): List<String> {
        val words = text.split("\\s+".toRegex())
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val width = font.getStringWidth(testLine) / 1000 * fontSize
            if (width > maxWidth && currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            } else {
                currentLine = StringBuilder(testLine)
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
        return lines
    }
}
