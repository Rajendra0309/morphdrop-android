package com.morphdrop.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import com.morphdrop.app.ui.theme.MidnightBlue
import com.morphdrop.app.ui.theme.NeonEmerald
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquefiable

@Composable
fun GradientBackground(
    liquidState: LiquidState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightBlue)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NeonEmerald.copy(alpha = 0.15f),
                        androidx.compose.ui.graphics.Color.Transparent
                    ),
                    center = Offset(0f, 0f),
                    radius = 1000f
                )
            )
            // Removed .liquefiable(liquidState) to prevent recursive SIGSEGV crash
    ) {
        content()
    }
}
