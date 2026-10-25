package com.morphdrop.app.data.pdf

import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** One raw text run from a PDF page: a chunk of text with its extent, in points. */
data class PdfTextRun(
    val text: String,
    val left: Float,
    val right: Float,
    val top: Float,
    val height: Float,
    val pageIndex: Int
)

/**
 * Collects the raw text runs of a document WITHOUT merging them into
 * reading lines. The line-based stripper joins runs separated by less
 * than ~14pt, which fuses the cells of tightly-spaced tables; table
 * reconstruction needs the runs as drawn. A chunk that spans several
 * cells (drawn as one text operation) is split only where the gap
 * between characters is wide relative to the font — cell gutters, not
 * word spaces.
 */
class PdfRunStripper : PDFTextStripper() {

    val runs = mutableListOf<PdfTextRun>()

    private var pageIndex = -1

    init {
        sortByPosition = true
    }

    override fun processPage(page: PDPage) {
        pageIndex++
        super.processPage(page)
    }

    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
        if (textPositions.isEmpty()) return
        if (textPositions.size == text.length && text.length > 1) {
            var start = 0
            for (i in 1 until textPositions.size) {
                val previous = textPositions[i - 1]
                val current = textPositions[i]
                val gap = current.xDirAdj - (previous.xDirAdj + previous.widthDirAdj)
                val threshold = max(4f, 0.55f * max(previous.fontSizeInPt, current.fontSizeInPt))
                if (gap > threshold) {
                    addRun(text.substring(start, i), textPositions.subList(start, i))
                    start = i
                }
            }
            addRun(text.substring(start), textPositions.subList(start, textPositions.size))
        } else {
            addRun(text, textPositions)
        }
    }

    private fun addRun(text: String, positions: List<TextPosition>) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val top = positions.minOf { it.yDirAdj }
        runs += PdfTextRun(
            text = trimmed.replace(Regex("\\s+"), " "),
            left = positions.minOf { it.xDirAdj },
            right = positions.maxOf { it.xDirAdj + it.widthDirAdj },
            top = top,
            height = positions.maxOf { it.yDirAdj + it.heightDir } - top,
            pageIndex = pageIndex
        )
    }
}

/**
 * Rebuilds a spreadsheet table from raw text runs.
 *
 * Columns are discovered by projecting every run onto the x-axis: a
 * vertical strip that no run crosses, on any row, is a gutter. Two
 * neighbouring bands that no single row ever uses together are then
 * merged — that is the signature of one logical column whose header
 * and values are aligned differently (a left-aligned header over
 * right-aligned numbers). Rows are runs sharing a baseline; a header
 * repeated at the top of later pages is kept once.
 *
 * Best effort for table-like, text-based PDFs: merged cells and
 * multi-line wrapped cells come out approximate.
 */
object PdfTableBuilder {

    /** X intervals closer than this are one band (split cell fragments). */
    private const val BAND_MERGE_GAP = 5f

