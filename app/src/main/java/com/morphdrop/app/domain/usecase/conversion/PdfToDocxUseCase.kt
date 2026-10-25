package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.net.Uri
import com.morphdrop.app.data.docx.DocxBlock
import com.morphdrop.app.data.docx.DocxWriter
import com.morphdrop.app.data.pdf.PdfLineStripper
import com.morphdrop.app.data.pdf.PdfTextLine
import com.morphdrop.app.data.pdf.readingOrder
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.StringWriter
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Converts a text-based PDF into an editable Word (.docx) document.
 *
 * The PDF text layer is extracted line by line together with font sizes,
 * then rebuilt as document structure: noticeably larger lines become
 * headings, and body lines are merged back into paragraphs using the
 * document's own line spacing. Two-column pages are read column by column.
 * Embedded images are carried over as pictures at the end of their page.
 * The result is a clean, editable document — not a pixel-perfect clone of
 * the PDF layout (text boxes and tables do not survive as layout, only as
 * text). Scanned PDFs have no text layer and fail with
 * [NoTextLayerException] so the UI can suggest OCR instead.
 */
class PdfToDocxUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    class NoTextLayerException :
        Exception("This PDF has no readable text — it looks like a scanned document. Use Extract Text (OCR) for scanned files.")

    class PasswordProtectedException :
        Exception("This PDF is password-protected. Unlock it first, then try again.")

    class CorruptFileException :
        Exception("This file appears to be damaged or is not a valid PDF.")

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

    private fun extractBlocks(pdfUri: Uri, onProgress: (Int) -> Unit): List<DocxBlock> {
        val inputStream = FileHelper.readFileFromUri(context, pdfUri)
        val document = try {
            inputStream.use { PDDocument.load(it) }
        } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            throw PasswordProtectedException()
        } catch (e: java.io.IOException) {
            throw CorruptFileException()
        }

        return document.use { doc ->
            if (doc.isEncrypted) throw PasswordProtectedException()

            val stripper = PdfLineStripper()
            stripper.startPage = 1
            stripper.endPage = doc.numberOfPages
            try {
                stripper.writeText(doc, StringWriter())
            } catch (e: java.io.IOException) {
                throw CorruptFileException()
            }
            onProgress(80)

            // One entry per original page, empty ones included, so page
            // boundaries survive even when a page has no text. Embedded
            // images are extracted per page and appended after its text.
            val linesByPage = stripper.lines.groupBy { it.pageIndex }
            val pageImages = mutableListOf<List<DocxBlock.Image>>()
            val pageImageFailures = mutableListOf<Int>()
            val pages = (0 until doc.numberOfPages).map { pageIndex ->
                val (images, failures) = extractPageImages(doc.getPage(pageIndex))
                pageImages.add(images)
                pageImageFailures.add(failures)
                val pageLines = linesByPage[pageIndex].orEmpty()
                if (pageLines.isEmpty()) emptyList() else readingOrder(pageLines)
            }
            buildDocxBlocks(pages, pageImages, pageImageFailures)
        }
    }

    /**
     * Decodes the image XObjects of one page into DOCX-ready pictures.
     * Images with transparency stay PNG; opaque images become JPEG so
     * photo-heavy PDFs do not balloon the document. Undecodable images
     * (exotic PDF filters) are counted so the output can note the loss.
     */
    private fun extractPageImages(
        page: com.tom_roush.pdfbox.pdmodel.PDPage
    ): Pair<List<DocxBlock.Image>, Int> {
        val images = mutableListOf<DocxBlock.Image>()
        var failures = 0
        val resources = page.resources ?: return images to failures
        val names = try {
            resources.xObjectNames
        } catch (e: Exception) {
            return images to failures
        }
        for (name in names) {
            try {
                val xObject = resources.getXObject(name)
                if (xObject is com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                    val bitmap = xObject.getImage()
                    if (bitmap == null) {
                        failures++
                        continue
                    }
                    val stream = java.io.ByteArrayOutputStream()
                    val encoded = if (bitmap.hasAlpha()) {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream) to "png"
                    } else {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream) to "jpg"
                    }
                    if (encoded.first) {
                        images.add(
                            DocxBlock.Image(stream.toByteArray(), bitmap.width, bitmap.height, encoded.second)
                        )
                    } else {
                        failures++
                    }
                }
            } catch (e: Exception) {
                failures++
            }
        }
        return images to failures
    }
}

