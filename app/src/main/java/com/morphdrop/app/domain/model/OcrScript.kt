package com.morphdrop.app.domain.model

enum class OcrScript(
    val displayName: String,
    val description: String
) {
    LATIN(
        displayName = "Latin (English / European)",
        description = "Default model for English, Spanish, French, German, etc."
    ),
    DEVANAGARI(
        displayName = "Devanagari (Hindi, Marathi, Nepali)",
        description = "Supports Hindi, Marathi, Sanskrit, Nepali, Konkani, etc."
    );

    // Note: South Indian languages (Kannada, Tamil, Telugu, Malayalam) require Tesseract OCR — planned for future update
}
