package com.morphdrop.app.data.spreadsheet

import com.morphdrop.app.data.docx.DocxWriter
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal hand-rolled Excel (.xlsx) writer — the spreadsheet counterpart
 * of the DOCX writer. An .xlsx file is a zip of XML parts; this writes
 * one worksheet with inline strings (no shared-strings bookkeeping) and
 * real numeric cells where the text is plainly a number, so Excel,
 * Google Sheets and LibreOffice open the result as a proper spreadsheet.
 */
object XlsxWriter {

    private val NUMERIC = Regex("^-?(0|[1-9]\\d*)(\\.\\d+)?$")

    fun write(table: List<List<String>>, sheetName: String = "Sheet1"): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putPart("[Content_Types].xml", CONTENT_TYPES)
            zip.putPart("_rels/.rels", PACKAGE_RELS)
            zip.putPart("xl/workbook.xml", workbookXml(sheetName))
            zip.putPart("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            zip.putPart("xl/worksheets/sheet1.xml", sheetXml(table))
        }
        return output.toByteArray()
    }

    private fun ZipOutputStream.putPart(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun workbookXml(sheetName: String): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """ +
            """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""" +
            "<sheets><sheet name=\"" + DocxWriter.escapeXml(sheetName) +
            "\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"

    private fun sheetXml(table: List<List<String>>): String {
        val body = StringBuilder()
        table.forEachIndexed { rowIndex, row ->
            val rowNumber = rowIndex + 1
            body.append("<row r=\"").append(rowNumber).append("\">")
            row.forEachIndexed { colIndex, cell ->
                if (cell.isEmpty()) return@forEachIndexed // sparse: skip empty cells
                val ref = columnLetters(colIndex) + rowNumber
                if (NUMERIC.matches(cell)) {
                    body.append("<c r=\"").append(ref).append("\"><v>")
                        .append(cell).append("</v></c>")
                } else {
                    body.append("<c r=\"").append(ref)
                        .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(DocxWriter.escapeXml(cell))
                        .append("</t></is></c>")
                }
            }
            body.append("</row>")
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            "<sheetData>" + body + "</sheetData></worksheet>"
    }

    /** 0 -> A, 25 -> Z, 26 -> AA, 27 -> AB ... */
    internal fun columnLetters(index: Int): String {
        var n = index + 1
        val letters = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            letters.append('A' + rem)
            n = (n - 1) / 26
        }
        return letters.reverse().toString()
    }

    private const val CONTENT_TYPES =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
            """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
            """<Default Extension="xml" ContentType="application/xml"/>""" +
            """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
            """<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" +
            "</Types>"

    private const val PACKAGE_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            "</Relationships>"

    private const val WORKBOOK_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>""" +
            "</Relationships>"
}
