package com.morphdrop.app.data.pdf

import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlin.math.max

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

/** A run of characters on one baseline, before gap-based line splitting. */
internal data class PdfLineSegment(
    val text: String,
    val fontSize: Float,
    val bold: Boolean,
    val left: Float,
    val right: Float,
    val top: Float,
    val height: Float,
    val spaceBefore: Boolean
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

    private val segments = mutableListOf<PdfLineSegment>()
    private var pendingSpace = false

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
        // A typeset row can arrive as one chunk spanning two columns that
        // share a baseline. When characters map 1:1 to positions, split the
        // chunk wherever a wide horizontal gap appears.
        if (textPositions.size == text.length && text.length > 1) {
            var start = 0
            for (i in 1 until textPositions.size) {
                val previous = textPositions[i - 1]
                val current = textPositions[i]
                val gap = current.xDirAdj - (previous.xDirAdj + previous.widthDirAdj)
                if (gap > segmentGapThreshold(previous.fontSizeInPt, current.fontSizeInPt)) {
                    addSegment(text.substring(start, i), textPositions.subList(start, i))
                    start = i
                }
            }
            addSegment(text.substring(start), textPositions.subList(start, textPositions.size))
        } else {
            addSegment(text, textPositions)
        }
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

    private fun addSegment(text: String, positions: List<TextPosition>) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            pendingSpace = true
            return
        }
        val top = positions.minOf { it.yDirAdj }
        segments.add(
            PdfLineSegment(
                text = trimmed,
                fontSize = positions.maxOf { it.fontSizeInPt },
                bold = positions.any { it.font?.name?.contains("Bold", ignoreCase = true) == true },
                left = positions.minOf { it.xDirAdj },
                right = positions.maxOf { it.xDirAdj + it.widthDirAdj },
                top = top,
                height = positions.maxOf { it.yDirAdj + it.heightDir } - top,
                spaceBefore = pendingSpace && segments.isNotEmpty()
            )
        )
        pendingSpace = false
    }

    private fun flushLine() {
        if (segments.isEmpty()) return
        lines.addAll(groupSegmentsIntoLines(segments, pageIndex, pageWidth))
        segments.clear()
        pendingSpace = false
    }
}

/** Horizontal gap wide enough to separate two runs of text (e.g. columns). */
internal fun segmentGapThreshold(sizeA: Float, sizeB: Float): Float =
    max(14f, 1.2f * max(sizeA, sizeB))

/**
 * Splits one physical baseline row into logical lines. Segments separated
 * by a wide horizontal gap (a column gutter, or a jump to a far-apart text
 * block) become separate lines; closely spaced segments merge back into
 * one line, honouring the word separators between them.
 */
internal fun groupSegmentsIntoLines(
    segments: List<PdfLineSegment>,
    pageIndex: Int,
    pageWidth: Float
): List<PdfTextLine> {
    if (segments.isEmpty()) return emptyList()
    val sorted = segments.sortedBy { it.left }
    val result = mutableListOf<PdfTextLine>()
    var group = mutableListOf<PdfLineSegment>()
    var groupRight = Float.NEGATIVE_INFINITY

    fun emitGroup() {
        if (group.isEmpty()) return
        result.add(mergeSegments(group, pageIndex, pageWidth))
        group = mutableListOf()
        groupRight = Float.NEGATIVE_INFINITY
    }

    for (segment in sorted) {
        val groupSize = group.maxOfOrNull { it.fontSize } ?: segment.fontSize
        if (group.isNotEmpty() &&
            segment.left - groupRight > segmentGapThreshold(groupSize, segment.fontSize)
        ) {
            emitGroup()
        }
        group.add(segment)
        groupRight = max(groupRight, segment.right)
    }
    emitGroup()
    return result
}

private fun mergeSegments(
    group: List<PdfLineSegment>,
    pageIndex: Int,
    pageWidth: Float
): PdfTextLine {
    val text = StringBuilder()
    var charCount = 0
    var weightedSize = 0f
    var boldChars = 0
    for (segment in group) {
        if (text.isNotEmpty() && segment.spaceBefore) text.append(' ')
        text.append(segment.text)
        charCount += segment.text.length
        weightedSize += segment.fontSize * segment.text.length
        if (segment.bold) boldChars += segment.text.length
    }
    val top = group.minOf { it.top }
    val bottom = group.maxOf { it.top + it.height }
    return PdfTextLine(
        text = text.toString().replace(Regex("\\s+"), " ").trim(),
        fontSize = if (charCount > 0) weightedSize / charCount else 0f,
        bold = charCount > 0 && boldChars * 2 >= charCount,
        left = group.minOf { it.left },
        right = group.maxOf { it.right },
        top = top,
        height = bottom - top,
        pageIndex = pageIndex,
        pageWidth = pageWidth
    )
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
