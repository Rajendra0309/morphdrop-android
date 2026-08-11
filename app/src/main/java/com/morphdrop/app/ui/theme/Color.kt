package com.morphdrop.app.ui.theme

import androidx.compose.ui.graphics.Color

// Primary Branding & Accent Colors
val NeonEmerald = Color(0xFF00FFAB)
val EmeraldDark = Color(0xFF004D40) // Very dark green for high contrast on light theme
val CrimsonGlow = Color(0xFFFF3366)
val CrimsonDark = Color(0xFF880E4F)
val AmberWarn = Color(0xFFFFB800)
val AmberDark = Color(0xFF7F6000)

// Liquid Glass Theme Colors
val MidnightBlue = Color(0xFF05070A)
val PremiumOffWhite = Color(0xFFF1F5F9) // Premium blue-grey for glass compatibility
val SurfaceContainerLow = Color(0xFF0D1117)
val SurfaceContainerHighest = Color(0xFF161B22)

// Card Styling
val LightCardBorder = Color(0xFFCBD5E0) // More visible border for light mode
val DarkCardBorder = Color(0xFFFFFFFF).copy(alpha = 0.15f)

// Glass Effects
val GlassCardBackground = Color(0xFFFFFFFF).copy(alpha = 0.07f)
val GlassCardBorder = Color(0xFFFFFFFF).copy(alpha = 0.10f)

// Text Hierarchy (Dark Theme Defaults)
val TextPrimaryDark = Color(0xFFFFFFFF)
val TextSecondaryDark = Color(0xFFFFFFFF).copy(alpha = 0.55f)
val TextTertiaryDark = Color(0xFFFFFFFF).copy(alpha = 0.38f)

// Text Hierarchy (Light Theme)
val TextPrimaryLight = Color(0xFF1C1B1F)
val TextSecondaryLight = Color(0xFF49454F)
val TextTertiaryLight = Color(0xFF49454F).copy(alpha = 0.6f)

// Use these variables for theme-aware components that don't use MaterialTheme yet
// But it's better to use MaterialTheme.colorScheme
val TextPrimary = TextPrimaryDark
val TextSecondary = TextSecondaryDark
val TextTertiary = TextTertiaryDark

// Theme Colors (Dark only for Editorial Tech)
val Primary = NeonEmerald
val Secondary = CrimsonGlow
val Tertiary = AmberWarn
val Background = MidnightBlue
val ColorImage = Color(0xFF9C27B0)  // Purple

// Standard Compose Colors
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
