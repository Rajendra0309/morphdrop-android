package com.morphdrop.app.data.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.morphdrop.app.domain.model.AudioMatrix
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject

class AudioTranscoderException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Decodes audio to PCM and re-encodes it to WAV (hand-written PCM) or
 * M4A (AAC via MediaCodec + MediaMuxer). Everything is streamed in
 * chunks — no whole-file byte arrays — and every codec is released in
 * a finally block.
 *
 * Trimming cuts on decoded-frame boundaries: a cut lands on the nearest
 * frame at or after the requested start, which the UI states plainly.
 * Timestamps are rebased so the first kept frame starts at zero.
 */
class AudioTranscoder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capabilities: AudioCapabilities
) {
    data class Request(
        val inputUri: Uri,
        val inputExtension: String,
        val outputFile: File,
        val outputFormat: String, // "wav" or "m4a"
        val startUs: Long? = null,
        val endUs: Long? = null
    )

    fun transcode(request: Request) {
        val format = request.outputFormat.lowercase()
        if (format == "m4a" && !capabilities.hasAacEncoder()) {
            throw AudioTranscoderException(
                "This device cannot encode AAC audio, so M4A output is not " +
                    "available here. WAV output still works."
            )
        }
        if (format != "wav" && format != "m4a") {
            throw AudioTranscoderException("Unsupported audio output format: ${request.outputFormat}.")
        }
        if (request.inputExtension.lowercase() == "wav") {
            transcodeWavInput(request, format)
        } else {
            transcodeCodecInput(request, format)
        }
    }

    // ---- WAV input: parsed directly, no decoder needed ----

    private fun transcodeWavInput(request: Request, format: String) {
        val inputStream = context.contentResolver.openInputStream(request.inputUri)
            ?: throw AudioTranscoderException("Could not open the input audio file.")
        val reader = try {
            WavPcmReader(inputStream)
        } catch (e: WavPcmReader.UnsupportedWavException) {
            throw AudioTranscoderException(e.message ?: "Unsupported WAV file.")
        }
        val frameSize = reader.channels * 2
        val startByte = request.startUs?.let {
            (it * reader.sampleRate.toLong() * frameSize / 1_000_000L / frameSize) * frameSize
        } ?: 0L
        val endByte = request.endUs?.let {
            (it * reader.sampleRate.toLong() * frameSize / 1_000_000L / frameSize) * frameSize
        }
        try {
            if (startByte > 0) reader.skipConvertedBytes(startByte)
            when (format) {
                "wav" -> {
                    WavWriter(request.outputFile, reader.sampleRate, reader.channels).apply {
                        try {
                            var written = 0L
                            while (true) {
                                val remaining = endByte?.let { it - startByte - written }
                                if (remaining != null && remaining <= 0) break
                                val want = remaining?.let { minOf(it, CHUNK_BYTES.toLong()).toInt() }
                                    ?: CHUNK_BYTES
                                val chunk = reader.readConvertedChunk(want) ?: break
                                writePcm(chunk)
                                written += chunk.size
                            }
                        } finally {
                            close()
                        }
                    }
                }
                "m4a" -> {
                    val encoder = AacEncoder(reader.sampleRate, reader.channels, request.outputFile)
                    try {
                        var written = 0L
                        while (true) {
                            val remaining = endByte?.let { it - startByte - written }
                            if (remaining != null && remaining <= 0) break
                            val want = remaining?.let { minOf(it, CHUNK_BYTES.toLong()).toInt() }
                                ?: CHUNK_BYTES
                            val chunk = reader.readConvertedChunk(want) ?: break
                            val ptsUs = written * 1_000_000L / (reader.sampleRate.toLong() * frameSize)
                            encoder.writePcm(chunk, ptsUs)
                            written += chunk.size
                        }
                    } finally {
                        encoder.finish()
                    }
                }
            }
        } finally {
            reader.close()
        }
    }

    // ---- Codec input: MediaExtractor + MediaCodec decode ----

    private fun transcodeCodecInput(request: Request, format: String) {
        val pfd = context.contentResolver.openFileDescriptor(request.inputUri, "r")
            ?: throw AudioTranscoderException("Could not open the input audio file.")
        pfd.use {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(pfd.fileDescriptor)
                var audioTrack = -1
                var trackFormat: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val f = extractor.getTrackFormat(i)
                    val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        audioTrack = i
                        trackFormat = f
                        break
                    }
                }
                if (audioTrack < 0 || trackFormat == null) {
                    throw AudioTranscoderException("This file has no audio track.")
                }
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (!capabilities.hasDecoder(mime)) {
                    throw AudioTranscoderException(
                        "This device cannot decode $mime audio."
                    )
                }
                val sampleRate = runCatching {
                    trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }.getOrDefault(44100)
                val channels = runCatching {
                    trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }.getOrDefault(2)
                extractor.selectTrack(audioTrack)
                if (request.startUs != null && request.startUs > 0) {
                    extractor.seekTo(request.startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                }
                when (format) {
                    "wav" -> {
                        val writer = WavWriter(request.outputFile, sampleRate, channels)
                        try {
                            decodePcm(extractor, audioTrack, mime, request.startUs, request.endUs) { bytes, _ ->
                                writer.writePcm(bytes)
                            }
                        } finally {
                            writer.close()
                        }
                    }
                    "m4a" -> {
                        val encoder = AacEncoder(sampleRate, channels, request.outputFile)
                        try {
                            decodePcm(extractor, audioTrack, mime, request.startUs, request.endUs) { bytes, ptsUs ->
                                encoder.writePcm(bytes, ptsUs)
                            }
                        } finally {
                            encoder.finish()
                        }
                    }
                }
            } finally {
                extractor.release()
            }
        }
    }

    /**
     * Decodes the selected extractor track to 16-bit PCM, invoking
     * [onPcm] for each kept buffer with a timestamp rebased so the
     * first kept frame is at zero. Buffers before [startUs] are
     * dropped; decoding stops once a buffer reaches [endUs].
     */
    private fun decodePcm(
        extractor: MediaExtractor,
        trackIndex: Int,
        mime: String,
        startUs: Long?,
        endUs: Long?,
        onPcm: (bytes: ByteArray, ptsUs: Long) -> Unit
    ) {
        val decoder = try {
            MediaCodec.createDecoderByType(mime)
        } catch (e: Exception) {
            throw AudioTranscoderException("This device cannot decode $mime audio.", e)
        }
        try {
            decoder.configure(extractor.getTrackFormat(trackIndex), null, null, 0)
            decoder.start()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputEos = false
            var outputEos = false
            var firstKeptPts: Long? = null
            var trimEnded = false
            var idlePasses = 0
            // Most decoders emit 16-bit PCM, but some emit float;
            // writing float bytes into a 16-bit WAV would be noise.
            var outputIsFloat = false
            while (!outputEos) {
                if (!inputEos) {
                    val inIndex = decoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)!!
                        val sampleSize = if (trimEnded) -1 else extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0 ||
                            (endUs != null && extractor.sampleTime >= endUs)
                        ) {
                            decoder.queueInputBuffer(
                                inIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputEos = true
                        } else {
                            decoder.queueInputBuffer(
                                inIndex, 0, sampleSize, extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outIndex >= 0) idlePasses = 0
                when {
                    outIndex >= 0 -> {
                        val outBuffer = decoder.getOutputBuffer(outIndex)
                        if (bufferInfo.size > 0 && outBuffer != null) {
                            val pts = bufferInfo.presentationTimeUs
                            if (endUs != null && pts >= endUs) {
                                trimEnded = true
                            } else if (startUs == null || pts >= startUs) {
                                if (firstKeptPts == null) firstKeptPts = pts
                                // Output buffers carry their data at
                                // info.offset, not at position 0.
                                val dup = outBuffer.duplicate()
                                dup.position(bufferInfo.offset)
                                dup.limit(bufferInfo.offset + bufferInfo.size)
                                var bytes = ByteArray(bufferInfo.size)
                                dup.get(bytes)
                                if (outputIsFloat) {
                                    bytes = floatPcmTo16Bit(bytes)
                                }
                                onPcm(bytes, pts - firstKeptPts)
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputEos = true
                        }
                    }
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // No output this pass. A healthy decoder drains
                        // to EOS after input EOS; only a stuck decoder
                        // idles forever, so cap idle passes as a guard.
                        idlePasses++
                        if (idlePasses > 5000) outputEos = true
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // Sample rate/channels come from the track
                        // format; only the PCM encoding matters here.
                        outputIsFloat = runCatching {
                            decoder.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }.getOrDefault(android.media.AudioFormat.ENCODING_PCM_16BIT) ==
                            android.media.AudioFormat.ENCODING_PCM_FLOAT
                    }
                    // INFO_OUTPUT_BUFFERS_CHANGED: nothing to do.
                }
            }
        } finally {
            try {
                decoder.stop()
            } catch (_: Exception) {
            }
            decoder.release()
        }
    }

    /** AAC encoder + MP4 muxer. Feeds PCM in slices; drains after each feed. */
    private inner class AacEncoder(
        private val sampleRate: Int,
        private val channels: Int,
        outputFile: File
    ) {
        private val encoder: MediaCodec = try {
            MediaCodec.createEncoderByType("audio/mp4a-latm")
        } catch (e: Exception) {
            throw AudioTranscoderException("This device cannot encode AAC audio.", e)
        }
        private val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        private var trackIndex = -1
        private var muxerStarted = false
        private var finished = false

        init {
            val format = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, channels)
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
        }

        fun writePcm(bytes: ByteArray, ptsUs: Long) {
            var offset = 0
            var stalledFeeds = 0
            // Slice timestamps advance with the bytes already fed, so
            // the muxed stream keeps real time.
            while (offset < bytes.size) {
                val inIndex = encoder.dequeueInputBuffer(10_000)
                if (inIndex < 0) {
                    drain(encoderEndOfStream = false)
                    if (++stalledFeeds > 100) {
                        throw AudioTranscoderException("Audio encoder stalled while writing output.")
                    }
                    continue
                }
                stalledFeeds = 0
                val buffer = encoder.getInputBuffer(inIndex)!!
                val take = minOf(bytes.size - offset, buffer.remaining())
                if (take <= 0) {
                    drain(encoderEndOfStream = false)
                    continue
                }
                val slicePts = ptsUs + offset * 1_000_000L / (sampleRate.toLong() * channels * 2)
                buffer.put(bytes, offset, take)
                encoder.queueInputBuffer(inIndex, 0, take, slicePts, 0)
                offset += take
            }
            drain(encoderEndOfStream = false)
        }

        fun finish() {
            if (finished) return
            finished = true
            try {
                val inIndex = encoder.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    encoder.queueInputBuffer(
                        inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                }
                drain(encoderEndOfStream = true)
            } finally {
                try {
                    if (muxerStarted) muxer.stop()
                } catch (_: Exception) {
                }
                muxer.release()
                try {
                    encoder.stop()
                } catch (_: Exception) {
                }
                encoder.release()
            }
        }

        private fun drain(encoderEndOfStream: Boolean) {
            val bufferInfo = MediaCodec.BufferInfo()
            while (true) {
                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIndex >= 0 -> {
                        val outBuffer = encoder.getOutputBuffer(outIndex)
                        if (bufferInfo.size > 0 && outBuffer != null) {
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                if (trackIndex < 0) {
                                    // Should not happen: format change
                                    // precedes the first sample. Skip.
                                } else {
                                    muxer.writeSampleData(trackIndex, outBuffer, bufferInfo)
                                }
                            }
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            return
                        }
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (trackIndex < 0) {
                            trackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                    else -> return
                }
            }
        }
    }

    /** Converts float PCM bytes to 16-bit PCM bytes (clamped). */
    private fun floatPcmTo16Bit(raw: ByteArray): ByteArray {
        val frames = raw.size / 4
        val out = ByteArray(frames * 2)
        for (i in 0 until frames) {
            val intBits = (raw[i * 4].toInt() and 0xFF) or
                ((raw[i * 4 + 1].toInt() and 0xFF) shl 8) or
                ((raw[i * 4 + 2].toInt() and 0xFF) shl 16) or
                (raw[i * 4 + 3].toInt() shl 24)
            val sample = java.lang.Float.intBitsToFloat(intBits)
                .coerceIn(-1f, 1f) * 32767f
            val s = sample.toInt()
            out[i * 2] = (s and 0xFF).toByte()
            out[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return out
    }

    private companion object {
        const val CHUNK_BYTES = 64 * 1024
    }
}
