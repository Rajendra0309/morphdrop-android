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
    ),
    CHINESE(
        displayName = "Chinese (Simplified / Traditional)",
        description = "Supports Chinese script text recognition"
    ),
    JAPANESE(
        displayName = "Japanese",
        description = "Supports Kanji, Hiragana, and Katakana"
    ),
    KOREAN(
        displayName = "Korean",
        description = "Supports Hangul script text recognition"
    )
}
