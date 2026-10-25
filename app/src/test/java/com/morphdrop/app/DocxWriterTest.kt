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

    @Test
    fun `images are written as media parts with relationships`() {
        val pngBytes = byteArrayOf(1, 2, 3, 4, 5)
        val bytes = DocxWriter.write(
            listOf(
                DocxBlock.Paragraph("Before"),
                DocxBlock.Image(pngBytes, widthPx = 200, heightPx = 100, extension = "png"),
                DocxBlock.Paragraph("After")
            )
        )
        // Raw parts (media bytes are not valid UTF-8 text in general).
        val rawParts = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                rawParts[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        assertTrue(rawParts.containsKey("word/media/image1.png"))
        assertTrue(pngBytes.contentEquals(rawParts.getValue("word/media/image1.png")))

        val document = rawParts.getValue("word/document.xml").toString(Charsets.UTF_8)
        assertTrue(document.contains("<w:drawing>"))
        assertTrue(document.contains("r:embed=\"rId2\""))

        val rels = rawParts.getValue("word/_rels/document.xml.rels").toString(Charsets.UTF_8)
        assertTrue(rels.contains("relationships/image"))
        assertTrue(rels.contains("media/image1.png"))

        val contentTypes = rawParts.getValue("[Content_Types].xml").toString(Charsets.UTF_8)
        assertTrue(contentTypes.contains("image/png"))

        // The document XML must stay well formed with an image inside.
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(document.byteInputStream())
    }
}
