package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.net.Uri
import com.morphdrop.app.data.docx.DocxBlock
import com.morphdrop.app.data.docx.DocxWriter
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.StringWriter
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Converts a text-based PDF into an editable Word (.docx) document.
 *
 * The PDF text layer is extracted line by line together with font sizes,
 * then rebuilt as document structure: noticeably larger lines become
 * headings, and body lines are merged back into paragraphs using line
 * spacing. The result is a clean, editable document — not a pixel-perfect
 * clone of the PDF layout (multi-column pages, text boxes and tables do
 * not survive as layout, only as text). Scanned PDFs have no text layer
 * and fail with [NoTextLayerException] so the UI can suggest OCR instead.
 */
class PdfToDocxUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    class NoTextLayerException :
        Exception("This PDF has no readable text — it looks like a scanned document. Use Extract Text (OCR) for scanned files.")

    class PasswordProtectedException :
        Exception("This PDF is password-protected. Unlock it first, then try again.")

    suspend operator fun invoke(
        pdfUri: Uri,
        outputFileName: String = "pdf_to_word_${System.currentTimeMillis()}.docx",
        onProgress: (Int) -> Unit = {}
    ): Uri = withContext(Dispatchers.IO) {
        val sanitizedFileName = if (outputFileName.endsWith(".docx", ignoreCase = true)) {
            outputFileName
        } else {
            "$outputFileName.docx"
        }

        onProgress(10)
        val blocks = extractBlocks(pdfUri, onProgress)
        if (blocks.none { it is DocxBlock.Paragraph || it is DocxBlock.Heading }) {
            throw NoTextLayerException()
        }

        onProgress(90)
        val savedUri = FileHelper.saveToFile(
            context,
            settingsRepository,
            sanitizedFileName,
            DocxWriter.write(blocks)
        )
        onProgress(100)
        savedUri
    }

    private data class Line(
        val text: String,
        val fontSize: Float,
        val bold: Boolean,
        val top: Float,
        val height: Float
    )

    /** Stripper that records one [Line] per visual line, with font metrics. */
    private class LineStripper : PDFTextStripper() {
        val lines = mutableListOf<Line>()
        private val currentText = StringBuilder()
        private var currentPositions = mutableListOf<TextPosition>()

        init {
            sortByPosition = true
        }

        override fun writeString(text: String, textPositions: MutableList<TextPosition>?) {
            currentText.append(text)
            if (textPositions != null) currentPositions.addAll(textPositions)
        }

        override fun writeWordSeparator() {
            currentText.append(' ')
        }

        override fun writeLineSeparator() {
            flushLine()
        }

        override fun writePageEnd() {
            flushLine()
            super.writePageEnd()
        }

        private fun flushLine() {
            val text = currentText.toString().replace(Regex("\\s+"), " ").trim()
            if (text.isNotEmpty() && currentPositions.isNotEmpty()) {
                lines.add(
                    Line(
                        text = text,
                        fontSize = currentPositions.maxOf { it.fontSizeInPt },
                        bold = currentPositions.any { it.font?.name?.contains("bold", ignoreCase = true) == true },
                        top = currentPositions.minOf { it.yDirAdj },
                        height = currentPositions.maxOf { it.heightDir }
                    )
                )
            }
            currentText.clear()
            currentPositions = mutableListOf()
        }
    }

    private suspend fun extractBlocks(pdfUri: Uri, onProgress: (Int) -> Unit): List<DocxBlock> {
        val inputStream = FileHelper.readFileFromUri(context, pdfUri)
        val document = try {
            inputStream.use { PDDocument.load(it) }
        } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            throw PasswordProtectedException()
        }

        return document.use { doc ->
            if (doc.isEncrypted) throw PasswordProtectedException()

            val pageCount = doc.numberOfPages.coerceAtLeast(1)
            val pages = mutableListOf<List<Line>>()
            for (page in 1..doc.numberOfPages) {
                yield() // Support cancellation on long documents
                val stripper = LineStripper()
                stripper.startPage = page
                stripper.endPage = page
                stripper.writeText(doc, StringWriter())
                pages.add(stripper.lines.toList())
                onProgress(10 + ((page.toFloat() / pageCount) * 70).toInt())
            }

            buildBlocks(pages)
        }
    }

    private fun buildBlocks(pages: List<List<Line>>): List<DocxBlock> {
        val allLines = pages.flatten()
        if (allLines.isEmpty()) return emptyList()

        // Body size = the font size carrying the most characters overall.
        val bodySize = allLines
            .groupBy { (it.fontSize * 2).roundToInt() }
            .maxByOrNull { entry -> entry.value.sumOf { it.text.length } }
            ?.key?.div(2f)
            ?: allLines.first().fontSize

        val blocks = mutableListOf<DocxBlock>()
        val paragraph = StringBuilder()
        var previousLine: Line? = null

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks.add(DocxBlock.Paragraph(paragraph.toString()))
                paragraph.clear()
            }
        }

        pages.forEachIndexed { pageIndex, lines ->
            if (pageIndex > 0 && lines.isNotEmpty()) {
                flushParagraph()
                blocks.add(DocxBlock.PageBreak)
                previousLine = null
            }
            for (line in lines) {
                val headingLevel = when {
                    line.fontSize >= bodySize * 1.4f -> 1
                    line.fontSize >= bodySize * 1.18f -> 2
                    line.bold && line.fontSize >= bodySize * 1.1f -> 2
                    else -> 0
                }
                if (headingLevel > 0) {
                    flushParagraph()
                    blocks.add(DocxBlock.Heading(headingLevel, line.text))
                    previousLine = line
                    continue
                }

                val prev = previousLine
                when {
                    paragraph.isEmpty() -> paragraph.append(line.text)
                    prev == null -> paragraph.append(line.text)
                    // A clear vertical gap starts a new paragraph.
                    line.top - (prev.top + prev.height) > prev.height * 1.1f -> {
                        flushParagraph()
                        paragraph.append(line.text)
                    }
                    // Soft-wrapped line ending in a hyphen: rejoin the word.
                    paragraph.endsWith("-") -> {
                        paragraph.deleteCharAt(paragraph.length - 1)
                        paragraph.append(line.text)
                    }
                    else -> paragraph.append(' ').append(line.text)
                }
                previousLine = line
            }
        }
        flushParagraph()
        return blocks
    }
}
