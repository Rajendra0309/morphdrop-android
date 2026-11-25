package com.morphdrop.app.data.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.morphdrop.app.domain.model.AudioMatrix
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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
        val endUs: Long? = null,
        /** 0..1 fraction of the (trimmed) source, throttled inside the transcoder. */
        val onProgress: ((Float) -> Unit)? = null,
        /** Checked periodically; false stops the job with CancellationException. */
        val shouldContinue: (() -> Boolean)? = null
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
            try {
                transcodeCodecInput(request, format)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AudioTranscoderException) {
                throw e
            } catch (e: Exception) {
                // MediaExtractor/MediaCodec surface framework internals
                // ("Failed to instantiate extractor") for corrupt or
                // empty files; the user gets a plain-language message.
                Log.w("AudioTranscoder", "Unreadable audio input", e)
                throw AudioTranscoderException(
                    "This file couldn't be read as audio — it may be corrupt or empty.",
                    e
                )
            }
        }
    }

    // ---- WAV input: parsed directly, no decoder needed ----

    private fun transcodeWavInput(request: Request, format: String) {
        val inputStream = context.contentResolver.openInputStream(request.inputUri)
            ?: throw AudioTranscoderException("Could not open the input audio file.")
        val reader = try {
            WavPcmReader(inputStream)
        } catch (e: WavPcmReader.UnsupportedWavException) {
            runCatching { inputStream.close() }
            throw AudioTranscoderException(e.message ?: "Unsupported WAV file.")
        } catch (e: Throwable) {
            runCatching { inputStream.close() }
            throw e
        }
        val frameSize = reader.channels * 2
        val startByte = request.startUs?.let {
            (it * reader.sampleRate.toLong() * frameSize / 1_000_000L / frameSize) * frameSize
        } ?: 0L
        val endByte = request.endUs?.let {
            (it * reader.sampleRate.toLong() * frameSize / 1_000_000L / frameSize) * frameSize
        }
        // Progress is throttled to 2% steps so the worker is not
        // flooded with progress updates on every 64 KB chunk.
        var lastReported = -1f
        fun reportProgress(written: Long, total: Long) {
            if (total <= 0) return
            val fraction = (written.toFloat() / total).coerceIn(0f, 1f)
            if (fraction - lastReported >= 0.02f || fraction >= 1f) {
                lastReported = fraction
                request.onProgress?.invoke(fraction)
            }
        }
        fun checkContinue() {
            if (request.shouldContinue?.invoke() == false) {
                throw CancellationException("Audio conversion cancelled")
            }
        }
        try {
            if (startByte > 0) reader.skipConvertedBytes(startByte, request.shouldContinue)
            val effectiveTotal = endByte?.let { it - startByte }
                ?: reader.remainingPcmBytes()
            var totalWritten = 0L
            when (format) {
                "wav" -> {
                    WavWriter(request.outputFile, reader.sampleRate, reader.channels).apply {
                        try {
                            var written = 0L
                            while (true) {
                                checkContinue()
                                val remaining = endByte?.let { it - startByte - written }
                                if (remaining != null && remaining <= 0) break
                                val want = remaining?.let { minOf(it, CHUNK_BYTES.toLong()).toInt() }
                                    ?: CHUNK_BYTES
                                val chunk = reader.readConvertedChunk(want) ?: break
                                writePcm(chunk)
                                written += chunk.size
                                reportProgress(written, effectiveTotal)
                            }
                            totalWritten = written
                            reportProgress(effectiveTotal, effectiveTotal)
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
                            checkContinue()
                            val remaining = endByte?.let { it - startByte - written }
                            if (remaining != null && remaining <= 0) break
                            val want = remaining?.let { minOf(it, CHUNK_BYTES.toLong()).toInt() }
                                ?: CHUNK_BYTES
                            val chunk = reader.readConvertedChunk(want) ?: break
                            val ptsUs = written * 1_000_000L / (reader.sampleRate.toLong() * frameSize)
                            encoder.writePcm(chunk, ptsUs)
                            written += chunk.size
                            reportProgress(written, effectiveTotal)
                        }
                        totalWritten = written
                        reportProgress(effectiveTotal, effectiveTotal)
                    } finally {
                        encoder.finish()
                    }
                }
            }
            if (totalWritten == 0L) {
                throw AudioTranscoderException("Trim range contains no audio.")
            }
        } finally {
            reader.close()
        }
    }

    // ---- Codec input: MediaExtractor + MediaCodec decode ----

    private fun transcodeCodecInput(request: Request, format: String) {
        var stagedFile: File? = null
        val afd = try {
            context.contentResolver.openAssetFileDescriptor(request.inputUri, "r")
        } catch (_: Exception) {
            null
        }
        val extractor = MediaExtractor()
        try {
            if (afd != null && afd.declaredLength < 0 && afd.startOffset == 0L) {
                extractor.setDataSource(afd.fileDescriptor)
            } else if (afd != null && afd.declaredLength > 0) {
                extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.declaredLength)
            } else {
                val temp = File.createTempFile("audio_stage_", ".tmp", context.cacheDir)
                stagedFile = temp
                context.contentResolver.openInputStream(request.inputUri)?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                } ?: throw AudioTranscoderException("Could not open the input audio file.")
                extractor.setDataSource(temp.absolutePath)
            }
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
            val durationUs = runCatching {
                trackFormat.getLong(MediaFormat.KEY_DURATION)
            }.getOrNull()?.takeIf { it > 0 }
            when (format) {
                "wav" -> {
                    val writer = WavWriter(request.outputFile, sampleRate, channels)
                    try {
                        decodePcm(
                            extractor, audioTrack, mime, sampleRate, channels, request.startUs, request.endUs,
                            durationUs = durationUs,
                            onProgress = request.onProgress,
                            shouldContinue = request.shouldContinue
                        ) { bytes, _ ->
                            writer.writePcm(bytes)
                        }
                    } finally {
                        writer.close()
                    }
                }
                "m4a" -> {
                    val encoder = AacEncoder(sampleRate, channels, request.outputFile)
                    try {
                        decodePcm(
                            extractor, audioTrack, mime, sampleRate, channels, request.startUs, request.endUs,
                            durationUs = durationUs,
                            onProgress = request.onProgress,
                            shouldContinue = request.shouldContinue
                        ) { bytes, ptsUs ->
                            encoder.writePcm(bytes, ptsUs)
                        }
                    } finally {
                        encoder.finish()
                    }
                }
            }
        } finally {
            runCatching { afd?.close() }
            stagedFile?.delete()
            extractor.release()
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
        sampleRate: Int,
        channels: Int,
        startUs: Long?,
        endUs: Long?,
        durationUs: Long? = null,
        onProgress: ((Float) -> Unit)? = null,
        shouldContinue: (() -> Boolean)? = null,
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
            var outputBufferCount = 0
            var lastReported = -1f
            var framesKept = 0L
            // Most decoders emit 16-bit PCM, but some emit float;
            // writing float bytes into a 16-bit WAV would be noise.
            var outputIsFloat = false
            while (!outputEos) {
                if (shouldContinue?.invoke() == false) {
                    throw CancellationException("Audio conversion cancelled")
                }
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
                        outputBufferCount++
                        val outBuffer = decoder.getOutputBuffer(outIndex)
                        if (bufferInfo.size > 0 && outBuffer != null) {
                            val pts = bufferInfo.presentationTimeUs
                            val dup = outBuffer.duplicate()
                            dup.position(bufferInfo.offset)
                            dup.limit(bufferInfo.offset + bufferInfo.size)
                            var bytes = ByteArray(bufferInfo.size)
                            dup.get(bytes)
                            if (outputIsFloat) {
                                bytes = floatPcmTo16Bit(bytes)
                            }
                            val frameBytes = channels * 2
                            val totalFrames = bytes.size / frameBytes
                            val bufferDurationUs = if (sampleRate > 0) totalFrames * 1_000_000L / sampleRate else 0L

                            if (endUs != null && pts >= endUs) {
                                trimEnded = true
                            } else if (startUs != null && (pts + bufferDurationUs <= startUs)) {
                                // Entire buffer is before startUs; skip
                            } else {
                                val startFrame = if (startUs != null && pts < startUs && sampleRate > 0) {
                                    ((startUs - pts) * sampleRate / 1_000_000L).toInt().coerceIn(0, totalFrames)
                                } else 0

                                val endFrame = if (endUs != null && (pts + bufferDurationUs > endUs) && sampleRate > 0) {
                                    trimEnded = true
                                    (((endUs - pts) * sampleRate + 999_999L) / 1_000_000L).toInt().coerceIn(0, totalFrames)
                                } else totalFrames

                                if (startFrame < endFrame) {
                                    val startByteOffset = startFrame * frameBytes
                                    val endByteOffset = endFrame * frameBytes
                                    val slicedBytes = bytes.copyOfRange(startByteOffset, endByteOffset)
                                    val slicedPts = pts + (startFrame * 1_000_000L / sampleRate)

                                    if (firstKeptPts == null) firstKeptPts = slicedPts
                                    val firstKept = firstKeptPts!!
                                    val rebasedPts = slicedPts - firstKept
                                    onPcm(slicedBytes, rebasedPts)
                                    framesKept += (endFrame - startFrame)

                                    val totalUs = (endUs ?: durationUs)?.let { it - firstKept }
                                    if (onProgress != null && totalUs != null && totalUs > 0) {
                                        val fraction = (rebasedPts.toFloat() / totalUs).coerceIn(0f, 1f)
                                        if (fraction - lastReported >= 0.02f || fraction >= 1f) {
                                            lastReported = fraction
                                            onProgress(fraction)
                                        }
                                    }
                                }
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputEos = true
                        }
                    }
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        idlePasses++
                        if (idlePasses > 5000) {
                            throw AudioTranscoderException("Audio decoder timed out waiting for output.")
                        }
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputIsFloat = runCatching {
                            decoder.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }.getOrDefault(android.media.AudioFormat.ENCODING_PCM_16BIT) ==
                            android.media.AudioFormat.ENCODING_PCM_FLOAT
                    }
                }
            }
            if (framesKept == 0L) {
                throw AudioTranscoderException("Trim range contains no audio.")
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
        private val encoder: MediaCodec
        private val muxer: MediaMuxer
        private var trackIndex = -1
        private var muxerStarted = false
        private var finished = false

        init {
            var enc: MediaCodec? = null
            var mux: MediaMuxer? = null
            try {
                enc = MediaCodec.createEncoderByType("audio/mp4a-latm")
                mux = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val format = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, channels)
                format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                format.setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                enc.start()
                encoder = enc
                muxer = mux
            } catch (e: Exception) {
                runCatching { enc?.release() }
                runCatching { mux?.release() }
                throw AudioTranscoderException("Failed to initialize AAC encoder: ${e.message}", e)
            }
        }

        fun writePcm(bytes: ByteArray, ptsUs: Long) {
            var offset = 0
            var stalledFeeds = 0
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
            var finalizeError: Throwable? = null
            try {
                var eosQueued = false
                val queueDeadline = System.currentTimeMillis() + 3000L
                while (!eosQueued && System.currentTimeMillis() < queueDeadline) {
                    val inIndex = encoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        encoder.queueInputBuffer(
                            inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        eosQueued = true
                    } else {
                        drain(encoderEndOfStream = false)
                    }
                }
                if (!eosQueued) {
                    throw AudioTranscoderException("Timed out waiting to enqueue end-of-stream to audio encoder.")
                }
                drainUntilEos(timeoutMs = 5000L)
            } catch (t: Throwable) {
                finalizeError = t
            } finally {
                try {
                    if (muxerStarted) {
                        try {
                            muxer.stop()
                        } catch (e: Exception) {
                            if (finalizeError == null) {
                                finalizeError = AudioTranscoderException("Failed to stop media muxer: ${e.message}", e)
                            }
                        }
                    }
                } finally {
                    runCatching { muxer.release() }
                    try {
                        encoder.stop()
                    } catch (_: Exception) {}
                    runCatching { encoder.release() }
                }
            }
            if (finalizeError != null) throw finalizeError
        }

        private fun drainUntilEos(timeoutMs: Long) {
            val deadline = System.currentTimeMillis() + timeoutMs
            val bufferInfo = MediaCodec.BufferInfo()
            while (System.currentTimeMillis() < deadline) {
                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIndex >= 0 -> {
                        val outBuffer = encoder.getOutputBuffer(outIndex)
                        if (bufferInfo.size > 0 && outBuffer != null) {
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                if (trackIndex >= 0) {
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
                }
            }
            throw AudioTranscoderException("Timed out waiting for AAC encoder to drain.")
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
