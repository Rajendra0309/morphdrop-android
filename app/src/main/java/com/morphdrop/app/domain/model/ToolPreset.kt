package com.morphdrop.app.domain.model

import org.json.JSONObject

/**
 * Per-tool conversion preset: the settings the user last used with a given
 * tool (e.g. compress quality). Saved on every conversion start, restored
 * when the tool's config screen opens, and used for one-tap "repeat last
 * conversion" via long-press on the tool card.
 *
 * Only portable, non-sensitive settings are stored — never file URIs,
 * passwords, or workbench state.
 */
data class ToolPreset(
    val outputFormat: String = "",
    val quality: Int = 90,
    val resizeOption: String = "Original",
    val stripMetadata: Boolean = false,
    val pageRangeStart: String = "",
    val pageRangeEnd: String = "",
    val compressionPreset: String = "Balanced",
    val targetSizeKb: String = "",
    val rotationDegrees: Int = 0,
    val targetWidth: String = "",
    val targetHeight: String = "",
    val aspectRatioPreset: String = "Original",
    val allowPrinting: Boolean = true,
    val allowCopying: Boolean = true,
    val allowEditing: Boolean = true,
    val splitMode: String = "",
    val splitEveryN: String = ""
) {
    fun toJson(): String = JSONObject()
        .put("outputFormat", outputFormat)
        .put("quality", quality)
        .put("resizeOption", resizeOption)
        .put("stripMetadata", stripMetadata)
        .put("pageRangeStart", pageRangeStart)
        .put("pageRangeEnd", pageRangeEnd)
        .put("compressionPreset", compressionPreset)
        .put("targetSizeKb", targetSizeKb)
        .put("rotationDegrees", rotationDegrees)
        .put("targetWidth", targetWidth)
        .put("targetHeight", targetHeight)
        .put("aspectRatioPreset", aspectRatioPreset)
        .put("allowPrinting", allowPrinting)
        .put("allowCopying", allowCopying)
        .put("allowEditing", allowEditing)
        .put("splitMode", splitMode)
        .put("splitEveryN", splitEveryN)
        .toString()

    companion object {
        fun fromJson(json: String): ToolPreset? {
            return try {
                val o = JSONObject(json)
                ToolPreset(
                    outputFormat = o.optString("outputFormat", ""),
                    quality = o.optInt("quality", 90),
                    resizeOption = o.optString("resizeOption", "Original"),
                    stripMetadata = o.optBoolean("stripMetadata", false),
                    pageRangeStart = o.optString("pageRangeStart", ""),
                    pageRangeEnd = o.optString("pageRangeEnd", ""),
                    compressionPreset = o.optString("compressionPreset", "Balanced"),
                    targetSizeKb = o.optString("targetSizeKb", ""),
                    rotationDegrees = o.optInt("rotationDegrees", 0),
                    targetWidth = o.optString("targetWidth", ""),
                    targetHeight = o.optString("targetHeight", ""),
                    aspectRatioPreset = o.optString("aspectRatioPreset", "Original"),
                    allowPrinting = o.optBoolean("allowPrinting", true),
                    allowCopying = o.optBoolean("allowCopying", true),
                    allowEditing = o.optBoolean("allowEditing", true),
                    splitMode = o.optString("splitMode", ""),
                    splitEveryN = o.optString("splitEveryN", "")
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
