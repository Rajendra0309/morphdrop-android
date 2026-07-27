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
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
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
        outputFileName: String = "excel_to_pdf_${System.currentTimeMillis()}.pdf"
    ): Uri = withContext(Dispatchers.IO) {
        val sanitizedFileName = if (outputFileName.endsWith(".pdf", ignoreCase = true)) {
            outputFileName
        } else {
            "$outputFileName.pdf"
        }

        val inputStream = FileHelper.readFileFromUri(context, xlsxUri)
        val bytes = inputStream.readBytes()
        try { inputStream.close() } catch (_: Exception) {}

        val fileName = FileHelper.getFileName(context, xlsxUri).lowercase()
        val isCsv = fileName.endsWith(".csv")

        if (isCsv) {
            return@withContext convertCsvToPdf(bytes, sanitizedFileName)
        }

        // Try POI first
        val workbook: Workbook? = try {
            WorkbookFactory.create(ByteArrayInputStream(bytes))
        } catch (_: Throwable) {
            null
        }

        if (workbook != null) {
            try {
                return@withContext convertPoiWorkbookToPdf(workbook, sanitizedFileName)
            } catch (_: Throwable) {
                // Fall back to native XML/ZIP parser
            } finally {
                try { workbook.close() } catch (_: Throwable) {}
            }
        }

        // Try native OOXML ZIP parser for .xlsx
        val table = parseXlsxFromZip(bytes)
        if (table.isNotEmpty()) {
            return@withContext renderTableToPdf(table, "Spreadsheet Content", sanitizedFileName)
        }

        // Fall back to CSV/text parsing
        return@withContext convertCsvToPdf(bytes, sanitizedFileName)
    }

    private suspend fun convertPoiWorkbookToPdf(workbook: Workbook, outputFileName: String): Uri {
        val pdfDocument = PdfDocument()
        val formatter = DataFormatter()

        try {
            var pageCounter = 1
            for (sheetIndex in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(sheetIndex) ?: continue
                if (sheet.physicalNumberOfRows == 0) continue

                val sheetTable = mutableListOf<List<String>>()
                var maxColIndex = 0
                for (row in sheet) {
                    if (row.lastCellNum > maxColIndex) {
                        maxColIndex = row.lastCellNum.toInt()
                    }
                }
                if (maxColIndex == 0) continue

                for (row in sheet) {
                    val rowCells = mutableListOf<String>()
                    for (colIdx in 0 until maxColIndex) {
                        val cell = row.getCell(colIdx)
                        val text = if (cell != null) {
                            try { formatter.formatCellValue(cell) } catch (_: Exception) { "" }
                        } else ""
                        rowCells.add(text)
                    }
                    sheetTable.add(rowCells)
                }

                if (sheetTable.isNotEmpty()) {
                    pageCounter = renderTableToDoc(pdfDocument, sheetTable, "Sheet: ${sheet.sheetName}", pageCounter)
                }
            }

            if (pdfDocument.pages.isEmpty()) {
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                pdfDocument.finishPage(page)
            }

            val baos = ByteArrayOutputStream()
            pdfDocument.writeTo(baos)
            return FileHelper.saveToFile(context, settingsRepository, outputFileName, baos.toByteArray())
        } finally {
            try { pdfDocument.close() } catch (_: Throwable) {}
        }
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

    private suspend fun convertCsvToPdf(bytes: ByteArray, outputFileName: String): Uri {
        val contentStr = String(bytes, Charsets.UTF_8)
        val lines = contentStr.split(Regex("[\\r\\n]+")).filter { it.isNotBlank() }
        val table = lines.map { line ->
            line.split(",").map { cell -> cell.trim().removeSurrounding("\"") }
        }
        return renderTableToPdf(table, "CSV Spreadsheet", outputFileName)
    }

    private suspend fun renderTableToPdf(
        table: List<List<String>>,
        title: String,
        outputFileName: String
    ): Uri {
        val pdfDocument = PdfDocument()
        try {
            renderTableToDoc(pdfDocument, table, title, 1)
            val baos = ByteArrayOutputStream()
            pdfDocument.writeTo(baos)
            return FileHelper.saveToFile(context, settingsRepository, outputFileName, baos.toByteArray())
        } finally {
            try { pdfDocument.close() } catch (_: Throwable) {}
        }
    }

    private fun renderTableToDoc(
        pdfDocument: PdfDocument,
        table: List<List<String>>,
        title: String,
        startPageNum: Int
    ): Int {
        if (table.isEmpty()) return startPageNum

        val usableWidth = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT
        val maxBottom = PAGE_HEIGHT - MARGIN_BOTTOM
        val totalCols = table.maxOfOrNull { it.size } ?: 1

        // Calculate max character lengths per column
        val colLengths = IntArray(totalCols) { 1 }
        for (row in table) {
            for (c in 0 until min(row.size, totalCols)) {
                val len = row[c].trim().length
                if (len > colLengths[c]) colLengths[c] = len
            }
        }

        // Horizontal pagination: split into column groups if wide (max 8 columns per part)
        val maxColsPerPage = 8
        val colGroups = mutableListOf<IntRange>()
        var startCol = 0
        while (startCol < totalCols) {
            val endCol = min(startCol + maxColsPerPage, totalCols)
            colGroups.add(startCol until endCol)
            startCol = endCol
        }

        var currentPageNumber = startPageNum

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D0D7DE")
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
        }

        val headerBgPaint = Paint().apply {
            color = Color.parseColor("#F6F8FA")
            style = Paint.Style.FILL
        }

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1F2328")
            textSize = spToPx(13f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        for ((groupIndex, colRange) in colGroups.withIndex()) {
            val groupColCount = colRange.count()
            val totalGroupUnits = colRange.sumOf { colLengths[it].coerceIn(5, 40) }
            val colWidths = FloatArray(groupColCount)
            for ((idx, colIdx) in colRange.withIndex()) {
                val units = colLengths[colIdx].coerceIn(5, 40)
                colWidths[idx] = (units.toFloat() / totalGroupUnits.toFloat()) * usableWidth
            }

            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas
            var yPos = MARGIN_TOP

            // Title line
            val headerTitle = if (colGroups.size > 1) "$title (Part ${groupIndex + 1} of ${colGroups.size})" else title
            canvas.drawText(headerTitle, MARGIN_LEFT, yPos + spToPx(11f), titlePaint)
            yPos += 24f

            for ((rIndex, row) in table.withIndex()) {
                val isHeaderRow = rIndex == 0
                val layoutsForCell = mutableListOf<StaticLayout>()
                var maxRowHeight = 22f

                for ((idx, colIdx) in colRange.withIndex()) {
                    val cellText = if (colIdx < row.size) row[colIdx].trim() else ""
                    val width = colWidths[idx]

                    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#1F2328")
                        textSize = spToPx(9.5f)
                        typeface = if (isHeaderRow) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
                    }

                    val layout = createStaticLayout(cellText, paint, (width - 8).toInt().coerceAtLeast(1))
                    layoutsForCell.add(layout)

                    val cellHeight = layout.height + 8f
                    if (cellHeight > maxRowHeight) {
                        maxRowHeight = cellHeight
                    }
                }

                // Check vertical page overflow
                if (yPos + maxRowHeight > maxBottom) {
                    pdfDocument.finishPage(page)
                    currentPageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    yPos = MARGIN_TOP
                }

                var xPos = MARGIN_LEFT
                for ((idx, layout) in layoutsForCell.withIndex()) {
                    val width = colWidths[idx]
                    val cellRect = RectF(xPos, yPos, xPos + width, yPos + maxRowHeight)

                    if (isHeaderRow) {
                        canvas.drawRect(cellRect, headerBgPaint)
                    }

                    canvas.drawRect(cellRect, borderPaint)

                    canvas.save()
                    canvas.translate(xPos + 4f, yPos + 4f)
                    layout.draw(canvas)
                    canvas.restore()

                    xPos += width
                }

                yPos += maxRowHeight
            }

            pdfDocument.finishPage(page)
            currentPageNumber++
        }

        return currentPageNumber
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
        return sp * context.resources.displayMetrics.scaledDensity
    }
}
