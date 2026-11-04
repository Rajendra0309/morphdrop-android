package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
import android.media.MediaMetadataRetriever
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
import javax.inject.Inject

/**
 * Cuts a segment out of an audio file, fully offline. The segment is
 * re-encoded to the chosen output format (WAV or M4A) — trimming does
 * not keep the original container, because Android has no MP3 encoder
 * and frame-accurate MP3 cutting would need one.
 *
 * Cuts land on decoded-frame boundaries (see [AudioTranscoder]): a
 * cut lands on the nearest frame at or after the requested start.
 * When the file's duration cannot be probed, the range is still
 * honored (start must be non-negative, end must be after start) and
 * a missing end means "to the end of the file".
 */
class AudioTrimmerUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val transcoder: AudioTranscoder,
    private val capabilities: AudioCapabilities
) {
    suspend operator fun invoke(
        inputUri: Uri,
        outputFormat: String,
        startMs: Long,
        endMs: Long?,
        outputFileName: String = "trimmed_audio_${System.currentTimeMillis()}",
        onProgress: ((Float) -> Unit)? = null,
        shouldContinue: (() -> Boolean)? = null
    ): Uri = withContext(Dispatchers.IO) {
        val inputName = FileHelper.getFileName(context, inputUri)
        val inputExt = inputName.substringAfterLast('.', "").lowercase()
        if (inputExt.isBlank()) {
            throw IllegalArgumentException("Could not determine the input audio format.")
        }
        AudioMatrix.validateInput(inputExt)
        val format = outputFormat.lowercase()
        AudioMatrix.validateOutput(format, capabilities.hasAacEncoder())

        val durationMs = probeDurationMs(inputUri)
        val (start, end) = if (durationMs != null && durationMs > 0) {
            AudioMatrix.validateTrimRange(startMs, endMs, durationMs)
        } else {
            if (startMs < 0) {
                throw IllegalArgumentException("Trim start cannot be negative.")
            }
            if (endMs != null && endMs != 0L && endMs <= startMs) {
                throw IllegalArgumentException("Trim end must be after trim start.")
            }
            startMs to (endMs?.takeIf { it != 0L })
        }

        val tmp = File(context.cacheDir, "audio_trim_${System.currentTimeMillis()}.tmp")
        try {
            transcoder.transcode(
                AudioTranscoder.Request(
                    inputUri = inputUri,
                    inputExtension = inputExt,
                    outputFile = tmp,
                    outputFormat = format,
                    startUs = start * 1000,
                    endUs = end?.let { it * 1000 },
                    onProgress = onProgress,
                    shouldContinue = shouldContinue
                )
            )
            val baseName = outputFileName.substringBeforeLast(".", outputFileName)
            FileHelper.saveStreamToFile(context, settingsRepository, "$baseName.$format", tmp)
        } finally {
            tmp.delete()
        }
    }

    private fun probeDurationMs(uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }
}
