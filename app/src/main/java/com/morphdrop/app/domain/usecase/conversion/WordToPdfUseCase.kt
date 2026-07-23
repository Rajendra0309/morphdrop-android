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
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class WordToPdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val MARGIN = 50f
        private const val LINE_SPACING = 1.4f
        private const val FONT_SIZE_NORMAL = 11f
        private const val FONT_SIZE_H1 = 20f
        private const val FONT_SIZE_H2 = 16f
        private const val FONT_SIZE_H3 = 13f
    }

    suspend operator fun invoke(
        docxUri: Uri,
        outputFileName: String = "word_to_pdf_${System.currentTimeMillis()}.pdf"
    ): Uri = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context)

        val inputStream = FileHelper.readFileFromUri(context, docxUri)
        val xwpfDoc = XWPFDocument(inputStream)
        val pdfDoc = PDDocument()

        try {
            val pageWidth = PDRectangle.A4.width
            val usableWidth = pageWidth - 2 * MARGIN
            var page = PDPage(PDRectangle.A4)
            pdfDoc.addPage(page)
            var contentStream = PDPageContentStream(pdfDoc, page)
            var yPos = PDRectangle.A4.height - MARGIN

            for (paragraph in xwpfDoc.paragraphs) {
                val fontSize = getFontSize(paragraph)
                val font = getFont(paragraph)
                val lineHeight = fontSize * LINE_SPACING

                // Check if we need a new page
                if (yPos - lineHeight < MARGIN) {
                    contentStream.close()
                    page = PDPage(PDRectangle.A4)
                    pdfDoc.addPage(page)
                    contentStream = PDPageContentStream(pdfDoc, page)
                    yPos = PDRectangle.A4.height - MARGIN
                }

                val text = paragraph.text.trim()
                if (text.isEmpty()) {
                    yPos -= lineHeight * 0.5f
                    continue
                }

                // Bullet/numbered list prefix
                val prefix = when {
                    paragraph.numFmt != null -> "• "
                    else -> ""
                }

                val fullText = prefix + text
                val lines = wrapText(fullText, font, fontSize, usableWidth)

                for (line in lines) {
                    if (yPos - lineHeight < MARGIN) {
                        contentStream.close()
                        page = PDPage(PDRectangle.A4)
                        pdfDoc.addPage(page)
                        contentStream = PDPageContentStream(pdfDoc, page)
                        yPos = PDRectangle.A4.height - MARGIN
                    }

                    contentStream.beginText()
                    contentStream.setFont(font, fontSize)
                    contentStream.newLineAtOffset(MARGIN, yPos)
                    contentStream.showText(line)
                    contentStream.endText()
                    yPos -= lineHeight
                }

                yPos -= lineHeight * 0.3f
            }

            contentStream.close()

            val baos = ByteArrayOutputStream()
            pdfDoc.save(baos)
            FileHelper.saveToCache(context, outputFileName, baos.toByteArray())
        } finally {
            pdfDoc.close()
            xwpfDoc.close()
            inputStream.close()
        }
    }

    private fun getFontSize(paragraph: XWPFParagraph): Float {
        val styleName = paragraph.style?.lowercase() ?: ""
        return when {
            styleName.contains("heading1") || styleName == "1" -> FONT_SIZE_H1
            styleName.contains("heading2") || styleName == "2" -> FONT_SIZE_H2
            styleName.contains("heading3") || styleName == "3" -> FONT_SIZE_H3
            else -> FONT_SIZE_NORMAL
        }
    }

    private fun getFont(paragraph: XWPFParagraph): PDType1Font {
        val isBold = paragraph.runs.any { it.isBold }
        val isItalic = paragraph.runs.any { it.isItalic }
        return when {
            isBold && isItalic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
            isBold -> PDType1Font.HELVETICA_BOLD
            isItalic -> PDType1Font.HELVETICA_OBLIQUE
            else -> PDType1Font.HELVETICA
        }
    }

    private fun wrapText(text: String, font: PDType1Font, fontSize: Float, maxWidth: Float): List<String> {
        val words = text.split(" ")
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
