package com.morphdrop.app

import com.morphdrop.app.data.pdf.ScriptSpans
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Script-span splitting for the searchable-PDF text layer: a mixed-script
 * element must never be written entirely with the Devanagari font (it has
 * no Latin glyphs and PDFBox throws), and word gaps between OCR elements
 * must be detectable so an explicit space can be written.
 */
class ScriptSpansTest {

    @Test
    fun `latin word is a single latin span`() {
        val spans = ScriptSpans.split("MorphDrop")
        assertEquals(1, spans.size)
        assertEquals("MorphDrop", spans[0].text)
        assertFalse(spans[0].devanagari)
    }

    @Test
    fun `devanagari word is a single devanagari span`() {
        val spans = ScriptSpans.split("दूसरी")
        assertEquals(1, spans.size)
        assertTrue(spans[0].devanagari)
    }

    @Test
    fun `mixed line splits at the script boundary`() {
        val spans = ScriptSpans.split("MorphDrop दूसरी पंक्ति 2026")
        assertEquals(5, spans.size)
        assertEquals("MorphDrop ", spans[0].text)
        assertFalse(spans[0].devanagari)
        assertEquals("दूसरी", spans[1].text)
        assertTrue(spans[1].devanagari)
        assertEquals(" ", spans[2].text)
        assertFalse(spans[2].devanagari)
        assertEquals("पंक्ति", spans[3].text)
        assertTrue(spans[3].devanagari)
        // Digits and the space before them are a latin span.
        assertEquals(" 2026", spans[4].text)
        assertFalse(spans[4].devanagari)
    }

    @Test
    fun `spans always reassemble to the input`() {
        val samples = listOf(
            "Quarterly Report 2026",
            "दूसरी पंक्ति में अधिक जानकारी है।",
            "MorphDrop दूसरी Report",
            "कुल 1,250 रुपये",
            ""
        )
        for (sample in samples) {
            assertEquals(sample, ScriptSpans.split(sample).joinToString("") { it.text })
        }
    }

    @Test
    fun `spans never mix scripts`() {
        // The device failure was "No glyph for U+004D (M)" from writing a
        // mixed line with the Devanagari font alone. The routing property
        // that prevents it: every code point in a devanagari span is in a
        // Devanagari block (the bundled Noto font's coverage, proven on
        // device for pure Hindi), and no latin span contains one. Font
        // objects themselves are not loaded here — the Android PDFBox port
        // needs the Android runtime for that (same reason TablePdfWriter's
        // writer is device-verified, per its test's note).
        for (span in ScriptSpans.split("MorphDrop दूसरी पंक्ति 2026 कुल")) {
            var index = 0
            while (index < span.text.length) {
                val codePoint = Character.codePointAt(span.text, index)
                assertEquals(
                    "U+%04X in the wrong span".format(codePoint),
                    span.devanagari,
                    ScriptSpans.isDevanagari(codePoint)
                )
                index += Character.charCount(codePoint)
            }
        }
    }

    @Test
    fun `word gap needs a real space sized gap`() {
        // Element height 40px (200 DPI): a 20px gap is a word space, a
        // 5px gap is letters the OCR split mid-word.
        assertTrue(ScriptSpans.isWordGap(previousRight = 100, nextLeft = 120, elementHeight = 40))
        assertFalse(ScriptSpans.isWordGap(previousRight = 100, nextLeft = 105, elementHeight = 40))
        assertFalse(ScriptSpans.isWordGap(previousRight = 100, nextLeft = 130, elementHeight = 0))
    }
}
