package com.morphdrop.app

import com.morphdrop.app.domain.model.AudioMatrix
import com.morphdrop.app.domain.model.AudioUnsupportedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioMatrixTest {

    @Test
    fun `all documented inputs are accepted`() {
        listOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac").forEach { ext ->
            assertTrue(ext, AudioMatrix.isSupportedInput(ext))
            AudioMatrix.validateInput(ext)
        }
    }

    @Test
    fun `unknown input extension is rejected with a clear message`() {
        val error = assertThrows(AudioUnsupportedException::class.java) {
            AudioMatrix.validateInput("avi")
        }
        assertTrue(error.message!!.contains("avi"))
    }

    @Test
    fun `wav output is always offered, m4a only with an aac encoder`() {
        assertEquals(listOf("wav", "m4a"), AudioMatrix.offeredOutputs(true))
        assertEquals(listOf("wav"), AudioMatrix.offeredOutputs(false))
    }

    @Test
    fun `mp3 output is refused, never silently attempted`() {
        val error = assertThrows(AudioUnsupportedException::class.java) {
            AudioMatrix.validateOutput("mp3", true)
        }
        assertTrue(error.message!!.contains("no MP3 encoder"))
    }

    @Test
    fun `m4a output without an aac encoder fails clearly`() {
        assertThrows(AudioUnsupportedException::class.java) {
            AudioMatrix.validateOutput("m4a", false)
        }
        AudioMatrix.validateOutput("m4a", true)
        AudioMatrix.validateOutput("wav", false)
    }

    @Test
    fun `duration formats as minutes and zero-padded seconds`() {
        assertEquals("0:00", AudioMatrix.formatDuration(0))
        assertEquals("0:05", AudioMatrix.formatDuration(5_000))
        assertEquals("1:05", AudioMatrix.formatDuration(65_000))
        assertEquals("10:00", AudioMatrix.formatDuration(600_000))
    }

    @Test
    fun `trim range normalizes a missing end to the file duration`() {
        assertEquals(10_000L to 60_000L, AudioMatrix.validateTrimRange(10_000, null, 60_000))
        assertEquals(10_000L to 60_000L, AudioMatrix.validateTrimRange(10_000, 0, 60_000))
    }

    @Test
    fun `trim range rejects end before start and start past duration`() {
        assertThrows(IllegalArgumentException::class.java) {
            AudioMatrix.validateTrimRange(30_000, 10_000, 60_000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AudioMatrix.validateTrimRange(70_000, null, 60_000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AudioMatrix.validateTrimRange(-1, null, 60_000)
        }
    }

    @Test
    fun `video containers are listed separately from audio inputs`() {
        assertFalse(AudioMatrix.isSupportedInput("mp4"))
        assertTrue(AudioMatrix.VIDEO_EXTENSIONS.contains("mp4"))
    }
}