    fun build(pages: List<List<PdfTextRun>>): List<List<String>> {
        val allRuns = pages.flatten()
        if (allRuns.isEmpty()) return emptyList()

        val pageRows: List<List<List<PdfTextRun>>> = pages.map { groupRows(it) }
        // Every row, with its page and position, in reading order.
        val indexedRows = ArrayList<Triple<Int, Int, List<PdfTextRun>>>()
        pageRows.forEachIndexed { pageIndex, rows ->
            rows.forEachIndexed { rowIndex, rowRuns ->
                indexedRows += Triple(pageIndex, rowIndex, rowRuns)
            }
        }

        // Bands and the header come from rows that actually look like
        // table rows (two or more runs). A single-run row is a title or a
        // prose line: letting it vote on bands lets a centred title bridge
        // two columns into one, and treating it as the header stops a
        // repeated page-2 header from being recognised as a repeat.
        val multiRunRows = indexedRows.filter { it.third.size >= 2 }
        val tableLike = multiRunRows.isNotEmpty()
        val bandRows = if (tableLike) multiRunRows else indexedRows
        var columns = discoverBands(bandRows.flatMap { it.third })

        // Merge adjacent bands that never co-occur in a row (alignment
        // splits). Repeat: a merge can make two other bands mergeable.
        var merged = true
        while (merged && columns.size > 1) {
            merged = false
            val touchedByRow: List<Set<Int>> = bandRows.map { (_, _, row) ->
                row.map { bandIndexFor(it, columns) }.toSet()
            }
            for (i in 0 until columns.size - 1) {
                val j = i + 1
                if (touchedByRow.none { row -> i in row && j in row }) {
                    columns = columns.toMutableList().also { list ->
                        val a = list[i]
                        val b = list[j]
                        list[i] = Column(min(a.left, b.left), max(a.right, b.right))
                        list.removeAt(j)
                    }
                    merged = true
                    break
                }
            }
        }

        val table = ArrayList<List<String>>()
        var headerRow: List<String>? = null
        val pagesWithEmittedRows = mutableSetOf<Int>()
        for ((pageIndex, _, rowRuns) in indexedRows) {
            // Titles and prose around a table are not table rows.
            if (tableLike && rowRuns.size < 2) continue
            val cells = arrayOfNulls<String>(columns.size)
            for (run in rowRuns.sortedBy { it.left }) {
                val col = bandIndexFor(run, columns)
                cells[col] = if (cells[col] == null) run.text else cells[col] + " " + run.text
            }
            val row = cells.map { it ?: "" }
            if (row.all { it.isEmpty() }) continue
            val isFirstRowOfPage = pagesWithEmittedRows.add(pageIndex)
            if (headerRow == null) {
                // The header is the first real table row, not blindly the
                // first row of the page (which may be a title).
                headerRow = row
                table += row
            } else if (pageIndex > 0 && isFirstRowOfPage && row == headerRow) {
                // Header repeated by the source document: keep one copy.
            } else {
                table += row
            }
        }
        return table
    }

    /** Groups one page's runs into baseline rows, top to bottom. */
    private fun groupRows(runs: List<PdfTextRun>): List<List<PdfTextRun>> {
        if (runs.isEmpty()) return emptyList()
        val sorted = runs.sortedBy { it.top }
        val heights = sorted.map { it.height }.filter { it > 0 }.sorted()
        val medianHeight = if (heights.isEmpty()) 12f else heights[heights.size / 2]
        val tolerance = max(5f, medianHeight * 0.5f)
        val rows = ArrayList<MutableList<PdfTextRun>>()
        var currentTop = Float.NaN
        for (run in sorted) {
            if (rows.isEmpty() || abs(run.top - currentTop) > tolerance) {
                rows += mutableListOf(run)
                currentTop = run.top
            } else {
                rows.last() += run
            }
        }
        return rows
    }

    private class Column(val left: Float, val right: Float)

    /** Projects all runs onto the x-axis and merges intervals into bands. */
    private fun discoverBands(runs: List<PdfTextRun>): List<Column> {
        // Runs spanning most of the width (titles, footers, merged cells)
        // would bridge every gutter, so bands are first discovered from
        // ordinary cell-sized runs. When those form fewer than two bands
        // the document is prose-like rather than a table with titles, and
        // all runs take part.
        val pageRight = runs.maxOf { it.right }
        val narrowRuns = runs.filter { it.right - it.left <= 0.6f * pageRight }
        val narrowBands = project(narrowRuns)
        return if (narrowBands.size >= 2) narrowBands else project(runs)
    }

    private fun project(runs: List<PdfTextRun>): List<Column> {
        val bands = ArrayList<Column>()
        for (run in runs.sortedBy { it.left }) {
            val last = bands.lastOrNull()
            if (last != null && run.left - last.right < BAND_MERGE_GAP) {
                bands[bands.lastIndex] = Column(last.left, max(last.right, run.right))
            } else {
                bands += Column(run.left, run.right)
            }
        }
        return bands
    }

    private fun bandIndexFor(run: PdfTextRun, columns: List<Column>): Int {
        var best = 0
        var bestOverlap = Float.NEGATIVE_INFINITY
        columns.forEachIndexed { index, column ->
            val overlap = min(run.right, column.right) - max(run.left, column.left)
            if (overlap > bestOverlap) {
                bestOverlap = overlap
                best = index
            }
        }
        return best
    }
}
