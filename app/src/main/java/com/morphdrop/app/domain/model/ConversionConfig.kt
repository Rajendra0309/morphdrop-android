package com.morphdrop.app.domain.model

import android.net.Uri

data class ConversionConfig(
    val inputUri: Uri,
    val conversionType: ConversionType,
    val outputFormat: String,
    val quality: Int = 100,
    val pageRange: IntRange? = null,
    val outputDirectory: Uri? = null
)
