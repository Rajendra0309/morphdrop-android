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
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
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
        val table = parseXlsxFromZip(bytes)
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

        throw IllegalArgumentException("Unable to parse spreadsheet. Please ensure the file is a valid, unencrypted .xlsx document.")
    }

    private fun parseXlsxFromZip(bytes: ByteArray): List<List<String>> {
        val sharedStrings = mutableListOf<String>()
        val rowsMap = mutableMapOf<Int, MutableMap<Int, String>>()

        try {
            val zipInputStream = ZipInputStream(ByteArrayInputStream(bytes))
            var entry = zipInputStream.nextEntry

            var sharedStringsBytes: ByteArray? = null
            val sheetBytesMap = mutableMapOf<String, ByteArray>()

            while (entry != null) {
                val name = entry.name
                if (name == "xl/sharedStrings.xml") {
                    sharedStringsBytes = zipInputStream.readBytes()
                } else if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) {
                    sheetBytesMap[name] = zipInputStream.readBytes()
                }
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }

            sharedStringsBytes?.let { sBytes ->
                val factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = true
                val parser = factory.newPullParser()
                parser.setInput(ByteArrayInputStream(sBytes), "UTF-8")

                var eventType = parser.eventType
                var currentText = StringBuilder()
                var insideT = false

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val tag = parser.name?.lowercase() ?: ""
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            if (tag == "t" || tag.endsWith(":t")) {
                                insideT = true
                                currentText = StringBuilder()
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (insideT) {
                                currentText.append(parser.text)
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (tag == "t" || tag.endsWith(":t")) {
                                insideT = false
                                sharedStrings.add(currentText.toString())
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }

            val firstSheetBytes = sheetBytesMap.entries.sortedBy { it.key }.firstOrNull()?.value
            if (firstSheetBytes != null) {
                val factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = true
                val parser = factory.newPullParser()
                parser.setInput(ByteArrayInputStream(firstSheetBytes), "UTF-8")

                var eventType = parser.eventType
                var currentRowIndex = 0
                var currentCellRef = ""
                var currentCellType = ""
                var currentValue = StringBuilder()
                var insideValue = false

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val tag = parser.name?.lowercase() ?: ""
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when {
                                tag == "row" || tag.endsWith(":row") -> {
                                     val rAttr = parser.getAttributeValue(null, "r")
                                    currentRowIndex = rAttr?.toIntOrNull() ?: (currentRowIndex + 1)
                                }
                                tag == "c" || tag.endsWith(":c") -> {
                                    currentCellRef = parser.getAttributeValue(null, "r") ?: ""
                                    currentCellType = parser.getAttributeValue(null, "t") ?: ""
                                    currentValue = StringBuilder()
                                }
                                tag == "v" || tag.endsWith(":v") || tag == "t" || tag.endsWith(":t") -> {
                                    insideValue = true
                                }
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (insideValue) {
                                currentValue.append(parser.text)
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            when {
                                tag == "v" || tag.endsWith(":v") || tag == "t" || tag.endsWith(":t") -> insideValue = false
                                tag == "c" || tag.endsWith(":c") -> {
                                    val rawVal = currentValue.toString().trim()
                                    val cellVal = if (currentCellType == "s") {
                                        val sIndex = rawVal.toIntOrNull()
                                        if (sIndex != null && sIndex in sharedStrings.indices) {
                                            sharedStrings[sIndex]
                                        } else rawVal
                                    } else {
                                        rawVal
                                    }
                                    if (cellVal.isNotEmpty()) {
                                        val colIdx = getColumnIndexFromRef(currentCellRef)
                                        val rowCells = rowsMap.getOrPut(currentRowIndex) { mutableMapOf() }
                                        rowCells[colIdx] = cellVal
                                    }
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (_: Throwable) {}

        if (rowsMap.isEmpty()) return emptyList()

        val sortedRowKeys = rowsMap.keys.sorted()
        val table = mutableListOf<List<String>>()
        val maxCol = rowsMap.values.flatMap { it.keys }.maxOrNull() ?: 0

        for (rKey in sortedRowKeys) {
            val rowMap = rowsMap[rKey] ?: emptyMap()
            val rowList = mutableListOf<String>()
            for (col in 0..maxCol) {
                rowList.add(rowMap[col] ?: "")
            }
            table.add(rowList)
        }

        return table
    }

    private fun getColumnIndexFromRef(ref: String): Int {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        var col = 0
        for (char in letters) {
            col = col * 26 + (char - 'A' + 1)
        }
        return if (col > 0) col - 1 else 0
    }

    private suspend fun convertCsvToPdf(
        bytes: ByteArray,
        outputFileName: String,
        onProgress: (Int) -> Unit,
        delimiter: String = ","
    ): Uri {
        val contentStr = String(bytes, Charsets.UTF_8)
        val lines = contentStr.split(Regex("[\\r\\n]+")).filter { it.isNotBlank() }
        val table = lines.map { line ->
            line.split(delimiter).map { cell -> cell.trim().removeSurrounding("\"") }
        }
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
            return FileHelper.saveToFile(context, settingsRepository, outputFileName, baos.toByteArray())
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
            for (i in 0 until totalCols) {
                finalColWidths[i] *= clampRatio
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
        for (rIndex in 1 until table.size) {
            kotlinx.coroutines.yield() // Support coroutine cancellation
            val row = table[rIndex]
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
