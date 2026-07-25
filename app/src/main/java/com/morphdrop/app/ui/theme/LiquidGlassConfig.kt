package com.morphdrop.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class GlassConfig(
    val frost: Dp,
    val refraction: Float,
    val curve: Float,
    val edge: Float,
    val tint: Color,
    val saturation: Float,
    val dispersion: Float,
    val contrast: Float = 1.0f
)

object LiquidGlassConfig {
    // Values from the screenshot
    val CardConfig = GlassConfig(
        frost = 4.dp,
        refraction = 0.37f,
        curve = 0.55f,
        edge = 0.07f,
        tint = Color.White.copy(alpha = 0.15f),
        saturation = 1.27f,
        dispersion = 0.18f,
        contrast = 1.00f
    )

    val NavBarConfig = GlassConfig(
        frost = 4.dp,
        refraction = 0.2f,
        curve = 0.3f,
        edge = 0f, // Use standard border modifier
        tint = Color.White.copy(alpha = 0.20f),
        saturation = 1.2f,
        dispersion = 0.15f,
        contrast = 1.00f
    )

    val SearchBarConfig = GlassConfig(
        frost = 4.dp,
        refraction = 0.3f,
        curve = 0.4f,
        edge = 0f, // Use OutlinedTextField border
        tint = Color.White.copy(alpha = 0.15f),
        saturation = 1.3f,
        dispersion = 0.2f,
        contrast = 1.00f
    )

    val DialogConfig = GlassConfig(
        frost = 12.dp,
        refraction = 0.6f,
        curve = 0.6f,
        edge = 0.12f,
        tint = Color.White.copy(alpha = 0.30f),
        saturation = 1.6f,
        dispersion = 0.3f
    )

    val ChipConfig = GlassConfig(
        frost = 6.dp,
        refraction = 0.3f,
        curve = 0.3f,
        edge = 0.05f,
        tint = Color.White.copy(alpha = 0.15f),
        saturation = 1.2f,
        dispersion = 0.15f
    )
}
