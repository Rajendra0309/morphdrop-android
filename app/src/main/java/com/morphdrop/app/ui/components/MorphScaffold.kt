package com.morphdrop.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Clearance reserved at the bottom of content for the floating pill
 * navigation (64dp pill + 16dp bottom margin). Documented here so no screen
 * needs a magic 120.dp bottom padding.
 */
val FloatingNavClearance = 80.dp

/**
 * Standard scaffold for MorphDrop screens: edge-to-edge aware system-bar
 * insets, warm background, and optional [reserveFloatingNavSpace] which adds
 * [FloatingNavClearance] below the navigation bars so the floating pill nav
 * never covers content. Opt in per screen — do not force-migrate screens
 * that manage their own insets.
 */
@Composable
fun MorphScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    reserveFloatingNavSpace: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable (PaddingValues) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        containerColor = containerColor,
        contentWindowInsets = WindowInsets.systemBars,
    ) { inner ->
        val bottomExtra = if (reserveFloatingNavSpace) FloatingNavClearance else 0.dp
        content(
            PaddingValues(
                start = inner.calculateStartPadding(layoutDirection),
                top = inner.calculateTopPadding(),
                end = inner.calculateEndPadding(layoutDirection),
                bottom = inner.calculateBottomPadding() + bottomExtra,
            )
        )
    }
}
