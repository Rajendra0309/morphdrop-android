package com.morphdrop.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Dashed rounded-rectangle border, drawn behind the content. Used by
 * drop-zone cards across Home and Conversion Config.
 *
 * Implemented directly on DrawScope (which is itself a Density), so no
 * `composed` indirection is needed.
 */
fun Modifier.dashedBorder(strokeWidth: Dp, color: Color, cornerRadius: Dp): Modifier =
    this.drawBehind {
        val strokePx = strokeWidth.toPx()
        val radiusPx = cornerRadius.toPx()
        val dashOn = 12.dp.toPx()
        val dashOff = 8.dp.toPx()
        drawRoundRect(
            color = color,
            style = Stroke(
                width = strokePx,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashOn, dashOff), 0f)
            ),
            cornerRadius = CornerRadius(radiusPx, radiusPx)
        )
    }
