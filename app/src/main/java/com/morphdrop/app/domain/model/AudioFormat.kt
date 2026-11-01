package com.morphdrop.app.domain.model

/**
 * Audio conversion rules, kept as pure logic so the matrix can be unit
 * tested without a device.
 *
 * Honest limits, baked into the code rather than the marketing copy:
 * - Any format Android can *decode* is a valid input.
 * - Outputs are only WAV and M4A. Android has no MP3 encoder, so MP3
 *   is an input, never an output — offering it would need a native
 *   encoder library (LAME/ffmpeg) that the APK size budget forbids.
 * - M4A output additionally needs an AAC encoder on the device; it is
 *   probed at runtime and reported clearly when missing.
 */
class AudioUnsupportedException(message: String) : Exception(message)

object AudioMatrix {

    /** Extensions the converter accepts as input. */
    val INPUT_EXTENSIONS = listOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac")

    /** Video containers accepted by Video to Audio. */
    val VIDEO_EXTENSIONS = listOf("mp4", "mkv", "mov", "webm", "3gp")

    /** Output extensions offered on the converter screen, before probing. */
    val OUTPUT_EXTENSIONS = listOf("wav", "m4a")

    fun isSupportedInput(extension: String): Boolean =
        extension.lowercase() in INPUT_EXTENSIONS

    fun validateInput(extension: String) {
        if (!isSupportedInput(extension)) {
            throw AudioUnsupportedException(
                "“.$extension” is not a supported audio input. Supported inputs: " +
                    INPUT_EXTENSIONS.joinToString(", ") { ".$it" } + "."
            )
        }
    }

    /**
     * Outputs actually offered on this device. WAV is hand-written PCM,
     * so it is always available; M4A needs an AAC encoder.
     */
    fun offeredOutputs(hasAacEncoder: Boolean): List<String> =
        if (hasAacEncoder) OUTPUT_EXTENSIONS else listOf("wav")

    fun validateOutput(outputFormat: String, hasAacEncoder: Boolean) {
        when (outputFormat.lowercase()) {
            "wav" -> Unit // Always available: PCM is written by hand.
            "m4a" -> if (!hasAacEncoder) {
                throw AudioUnsupportedException(
                    "This device cannot encode AAC audio, so M4A output is not " +
                        "available here. WAV output still works."
                )
            }
            "mp3" -> throw AudioUnsupportedException(
                "MP3 output is not available: Android has no MP3 encoder. " +
                    "Convert to M4A or WAV instead."
            )
            else -> throw AudioUnsupportedException(
                "“.$outputFormat” is not a supported audio output. " +
                    "Supported outputs: WAV, M4A."
            )
        }
    }

    /** Formats a millisecond duration as M:SS for the trim screen. */
    fun formatDuration(durationMs: Long): String {
        if (durationMs < 0) return "0:00"
        val totalSeconds = durationMs / 1000
        return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
    }

    /**
     * Normalizes a trim range to (startMs, endMs). A null or zero end
     * means "to the end of the file". Throws [IllegalArgumentException]
     * with a user-readable message for anything unusable.
     */
    fun validateTrimRange(startMs: Long, endMs: Long?, durationMs: Long): Pair<Long, Long> {
        if (startMs < 0) {
            throw IllegalArgumentException("Trim start cannot be negative.")
        }
        val end = when {
            endMs == null || endMs == 0L -> durationMs
            else -> endMs
        }
        if (durationMs > 0 && startMs >= durationMs) {
            throw IllegalArgumentException(
                "Trim start (${formatDuration(startMs)}) is past the end of " +
                    "this audio (${formatDuration(durationMs)})."
            )
        }
        if (end <= startMs) {
            throw IllegalArgumentException("Trim end must be after trim start.")
        }
        if (durationMs > 0 && end > durationMs) {
            throw IllegalArgumentException(
                "Trim end (${formatDuration(end)}) is past the end of " +
                    "this audio (${formatDuration(durationMs)})."
            )
        }
        return startMs to end
    }
}
