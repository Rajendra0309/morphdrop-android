package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import com.morphdrop.app.data.spreadsheet.SpreadsheetTables
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class ExcelToPdfUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "ExcelToPdfUseCase"
        private const val PAGE_WIDTH_PORTRAIT = 595f // A4 Portrait Width in points
        private const val PAGE_HEIGHT_PORTRAIT = 842f // A4 Portrait Height in points
        private const val PAGE_WIDTH_LANDSCAPE = 842f // A4 Landscape Width in points
        private const val PAGE_HEIGHT_LANDSCAPE = 595f // A4 Landscape Height in points
        private const val MARGIN_LEFT = 30f
        private const val MARGIN_RIGHT = 30f
        private const val MARGIN_TOP = 30f
        private const val MARGIN_BOTTOM = 30f
        private const val MIN_COLUMN_WIDTH = 48f
        private const val MAX_COLUMN_WIDTH = 200f
        private const val MAX_PAGE_WIDTH = 14400f // PDF standard maximum page dimension (200 inches)
    }

    suspend operator fun invoke(
        xlsxUri: Uri,
        outputFileName: String = "excel_to_pdf_${System.currentTimeMillis()}.pdf",
        onProgress: (Int) -> Unit = {}
    ): Uri = withContext(Dispatchers.IO) {
        val sanitizedFileName = if (outputFileName.endsWith(".pdf", ignoreCase = true)) {
            outputFileName
        } else {
            "$outputFileName.pdf"
        }

        onProgress(10)
        val inputStream = FileHelper.readFileFromUri(context, xlsxUri)
        val bytes = inputStream.readBytes()
        try { inputStream.close() } catch (_: Exception) {}
        onProgress(30)

        val fileName = FileHelper.getFileName(context, xlsxUri).lowercase()
        val isCsv = fileName.endsWith(".csv")
        val isTsv = fileName.endsWith(".tsv")

        if (isCsv) {
            return@withContext convertCsvToPdf(bytes, sanitizedFileName, onProgress, delimiter = ",")
        }

        if (isTsv) {
            return@withContext convertCsvToPdf(bytes, sanitizedFileName, onProgress, delimiter = "\t")
        }

        if (fileName.endsWith(".xls")) {
            throw IllegalArgumentException("Legacy binary Excel (.xls) format is not supported. Please convert or save the spreadsheet as modern .xlsx or .csv format.")
        }

        // Native OOXML ZIP parser for .xlsx (lightweight, zero external dependencies)
        val (table, parseError) = SpreadsheetTables.parseXlsx(bytes)
        if (table.isNotEmpty()) {
            return@withContext renderTableToPdf(table, sanitizedFileName, onProgress)
        }

        // Fall back to delimited text parsing for .tsv or .txt files
        if (fileName.endsWith(".tsv")) {
            return@withContext convertCsvToPdf(bytes, sanitizedFileName, onProgress, delimiter = "\t")
        }
        if (fileName.endsWith(".txt")) {
            val sample = String(bytes.take(2048).toByteArray(), Charsets.UTF_8)
            val delimiter = if (sample.contains("\t")) "\t" else ","
            return@withContext convertCsvToPdf(bytes, sanitizedFileName, onProgress, delimiter = delimiter)
        }

        throw IllegalArgumentException(
            "Unable to parse spreadsheet. Please ensure the file is a valid, unencrypted .xlsx document.",
            parseError
        )
    }

    private suspend fun convertCsvToPdf(
        bytes: ByteArray,
        outputFileName: String,
        onProgress: (Int) -> Unit,
        delimiter: String = ","
    ): Uri {
        // Strip a BOM so it never becomes part of the first cell, then parse with a
        // proper quoted-field parser: handles "Smith, John",42, embedded newlines in
        // quoted fields, and "" escaped quotes. Intentional blank rows are preserved.
        val contentStr = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        val table = SpreadsheetTables.parseDelimited(contentStr, delimiter.firstOrNull() ?: ',')
        return renderTableToPdf(table, outputFileName, onProgress)
    }

    private suspend fun renderTableToPdf(
        table: List<List<String>>,
        outputFileName: String,
        onProgress: (Int) -> Unit
    ): Uri {
        val pdfDocument = PdfDocument()
        try {
            renderTableToDoc(pdfDocument, table, 1)
            val baos = ByteArrayOutputStream()
            pdfDocument.writeTo(baos)
            onProgress(95)
            val uri = FileHelper.saveToFile(context, settingsRepository, outputFileName, baos.toByteArray())
            onProgress(100)
            return uri
        } finally {
            try { pdfDocument.close() } catch (_: Throwable) {}
        }
    }

    private suspend fun renderTableToDoc(
        pdfDocument: PdfDocument,
        table: List<List<String>>,
        startPageNum: Int
    ): Int {
        if (table.isEmpty()) return startPageNum

        val totalCols = table.maxOfOrNull { it.size } ?: 1

        // 3. Auto Orientation Detection (>5 cols = Landscape, <=5 cols = Portrait)
        val isLandscape = totalCols > 5
        val orientation = if (isLandscape) "Landscape" else "Portrait"
        Log.d(TAG, "Sheet has $totalCols columns, using $orientation orientation")

        val basePageWidth = if (isLandscape) PAGE_WIDTH_LANDSCAPE else PAGE_WIDTH_PORTRAIT
        val basePageHeight = if (isLandscape) PAGE_HEIGHT_LANDSCAPE else PAGE_HEIGHT_PORTRAIT
        val maxBottom = basePageHeight - MARGIN_BOTTOM

        // 6. Font Size (Default: 9pt for data, 10pt bold for header, auto-reduce for very wide sheets)
        val dataFontSize = when {
            totalCols >= 20 -> 7f
            totalCols >= 12 -> 8f
            else -> 9f
        }
        val headerFontSize = (dataFontSize + 1f).coerceAtLeast(8f)

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = headerFontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val dataPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = dataFontSize
            typeface = Typeface.DEFAULT
        }

        // 2. Smart Column Width Bounds (min 48f, max 200f, scan first 10 rows)
        val sampleRows = table.take(10)
        val finalColWidths = FloatArray(totalCols)
        var totalTableWidth = 0f

        for (c in 0 until totalCols) {
            var maxContentWidth = 0f
            for ((rIdx, row) in sampleRows.withIndex()) {
                if (c < row.size) {
                    val text = row[c].trim()
                    val paint = if (rIdx == 0) headerPaint else dataPaint
                    val measuredWidth = paint.measureText(text)
                    if (measuredWidth > maxContentWidth) {
                        maxContentWidth = measuredWidth
                    }
                }
            }
            // Add padding (4f left + 4f right + 4f safety buffer = 12f) and enforce bounds
            val colWidth = (maxContentWidth + 12f).coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH)
            finalColWidths[c] = colWidth
            totalTableWidth += colWidth
        }

        // 1. Dynamic Page Width: expand canvas if table exceeds default width (capped at PDF standard limit)
        val desiredPageWidth = max(basePageWidth, totalTableWidth + MARGIN_LEFT + MARGIN_RIGHT)
        val actualPageWidth = desiredPageWidth.coerceAtMost(MAX_PAGE_WIDTH)
        val actualPageHeight = basePageHeight

        if (desiredPageWidth > MAX_PAGE_WIDTH) {
            val usableWidth = actualPageWidth - MARGIN_LEFT - MARGIN_RIGHT
            val clampRatio = usableWidth / totalTableWidth
            val minWidth = if (totalCols * MIN_COLUMN_WIDTH > usableWidth) {
                usableWidth / totalCols
            } else {
                MIN_COLUMN_WIDTH
            }
            for (i in 0 until totalCols) {
                finalColWidths[i] = max(minWidth, finalColWidths[i] * clampRatio)
            }
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CCCCCC")
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
        }

        // 4 & 5. Background styling: #E8EAF6 for header, #FFFFFF / #F9F9FB for zebra striping
        val headerBgPaint = Paint().apply {
            color = Color.parseColor("#E8EAF6")
            style = Paint.Style.FILL
        }

        val evenRowBgPaint = Paint().apply {
            color = Color.parseColor("#FFFFFF")
            style = Paint.Style.FILL
        }

        val oddRowBgPaint = Paint().apply {
            color = Color.parseColor("#F9F9FB")
            style = Paint.Style.FILL
        }

        // Helper to draw a row with backgrounds, borders, and cell text
        fun drawRow(
            canvas: Canvas,
            layouts: List<StaticLayout>,
            rowHeight: Float,
            y: Float,
            isHeader: Boolean,
            dataRowIndex: Int
        ) {
            val bgPaint = if (isHeader) {
                headerBgPaint
            } else {
                if (dataRowIndex % 2 == 0) evenRowBgPaint else oddRowBgPaint
            }

            var xPos = MARGIN_LEFT
            for (cIndex in 0 until totalCols) {
                val width = finalColWidths[cIndex]
                val rect = RectF(xPos, y, xPos + width, y + rowHeight)
                canvas.drawRect(rect, bgPaint)
                canvas.drawRect(rect, borderPaint)

                if (cIndex < layouts.size) {
                    canvas.save()
                    canvas.translate(xPos + 4f, y + 4f)
                    layouts[cIndex].draw(canvas)
                    canvas.restore()
                }
                xPos += width
            }
        }

        // 4. Header Repetition: Precompute header layouts from row 0
        val headerRow = table[0]
        val headerLayouts = mutableListOf<StaticLayout>()
        var headerRowHeight = 0f

        for (cIndex in 0 until totalCols) {
            val text = if (cIndex < headerRow.size) headerRow[cIndex].trim() else ""
            val width = finalColWidths[cIndex]
            val layout = createStaticLayout(text, headerPaint, (width - 8f).toInt().coerceAtLeast(1))
            headerLayouts.add(layout)
            if (layout.height + 8f > headerRowHeight) {
                headerRowHeight = layout.height + 8f
            }
        }

        // Split rows that exceed the printable page height
        val maxPrintableHeight = (maxBottom - MARGIN_TOP - headerRowHeight).coerceAtLeast(50f)
        val processedTable = splitOversizedRows(table, finalColWidths, maxPrintableHeight, dataPaint)

        var currentPageNumber = startPageNum
        var pageInfo = PdfDocument.PageInfo.Builder(
            actualPageWidth.toInt(),
            actualPageHeight.toInt(),
            currentPageNumber
        ).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        var yPos = MARGIN_TOP

        // Draw header row on Page 1
        drawRow(canvas, headerLayouts, headerRowHeight, yPos, isHeader = true, dataRowIndex = 0)
        yPos += headerRowHeight

        // Render Data Rows (rIndex starting at 1)
        for (rIndex in 1 until processedTable.size) {
            kotlinx.coroutines.yield() // Support coroutine cancellation
            val row = processedTable[rIndex]
            val cellLayouts = mutableListOf<StaticLayout>()
            var maxRowHeight = 0f

            for (cIndex in 0 until totalCols) {
                val text = if (cIndex < row.size) row[cIndex].trim() else ""
                val width = finalColWidths[cIndex]
                val layout = createStaticLayout(text, dataPaint, (width - 8f).toInt().coerceAtLeast(1))
                cellLayouts.add(layout)
                if (layout.height + 8f > maxRowHeight) {
                    maxRowHeight = layout.height + 8f
                }
            }

            // Check for page overflow
            if (yPos + maxRowHeight > maxBottom) {
                pdfDocument.finishPage(page)
                currentPageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(
                    actualPageWidth.toInt(),
                    actualPageHeight.toInt(),
                    currentPageNumber
                ).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                yPos = MARGIN_TOP

                // 4. Re-render header row at top of every new page
                drawRow(canvas, headerLayouts, headerRowHeight, yPos, isHeader = true, dataRowIndex = 0)
                yPos += headerRowHeight
            }

            // 5. Zebra Striping for data rows (0-indexed: dataRowIndex 0, 2 -> white, 1, 3 -> light gray)
            val dataRowIndex = rIndex - 1
            drawRow(canvas, cellLayouts, maxRowHeight, yPos, isHeader = false, dataRowIndex = dataRowIndex)
            yPos += maxRowHeight
        }

        pdfDocument.finishPage(page)
        return currentPageNumber + 1
    }

    private fun splitOversizedRows(
        table: List<List<String>>,
        finalColWidths: FloatArray,
        maxAllowedHeight: Float,
        dataPaint: TextPaint
    ): List<List<String>> {
        if (table.size <= 1) return table

        val totalCols = finalColWidths.size
        val result = ArrayList<List<String>>(table.size)
        // Header stays as row 0
        result.add(table[0])

        for (rIndex in 1 until table.size) {
            val originalRow = table[rIndex]
            var currentRow = originalRow

            while (true) {
                var exceeds = false
                val cellLayouts = ArrayList<StaticLayout>(totalCols)
                for (cIndex in 0 until totalCols) {
                    val text = if (cIndex < currentRow.size) currentRow[cIndex].trim() else ""
                    val width = finalColWidths[cIndex]
                    val layout = createStaticLayout(text, dataPaint, (width - 8f).toInt().coerceAtLeast(1))
                    cellLayouts.add(layout)
                    if (layout.height + 8f > maxAllowedHeight) {
                        exceeds = true
                    }
                }

                if (!exceeds) {
                    result.add(currentRow)
                    break
                }

                // Row exceeds maxAllowedHeight: split into part 1 (fits) and part 2 (remainder)
                val part1 = ArrayList<String>(totalCols)
                val part2 = ArrayList<String>(totalCols)
                var anyRemainder = false

                for (cIndex in 0 until totalCols) {
                    val text = if (cIndex < currentRow.size) currentRow[cIndex] else ""
                    val layout = cellLayouts[cIndex]
                    if (layout.height + 8f > maxAllowedHeight && layout.lineCount > 1) {
                        var splitLine = 0
                        while (splitLine < layout.lineCount && layout.getLineBottom(splitLine) + 8f <= maxAllowedHeight) {
                            splitLine++
                        }
                        if (splitLine == 0) splitLine = 1

                        val splitOffset = layout.getLineEnd(splitLine - 1)
                        val head = text.substring(0, splitOffset).trimEnd()
                        val tail = text.substring(splitOffset).trimStart()

                        part1.add(head)
                        part2.add(tail)
                        if (tail.isNotEmpty()) anyRemainder = true
                    } else {
                        part1.add(text)
                        part2.add("")
                    }
                }

                result.add(part1)
                if (!anyRemainder) {
                    break
                }
                currentRow = part2
            }
        }

        return result
    }

    private fun createStaticLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): StaticLayout {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(alignment)
                .setLineSpacing(0f, 1.2f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, width, alignment, 1.2f, 0f, false)
        }
    }

    private fun spToPx(sp: Float): Float {
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP,
            sp,
            context.resources.displayMetrics
        )
    }
}
