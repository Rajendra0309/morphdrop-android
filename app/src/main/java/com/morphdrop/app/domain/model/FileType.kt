package com.morphdrop.app.domain.model

import androidx.compose.ui.graphics.Color


enum class FileType(
    val displayName: String,
    val extension: String,
    val color: Color
) {
    PDF("PDF", "pdf", Color(0xFFE53935)), // Red
    DOCX("DOCX", "docx", Color(0xFF1E88E5)), // Blue
    XLSX("XLSX", "xlsx", Color(0xFF43A047)), // Green
    PPTX("PPTX", "pptx", Color(0xFFFF8F00)), // Orange
    PNG("PNG", "png", Color(0xFF8E24AA)), // Purple
    JPG("JPG", "jpg", Color(0xFFE91E63)),
    WEBP("WEBP", "webp", Color(0xFF00BCD4)),
    BMP("BMP", "bmp", Color(0xFF673AB7)),
    TXT("TXT", "txt", Color(0xFF607D8B)),
    MD("MD", "md", Color(0xFF00E676))
}
