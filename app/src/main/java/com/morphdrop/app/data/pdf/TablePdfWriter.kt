package com.morphdrop.app.data.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.OutputStream
import kotlin.math.max

/**
 * Renders a spreadsheet table to PDF with PDFBox and the standard
 * Helvetica fonts.
 *
 * This replaces the previous android.graphics.pdf.PdfDocument renderer:
 * the platform renderer produced pages that LOOKED right but whose text
 * layer encoded characters like the decimal point as NUL, breaking
 * copy/paste, search and text extraction. Type-1 base fonts carry a
 * proper WinAnsi encoding, so text in these PDFs extracts exactly as
 * written.
 *
 * Layout mirrors the established design: A4 (wider when the table needs
 * it), landscape above five columns, content-sized columns, a shaded
 * header repeated on every page, and zebra striping.
 */
object TablePdfWriter {

    private const val PAGE_WIDTH_PORTRAIT = 595f // A4 portrait, points
    private const val PAGE_HEIGHT_PORTRAIT = 842f
    private const val PAGE_WIDTH_LANDSCAPE = 842f // A4 landscape, points
    private const val PAGE_HEIGHT_LANDSCAPE = 595f
    private const val MARGIN = 30f
    private const val MIN_COLUMN_WIDTH = 48f
    private const val MAX_COLUMN_WIDTH = 200f
    private const val MAX_PAGE_WIDTH = 14400f // PDF standard maximum page dimension
    private const val CELL_PADDING = 4f
    private const val LINE_SPACING = 1.2f

    private val HEADER_BG = 0xE8EAF6
    private val ROW_EVEN_BG = 0xFFFFFF
    private val ROW_ODD_BG = 0xF9F9FB
    private val BORDER = 0xCCCCCC

    fun write(table: List<List<String>>, output: OutputStream) {
        require(table.isNotEmpty()) { "Cannot render an empty table" }
        val totalCols = table.maxOf { it.size }

        val dataFont = PDType1Font.HELVETICA
        val headerFont = PDType1Font.HELVETICA_BOLD
        val dataFontSize = when {
            totalCols >= 20 -> 7f
            totalCols >= 12 -> 8f
            else -> 9f
        }
        val headerFontSize = (dataFontSize + 1f).coerceAtLeast(8f)

        // Content-sized column widths from the first rows, bounded like before.
        val colWidths = FloatArray(totalCols)
        var totalTableWidth = 0f
        val sampleRows = table.take(10)
        for (c in 0 until totalCols) {
            var maxContent = 0f
            for ((rIdx, row) in sampleRows.withIndex()) {
                if (c < row.size) {
                    val font = if (rIdx == 0) headerFont else dataFont
                    val size = if (rIdx == 0) headerFontSize else dataFontSize
                    maxContent = max(maxContent, textWidth(row[c].trim(), font, size))
                }
            }
            val width = (maxContent + 12f).coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH)
            colWidths[c] = width
            totalTableWidth += width
        }

        val isLandscape = totalCols > 5
        val baseWidth = if (isLandscape) PAGE_WIDTH_LANDSCAPE else PAGE_WIDTH_PORTRAIT
        val pageHeight = if (isLandscape) PAGE_HEIGHT_LANDSCAPE else PAGE_HEIGHT_PORTRAIT
        var pageWidth = max(baseWidth, totalTableWidth + MARGIN * 2).coerceAtMost(MAX_PAGE_WIDTH)
        if (pageWidth >= MAX_PAGE_WIDTH && totalTableWidth + MARGIN * 2 > MAX_PAGE_WIDTH) {
            val usable = pageWidth - MARGIN * 2
            val ratio = usable / totalTableWidth
            val floor = if (totalCols * MIN_COLUMN_WIDTH > usable) usable / totalCols else MIN_COLUMN_WIDTH
            for (i in 0 until totalCols) {
                colWidths[i] = max(floor, colWidths[i] * ratio)
            }
            pageWidth = MAX_PAGE_WIDTH
        }

        // Pre-wrap every cell; a row's height follows its tallest cell.
        val headerLines = wrapRow(table[0], totalCols, colWidths, headerFont, headerFontSize)
        val headerHeight = rowHeight(headerLines, headerFontSize)
        val maxRowHeight = (pageHeight - MARGIN * 2 - headerHeight).coerceAtLeast(50f)
        val bodyRows = ArrayList<List<List<String>>>()
        for (r in 1 until table.size) {
            bodyRows += splitRow(
                wrapRow(table[r], totalCols, colWidths, dataFont, dataFontSize),
                dataFontSize,
                maxRowHeight
            )
        }

