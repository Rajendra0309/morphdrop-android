package com.morphdrop.app.data.pdf

import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

/** One visual line of text extracted from a PDF page, in reading units (points). */
data class PdfTextLine(
    val text: String,
    val fontSize: Float,
    val bold: Boolean,
    val left: Float,
    val right: Float,
    val top: Float,
    val height: Float,
    val pageIndex: Int,
    val pageWidth: Float
)

/**
 * Records the text of a document line by line (rather than as one flowed
 * string), keeping font size, weight and geometry for each line. Callers use
 * the lines to rebuild paragraphs, detect two-column layouts, and notice
 * pages that carry no text at all.
 */
class PdfLineStripper : PDFTextStripper() {

    val lines = mutableListOf<PdfTextLine>()

    private var pageIndex = -1
    private var pageWidth = 0f

    private val words = mutableListOf<String>()
    private val sizes = mutableListOf<Float>()
    private val bolds = mutableListOf<Boolean>()
    private var pendingSpace = false
    private var lineTop = 0f
    private var lineBottom = 0f
    private var lineLeft = 0f
    private var lineRight = 0f
    private var hasGeometry = false

    init {
        sortByPosition = true
    }

    override fun processPage(page: PDPage) {
        pageIndex++
        pageWidth = page.cropBox?.width ?: 0f
        super.processPage(page)
        flushLine()
    }

    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
        if (textPositions.isEmpty()) return
        val size = textPositions.maxOf { it.fontSizeInPt }
        val bold = textPositions.any { it.font?.name?.contains("Bold", ignoreCase = true) == true }
        if (!hasGeometry) {
            lineTop = textPositions.minOf { it.yDirAdj }
            lineBottom = textPositions.maxOf { it.yDirAdj + it.heightDir }
            lineLeft = textPositions.minOf { it.xDirAdj }
            lineRight = textPositions.maxOf { it.xDirAdj + it.widthDirAdj }
            hasGeometry = true
        } else {
            lineTop = minOf(lineTop, textPositions.minOf { it.yDirAdj })
            lineBottom = maxOf(lineBottom, textPositions.maxOf { it.yDirAdj + it.heightDir })
            lineLeft = minOf(lineLeft, textPositions.minOf { it.xDirAdj })
            lineRight = maxOf(lineRight, textPositions.maxOf { it.xDirAdj + it.widthDirAdj })
        }
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            pendingSpace = true
            return
        }
        if (pendingSpace && words.isNotEmpty()) {
            words[words.lastIndex] = words.last() + " "
        }
        words.add(trimmed)
        sizes.add(size)
        bolds.add(bold)
        pendingSpace = false
    }

    override fun writeWordSeparator() {
        pendingSpace = true
    }

    override fun writeLineSeparator() {
        flushLine()
    }

    override fun writePageEnd() {
        flushLine()
        super.writePageEnd()
    }

    /** Number of pages the stripper has visited so far. */
    val visitedPages: Int get() = pageIndex + 1

    private fun flushLine() {
        if (words.isEmpty()) {
            hasGeometry = false
            return
        }
        val text = words.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        var charCount = 0
        var weightedSize = 0f
        words.forEachIndexed { index, word ->
            charCount += word.length
            weightedSize += sizes[index] * word.length
        }
        val boldChars = words.indices
            .filter { bolds[it] }
            .sumOf { words[it].length }
        lines.add(
            PdfTextLine(
                text = text,
                fontSize = if (charCount > 0) weightedSize / charCount else 0f,
                bold = charCount > 0 && boldChars * 2 >= charCount,
                left = lineLeft,
                right = lineRight,
                top = lineTop,
                height = lineBottom - lineTop,
                pageIndex = pageIndex,
                pageWidth = pageWidth
            )
        )
        words.clear()
        sizes.clear()
        bolds.clear()
        pendingSpace = false
        hasGeometry = false
    }
}

/**
 * Orders one page's lines the way a reader expects. Plain pages read top to
 * bottom. When the page clearly uses two columns (enough substantial lines
 * starting in each half, with overlapping vertical ranges), the left column
 * is read fully before the right one; full-width lines (titles, headings)
 * keep their vertical place between the column sections.
 */
internal fun readingOrder(pageLines: List<PdfTextLine>): List<PdfTextLine> {
    if (pageLines.size < 4) return pageLines.sortedBy { it.top }
    val width = pageLines.first().pageWidth
    if (width <= 0f) return pageLines.sortedBy { it.top }
    val mid = width / 2f
    val gutter = mid - width * 0.03f

    fun isSpanning(line: PdfTextLine) =
        line.left < width * 0.32f && line.right > width * 0.68f

    val substantial = pageLines.filter { it.text.length >= 12 && !isSpanning(it) }
    val leftCount = substantial.count { it.left < gutter }
    val rightCount = substantial.count { it.left >= gutter }
    if (leftCount < 3 || rightCount < 3) return pageLines.sortedBy { it.top }

    val leftLines = substantial.filter { it.left < gutter }
    val rightLines = substantial.filter { it.left >= gutter }
    val leftSpan = leftLines.maxOf { it.top } - leftLines.minOf { it.top }
    val rightSpan = rightLines.maxOf { it.top } - rightLines.minOf { it.top }
    val overlap = minOf(
        leftLines.maxOf { it.top + it.height },
        rightLines.maxOf { it.top + it.height }
    ) - maxOf(leftLines.minOf { it.top }, rightLines.minOf { it.top })
    if (overlap <= 0f || overlap < 0.35f * minOf(leftSpan, rightSpan).coerceAtLeast(1f)) {
        return pageLines.sortedBy { it.top }
    }

    val spanning = pageLines.filter { isSpanning(it) }.sortedBy { it.top }
    val left = pageLines.filter { !isSpanning(it) && it.left < gutter }.sortedBy { it.top }
    val right = pageLines.filter { !isSpanning(it) && it.left >= gutter }.sortedBy { it.top }

    val ordered = mutableListOf<PdfTextLine>()
    var leftIndex = 0
    var rightIndex = 0
    for (spanLine in spanning) {
        while (leftIndex < left.size && left[leftIndex].top < spanLine.top) {
            ordered.add(left[leftIndex]); leftIndex++
        }
        while (rightIndex < right.size && right[rightIndex].top < spanLine.top) {
            ordered.add(right[rightIndex]); rightIndex++
        }
        ordered.add(spanLine)
    }
    while (leftIndex < left.size) { ordered.add(left[leftIndex]); leftIndex++ }
    while (rightIndex < right.size) { ordered.add(right[rightIndex]); rightIndex++ }
    return ordered
}
