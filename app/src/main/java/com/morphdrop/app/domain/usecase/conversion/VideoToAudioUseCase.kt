package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.morphdrop.app.data.audio.AudioCapabilities
import com.morphdrop.app.data.audio.AudioTranscoder
import com.morphdrop.app.domain.model.AudioMatrix
import com.morphdrop.app.domain.repository.SettingsRepository
import com.morphdrop.app.util.FileHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject

/**
 * Extracts the audio track from a video file, fully offline.
 *
 * Fast path: when the video's audio track is already AAC and the
 * requested output is M4A, samples are copied straight into a new
 * MP4 container — no decode, no re-encode, no quality loss. Every
 * other combination falls back to the shared [AudioTranscoder]
 * (decode the audio track, encode to WAV or M4A).
 */
class VideoToAudioUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val transcoder: AudioTranscoder,
    private val capabilities: AudioCapabilities
) {
    suspend operator fun invoke(
        inputUri: Uri,
        outputFormat: String,
        outputFileName: String = "extracted_audio_${System.currentTimeMillis()}",
        onProgress: ((Float) -> Unit)? = null,
        shouldContinue: (() -> Boolean)? = null
    ): Uri = withContext(Dispatchers.IO) {
        val inputName = FileHelper.getFileName(context, inputUri)
        val inputExt = inputName.substringAfterLast('.', "").lowercase()
        if (inputExt !in AudioMatrix.VIDEO_EXTENSIONS) {
            throw IllegalArgumentException(
                "“.$inputExt” is not a supported video input. Supported inputs: " +
                    AudioMatrix.VIDEO_EXTENSIONS.joinToString(", ") { ".$it" } + "."
            )
        }
        val format = outputFormat.lowercase()
        AudioMatrix.validateOutput(format, capabilities.hasAacEncoder())

        val tmp = File.createTempFile("video_audio_", ".tmp", context.cacheDir)
        try {
            val copied = format == "m4a" && tryCopyAacTrack(inputUri, tmp, shouldContinue)
            if (!copied) {
                transcoder.transcode(
                    AudioTranscoder.Request(
                        inputUri = inputUri,
                        inputExtension = inputExt,
                        outputFile = tmp,
                        outputFormat = format,
                        onProgress = onProgress,
                        shouldContinue = shouldContinue
                    )
                )
            }
            val baseName = outputFileName.substringBeforeLast(".", outputFileName)
            FileHelper.saveStreamToFile(context, settingsRepository, "$baseName.$format", tmp)
        } finally {
            tmp.delete()
        }
    }

    /**
     * Copies an AAC audio track into an MP4 container without decoding.
     * Returns false (leaving [outputFile] for the caller to overwrite)
     * when the video has no AAC track or the copy fails.
     */
    private fun tryCopyAacTrack(inputUri: Uri, outputFile: File, shouldContinue: (() -> Boolean)? = null): Boolean {
        var stagedFile: File? = null
        val afd = try {
            context.contentResolver.openAssetFileDescriptor(inputUri, "r")
        } catch (_: Exception) {
            null
        }
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            if (afd != null && AudioTranscoder.isSeekable(afd.fileDescriptor)) {
                if (afd.declaredLength < 0 && afd.startOffset == 0L) {
                    extractor.setDataSource(afd.fileDescriptor)
                } else if (afd.declaredLength > 0) {
                    extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.declaredLength)
                } else {
                    extractor.setDataSource(afd.fileDescriptor)
                }
            } else {
                val temp = File.createTempFile("video_stage_", ".tmp", context.cacheDir)
                stagedFile = temp
                context.contentResolver.openInputStream(inputUri)?.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            if (shouldContinue?.invoke() == false) {
                                throw kotlinx.coroutines.CancellationException("Video to audio cancelled")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: return false
                extractor.setDataSource(temp.absolutePath)
            }
            val trackInfo = AudioTranscoder.selectFirstAudioTrack(extractor) ?: return false
            val (audioTrack, trackFormat) = trackInfo
            if (trackFormat.getString(MediaFormat.KEY_MIME) != "audio/mp4a-latm") return false
            extractor.selectTrack(audioTrack)
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outTrack = muxer.addTrack(trackFormat)
            muxer.start()
            muxerStarted = true
            val buffer = ByteBuffer.allocate(256 * 1024)
            val bufferInfo = android.media.MediaCodec.BufferInfo()
            while (true) {
                if (shouldContinue?.invoke() == false) {
                    throw kotlinx.coroutines.CancellationException("Video to audio cancelled")
                }
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                bufferInfo.set(
                    0, sampleSize, extractor.sampleTime, extractor.sampleFlags
                )
                muxer.writeSampleData(outTrack, buffer, bufferInfo)
                extractor.advance()
            }
            if (shouldContinue?.invoke() == false) {
                throw kotlinx.coroutines.CancellationException("Video to audio cancelled")
            }
            muxer.stop()
            muxerStarted = false
            return true
        } catch (e: kotlinx.coroutines.CancellationException) {
            outputFile.delete()
            throw e
        } catch (_: Exception) {
            outputFile.delete()
            return false
        } finally {
            if (muxerStarted) {
                runCatching { muxer?.stop() }
            }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
            runCatching { afd?.close() }
            stagedFile?.delete()
        }
    }
}
