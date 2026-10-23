package com.morphdrop.app.data.pdf

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Rebuilds a spreadsheet table from positioned PDF text lines.
 *
 * Rows are lines that share a baseline; columns are discovered from the
 * lines' horizontal extents — a line joins a column when its extent
 * overlaps the column's, which handles both left-aligned text columns
 * and right-aligned number columns. A header row repeated at the top of
 * later pages is kept only once.
 *
 * This is a best-effort reconstruction for table-like, text-based PDFs:
 * merged cells and ruled multi-line layouts come out approximate.
 */
object PdfTableBuilder {

    fun build(pages: List<List<PdfTextLine>>): List<List<String>> {
        if (pages.all { it.isEmpty() }) return emptyList()

        // Discover columns across the whole document so every page's rows
        // line up in the same grid.
        val columns = discoverColumns(pages.flatten())
        if (columns.isEmpty()) return emptyList()

        val table = ArrayList<List<String>>()
        var headerRow: List<String>? = null
        for ((pageIndex, pageLines) in pages.withIndex()) {
            val rows = groupRows(pageLines)
            for ((rowIndex, rowLines) in rows.withIndex()) {
                val cells = arrayOfNulls<String>(columns.size)
                for (line in rowLines) {
                    val col = columnIndexFor(line, columns)
                    cells[col] = if (cells[col] == null) line.text else cells[col] + " " + line.text
                }
                val row = cells.map { it ?: "" }
                if (row.all { it.isEmpty() }) continue
                if (pageIndex == 0 && rowIndex == 0) {
                    headerRow = row
                    table += row
                } else if (pageIndex > 0 && rowIndex == 0 && row == headerRow) {
                    // Header repeated by the source on every page: keep one.
                    continue
                } else {
                    table += row
                }
            }
        }
        return table
    }

    /** Groups a page's lines into baseline rows, top to bottom. */
    private fun groupRows(lines: List<PdfTextLine>): List<List<PdfTextLine>> {
        if (lines.isEmpty()) return emptyList()
        val sorted = lines.sortedBy { it.top }
        val heights = sorted.map { it.height }.filter { it > 0 }.sorted()
        val medianHeight = if (heights.isEmpty()) 12f else heights[heights.size / 2]
        val tolerance = max(5f, medianHeight * 0.5f)
        val rows = ArrayList<MutableList<PdfTextLine>>()
        var currentTop = Float.NaN
        for (line in sorted) {
            if (rows.isEmpty() || abs(line.top - currentTop) > tolerance) {
                rows += mutableListOf(line)
                currentTop = line.top
            } else {
                rows.last() += line
            }
        }
        return rows.map { row -> row.sortedBy { it.left } }
    }

    private class Column(var left: Float, var right: Float)

    private fun discoverColumns(lines: List<PdfTextLine>): List<Column> {
        val columns = ArrayList<Column>()
        for (line in lines.sortedBy { it.left }) {
            val match = columns.firstOrNull { column -> overlaps(line, column) }
            if (match != null) {
                match.left = min(match.left, line.left)
                match.right = max(match.right, line.right)
            } else {
                columns += Column(line.left, line.right)
            }
        }
        return columns.sortedBy { it.left }
    }

    private fun columnIndexFor(line: PdfTextLine, columns: List<Column>): Int {
        var best = 0
        var bestOverlap = -1f
        columns.forEachIndexed { index, column ->
            val overlap = overlapAmount(line.left, line.right, column.left, column.right)
            if (overlap > bestOverlap) {
                bestOverlap = overlap
                best = index
            }
        }
        return best
    }

    private fun overlaps(line: PdfTextLine, column: Column): Boolean {
        val overlap = overlapAmount(line.left, line.right, column.left, column.right)
        if (overlap <= 0f) {
            // Adjacent-but-touching extents with near-identical lefts still
            // belong together (short values in a wide column).
            return abs(line.left - column.left) <= 12f
        }
        val lineWidth = max(1f, line.right - line.left)
        return overlap >= 0.35f * lineWidth
    }

    private fun overlapAmount(aLeft: Float, aRight: Float, bLeft: Float, bRight: Float): Float =
        min(aRight, bRight) - max(aLeft, bLeft)
}
