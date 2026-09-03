package com.morphdrop.app.domain.model

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import java.util.UUID

sealed class PdfAnnotation {
    abstract val id: String
    abstract val pageIndex: Int
    abstract val color: Color

    data class Highlight(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val color: Color,
        val boundingBoxes: List<Rect>
    ) : PdfAnnotation()

    data class Drawing(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val color: Color,
        val pathPoints: List<Point>, // Use a simple list of points for serialization
        val strokeWidth: Float = 3f
    ) : PdfAnnotation() {
        data class Point(val x: Float, val y: Float)
    }
}