/**
 * Rebuilds document structure from extracted lines. Larger lines become
 * headings; body lines merge into paragraphs. Whether two lines belong to
 * the same paragraph is decided from the document's median line spacing
 * (leading), so 1.5-spaced documents merge their wrapped lines while real
 * paragraph gaps — visibly larger than the normal leading — still split.
 */
internal fun buildDocxBlocks(
    orderedPages: List<List<PdfTextLine>>,
    pageImages: List<List<DocxBlock.Image>> = emptyList(),
    pageImageFailures: List<Int> = emptyList()
): List<DocxBlock> {
    val allLines = orderedPages.flatten()
    if (allLines.isEmpty()) return emptyList()

    // Body size = the font size carrying the most characters overall.
    val bodySize = allLines
        .groupBy { (it.fontSize * 2).roundToInt() }
        .maxByOrNull { entry -> entry.value.sumOf { it.text.length } }
        ?.key?.div(2f)
        ?: allLines.first().fontSize

    val leadings = mutableListOf<Float>()
    for (page in orderedPages) {
        for (i in 1 until page.size) {
            val diff = page[i].top - page[i - 1].top
            if (diff > 1f) leadings.add(diff)
        }
    }
    val medianLeading = if (leadings.isEmpty()) {
        0f
    } else {
        // Lower median: a few genuine paragraph gaps must not inflate it.
        leadings.sorted()[(leadings.size - 1) / 2]
    }

    fun startsNewParagraph(previous: PdfTextLine, line: PdfTextLine): Boolean {
        if (abs(line.fontSize - previous.fontSize) > bodySize * 0.25f) return true
        val leading = line.top - previous.top
        // A jump back up the page means the reader moved to a new column
        // (or block): never merge across it.
        if (leading < -1f) return true
        if (leading <= 0f) return false
        if (medianLeading > 0f) return leading > medianLeading * 1.45f
        val gap = line.top - (previous.top + previous.height)
        return gap > previous.height * 1.1f
    }

    val blocks = mutableListOf<DocxBlock>()
    val paragraph = StringBuilder()
    var previousLine: PdfTextLine? = null

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks.add(DocxBlock.Paragraph(paragraph.toString()))
            paragraph.clear()
        }
    }

    fun appendImageFailureNote(pageIndex: Int) {
        val failed = pageImageFailures.getOrElse(pageIndex) { 0 }
        if (failed > 0) {
            blocks.add(
                DocxBlock.Paragraph(
                    "[Page ${pageIndex + 1}: $failed image(s) could not be included.]"
                )
            )
        }
    }

    orderedPages.forEachIndexed { pageIndex, lines ->
        if (pageIndex > 0 && (blocks.isNotEmpty() || paragraph.isNotEmpty())) {
            flushParagraph()
            blocks.add(DocxBlock.PageBreak)
            previousLine = null
        }
        val images = pageImages.getOrElse(pageIndex) { emptyList() }
        if (lines.isEmpty()) {
            if (images.isEmpty()) {
                // Keep the page's place instead of silently dropping it.
                blocks.add(
                    DocxBlock.Paragraph(
                        "[Page ${pageIndex + 1}: no readable text — this page may be a scanned image. " +
                            "Use Extract Text (OCR) to read scanned pages.]"
                    )
                )
            } else {
                blocks.addAll(images)
            }
            appendImageFailureNote(pageIndex)
            previousLine = null
            return@forEachIndexed
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
                paragraph.isEmpty() || prev == null -> paragraph.append(line.text)
                startsNewParagraph(prev, line) -> {
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
        // The page's pictures follow its text.
        if (images.isNotEmpty()) {
            flushParagraph()
            blocks.addAll(images)
            previousLine = null
        }
        appendImageFailureNote(pageIndex)
    }
    flushParagraph()
    return blocks
}
