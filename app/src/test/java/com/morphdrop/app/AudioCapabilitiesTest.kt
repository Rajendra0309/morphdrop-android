package com.morphdrop.app

import com.morphdrop.app.data.audio.AudioCapabilities
import com.morphdrop.app.data.audio.CodecInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCapabilitiesTest {

    private fun caps(vararg infos: CodecInfo) =
        AudioCapabilities { infos.toList() }

    @Test
    fun `decoder and encoder presence are distinguished`() {
        val capabilities = caps(
            CodecInfo("OMX.decoder.mp3", false, listOf("audio/mpeg")),
            CodecInfo("OMX.encoder.aac", true, listOf("audio/mp4a-latm"))
        )
        assertTrue(capabilities.hasDecoder("audio/mpeg"))
        assertFalse(capabilities.hasEncoder("audio/mpeg"))
        assertTrue(capabilities.hasEncoder("audio/mp4a-latm"))
        assertFalse(capabilities.hasDecoder("audio/mp4a-latm"))
        assertTrue(capabilities.hasAacEncoder())
    }

    @Test
    fun `a device without an aac encoder reports wav-only`() {
        val capabilities = caps(
            CodecInfo("OMX.decoder.aac", false, listOf("audio/mp4a-latm"))
        )
        assertFalse(capabilities.hasAacEncoder())
    }

    @Test
    fun `empty codec list means nothing is supported`() {
        val capabilities = caps()
        assertFalse(capabilities.hasDecoder("audio/mpeg"))
        assertFalse(capabilities.hasAacEncoder())
    }
}
