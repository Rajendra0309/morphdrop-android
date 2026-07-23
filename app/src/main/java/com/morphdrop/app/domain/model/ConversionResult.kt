package com.morphdrop.app.domain.model

import android.net.Uri

data class ConversionResult(
    val success: Boolean,
    val outputFiles: List<Uri> = emptyList(),
    val outputFileNames: List<String> = emptyList(),
    val outputFileSizes: List<Long> = emptyList(),
    val errorMessage: String? = null
)
