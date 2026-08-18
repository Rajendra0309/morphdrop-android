package com.morphdrop.app.domain.model

import android.graphics.RectF

data class SearchMatch(
    val pageIndex: Int,
    val bounds: List<RectF>,
    val pageWidth: Float,
    val pageHeight: Float
)
