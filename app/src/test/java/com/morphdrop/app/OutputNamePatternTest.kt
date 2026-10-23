package com.morphdrop.app

import com.morphdrop.app.worker.applyOutputNamePattern
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OutputNamePatternTest {

    @Test
    fun `tokens are replaced`() {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2026-10-23")!!
        assertEquals(
            "IMG_2041_3",
            applyOutputNamePattern("{name}_{n}", "IMG_2041", 3, date)
        )
        assertEquals(
            "photo_03",
            applyOutputNamePattern("photo_{nn}", "ignored", 3, date)
        )
        assertEquals(
            "20261023_scan",
            applyOutputNamePattern("{date}_scan", "ignored", 1, date)
        )
    }

    @Test
    fun `illegal filename characters are replaced`() {
        assertEquals(
            "a_b_c_1",
            applyOutputNamePattern("a/b:c_{n}", "x", 1, Date())
        )
    }

    @Test
    fun `blank rendering falls back to the original name`() {
        assertEquals(
            "original",
            applyOutputNamePattern("   ", "original", 2, Date())
        )
    }
}
