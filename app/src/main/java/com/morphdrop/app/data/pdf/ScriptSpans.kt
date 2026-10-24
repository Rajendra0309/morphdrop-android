package com.morphdrop.app.data.pdf

/**
 * Splits recognized text into Devanagari and non-Devanagari spans so each
 * span can be written with a font that actually has its glyphs. The
 * bundled Noto Sans Devanagari covers no Latin letters, so writing a
 * mixed-script line ("MorphDrop दूसरी पंक्ति") with it alone throws
 * "No glyph for U+004D" in PDFBox and fails the whole conversion; Latin,
 * digits and punctuation belong in Helvetica instead.
 */
object ScriptSpans {

    data class Span(val text: String, val devanagari: Boolean)

    /** True when [codePoint] belongs to a Devanagari Unicode block. */
    fun isDevanagari(codePoint: Int): Boolean =
        codePoint in 0x0900..0x097F || // Devanagari
            codePoint in 0xA8E0..0xA8FF || // Devanagari Extended
            codePoint in 0x1CD0..0x1CFF || // Vedic Extensions (used with Devanagari)
            codePoint in 0x11B00..0x11B5F // Devanagari Extended-A

    /**
     * Splits [text] into maximal same-script spans. Zero-width joiner /
     * non-joiner inherit the previous span's script: they glue Devanagari
     * conjuncts together and have no glyph of their own to choose by.
     */
    fun split(text: String): List<Span> {
        if (text.isEmpty()) return emptyList()
        val spans = ArrayList<Span>()
        val current = StringBuilder()
        var currentDevanagari = false
        var started = false
        var index = 0
        while (index < text.length) {
            val codePoint = Character.codePointAt(text, index)
            val devanagari = when (codePoint) {
                0x200C, 0x200D -> if (started) currentDevanagari else false
                else -> isDevanagari(codePoint)
            }
            if (!started) {
                currentDevanagari = devanagari
                started = true
            } else if (devanagari != currentDevanagari) {
                spans += Span(current.toString(), currentDevanagari)
                current.setLength(0)
                currentDevanagari = devanagari
            }
            current.appendCodePoint(codePoint)
            index += Character.charCount(codePoint)
        }
        if (current.isNotEmpty()) {
            spans += Span(current.toString(), currentDevanagari)
        }
        return spans
    }

    /**
     * True when the horizontal gap before an OCR element is wide enough to
     * be a word space. The searchable-PDF layer writes one text entry per
     * element; without an explicit space character, extraction joins words
     * that merely sit close together ("दूसरीपंक्त").
     */
    fun isWordGap(previousRight: Int, nextLeft: Int, elementHeight: Int): Boolean {
        if (elementHeight <= 0) return false
        return nextLeft - previousRight > elementHeight * 0.35f
    }
}
