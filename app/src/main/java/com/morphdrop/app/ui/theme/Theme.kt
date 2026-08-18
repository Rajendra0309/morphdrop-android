package com.morphdrop.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = NeonEmerald,
    onPrimary = MidnightBlue,
    primaryContainer = Color(0xFF00513B),
    onPrimaryContainer = Color(0xFF9CFBC1),
    secondary = CrimsonGlow,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF91002A),
    onSecondaryContainer = Color(0xFFFFD9DF),
    tertiary = AmberWarn,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF574500),
    onTertiaryContainer = Color(0xFFFFE18F),
    background = MidnightBlue,
    onBackground = Color(0xFFE2E2E6),
    surface = SurfaceContainerLow,
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = SurfaceContainerHighest,
    onSurfaceVariant = Color(0xFFC4C7C5),
    outline = Color(0xFF8E918F)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006A60),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF70F7E8),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF984061),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9DF),
    onSecondaryContainer = Color(0xFF3E001D),
    tertiary = Color(0xFF705D00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE18F),
    onTertiaryContainer = Color(0xFF221B00),
    background = PremiumOffWhite,
    onBackground = Color(0xFF191C1E),
    surface = Color.White,
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDBE4E1),
    onSurfaceVariant = Color(0xFF3F4947),
    outline = Color(0xFF6F7977)
)

@Composable
fun MorphDropTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Enabled by default for native M3 feel
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // HyperOS Bug Workaround: Extract colors natively if standard dynamic color is stale
    if (dynamicColor && (Build.MANUFACTURER.lowercase() == "xiaomi" || Build.MANUFACTURER.lowercase() == "poco")) {
        try {
            val wallpaperManager = android.app.WallpaperManager.getInstance(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                val colors = wallpaperManager.getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)
                colors?.primaryColor?.toArgb()?.let { argb ->
                    val primaryColor = Color(argb)
                    val secondaryColor = colors.secondaryColor?.toArgb()?.let { Color(it) } ?: primaryColor
                    val tertiaryColor = colors.tertiaryColor?.toArgb()?.let { Color(it) } ?: primaryColor
                    
                    colorScheme = if (darkTheme) {
                        colorScheme.copy(
                            primary = primaryColor,
                            primaryContainer = primaryColor.copy(alpha = 0.3f),
                            secondary = secondaryColor,
                            secondaryContainer = secondaryColor.copy(alpha = 0.3f),
                            tertiary = tertiaryColor,
                            tertiaryContainer = tertiaryColor.copy(alpha = 0.3f)
                        )
                    } else {
                        colorScheme.copy(
                            primary = primaryColor,
                            primaryContainer = primaryColor.copy(alpha = 0.2f),
                            secondary = secondaryColor,
                            secondaryContainer = secondaryColor.copy(alpha = 0.2f),
                            tertiary = tertiaryColor,
                            tertiaryContainer = tertiaryColor.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback gracefully
        }
    }

    val view = LocalView.current

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
