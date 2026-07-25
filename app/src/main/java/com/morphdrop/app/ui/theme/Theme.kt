package com.morphdrop.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.fletchmckee.liquid.rememberLiquidState

private val DarkColorScheme = darkColorScheme(
    primary = NeonEmerald,
    secondary = CrimsonGlow,
    tertiary = AmberWarn,
    background = MidnightBlue,
    surface = SurfaceContainerLow,
    outline = DarkCardBorder,
    outlineVariant = DarkCardBorder,
    onPrimary = Color.Black,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldDark,
    secondary = CrimsonDark,
    tertiary = AmberDark,
    background = PremiumOffWhite,
    surface = Color.White,
    outline = Color(0xFFCBD5E0),
    outlineVariant = Color(0xFFE2E8F0),
    onPrimary = Color.White,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    onSurfaceVariant = Color(0xFF2D3748) // Darker for better contrast with glass effects
)

@Composable
fun MorphDropTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    val liquidState = rememberLiquidState()

    CompositionLocalProvider(LocalLiquidState provides liquidState) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
