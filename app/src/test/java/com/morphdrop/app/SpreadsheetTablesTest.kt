package com.morphdrop.app

import com.morphdrop.app.data.spreadsheet.SpreadsheetTables
import org.junit.Assert.assertEquals
import org.junit.Test

class SpreadsheetTablesTest {

    @Test
    fun `delimited parser honours quoted fields`() {
        val table = SpreadsheetTables.parseDelimited(
            "Name,Note\r\n\"Smith, John\",\"said \"\"hi\"\"\"\r\nPlain,Simple",
            ','
        )
        assertEquals(3, table.size)
        assertEquals(listOf("Name", "Note"), table[0])
        assertEquals(listOf("Smith, John", "said \"hi\""), table[1])
        assertEquals(listOf("Plain", "Simple"), table[2])
    }

    @Test
    fun `delimited parser keeps newlines inside quotes`() {
        val table = SpreadsheetTables.parseDelimited("a,\"line1\nline2\",b\n1,2,3", ',')
        assertEquals(2, table.size)
        assertEquals("line1\nline2", table[0][1])
        assertEquals(listOf("1", "2", "3"), table[1])
    }

    @Test
    fun `csv writer quotes only when needed`() {
        val csv = SpreadsheetTables.toCsv(
            listOf(
                listOf("Name", "Note"),
                listOf("Smith, John", "plain"),
                listOf("He said \"hi\"", "two\nlines")
            )
        )
        assertEquals(
            "Name,Note\r\n\"Smith, John\",plain\r\n\"He said \"\"hi\"\"\",\"two\nlines\"\r\n",
            csv
        )
    }

    @Test
    fun `csv round trip preserves cell values`() {
        val table = listOf(
            listOf("Item", "Qty", "Note"),
            listOf("Apples, red", "42", "fresh \"today\""),
            listOf("Multi\nline", "", "x")
        )
        val reparsed = SpreadsheetTables.parseDelimited(SpreadsheetTables.toCsv(table), ',')
        assertEquals(table, reparsed)
    }
}
