package com.morphdrop.app

import com.morphdrop.app.data.spreadsheet.XlsxWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class XlsxWriterTest {

    private fun unzipParts(bytes: ByteArray): Map<String, String> {
        val parts = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                parts[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        return parts
    }

    @Test
    fun `package contains every part Excel needs`() {
        val parts = unzipParts(XlsxWriter.write(listOf(listOf("A", "B"))))
        assertTrue(parts.containsKey("[Content_Types].xml"))
        assertTrue(parts.containsKey("_rels/.rels"))
        assertTrue(parts.containsKey("xl/workbook.xml"))
        assertTrue(parts.containsKey("xl/_rels/workbook.xml.rels"))
        assertTrue(parts.containsKey("xl/worksheets/sheet1.xml"))
    }

    @Test
    fun `all xml parts are well formed`() {
        val parts = unzipParts(
            XlsxWriter.write(listOf(listOf("A & B", "<tag>"), listOf("1", "2")))
        )
        val factory = DocumentBuilderFactory.newInstance()
        for ((name, xml) in parts) {
            try {
                factory.newDocumentBuilder().parse(xml.byteInputStream())
            } catch (e: Exception) {
                throw AssertionError("Malformed XML in $name", e)
            }
        }
    }

    @Test
    fun `numbers become numeric cells and text is escaped`() {
        val parts = unzipParts(
            XlsxWriter.write(
                listOf(
                    listOf("Item", "Qty"),
                    listOf("Fish & Chips", "42"),
                    listOf("Code", "007")
                )
            )
        )
        val sheet = parts.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("<c r=\"B2\"><v>42</v></c>"))
        assertTrue(sheet.contains("Fish &amp; Chips"))
        // Leading-zero codes must stay text, not collapse to the number 7.
        assertTrue(sheet.contains("t=\"inlineStr\""))
        assertTrue(sheet.contains(">007</t>"))
    }

    @Test
    fun `column letters continue past Z`() {
        assertEquals("A", XlsxWriter.columnLetters(0))
        assertEquals("Z", XlsxWriter.columnLetters(25))
        assertEquals("AA", XlsxWriter.columnLetters(26))
        assertEquals("AB", XlsxWriter.columnLetters(27))
        assertEquals("AZ", XlsxWriter.columnLetters(51))
        assertEquals("BA", XlsxWriter.columnLetters(52))
    }
}
