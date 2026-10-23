package com.morphdrop.app

import com.morphdrop.app.data.pdf.PdfTableBuilder
import com.morphdrop.app.data.pdf.PdfTextLine
import org.junit.Assert.assertEquals
import org.junit.Test

/** Table reconstruction from positioned PDF lines: rows, columns, headers. */
class PdfTableBuilderTest {

    private fun line(text: String, left: Float, right: Float, top: Float, page: Int = 0) =
        PdfTextLine(
            text = text, fontSize = 10f, bold = false,
            left = left, right = right, top = top, height = 12f,
            pageIndex = page, pageWidth = 595f
        )

    @Test
    fun `three column table rebuilds rows and cells`() {
        val page = listOf(
            line("Name", 40f, 90f, 40f), line("Price", 200f, 240f, 40f), line("Qty", 320f, 350f, 40f),
            line("Widget", 40f, 95f, 60f), line("12.5", 205f, 240f, 60f), line("3", 335f, 350f, 60f),
            line("Gadget", 40f, 98f, 80f), line("3.25", 200f, 240f, 80f), line("10", 328f, 350f, 80f)
        )
        val table = PdfTableBuilder.build(listOf(page))
        assertEquals(3, table.size)
        assertEquals(listOf("Name", "Price", "Qty"), table[0])
        assertEquals(listOf("Widget", "12.5", "3"), table[1])
        assertEquals(listOf("Gadget", "3.25", "10"), table[2])
    }

    @Test
    fun `header repeated on page two is kept once`() {
        val page1 = listOf(
            line("Item", 40f, 80f, 40f, page = 0), line("Cost", 200f, 240f, 40f, page = 0),
            line("Pen", 40f, 75f, 60f, page = 0), line("1.5", 205f, 240f, 60f, page = 0)
        )
        val page2 = listOf(
            line("Item", 40f, 80f, 40f, page = 1), line("Cost", 200f, 240f, 40f, page = 1),
            line("Book", 40f, 85f, 60f, page = 1), line("4", 210f, 240f, 60f, page = 1)
        )
        val table = PdfTableBuilder.build(listOf(page1, page2))
        assertEquals(3, table.size)
        assertEquals(listOf("Pen", "1.5"), table[1])
        assertEquals(listOf("Book", "4"), table[2])
    }

    @Test
    fun `plain text page becomes a single column`() {
        val page = listOf(
            line("Hello world", 40f, 140f, 40f),
            line("Second line", 40f, 130f, 60f)
        )
        val table = PdfTableBuilder.build(listOf(page))
        assertEquals(2, table.size)
        assertEquals(listOf("Hello world"), table[0])
    }

    @Test
    fun `empty input gives empty table`() {
        assertEquals(0, PdfTableBuilder.build(emptyList()).size)
        assertEquals(0, PdfTableBuilder.build(listOf(emptyList())).size)
    }
}
