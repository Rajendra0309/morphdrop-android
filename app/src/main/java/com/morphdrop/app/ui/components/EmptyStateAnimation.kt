package com.morphdrop.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp

@Composable
fun EmptyStateAnimation(
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Soft background glow disc (no expanding ripples)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(
                color = primaryColor.copy(alpha = 0.08f),
                radius = 56.dp.toPx(),
                center = center
            )
        }

        val painter = rememberVectorPainter(image = icon)
        
        Canvas(
            modifier = Modifier.size(72.dp)
        ) {
            with(painter) {
                draw(
                    size = size,
                    colorFilter = ColorFilter.tint(primaryColor)
                )
            }
        }
    }
}
