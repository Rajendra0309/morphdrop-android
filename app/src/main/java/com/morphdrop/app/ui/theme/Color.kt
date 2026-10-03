package com.morphdrop.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.morphdrop.app.domain.model.FileType

// ---------------------------------------------------------------------------
// Ember brand palette — the single source of truth for MorphDrop's identity.
// One brand, both themes: warm paper + burnt ember (light), deep warm
// charcoal + amber (dark). No wallpaper tints, no neon, no gradients.
// ---------------------------------------------------------------------------

// Light theme — warm paper, ink, burnt ember
val EmberBackgroundLight = Color(0xFFFAF6F0)
val EmberOnBackgroundLight = Color(0xFF1B1A17)
val EmberSurfaceLight = Color(0xFFFAF6F0)
val EmberOnSurfaceLight = Color(0xFF1B1A17)
val EmberSurfaceVariantLight = Color(0xFFEAE0D2)
val EmberOnSurfaceVariantLight = Color(0xFF6B5E4F)
val EmberSurfaceLowestLight = Color(0xFFFFFFFF)
val EmberSurfaceLowLight = Color(0xFFF4EEE4)
val EmberSurfaceContainerLight = Color(0xFFEDE4D5)
val EmberSurfaceHighLight = Color(0xFFE6D9C4)
val EmberSurfaceHighestLight = Color(0xFFDFCFB6)
val EmberPrimaryLight = Color(0xFFC2410C) // burnt ember
val EmberOnPrimaryLight = Color(0xFFFFFFFF)
val EmberPrimaryContainerLight = Color(0xFFFFDCC8)
val EmberOnPrimaryContainerLight = Color(0xFF5F2708)
val EmberSecondaryLight = Color(0xFF7D5A3C) // warm umber
val EmberOnSecondaryLight = Color(0xFFFFFFFF)
val EmberSecondaryContainerLight = Color(0xFFF0DCC4)
val EmberOnSecondaryContainerLight = Color(0xFF3E2A16)
val EmberTertiaryLight = Color(0xFF9A6B1A) // golden
val EmberOnTertiaryLight = Color(0xFFFFFFFF)
val EmberTertiaryContainerLight = Color(0xFFF5E2B8)
val EmberOnTertiaryContainerLight = Color(0xFF3A2A05)
val EmberOutlineLight = Color(0xFF857667)
val EmberOutlineVariantLight = Color(0xFFD9CCB8)
val EmberInverseSurfaceLight = Color(0xFF2F2A23)
val EmberInverseOnSurfaceLight = Color(0xFFF6F0E5)
val EmberInversePrimaryLight = Color(0xFFE8A33D)

// Dark theme — deep warm charcoal, warm amber
val EmberBackgroundDark = Color(0xFF14120F)
val EmberOnBackgroundDark = Color(0xFFF1EAE0)
val EmberSurfaceDark = Color(0xFF14120F)
val EmberOnSurfaceDark = Color(0xFFF1EAE0)
val EmberSurfaceVariantDark = Color(0xFF3B342A)
val EmberOnSurfaceVariantDark = Color(0xFFCFC2B0)
val EmberSurfaceLowestDark = Color(0xFF0D0B09)
val EmberSurfaceLowDark = Color(0xFF1A1611)
val EmberSurfaceContainerDark = Color(0xFF201B15)
val EmberSurfaceHighDark = Color(0xFF292219)
val EmberSurfaceHighestDark = Color(0xFF332B1F)
val EmberPrimaryDark = Color(0xFFE8A33D) // warm amber
val EmberOnPrimaryDark = Color(0xFF2E1D05)
val EmberPrimaryContainerDark = Color(0xFF5C3A10)
val EmberOnPrimaryContainerDark = Color(0xFFFFDFAC)
val EmberSecondaryDark = Color(0xFFC9A06E)
val EmberOnSecondaryDark = Color(0xFF2E2010)
val EmberSecondaryContainerDark = Color(0xFF4A3826)
val EmberOnSecondaryContainerDark = Color(0xFFF0DCC4)
val EmberTertiaryDark = Color(0xFFD9B25F)
val EmberOnTertiaryDark = Color(0xFF2A2005)
val EmberTertiaryContainerDark = Color(0xFF54400F)
val EmberOnTertiaryContainerDark = Color(0xFFF3DFA8)
val EmberOutlineDark = Color(0xFF8F8171)
val EmberOutlineVariantDark = Color(0xFF4B4335)
val EmberInverseSurfaceDark = Color(0xFFF1EAE0)
val EmberInverseOnSurfaceDark = Color(0xFF322C24)
val EmberInversePrimaryDark = Color(0xFFC2410C)

