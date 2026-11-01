package com.morphdrop.app.data.audio

import java.io.BufferedInputStream
import java.io.InputStream
import kotlin.math.roundToInt

/**
 * Streams PCM samples out of a WAV file, normalizing them to 16-bit
 * little-endian PCM. Supports the WAV variants phones actually produce:
 * 8/16/24/32-bit integer PCM and 32-bit IEEE float. Anything else
 * (ADPCM, A-law, ...) fails with a clear message instead of noise.
 *
 * WAV is parsed directly rather than going through MediaCodec because
 * the platform WAV extractor's track mime is not stable across devices,
 * while the RIFF format itself is trivially parseable. Reading is
 * strictly forward and chunked, so a multi-hundred-MB recording never
 * sits in memory whole.
 */
class WavPcmReader(input: InputStream) {

    class UnsupportedWavException(message: String) : Exception(message)

    val sampleRate: Int
    val channels: Int

    private val stream = BufferedInputStream(input)
    private val sourceBits: Int
    private val sourceFormat: Int
    private var dataRemaining: Long
    private var closed = false

    init {
        val header = ByteArray(12)
        if (readFully(header) != 12 || header.ascii(0, 4) != "RIFF" ||
            header.ascii(8, 12) != "WAVE"
        ) {
            throw UnsupportedWavException("This file is not a WAV file.")
        }
        var parsedChannels = 0
        var parsedSampleRate = 0
        var parsedBits = 0
        var parsedFormat = -1
        var parsedDataSize = -1L
        while (true) {
            val chunkHeader = ByteArray(8)
            if (readFully(chunkHeader) != 8) break
            val chunkId = chunkHeader.ascii(0, 4)
            val chunkSize = readIntLe(chunkHeader, 4)
            if (chunkSize < 0) break
            when (chunkId) {
                "fmt " -> {
                    val fmt = ByteArray(chunkSize)
                    if (readFully(fmt) != chunkSize) {
                        throw UnsupportedWavException("This WAV file has a truncated format header.")
                    }
                    parsedFormat = readShortLe(fmt, 0)
                    parsedChannels = readShortLe(fmt, 2)
                    parsedSampleRate = readIntLe(fmt, 4)
                    parsedBits = readShortLe(fmt, 14)
                }
                "data" -> {
                    parsedDataSize = chunkSize.toLong()
                    break
                }
                else -> skipFully(chunkSize.toLong() + (chunkSize and 1))
            }
        }
        if (parsedDataSize < 0) {
            throw UnsupportedWavException("This WAV file has no audio data.")
        }
        if (parsedChannels <= 0 || parsedSampleRate <= 0) {
            throw UnsupportedWavException("This WAV file has an invalid format header.")
        }
        if (parsedFormat != 1 && parsedFormat != 3) {
            throw UnsupportedWavException(
                "This WAV encoding (format $parsedFormat) is not supported. " +
                    "Only PCM and float WAV files are supported."
            )
        }
        if (parsedFormat == 3 && parsedBits != 32) {
            throw UnsupportedWavException("Unsupported float WAV bit depth: $parsedBits.")
        }
        if (parsedFormat == 1 && parsedBits !in listOf(8, 16, 24, 32)) {
            throw UnsupportedWavException("Unsupported WAV bit depth: $parsedBits.")
        }
        channels = parsedChannels
        sampleRate = parsedSampleRate
        sourceBits = parsedBits
        sourceFormat = parsedFormat
        dataRemaining = parsedDataSize
    }

    /** Bytes of 16-bit PCM still unread. */
    fun remainingPcmBytes(): Long {
        val sourceFrame = channels * (sourceBits / 8)
        val framesLeft = dataRemaining / sourceFrame
        return framesLeft * channels * 2
    }

