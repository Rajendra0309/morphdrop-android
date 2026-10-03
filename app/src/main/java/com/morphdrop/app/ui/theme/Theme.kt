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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val EmberLightColorScheme = lightColorScheme(
    primary = EmberPrimaryLight,
    onPrimary = EmberOnPrimaryLight,
    primaryContainer = EmberPrimaryContainerLight,
    onPrimaryContainer = EmberOnPrimaryContainerLight,
    secondary = EmberSecondaryLight,
    onSecondary = EmberOnSecondaryLight,
    secondaryContainer = EmberSecondaryContainerLight,
    onSecondaryContainer = EmberOnSecondaryContainerLight,
    tertiary = EmberTertiaryLight,
    onTertiary = EmberOnTertiaryLight,
    tertiaryContainer = EmberTertiaryContainerLight,
    onTertiaryContainer = EmberOnTertiaryContainerLight,
    background = EmberBackgroundLight,
    onBackground = EmberOnBackgroundLight,
    surface = EmberSurfaceLight,
    onSurface = EmberOnSurfaceLight,
    surfaceVariant = EmberSurfaceVariantLight,
    onSurfaceVariant = EmberOnSurfaceVariantLight,
    surfaceContainerLowest = EmberSurfaceLowestLight,
    surfaceContainerLow = EmberSurfaceLowLight,
    surfaceContainer = EmberSurfaceContainerLight,
    surfaceContainerHigh = EmberSurfaceHighLight,
    surfaceContainerHighest = EmberSurfaceHighestLight,
    outline = EmberOutlineLight,
    outlineVariant = EmberOutlineVariantLight,
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = EmberInverseSurfaceLight,
    inverseOnSurface = EmberInverseOnSurfaceLight,
    inversePrimary = EmberInversePrimaryLight,
    scrim = Color(0xFF000000)
)

private val EmberDarkColorScheme = darkColorScheme(
    primary = EmberPrimaryDark,
    onPrimary = EmberOnPrimaryDark,
    primaryContainer = EmberPrimaryContainerDark,
    onPrimaryContainer = EmberOnPrimaryContainerDark,
    secondary = EmberSecondaryDark,
    onSecondary = EmberOnSecondaryDark,
    secondaryContainer = EmberSecondaryContainerDark,
    onSecondaryContainer = EmberOnSecondaryContainerDark,
    tertiary = EmberTertiaryDark,
    onTertiary = EmberOnTertiaryDark,
    tertiaryContainer = EmberTertiaryContainerDark,
    onTertiaryContainer = EmberOnTertiaryContainerDark,
    background = EmberBackgroundDark,
    onBackground = EmberOnBackgroundDark,
    surface = EmberSurfaceDark,
    onSurface = EmberOnSurfaceDark,
    surfaceVariant = EmberSurfaceVariantDark,
    onSurfaceVariant = EmberOnSurfaceVariantDark,
    surfaceContainerLowest = EmberSurfaceLowestDark,
    surfaceContainerLow = EmberSurfaceLowDark,
    surfaceContainer = EmberSurfaceContainerDark,
    surfaceContainerHigh = EmberSurfaceHighDark,
    surfaceContainerHighest = EmberSurfaceHighestDark,
    outline = EmberOutlineDark,
    outlineVariant = EmberOutlineVariantDark,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = EmberInverseSurfaceDark,
    inverseOnSurface = EmberInverseOnSurfaceDark,
    inversePrimary = EmberInversePrimaryDark,
    scrim = Color(0xFF000000)
)

@Composable
fun MorphDropTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is OFF by default: MorphDrop keeps its Ember identity on
    // every device. Users can opt in via the "Match wallpaper" setting, which
    // passes dynamicColor = true.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> EmberDarkColorScheme
        else -> EmberLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            var ctx = view.context
            while (ctx is android.content.ContextWrapper) {
                if (ctx is Activity) break
                ctx = ctx.baseContext
            }
            if (ctx is Activity) {
                val window = ctx.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalMorphColors provides if (darkTheme) darkMorphColors else lightMorphColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
