package com.morphdrop.app.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    maxScale: Float = 5f,
    minScale: Float = 1f,
    content: @Composable () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        scale = if (scale > 1.1f) 1f else 2f
                        offset = if (scale == 1f) Offset.Zero else Offset.Zero // Centered zoom for simplicity
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val zoomIntensity = if (zoom > 1f) (zoom - 1f) * 1.8f + 1f else zoom
                    val newScale = (scale * zoomIntensity).coerceIn(minScale, maxScale)
                    
                    val extraWidth = (newScale - 1) * size.width
                    val extraHeight = (newScale - 1) * size.height
                    
                    val maxX = extraWidth / 2
                    val maxY = extraHeight / 2
                    
                    if (newScale > 1f) {
                        offset = Offset(
                            x = (offset.x + pan.x * newScale).coerceIn(-maxX, maxX),
                            y = (offset.y + pan.y * newScale).coerceIn(-maxY, maxY)
                        )
                    } else {
                        offset = Offset.Zero
                    }
                    scale = newScale
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
    ) {
        content()
    }
}