// ---------------------------------------------------------------------------
// Semantic roles beyond the M3 slots (success / warning), theme-aware via
// a CompositionLocal. Consume with MaterialTheme.morphColors.
// ---------------------------------------------------------------------------
data class MorphColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

val lightMorphColors = MorphColors(
    success = Color(0xFF2E7D32),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFCCE8CC),
    onSuccessContainer = Color(0xFF1B3A1D),
    warning = Color(0xFFB26A00),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFE1B3),
    onWarningContainer = Color(0xFF3A2700),
)

val darkMorphColors = MorphColors(
    success = Color(0xFF7BC47F),
    onSuccess = Color(0xFF0A2A0D),
    successContainer = Color(0xFF1E4A24),
    onSuccessContainer = Color(0xFFC9E8CB),
    warning = Color(0xFFE8A33D),
    onWarning = Color(0xFF2E1D05),
    warningContainer = Color(0xFF5C3A10),
    onWarningContainer = Color(0xFFFFDFAC),
)

val LocalMorphColors = staticCompositionLocalOf { lightMorphColors }

val MaterialTheme.morphColors: MorphColors
    @Composable
    @ReadOnlyComposable
    get() = LocalMorphColors.current

// ---------------------------------------------------------------------------
// Per-format chip colors: (container, content) derived from the FileType
// accent. Container at low alpha over the surface, content darkened/lightened
// for contrast. Never raw accent as body text.
// ---------------------------------------------------------------------------
fun formatChipColors(fileType: FileType, darkTheme: Boolean): Pair<Color, Color> {
    val accent = fileType.color
    val content = if (darkTheme) {
        Color(
            red = accent.red + (1f - accent.red) * 0.35f,
            green = accent.green + (1f - accent.green) * 0.35f,
            blue = accent.blue + (1f - accent.blue) * 0.35f,
            alpha = 1f
        )
    } else {
        Color(
            red = accent.red * 0.58f,
            green = accent.green * 0.58f,
            blue = accent.blue * 0.58f,
            alpha = 1f
        )
    }
    val container = accent.copy(alpha = if (darkTheme) 0.22f else 0.12f)
    return container to content
}

@Composable
@ReadOnlyComposable
fun FileType.chipColors(): Pair<Color, Color> {
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return formatChipColors(this, darkTheme)
}

// ---------------------------------------------------------------------------
// Legacy aliases — kept only so older components keep compiling. Do not use
// in new code; prefer MaterialTheme.colorScheme or the Ember tokens above.
// ---------------------------------------------------------------------------
@Deprecated("Legacy identity. Use MaterialTheme.colorScheme instead.")
val NeonEmerald = Color(0xFF00FFAB)
@Deprecated("Legacy identity. Use MaterialTheme.colorScheme instead.")
val EmeraldDark = Color(0xFF004D40)
@Deprecated("Legacy identity. Use MaterialTheme.colorScheme instead.")
val CrimsonGlow = Color(0xFFFF3366)
@Deprecated("Legacy identity. Use MaterialTheme.colorScheme instead.")
val CrimsonDark = Color(0xFF880E4F)
@Deprecated("Legacy identity. Use MaterialTheme.colorScheme instead.")
val AmberWarn = Color(0xFFFFB800)
@Deprecated("Legacy identity. Use MaterialTheme.colorScheme instead.")
val AmberDark = Color(0xFF7F6000)
@Deprecated("Legacy identity. Use MaterialTheme.colorScheme instead.")
val MidnightBlue = Color(0xFF05070A)
@Deprecated("Legacy identity. Use MaterialTheme.colorScheme instead.")
val PremiumOffWhite = Color(0xFFF1F5F9)
@Deprecated("Legacy identity. Use MaterialTheme.colorScheme instead.")
val SurfaceContainerLow = Color(0xFF0D1117)
@Deprecated("Legacy identity. Use MaterialTheme.colorScheme instead.")
val SurfaceContainerHighest = Color(0xFF161B22)
@Deprecated("Legacy identity. Use MaterialTheme.colorScheme instead.")
val LightCardBorder = Color(0xFFCBD5E0)
@Deprecated("Legacy identity. Use MaterialTheme.colorScheme instead.")
val DarkCardBorder = Color(0xFFFFFFFF).copy(alpha = 0.15f)
@Deprecated("Legacy identity. Use MaterialTheme.colorScheme instead.")
val ColorImage = Color(0xFF9C27B0)
