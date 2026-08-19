package com.morphdrop.app.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    onTap: (() -> Unit)? = null,
    onScaleChange: ((Float) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(scale) {
        onScaleChange?.invoke(scale)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2f
                            val extraWidth = (2f - 1) * size.width
                            val extraHeight = (2f - 1) * size.height
                            val maxX = extraWidth / 2
                            val maxY = extraHeight / 2
                            
                            val targetX = (size.width / 2 - tapOffset.x) * 2f
                            val targetY = (size.height / 2 - tapOffset.y) * 2f
                            
                            offset = Offset(
                                targetX.coerceIn(-maxX, maxX),
                                targetY.coerceIn(-maxY, maxY)
                            )
                        }
                    },
                    onTap = {
                        onTap?.invoke()
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