    /**
     * Returns up to [maxBytes] of 16-bit PCM, or null at end of data.
     * [maxBytes] is rounded down to a whole frame.
     */
    fun readConvertedChunk(maxBytes: Int): ByteArray? {
        check(!closed) { "WavPcmReader is closed." }
        val frameOut = channels * 2
        val framesWanted = maxBytes / frameOut
        if (framesWanted <= 0 || dataRemaining <= 0) return null
        val sourceFrame = channels * (sourceBits / 8)
        val sourceWanted = minOf(framesWanted.toLong() * sourceFrame, dataRemaining).toInt()
        val raw = ByteArray(sourceWanted)
        val read = readFully(raw)
        if (read <= 0) return null
        dataRemaining -= read
        val wholeFrames = read / sourceFrame
        val usable = wholeFrames * sourceFrame
        return when {
            sourceFormat == 3 -> convertFloatPcm(raw, usable)
            sourceBits == 16 -> raw.copyOf(usable)
            else -> convertIntegerPcm(raw, usable, sourceBits, channels)
        }
    }

    /** Discards [bytes] of converted PCM (used to reach a trim start). */
    fun skipConvertedBytes(bytes: Long) {
        var left = bytes
        while (left > 0) {
            val chunk = readConvertedChunk(minOf(left, 256 * 1024).toInt()) ?: return
            left -= chunk.size
        }
    }

    fun close() {
        if (!closed) {
            closed = true
            stream.close()
        }
    }

    private fun convertIntegerPcm(raw: ByteArray, usable: Int, bits: Int, channels: Int): ByteArray {
        val frameIn = channels * (bits / 8)
        val frames = usable / frameIn
        val out = ByteArray(frames * channels * 2)
        var o = 0
        for (f in 0 until frames) {
            for (c in 0 until channels) {
                val base = f * frameIn + c * (bits / 8)
                val sample16 = when (bits) {
                    8 -> ((raw[base].toInt() and 0xFF) - 128) shl 8
                    16 -> readShortLe(raw, base)
                    24 -> {
                        val v = (raw[base].toInt() and 0xFF) or
                            ((raw[base + 1].toInt() and 0xFF) shl 8) or
                            (raw[base + 2].toInt() shl 16)
                        v shr 8
                    }
                    32 -> readIntLe(raw, base) shr 16
                    else -> 0
                }
                out[o++] = (sample16 and 0xFF).toByte()
                out[o++] = ((sample16 shr 8) and 0xFF).toByte()
            }
        }
        return out
    }

    private fun convertFloatPcm(raw: ByteArray, usable: Int): ByteArray {
        val frames = usable / 4
        val out = ByteArray(frames * 2)
        for (i in 0 until frames) {
            val sample = java.lang.Float.intBitsToFloat(readIntLe(raw, i * 4))
                .coerceIn(-1f, 1f) * 32767f
            val s = sample.roundToInt()
            out[i * 2] = (s and 0xFF).toByte()
            out[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun readFully(buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val read = stream.read(buffer, offset, buffer.size - offset)
            if (read < 0) break
            offset += read
        }
        return offset
    }

    private fun skipFully(count: Long) {
        var left = count
        val scratch = ByteArray(8192)
        while (left > 0) {
            val read = stream.read(scratch, 0, minOf(left, scratch.size.toLong()).toInt())
            if (read < 0) return
            left -= read
        }
    }

    private fun readShortLe(bytes: ByteArray, pos: Int): Int {
        val v = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8)
        return if (v >= 32768) v - 65536 else v
    }

    private fun readIntLe(bytes: ByteArray, pos: Int): Int =
        (bytes[pos].toInt() and 0xFF) or
            ((bytes[pos + 1].toInt() and 0xFF) shl 8) or
            ((bytes[pos + 2].toInt() and 0xFF) shl 16) or
            (bytes[pos + 3].toInt() shl 24)

    private fun ByteArray.ascii(from: Int, to: Int): String =
        String(copyOfRange(from, to), Charsets.US_ASCII)
}
