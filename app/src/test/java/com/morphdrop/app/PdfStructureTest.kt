package com.morphdrop.app

import com.morphdrop.app.data.docx.DocxBlock
import com.morphdrop.app.data.pdf.PdfLineSegment
import com.morphdrop.app.data.pdf.PdfTextLine
import com.morphdrop.app.data.pdf.groupSegmentsIntoLines
import com.morphdrop.app.data.pdf.readingOrder
import com.morphdrop.app.domain.usecase.conversion.buildDocxBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfStructureTest {

    private fun line(
        text: String,
        size: Float = 11f,
        top: Float,
        left: Float = 72f,
        right: Float = 500f,
        page: Int = 0,
        pageWidth: Float = 612f
    ) = PdfTextLine(
        text = text,
        fontSize = size,
        bold = false,
        left = left,
        right = right,
        top = top,
        height = size,
        pageIndex = page,
        pageWidth = pageWidth
    )

    @Test
    fun `wrapped lines at 1_5 spacing merge into one paragraph`() {
        val lines = listOf(
            line("The quick brown fox jumps over", top = 100f),
            line("the lazy dog and keeps running", top = 116f),
            line("across the field towards home.", top = 132f)
        )
        val blocks = buildDocxBlocks(listOf(lines))
        assertEquals(1, blocks.size)
        val paragraph = blocks.single() as DocxBlock.Paragraph
        assertEquals(
            "The quick brown fox jumps over the lazy dog and keeps running across the field towards home.",
            paragraph.text
        )
    }

    @Test
    fun `a larger vertical gap starts a new paragraph`() {
        val lines = listOf(
            line("First paragraph line one here", top = 100f),
            line("first paragraph line two here", top = 116f),
            line("Second paragraph starts here now", top = 164f)
        )
        val blocks = buildDocxBlocks(listOf(lines))
        assertEquals(2, blocks.size)
        assertTrue((blocks[0] as DocxBlock.Paragraph).text.startsWith("First paragraph"))
        assertTrue((blocks[1] as DocxBlock.Paragraph).text.startsWith("Second paragraph"))
    }

    @Test
    fun `hyphenated word is rejoined across lines`() {
        val lines = listOf(
            line("This is an exam-", top = 100f),
            line("ple of a split word.", top = 116f)
        )
        val blocks = buildDocxBlocks(listOf(lines))
        assertEquals(1, blocks.size)
        assertEquals("This is an example of a split word.", (blocks.single() as DocxBlock.Paragraph).text)
    }

    @Test
    fun `larger line becomes a heading`() {
        val lines = listOf(
            line("Big Section Title", size = 16f, top = 80f),
            line("Body text follows the title here", top = 116f),
            line("and continues on the next line.", top = 132f)
        )
        val blocks = buildDocxBlocks(listOf(lines))
        assertEquals(2, blocks.size)
        val heading = blocks[0] as DocxBlock.Heading
        assertEquals(1, heading.level)
        assertEquals("Big Section Title", heading.text)
        assertTrue(blocks[1] is DocxBlock.Paragraph)
    }

    @Test
    fun `page break is inserted between pages`() {
        val pageOne = listOf(line("Text on the first page here", top = 100f))
        val pageTwo = listOf(line("Text on the second page", top = 100f, page = 1))
        val blocks = buildDocxBlocks(listOf(pageOne, pageTwo))
        assertEquals(
            listOf("Paragraph", "PageBreak", "Paragraph"),
            blocks.map { it::class.simpleName }
        )
    }

    @Test
    fun `page without text keeps its place with a marker`() {
        val pageOne = listOf(line("Text on the first page here", top = 100f, page = 0))
        val pageThree = listOf(line("Text on the third page here", top = 100f, page = 2))
        val blocks = buildDocxBlocks(listOf(pageOne, emptyList(), pageThree))
        assertEquals(
            listOf("Paragraph", "PageBreak", "Paragraph", "PageBreak", "Paragraph"),
            blocks.map { it::class.simpleName }
        )
        val marker = blocks[2] as DocxBlock.Paragraph
        assertTrue(marker.text.contains("Page 2"))
        assertTrue(marker.text.contains("no readable text"))
    }

    @Test
    fun `document with no text on any page produces no blocks`() {
        val blocks = buildDocxBlocks(listOf(emptyList(), emptyList()))
        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `two column page reads left column then right column`() {
        fun col(text: String, top: Float, left: Float, right: Float) =
            line(text, top = top, left = left, right = right)

        val interleaved = listOf(
            col("Left column first line", 100f, 72f, 280f),
            col("Right column first line", 100f, 330f, 540f),
            col("Left column second line", 116f, 72f, 280f),
            col("Right column second line", 116f, 330f, 540f),
            col("Left column third line", 132f, 72f, 280f),
            col("Right column third line", 132f, 330f, 540f)
        )
        val ordered = readingOrder(interleaved)
        assertEquals(
            listOf(
                "Left column first line",
                "Left column second line",
                "Left column third line",
                "Right column first line",
                "Right column second line",
                "Right column third line"
            ),
            ordered.map { it.text }
        )
    }

    @Test
    fun `single column page keeps top to bottom order`() {
        val lines = listOf(
            line("Third line of the page text", top = 132f),
            line("First line of the page text", top = 100f),
            line("Second line of the page text", top = 116f)
        )
        val ordered = readingOrder(lines)
        assertEquals(
            listOf(
                "First line of the page text",
                "Second line of the page text",
                "Third line of the page text"
            ),
            ordered.map { it.text }
        )
    }

    private fun segment(
        text: String,
        left: Float,
        right: Float,
        spaceBefore: Boolean = false
    ) = PdfLineSegment(
        text = text,
        fontSize = 11f,
        bold = false,
        left = left,
        right = right,
        top = 100f,
        height = 11f,
        spaceBefore = spaceBefore
    )

    @Test
    fun `segments split by a column gutter become separate lines`() {
        // One physical row as PDFBox delivers it for a two-column page:
        // left column text ends at x=280, right column starts at x=330.
        val segments = listOf(
            segment("LEFT COLUMN line one", left = 72f, right = 280f),
            segment("RIGHT COLUMN line one", left = 330f, right = 540f)
        )
        val lines = groupSegmentsIntoLines(segments, pageIndex = 0, pageWidth = 612f)
        assertEquals(2, lines.size)
        assertEquals("LEFT COLUMN line one", lines[0].text)
        assertEquals("RIGHT COLUMN line one", lines[1].text)
    }

    @Test
    fun `closely spaced segments merge into one line`() {
        val segments = listOf(
            segment("Hello", left = 72f, right = 110f),
            segment("world", left = 114f, right = 160f, spaceBefore = true)
        )
        val lines = groupSegmentsIntoLines(segments, pageIndex = 0, pageWidth = 612f)
        assertEquals(1, lines.size)
        assertEquals("Hello world", lines.single().text)
    }

    @Test
    fun `column jump forces a paragraph break in word output`() {
        // Reading order output for a two-column page: the right column's
        // first line sits higher than the left column's last line.
        val lines = listOf(
            line("Left column first row text", top = 100f, left = 72f, right = 280f),
            line("Left column second row text", top = 116f, left = 72f, right = 280f),
            line("Right column first row text", top = 100f, left = 330f, right = 540f),
            line("Right column second row text", top = 116f, left = 330f, right = 540f)
        )
        val blocks = buildDocxBlocks(listOf(lines))
        val paragraphs = blocks.filterIsInstance<DocxBlock.Paragraph>()
        assertEquals(2, paragraphs.size)
        assertTrue(paragraphs[0].text.contains("Left column"))
        assertTrue(paragraphs[1].text.contains("Right column"))
    }
}
