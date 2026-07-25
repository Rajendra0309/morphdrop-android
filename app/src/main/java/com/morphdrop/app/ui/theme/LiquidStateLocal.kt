package com.morphdrop.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import io.github.fletchmckee.liquid.LiquidState

val LocalLiquidState = compositionLocalOf<LiquidState> {
    error("No LiquidState provided in MorphDropTheme")
}
