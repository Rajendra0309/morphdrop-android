package com.morphdrop.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorphDropTopAppBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    showBackArrow: Boolean = false,
    onBackClick: () -> Unit = {},
    showTagline: Boolean = false,
    actions: @Composable () -> Unit = {}
) {
    val collapsedFraction = scrollBehavior.state.collapsedFraction
    
    // Smooth linear scaling: 36sp expanded -> 22sp collapsed
    val expandedSize = 36f
    val collapsedSize = 22f
    val fontSize = (collapsedSize + (expandedSize - collapsedSize) * (1f - collapsedFraction)).sp
    
    val isHome = title == "MorphDrop"
    
    // Smooth diagonal move: -1.0 (Start) to 0.0 (Center)
    val horizontalBias = if (isHome) {
        (-1f + (1f * collapsedFraction)).coerceIn(-1f, 0f)
    } else {
        0f
    }

    // Weight policy: Bold only for Home brand
    val titleWeight = if (isHome) FontWeight.Black else FontWeight.Medium

    LargeTopAppBar(
        title = {
            // Absolute Centering Logic
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        // Offset to counteract the TopAppBar's internal slots
                        // Nav icon slot is 48dp. Actions slot is at the end.
                        // When centered (bias 0), we shift left by 24dp to align with screen center.
                        if (showBackArrow || horizontalBias > -0.5f) {
                            translationX = if (showBackArrow) (-24).dp.toPx() else 0f
                        }
                    },
                contentAlignment = BiasAlignment(horizontalBias, 0f)
            ) {
                Column(
                    horizontalAlignment = if (horizontalBias > -0.1f) Alignment.CenterHorizontally else Alignment.Start,
                    modifier = Modifier.padding(
                        start = if (horizontalBias < -0.9f) 16.dp else 0.dp,
                        bottom = if (horizontalBias > -0.1f) 0.dp else (8 * (1f - collapsedFraction)).dp
                    )
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = titleWeight,
                        fontSize = fontSize,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        textAlign = if (horizontalBias > -0.1f) TextAlign.Center else TextAlign.Start
                    )
                    
                    if (showTagline && isHome) {
                        val taglineOpacity = (1f - (collapsedFraction * 4f)).coerceIn(0f, 1f)
                        if (taglineOpacity > 0.01f) {
                            Text(
                                text = "Drop. Transform. Done.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = taglineOpacity),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        navigationIcon = {
            if (showBackArrow) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        },
        actions = { actions() },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}
