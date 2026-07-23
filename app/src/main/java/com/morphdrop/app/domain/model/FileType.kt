package com.morphdrop.app.domain.model

import androidx.compose.ui.graphics.Color
import com.morphdrop.app.ui.theme.ColorExcel
import com.morphdrop.app.ui.theme.ColorImage
import com.morphdrop.app.ui.theme.ColorPDF
import com.morphdrop.app.ui.theme.ColorPPT
import com.morphdrop.app.ui.theme.ColorWord

enum class FileType(
    val displayName: String,
    val extension: String,
    val color: Color
) {
    PDF("PDF", "pdf", ColorPDF),
    DOCX("DOCX", "docx", ColorWord),
    XLSX("XLSX", "xlsx", ColorExcel),
    PPTX("PPTX", "pptx", ColorPPT),
    PNG("PNG", "png", ColorImage),
    JPG("JPG", "jpg", Color(0xFFE91E63)),
    WEBP("WEBP", "webp", Color(0xFF00BCD4)),
    BMP("BMP", "bmp", Color(0xFF673AB7)),
    TXT("TXT", "txt", Color(0xFF607D8B))
}
