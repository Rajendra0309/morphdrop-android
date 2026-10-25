package com.morphdrop.app.data.spreadsheet

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Shared spreadsheet table engine: reads .xlsx files (native OOXML zip
 * parsing, no external dependency) and delimited text (CSV/TSV) into a
 * plain row/column table, and writes tables back out as CSV text.
 *
 * Used by Excel to PDF, XLSX to CSV, CSV to PDF and CSV to XLSX so every
 * spreadsheet tool reads and writes files exactly the same way.
 */
object SpreadsheetTables {

    /**
     * Parses the first worksheet of an .xlsx file. Returns the table and,
     * when parsing failed, the error that caused it (the table is empty
     * in that case).
     */
    fun parseXlsx(bytes: ByteArray): Pair<List<List<String>>, Throwable?> {
        val sharedStrings = mutableListOf<String>()
        val rowsMap = mutableMapOf<Int, MutableMap<Int, String>>()
        var parseError: Throwable? = null

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
                // A shared string (<si>) may contain several <r> runs, each with its
                // own <t>. Accumulate ALL runs into one entry and add it once on </si>;
                // adding on every </t> corrupted multi-run strings.
                var insideSi = false
                var insideRPh = false
                var currentItem = StringBuilder()
                var insideT = false
                var currentRun = StringBuilder()

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val tag = parser.name?.lowercase() ?: ""
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when {
                                tag == "si" || tag.endsWith(":si") -> {
                                    insideSi = true
                                    insideRPh = false
                                    currentItem = StringBuilder()
                                }
                                tag == "rph" || tag.endsWith(":rph") -> {
                                    insideRPh = true
                                }
                                (tag == "t" || tag.endsWith(":t")) && insideSi && !insideRPh -> {
                                    insideT = true
                                    currentRun = StringBuilder()
                                }
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (insideT) {
                                currentRun.append(parser.text)
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            when {
                                tag == "rph" || tag.endsWith(":rph") -> {
                                    insideRPh = false
                                }
                                (tag == "t" || tag.endsWith(":t")) && insideT -> {
                                    insideT = false
                                    currentItem.append(currentRun)
                                }
                                (tag == "si" || tag.endsWith(":si")) && insideSi -> {
                                    insideSi = false
                                    insideRPh = false
                                    sharedStrings.add(currentItem.toString())
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }

            // Numeric-aware sheet order: "sheet10" must come after "sheet2".
            val firstSheetBytes = sheetBytesMap.entries
                .sortedWith(compareBy({ sheetOrderKey(it.key) }, { it.key }))
                .firstOrNull()?.value
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
                                        } else {
                                            rawVal
                                        }
                                    } else {
                                        rawVal
                                    }
                                    if (cellVal.isNotEmpty()) {
                                        val colIdx = columnIndexFromRef(currentCellRef)
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
        } catch (t: Throwable) {
            parseError = t
        }

        if (rowsMap.isEmpty()) return emptyList<List<String>>() to parseError

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

        return table to parseError
    }

    /**
     * Parses delimited text honoring RFC-4180-style quoting: a field wrapped in
     * double quotes may contain the delimiter, line breaks, and "" escapes.
     */
    fun parseDelimited(content: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < content.length) {
            val c = content[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < content.length && content[i + 1] == '"') {
                        field.append('"')
                        i++ // consume the escaped quote
                    } else if (inQuotes) {
                        inQuotes = false
                    } else if (field.isBlank()) {
                        inQuotes = true
                    } else {
                        field.append(c) // literal quote inside an unquoted field
                    }
                }
                c == delimiter && !inQuotes -> {
                    currentRow.add(field.toString().trim())
                    field.clear()
                }
                (c == '\n' || c == '\r') && !inQuotes -> {
                    if (c == '\r' && i + 1 < content.length && content[i + 1] == '\n') i++
                    currentRow.add(field.toString().trim())
                    field.clear()
                    rows.add(currentRow.toList())
                    currentRow.clear()
                }
                else -> field.append(c)
            }
            i++
        }
        // Trailing field/row without a line terminator (but not a phantom row for
        // content that simply ends with a newline).
        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(field.toString().trim())
            rows.add(currentRow.toList())
        }
        return rows
    }

    /** Serializes a table as CSV text (RFC 4180 quoting, CRLF line endings). */
    fun toCsv(table: List<List<String>>, delimiter: Char = ','): String {
        val out = StringBuilder()
        for (row in table) {
            row.forEachIndexed { index, cell ->
                if (index > 0) out.append(delimiter)
                out.append(escapeCsvField(cell, delimiter))
            }
            out.append("\r\n")
        }
        return out.toString()
    }

    private fun escapeCsvField(cell: String, delimiter: Char): String {
        val needsQuotes = cell.contains(delimiter) || cell.contains('"') ||
            cell.contains('\n') || cell.contains('\r')
        return if (needsQuotes) {
            "\"" + cell.replace("\"", "\"\"") + "\""
        } else {
            cell
        }
    }

    /** Numeric-aware sheet key: "xl/worksheets/sheet10.xml" sorts after "sheet2.xml". */
    private fun sheetOrderKey(name: String): Int {
        return Regex("""sheet(\d+)""").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun columnIndexFromRef(ref: String): Int {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        var col = 0
        for (char in letters) {
            col = col * 26 + (char - 'A' + 1)
        }
        return if (col > 0) col - 1 else 0
    }
}
