package com.morphdrop.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import com.morphdrop.app.ui.theme.NeonEmerald

@Composable
fun EmptyStateAnimation(
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "emptyState")
    
    val floatAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -20f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )
    
    val rotationAnim by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotate"
    )

    val scaleAnim by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Background particles / bubbles
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(
                color = NeonEmerald.copy(alpha = 0.1f),
                radius = 60.dp.toPx() * scaleAnim,
                center = center
            )
            
            drawCircle(
                color = NeonEmerald.copy(alpha = 0.05f),
                radius = 100.dp.toPx() * (2f - scaleAnim),
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        val painter = rememberVectorPainter(image = icon)
        
        Canvas(
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer {
                    translationY = floatAnim
                    rotationZ = rotationAnim
                    scaleX = scaleAnim
                    scaleY = scaleAnim
                }
        ) {
            with(painter) {
                draw(
                    size = size,
                    colorFilter = ColorFilter.tint(NeonEmerald)
                )
            }
        }
    }
}
