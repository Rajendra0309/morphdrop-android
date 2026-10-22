package com.morphdrop.app

import com.morphdrop.app.data.qr.QrPayloads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadsTest {

    @Test
    fun `url adds https when scheme is missing`() {
        assertEquals("https://example.com", QrPayloads.url("example.com"))
        assertEquals("https://example.com/a", QrPayloads.url("  example.com/a  "))
    }

    @Test
    fun `url keeps an existing scheme`() {
        assertEquals("http://example.com", QrPayloads.url("http://example.com"))
        assertEquals("https://example.com", QrPayloads.url("https://example.com"))
    }

    @Test
    fun `wifi payload follows the standard format`() {
        val payload = QrPayloads.wifi("HomeNet", "secret123", "WPA", hidden = false)
        assertEquals("WIFI:T:WPA;S:HomeNet;P:secret123;;", payload)
    }

    @Test
    fun `wifi payload escapes separators in values`() {
        val payload = QrPayloads.wifi("My;Net", "p:a,s\\s", "WPA", hidden = true)
        assertTrue(payload.contains("S:My\\;Net;"))
        assertTrue(payload.contains("P:p\\:a\\,s\\\\s;"))
        assertTrue(payload.contains("H:true;"))
    }

    @Test
    fun `open wifi payload omits the password field`() {
        val payload = QrPayloads.wifi("Cafe", "", "nopass", hidden = false)
        assertEquals("WIFI:T:nopass;S:Cafe;;", payload)
        assertFalse(payload.contains("P:"))
    }

    @Test
    fun `contact payload is a mecard with present fields only`() {
        val payload = QrPayloads.contact("Asha Rao", "+91 98765 43210", "")
        assertEquals("MECARD:N:Asha Rao;TEL:+919876543210;;", payload)
    }

    @Test
    fun `phone payload keeps digits and plus only`() {
        assertEquals("tel:+919876543210", QrPayloads.phone("+91 98765-43210"))
    }
}
