package com.morphdrop.app

import com.morphdrop.app.data.audio.WavPcmReader
import com.morphdrop.app.data.audio.WavWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavWriterTest {

    @Test
    fun `written wav has a valid header and round-trips its pcm`() {
        val file = File.createTempFile("morphdrop_wav_test", ".wav")
        try {
            val pcm = byteArrayOf(0, 0, 0xFF.toByte(), 0x7F, 0x00, 0x80.toByte(), 0x01, 0x00)
            WavWriter(file, 44100, 2).apply {
                writePcm(pcm)
                close()
            }
            val bytes = file.readBytes()
            val header = ByteBuffer.wrap(bytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
            assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
            assertEquals(36 + pcm.size, header.getInt(4))
            assertEquals(1, header.getShort(20).toInt())
            assertEquals(2, header.getShort(22).toInt())
            assertEquals(44100, header.getInt(24))
            assertEquals(pcm.size, header.getInt(40))
            assertArrayEquals(pcm, bytes.copyOfRange(44, bytes.size))

            // And the reader side parses the same file back.
            val reader = WavPcmReader(file.inputStream())
            assertEquals(44100, reader.sampleRate)
            assertEquals(2, reader.channels)
            assertArrayEquals(pcm, reader.readConvertedChunk(1024))
            reader.close()
        } finally {
            file.delete()
        }
    }

    @Test
    fun `non-wav input is rejected with a clear message`() {
        val file = File.createTempFile("morphdrop_not_wav", ".bin")
        try {
            file.writeBytes("this is definitely not a riff file".toByteArray(Charsets.US_ASCII))
            try {
                WavPcmReader(file.inputStream())
                throw AssertionError("Expected UnsupportedWavException")
            } catch (e: WavPcmReader.UnsupportedWavException) {
                assertTrue(e.message!!.contains("not a WAV"))
            }
        } finally {
            file.delete()
        }
    }
}
