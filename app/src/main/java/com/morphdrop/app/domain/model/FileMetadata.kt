package com.morphdrop.app.domain.model

data class FileMetadata(
    // File System / General
    val fileName: String,
    val fileSize: Long,
    val fileSizeFormatted: String,
    val mimeType: String,
    val fileExtension: String,
    val fileType: FileType? = null,
    val dateModified: String? = null,
    val fileHash: String? = null,

    // Camera & EXIF Info
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val software: String? = null,
    val lensModel: String? = null,
    val focalLength: String? = null,
    val fNumber: String? = null,
    val isoSpeed: String? = null,
    val exposureTime: String? = null,
    val flash: String? = null,

    // Location Info
    val latitude: Double? = null,
    val longitude: Double? = null,
    val gpsAltitude: Double? = null,
    val locationAddress: String? = null,

    // Dates
    val dateCreated: String? = null,
    val dateOriginal: String? = null,
    val dateDigitized: String? = null,

    // Document / Media Info
    val author: String? = null,
    val title: String? = null,
    val subject: String? = null,
    val creator: String? = null,
    val producer: String? = null,
    val keywords: String? = null,
    val copyright: String? = null,

    // Video / Audio Info
    val duration: String? = null,
    val bitrate: String? = null,

    // Technical / Dimensions
    val width: Int? = null,
    val height: Int? = null,
    val dpi: String? = null,
    val colorSpace: String? = null,
    val pageCount: Int? = null,

    // Status Flags
    val hasMetadata: Boolean = false,
    val isScrubbable: Boolean = true,
    val isEditable: Boolean = true
)

data class MetadataEditParams(
    val author: String? = null,
    val title: String? = null,
    val subject: String? = null,
    val software: String? = null,
    val copyright: String? = null,
    val dateCreated: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null
)
