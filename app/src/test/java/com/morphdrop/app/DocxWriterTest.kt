package com.morphdrop.app

import com.morphdrop.app.data.docx.DocxBlock
import com.morphdrop.app.data.docx.DocxWriter
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class DocxWriterTest {

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
    fun `package contains every part Word needs`() {
        val parts = unzipParts(
            DocxWriter.write(listOf(DocxBlock.Paragraph("Hello")))
        )
        assertTrue(parts.containsKey("[Content_Types].xml"))
        assertTrue(parts.containsKey("_rels/.rels"))
        assertTrue(parts.containsKey("word/document.xml"))
        assertTrue(parts.containsKey("word/styles.xml"))
        assertTrue(parts.containsKey("word/_rels/document.xml.rels"))
    }

    @Test
    fun `all xml parts are well formed`() {
        val parts = unzipParts(
            DocxWriter.write(
                listOf(
                    DocxBlock.Heading(1, "Title"),
                    DocxBlock.Paragraph("Body text"),
                    DocxBlock.PageBreak,
                    DocxBlock.Paragraph("Second page")
                )
            )
        )
        val factory = DocumentBuilderFactory.newInstance()
        for ((name, xml) in parts) {
            // Throws on malformed XML, failing the test with the part name.
            try {
                factory.newDocumentBuilder().parse(xml.byteInputStream())
            } catch (e: Exception) {
                throw AssertionError("Malformed XML in $name", e)
            }
        }
    }

    @Test
    fun `headings use heading styles and text is escaped`() {
        val parts = unzipParts(
            DocxWriter.write(
                listOf(
                    DocxBlock.Heading(1, "A & B <Test>"),
                    DocxBlock.Paragraph("5 > 3 & 2 < 4")
                )
            )
        )
        val document = parts.getValue("word/document.xml")
        assertTrue(document.contains("Heading1"))
        assertTrue(document.contains("A &amp; B &lt;Test&gt;"))
        assertTrue(document.contains("5 &gt; 3 &amp; 2 &lt; 4"))
    }
}
