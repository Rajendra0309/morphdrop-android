package com.morphdrop.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun ColorWheelDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit
) {
    var selectedColor by remember { mutableStateOf(Color(initialColor)) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "Custom Color",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                ) {
                    val colors = remember {
                        listOf(
                            Color.Red, Color.Yellow, Color.Green,
                            Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                        )
                    }
                    
                    var offset by remember { mutableStateOf(Offset(100f, 100f)) } // Center initially
                    
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { tapOffset ->
                                    offset = tapOffset
                                    selectedColor = getColorFromOffset(tapOffset, size.width.toFloat())
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    offset = change.position
                                    selectedColor = getColorFromOffset(change.position, size.width.toFloat())
                                }
                            }
                    ) {
                        drawCircle(
                            brush = Brush.sweepGradient(colors, center = center),
                            radius = size.width / 2f
                        )
                        // Draw saturation overlay (white at center to transparent at edge)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White, Color.Transparent),
                                center = center,
                                radius = size.width / 2f
                            ),
                            radius = size.width / 2f
                        )
                        
                        // Draw selection indicator
                        val indicatorRadius = 10.dp.toPx()
                        val clampedOffset = clampOffsetToCircle(offset, center, size.width / 2f - indicatorRadius)
                        drawCircle(
                            color = Color.White,
                            radius = indicatorRadius,
                            center = clampedOffset,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawCircle(
                            color = Color.Black,
                            radius = indicatorRadius + 1.dp.toPx(),
                            center = clampedOffset,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }
                
                // Selected Color Preview
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(selectedColor, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    Text(
                        text = String.format("#%06X", (0xFFFFFF and selectedColor.toArgb())),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onColorSelected(selectedColor.toArgb()) }) {
                        Text("Select")
                    }
                }
            }
        }
    }
}

private fun getColorFromOffset(offset: Offset, size: Float): Color {
    val center = size / 2f
    val dx = offset.x - center
    val dy = offset.y - center
    val radius = hypot(dx.toDouble(), dy.toDouble()).toFloat()
    
    val maxRadius = size / 2f
    val saturation = (radius / maxRadius).coerceIn(0f, 1f)
    
    var hue = (atan2(dy.toDouble(), dx.toDouble()) * 180 / Math.PI).toFloat()
    if (hue < 0) hue += 360f
    
    val hsv = floatArrayOf(hue, saturation, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun clampOffsetToCircle(offset: Offset, center: Offset, maxRadius: Float): Offset {
    val dx = offset.x - center.x
    val dy = offset.y - center.y
    val radius = hypot(dx.toDouble(), dy.toDouble()).toFloat()
    
    if (radius <= maxRadius) return offset
    
    val ratio = maxRadius / radius
    return Offset(
        x = center.x + dx * ratio,
        y = center.y + dy * ratio
    )
}
