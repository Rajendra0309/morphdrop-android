package com.morphdrop.app.domain.usecase.conversion

import android.content.Context
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
 * Converts an audio file to WAV or M4A, fully offline, via the
 * platform codecs. The output is streamed through a cache file into
 * Downloads, so a long recording never sits in memory as bytes.
 *
 * Deliberate limits (also stated on the config screen): MP3 is an
 * input only — Android has no MP3 encoder — and M4A needs an AAC
 * encoder on the device, probed at runtime.
 */
class AudioConverterUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val transcoder: AudioTranscoder,
    private val capabilities: AudioCapabilities
) {
    suspend operator fun invoke(
        inputUri: Uri,
        outputFormat: String,
        outputFileName: String = "converted_audio_${System.currentTimeMillis()}",
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

        val tmp = File(context.cacheDir, "audio_convert_${System.currentTimeMillis()}.tmp")
        try {
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
            val baseName = outputFileName.substringBeforeLast(".", outputFileName)
            FileHelper.saveStreamToFile(context, settingsRepository, "$baseName.$format", tmp)
        } finally {
            tmp.delete()
        }
    }
}
