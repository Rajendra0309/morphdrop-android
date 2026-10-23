package com.morphdrop.app

import com.morphdrop.app.data.pdf.PdfTableBuilder
import com.morphdrop.app.data.pdf.PdfTextRun
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Table reconstruction from raw PDF text runs. Fixtures mirror the two
 * layouts that broke the first version on device: right-aligned numeric
 * columns under left-positioned headers, and a tight four-column table
 * whose gutters are narrower than a word gap threshold.
 */
class PdfTableBuilderTest {

    private fun run(text: String, left: Float, right: Float, top: Float, page: Int = 0) =
        PdfTextRun(text = text, left = left, right = right, top = top, height = 12f, pageIndex = page)

    @Test
    fun `right aligned numbers stay under their headers`() {
        // Header words sit at the left of wide columns; the numbers are
        // right-aligned to the columns' right edges, so header and value
        // extents do not overlap at all.
        val page = listOf(
            run("Item", 40f, 72f, 40f), run("Qty", 150f, 168f, 40f),
            run("Price", 230f, 262f, 40f), run("Note", 320f, 352f, 40f),
            run("Widget A", 40f, 98f, 60f), run("2", 204f, 210f, 60f),
            run("12.50", 268f, 300f, 60f), run("small", 320f, 356f, 60f),
            run("Widget B", 40f, 96f, 80f), run("10", 198f, 210f, 80f),
            run("3.75", 274f, 300f, 80f), run("large", 320f, 354f, 80f)
        )
        val table = PdfTableBuilder.build(listOf(page))
        assertEquals(3, table.size)
        assertEquals(listOf("Item", "Qty", "Price", "Note"), table[0])
        assertEquals(listOf("Widget A", "2", "12.50", "small"), table[1])
        assertEquals(listOf("Widget B", "10", "3.75", "large"), table[2])
    }

    @Test
    fun `tight four column table keeps all columns`() {
        // Six-point gutters: too narrow for line-merging strippers, wide
        // enough to be real gutters no run ever crosses.
        val page = listOf(
            run("ID", 30f, 42f, 40f), run("Name", 48f, 84f, 40f),
            run("Amount", 101f, 145f, 40f), run("City", 151f, 178f, 40f),
            run("1", 30f, 36f, 58f), run("Person 1", 48f, 96f, 58f),
            run("1.25", 118f, 142f, 58f), run("City1", 151f, 182f, 58f),
            run("2", 30f, 36f, 76f), run("Person 2", 48f, 96f, 76f),
            run("2.50", 118f, 142f, 76f), run("City2", 151f, 182f, 76f)
        )
        val table = PdfTableBuilder.build(listOf(page))
        assertEquals(3, table.size)
        assertEquals(listOf("ID", "Name", "Amount", "City"), table[0])
        assertEquals(listOf("1", "Person 1", "1.25", "City1"), table[1])
        assertEquals(listOf("2", "Person 2", "2.50", "City2"), table[2])
    }

    @Test
    fun `header repeated on page two is kept once`() {
        val page1 = listOf(
            run("Item", 40f, 72f, 40f, page = 0), run("Cost", 200f, 232f, 40f, page = 0),
            run("Pen", 40f, 66f, 60f, page = 0), run("1.5", 206f, 232f, 60f, page = 0)
        )
        val page2 = listOf(
            run("Item", 40f, 72f, 40f, page = 1), run("Cost", 200f, 232f, 40f, page = 1),
            run("Book", 40f, 74f, 60f, page = 1), run("4", 214f, 232f, 60f, page = 1)
        )
        val table = PdfTableBuilder.build(listOf(page1, page2))
        assertEquals(3, table.size)
        assertEquals(listOf("Pen", "1.5"), table[1])
        assertEquals(listOf("Book", "4"), table[2])
    }

    @Test
    fun `plain text page becomes a single column`() {
        val page = listOf(
            run("Hello world", 40f, 140f, 40f),
            run("Second line", 40f, 130f, 60f)
        )
        val table = PdfTableBuilder.build(listOf(page))
        assertEquals(2, table.size)
        assertEquals(listOf("Hello world"), table[0])
        assertEquals(listOf("Second line"), table[1])
    }

    @Test
    fun `a full width title does not collapse the columns`() {
        val page = listOf(
            run("Quarterly Sales Report", 40f, 400f, 20f),
            run("Item", 40f, 72f, 40f), run("Cost", 200f, 232f, 40f),
            run("Pen", 40f, 66f, 60f), run("1.5", 206f, 232f, 60f)
        )
        val table = PdfTableBuilder.build(listOf(page))
        assertEquals(3, table.size)
        assertEquals(2, table[1].size)
        assertEquals(listOf("Item", "Cost"), table[1])
        assertEquals(listOf("Pen", "1.5"), table[2])
    }

    @Test
    fun `empty input gives empty table`() {
        assertEquals(0, PdfTableBuilder.build(emptyList()).size)
        assertEquals(0, PdfTableBuilder.build(listOf(emptyList())).size)
    }
}
