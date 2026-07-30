package com.morphdrop.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.hypot

object ThemeAnimationManager {
    var revealCenter by mutableStateOf(Offset.Zero)
}

@Composable
fun CircularThemeRevealContainer(
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var previousDarkState by remember { mutableStateOf(isDarkMode) }
    var animating by remember { mutableStateOf(false) }
    var isExpandingDark by remember { mutableStateOf(false) }
    
    // Theme colors
    val lightBg = Color(0xFFFBFCFF)
    val darkBg = Color(0xFF0F1115)
    
    // Track the actual visible colors to prevent flashing
    var revealBgColor by remember { mutableStateOf(if (isDarkMode) darkBg else lightBg) }
    var baseBgColor by remember { mutableStateOf(if (isDarkMode) darkBg else lightBg) }

    val animProgress by animateFloatAsState(
        targetValue = if (animating) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        finishedListener = {
            animating = false
            previousDarkState = isDarkMode
            baseBgColor = if (isDarkMode) darkBg else lightBg
        },
        label = "circularThemeReveal"
    )

    LaunchedEffect(isDarkMode) {
        if (isDarkMode != previousDarkState) {
            isExpandingDark = isDarkMode
            revealBgColor = if (isDarkMode) darkBg else lightBg
            animating = true
        }
    }

    Box(
        modifier = modifier
            .background(baseBgColor) // Maintain old color as base during animation
            .drawWithCache {
                onDrawWithContent {
                    drawContent()
                    if (animating) {
                        val centerOffset = if (ThemeAnimationManager.revealCenter == Offset.Zero) {
                            Offset(size.width * 0.85f, size.height * 0.15f)
                        } else {
                            ThemeAnimationManager.revealCenter
                        }

                        val maxRadius = hypot(
                            maxOf(centerOffset.x, size.width - centerOffset.x).toDouble(),
                            maxOf(centerOffset.y, size.height - centerOffset.y).toDouble()
                        ).toFloat() * 1.3f

                        if (isExpandingDark) {
                            // Reveal dark over light
                            val radius = maxRadius * animProgress
                            drawCircle(
                                color = revealBgColor,
                                radius = radius,
                                center = centerOffset
                            )
                        } else {
                            // Reveal light over dark (shrinking circle or expanding light)
                            // We expand a light circle over the dark base
                            val radius = maxRadius * animProgress
                            drawCircle(
                                color = revealBgColor,
                                radius = radius,
                                center = centerOffset
                            )
                        }
                    }
                }
            }
    ) {
        content()
    }
}
