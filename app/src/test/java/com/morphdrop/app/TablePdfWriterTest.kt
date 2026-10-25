package com.morphdrop.app

import com.morphdrop.app.data.pdf.TablePdfWriter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit coverage for TablePdfWriter's text sanitizer — the part that keeps
 * table text inside the WinAnsi encoding of the standard PDF fonts, so
 * generated PDFs extract cleanly (the old platform renderer turned
 * decimal points into NUL in the text layer).
 *
 * The writer itself runs on PDFBox-Android, which needs the Android
 * runtime; full text-extraction verification happens on device by
 * running PDF to Text over a generated table PDF.
 */
class TablePdfWriterTest {

    @Test
    fun `plain ascii and decimals pass through unchanged`() {
        assertEquals("Price 12.5, ratio 0.125", TablePdfWriter.sanitize("Price 12.5, ratio 0.125"))
        assertEquals("café £5", TablePdfWriter.sanitize("café £5"))
    }

    @Test
    fun `smart punctuation maps to ascii equivalents`() {
        assertEquals("a'b \"c\" - d", TablePdfWriter.sanitize("a’b “c” – d"))
        assertEquals("wait.", TablePdfWriter.sanitize("wait…"))
    }

    @Test
    fun `line breaks inside cells survive for the wrapper`() {
        assertEquals("Line one\nLine two", TablePdfWriter.sanitize("Line one\nLine two"))
    }

    @Test
    fun `other control characters never reach the page`() {
        assertEquals("a b c", TablePdfWriter.sanitize("a\u0000b\tc"))
    }
}
