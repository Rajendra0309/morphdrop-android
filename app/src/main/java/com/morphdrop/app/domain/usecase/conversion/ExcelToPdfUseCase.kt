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
        private const val PAGE_WIDTH = 842 // A4 Landscape Width in points
        private const val PAGE_HEIGHT = 595 // A4 Landscape Height in points
        private const val MARGIN_LEFT = 30f
        private const val MARGIN_RIGHT = 30f
        private const val MARGIN_TOP = 30f
        private const val MARGIN_BOTTOM = 30f
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

        val usableWidth = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT
        val maxBottom = PAGE_HEIGHT - MARGIN_BOTTOM
        val totalCols = table.maxOfOrNull { it.size } ?: 1

        val colLengths = IntArray(totalCols) { 1 }
        for (row in table) {
            for (c in 0 until min(row.size, totalCols)) {
                val len = row[c].trim().length
                if (len > colLengths[c]) colLengths[c] = len
            }
        }

        // Measure text precisely using Paint
        val basePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f // Default point size
            typeface = Typeface.DEFAULT
        }

        val rawColWidths = FloatArray(totalCols)
        var totalTableWidth = 0f
        for (i in 0 until totalCols) {
            // Find max text width in this column
            var maxWidth = 10f
            for (row in table) {
                if (i < row.size) {
                    val w = basePaint.measureText(row[i].trim())
                    if (w > maxWidth) maxWidth = w
                }
            }
            rawColWidths[i] = maxWidth + 12f // Add padding
            totalTableWidth += rawColWidths[i]
        }

        // Scaling logic: Fit all columns to width
        val scaleFactor = if (totalTableWidth > usableWidth) {
            usableWidth / totalTableWidth
        } else 1.0f

        val finalColWidths = FloatArray(totalCols) { rawColWidths[it] * scaleFactor }
        val fontSize = 9f * scaleFactor.coerceAtLeast(0.5f)

        var currentPageNumber = startPageNum

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#999999")
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
        }

        val headerBgPaint = Paint().apply {
            color = Color.parseColor("#EEEEEE")
            style = Paint.Style.FILL
        }

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = spToPx(13f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = fontSize
            typeface = Typeface.DEFAULT
        }

        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        var yPos = MARGIN_TOP

        // Title line removed as requested

        for ((rIndex, row) in table.withIndex()) {
            kotlinx.coroutines.yield() // Support cancellation
            val isHeaderRow = rIndex == 0
            val layoutsForCell = mutableListOf<StaticLayout>()
            var maxRowHeight = 0f

            val currentCellPaint = TextPaint(cellPaint).apply {
                if (isHeaderRow) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            // Create layouts for each cell
            for (cIndex in 0 until totalCols) {
                val text = if (cIndex < row.size) row[cIndex].trim() else ""
                val width = finalColWidths[cIndex]
                val layout = createStaticLayout(text, currentCellPaint, (width - 8f).toInt().coerceAtLeast(1))
                layoutsForCell.add(layout)
                if (layout.height + 8f > maxRowHeight) {
                    maxRowHeight = layout.height + 8f
                }
            }

            // Page overflow
            if (yPos + maxRowHeight > maxBottom) {
                pdfDocument.finishPage(page)
                currentPageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                yPos = MARGIN_TOP
            }

            // Draw cells
            var xPos = MARGIN_LEFT
            for (cIndex in 0 until totalCols) {
                val width = finalColWidths[cIndex]
                val rect = RectF(xPos, yPos, xPos + width, yPos + maxRowHeight)

                if (isHeaderRow) canvas.drawRect(rect, headerBgPaint)
                canvas.drawRect(rect, borderPaint)

                canvas.save()
                canvas.translate(xPos + 4f, yPos + 4f)
                layoutsForCell[cIndex].draw(canvas)
                canvas.restore()

                xPos += width
            }
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
