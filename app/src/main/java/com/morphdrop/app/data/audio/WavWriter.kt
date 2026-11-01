package com.morphdrop.app.data.audio

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Streams 16-bit little-endian PCM into a WAV file. The RIFF header is
 * written as a placeholder first and patched with the real sizes on
 * [close], so arbitrarily long audio never sits in memory as bytes.
 */
class WavWriter(
    private val file: File,
    private val sampleRate: Int,
    private val channels: Int
) {
    private val out = FileOutputStream(file)
    private var dataBytes = 0L
    private var closed = false

    init {
        // 44-byte placeholder header; close() patches the sizes.
        out.write(ByteArray(HEADER_SIZE))
    }

    fun writePcm(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
        check(!closed) { "WavWriter is closed." }
        out.write(bytes, offset, length)
        dataBytes += length
    }

    fun close() {
        if (closed) return
        closed = true
        out.flush()
        out.close()
        RandomAccessFile(file, "rw").use { raf ->
            val header = wavHeaderBytes(sampleRate, channels, dataBytes)
            raf.seek(0)
            raf.write(header)
        }
    }

    companion object {
        const val HEADER_SIZE = 44

        /** Builds a standard 44-byte PCM WAV header for [dataBytes]. */
        fun wavHeaderBytes(sampleRate: Int, channels: Int, dataBytes: Long): ByteArray {
            val byteRate = sampleRate * channels * 2
            val blockAlign = (channels * 2).toShort()
            return ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt((36 + dataBytes).toInt())
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16) // PCM subchunk size
                putShort(1) // Audio format: PCM
                putShort(channels.toShort())
                putInt(sampleRate)
                putInt(byteRate)
                putShort(blockAlign)
                putShort(16) // Bits per sample
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(dataBytes.toInt())
            }.array()
        }
    }
}
