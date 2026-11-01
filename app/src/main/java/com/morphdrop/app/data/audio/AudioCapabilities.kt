package com.morphdrop.app.data.audio

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import javax.inject.Inject

/**
 * Probes what this device's codecs can actually do, instead of assuming
 * a fixed format matrix. Android's codec support varies by device and
 * by direction: a device that *decodes* MP3 very often cannot *encode*
 * it, and AAC encoding — the one M4A output needs — is not universal.
 *
 * The codec list is injectable so unit tests can feed fake lists; the
 * production constructor reads the real [MediaCodecList].
 */
data class CodecInfo(
    val name: String,
    val isEncoder: Boolean,
    val supportedTypes: List<String>
)

class AudioCapabilities @Inject constructor() {

    /** Overridden by unit tests; production reads the real codec list. */
    internal var codecInfosProvider: () -> List<CodecInfo> = { realCodecInfos() }

    constructor(provider: () -> List<CodecInfo>) : this() {
        codecInfosProvider = provider
    }

    fun hasDecoder(mime: String): Boolean =
        codecInfosProvider().any { !it.isEncoder && mime in it.supportedTypes }

    fun hasEncoder(mime: String): Boolean =
        codecInfosProvider().any { it.isEncoder && mime in it.supportedTypes }

    fun hasAacEncoder(): Boolean = hasEncoder("audio/mp4a-latm")

    private fun realCodecInfos(): List<CodecInfo> = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.map { info ->
            CodecInfo(
                name = info.name,
                isEncoder = info.isEncoder,
                supportedTypes = info.supportedTypes.toList()
            )
        }
    } catch (_: Exception) {
        // A codec list that cannot be read is treated as "nothing
        // supported" — callers then fail with a clear message instead
        // of crashing on an unknown device quirk.
        emptyList()
    }
}
