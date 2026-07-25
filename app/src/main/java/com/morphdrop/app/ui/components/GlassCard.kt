package com.morphdrop.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.morphdrop.app.ui.theme.GlassCardBackground
import com.morphdrop.app.ui.theme.GlassCardBorder
import com.morphdrop.app.ui.theme.GlassConfig
import com.morphdrop.app.ui.theme.LiquidGlassConfig
import com.morphdrop.app.ui.theme.MorphDropTheme
import com.morphdrop.app.ui.utils.rememberHapticHelper
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquid
import io.github.fletchmckee.liquid.rememberLiquidState

@Composable
fun GlassCard(
    liquidState: LiquidState,
    modifier: Modifier = Modifier,
    config: GlassConfig = LiquidGlassConfig.CardConfig,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = rememberHapticHelper()
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "GlassCardPressScale"
    )

    val shape = RoundedCornerShape(32.dp)

    val cardModifier = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.shape = shape
            clip = true
        }
        .clip(shape)
        .background(GlassCardBackground)
        .border(0.5.dp, GlassCardBorder, shape)
        .let {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                it.liquid(liquidState) {
                    frost = config.frost
                    refraction = config.refraction
                    curve = config.curve
                    edge = config.edge
                    tint = config.tint
                    saturation = config.saturation
                    dispersion = config.dispersion
                }
            } else {
                it
            }
        }

    val finalModifier = if (onClick != null) {
        cardModifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(color = Color.White.copy(alpha = 0.2f)),
            onClick = {
                haptic.click()
                onClick()
            }
        )
    } else {
        cardModifier
    }

    Box(modifier = finalModifier) {
        content()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF0F0F0)
@Composable
fun GlassCardLightPreview() {
    MorphDropTheme(darkTheme = false) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                liquidState = rememberLiquidState(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Glass Card Light", color = Color.Black)
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun GlassCardDarkPreview() {
    MorphDropTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                liquidState = rememberLiquidState(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Glass Card Dark", color = Color.White)
                }
            }
        }
    }
}
