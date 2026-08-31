package com.morphdrop.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_annotations")
data class PdfAnnotationEntity(
    @PrimaryKey val id: String,
    val pdfUri: String,
    val pageIndex: Int,
    val type: String, // "HIGHLIGHT" or "DRAWING"
    val color: Int,
    val data: String // JSON string containing rects or path points
)