        PDDocument().use { document ->
            var page: PDPage? = null
            var stream: PDPageContentStream? = null
            var yTop = 0f

            fun startPage() {
                stream?.close()
                val newPage = PDPage(PDRectangle(pageWidth, pageHeight))
                document.addPage(newPage)
                page = newPage
                stream = PDPageContentStream(document, newPage)
                yTop = pageHeight - MARGIN
                drawRow(stream!!, headerLines, colWidths, yTop, headerHeight, headerFont, headerFontSize, HEADER_BG)
                yTop -= headerHeight
            }

            startPage()
            bodyRows.forEachIndexed { index, lines ->
                val height = rowHeight(lines, dataFontSize)
                if (yTop - height < MARGIN) startPage()
                val bg = if (index % 2 == 0) ROW_EVEN_BG else ROW_ODD_BG
                drawRow(stream!!, lines, colWidths, yTop, height, dataFont, dataFontSize, bg)
                yTop -= height
            }
            stream?.close()
            document.save(output)
        }
    }

    private fun drawRow(
        stream: PDPageContentStream,
        cellLines: List<List<String>>,
        colWidths: FloatArray,
        yTop: Float,
        height: Float,
        font: PDType1Font,
        fontSize: Float,
        bgColor: Int
    ) {
        var x = MARGIN
        val yBottom = yTop - height
        for (c in colWidths.indices) {
            val width = colWidths[c]
            // Cell background, then border.
            stream.setNonStrokingColor(red(bgColor), green(bgColor), blue(bgColor))
            stream.addRect(x, yBottom, width, height)
            stream.fill()
            stream.setStrokingColor(red(BORDER), green(BORDER), blue(BORDER))
            stream.setLineWidth(0.5f)
            stream.addRect(x, yBottom, width, height)
            stream.stroke()

            // Cell text, top-aligned with padding.
            stream.setNonStrokingColor(0f, 0f, 0f)
            val lines = cellLines.getOrElse(c) { listOf("") }
            var baseline = yTop - CELL_PADDING - fontSize
            for (line in lines) {
                if (line.isNotEmpty()) {
                    stream.beginText()
                    stream.setFont(font, fontSize)
                    stream.newLineAtOffset(x + CELL_PADDING, baseline)
                    stream.showText(sanitize(line))
                    stream.endText()
                }
                baseline -= fontSize * LINE_SPACING
            }
            x += width
        }
    }

    /** Slices a wrapped row into chunks whose height fits [maxHeight]. */
    private fun splitRow(
        cellLines: List<List<String>>,
        fontSize: Float,
        maxHeight: Float
    ): List<List<List<String>>> {
        val maxLines = cellLines.maxOfOrNull { it.size } ?: 1
        if (maxLines * fontSize * LINE_SPACING + CELL_PADDING * 2 <= maxHeight) {
            return listOf(cellLines)
        }
        val linesPerChunk = max(1, ((maxHeight - CELL_PADDING * 2) / (fontSize * LINE_SPACING)).toInt())
        val chunks = ArrayList<List<List<String>>>()
        var offset = 0
        while (offset < maxLines) {
            chunks += cellLines.map { lines ->
                if (offset < lines.size) {
                    lines.subList(offset, minOf(offset + linesPerChunk, lines.size))
                } else {
                    listOf("")
                }
            }
            offset += linesPerChunk
        }
        return chunks
    }

    private fun wrapRow(
        row: List<String>,
        totalCols: Int,
        colWidths: FloatArray,
        font: PDType1Font,
        fontSize: Float
    ): List<List<String>> =
        (0 until totalCols).map { c ->
            val text = sanitize(if (c < row.size) row[c].trim() else "")
            wrapText(text, font, fontSize, colWidths[c] - CELL_PADDING * 2)
        }

    /** Greedy word wrap; words longer than the line are hard-split. */
    internal fun wrapText(text: String, font: PDType1Font, fontSize: Float, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val lines = ArrayList<String>()
        for (paragraph in text.split('\n')) {
            if (paragraph.isEmpty()) {
                lines += ""
                continue
            }
            var current = StringBuilder()
            for (word in paragraph.split(' ')) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (textWidth(candidate, font, fontSize) <= maxWidth || current.isEmpty()) {
                    if (textWidth(word, font, fontSize) > maxWidth && current.isEmpty()) {
                        // Single over-long word: hard-split by characters.
                        var rest = word
                        while (textWidth(rest, font, fontSize) > maxWidth && rest.length > 1) {
                            var cut = rest.length - 1
                            while (cut > 1 && textWidth(rest.substring(0, cut), font, fontSize) > maxWidth) cut--
                            lines += rest.substring(0, cut)
                            rest = rest.substring(cut)
                        }
                        current = StringBuilder(rest)
                    } else {
                        current = StringBuilder(candidate)
                    }
                } else {
                    lines += current.toString()
                    current = StringBuilder(word)
                }
            }
            lines += current.toString()
        }
        return lines
    }

    private fun rowHeight(cellLines: List<List<String>>, fontSize: Float): Float {
        val maxLines = cellLines.maxOfOrNull { it.size } ?: 1
        return maxLines * fontSize * LINE_SPACING + CELL_PADDING * 2
    }

    private fun textWidth(text: String, font: PDType1Font, fontSize: Float): Float =
        // A multi-line cell measures as its widest line.
        text.split('\n').maxOf { line -> font.getStringWidth(sanitize(line)) / 1000f * fontSize }

    /**
     * WinAnsi (CP1252) is the encoding of the standard fonts: replace the
     * few characters it cannot represent instead of letting them degrade.
     * Applied identically at measure and draw time so widths stay true.
     */
    internal fun sanitize(text: String): String {
        val out = StringBuilder(text.length)
        for (ch in text) {
            out.append(
                when {
                    ch.code in 32..126 || ch.code in 160..255 -> ch
                    ch == '‘' || ch == '’' -> '\''
                    ch == '“' || ch == '”' -> '"'
                    ch == '–' || ch == '—' -> '-'
                    ch == '…' -> '.'
                    ch == '\n' -> '\n' // wrapText splits lines on it
                    ch == '\t' -> ' '
                    ch.code < 32 -> ' '
                    else -> '?'
                }
            )
        }
        return out.toString()
    }

    private fun red(color: Int) = ((color shr 16) and 0xFF) / 255f
    private fun green(color: Int) = ((color shr 8) and 0xFF) / 255f
    private fun blue(color: Int) = (color and 0xFF) / 255f
}
