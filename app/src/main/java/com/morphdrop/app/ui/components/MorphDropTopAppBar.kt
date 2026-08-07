package com.morphdrop.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.lerp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorphDropTopAppBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    showBackArrow: Boolean = false,
    onBackClick: () -> Unit = {},
    showTagline: Boolean = false,
    hasActions: Boolean = false,
    actions: @Composable () -> Unit = {}
) {
    val collapsedFraction = scrollBehavior.state.collapsedFraction

    // Smooth alpha for the tagline (fades out early)
    val taglineAlpha = (1f - (collapsedFraction * 4f)).coerceIn(0f, 1f)
    
    val isHomeBrand = title == "MorphDrop"
    val titleWeight = if (isHomeBrand) FontWeight.Black else FontWeight.Bold

    // We need to calculate the offset to keep the title exactly in the screen center
    // when the bar is collapsed.
    // Nav Icon area: 48dp (if present)
    // Actions area: variable (48dp per action)

    LargeTopAppBar(
        title = {
            // Expanded state: standard slot behavior
            // Collapsed state: absolute centering using negative translation
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                // Left Expanded Title (Fades out)
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier
                        .padding(start = if (showBackArrow) 0.dp else 4.dp)
                        .graphicsLayer { 
                            alpha = (1f - collapsedFraction * 2.5f).coerceIn(0f, 1f)
                        }
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = titleWeight,
                        fontSize = 32.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    
                    if (showTagline && isHomeBrand && taglineAlpha > 0.01f) {
                        Text(
                            text = "Drop. Transform. Done.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = taglineAlpha),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Centered Collapsed Title (Fades in)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { 
                            alpha = if (collapsedFraction > 0.6f) (collapsedFraction - 0.6f) * 2.5f else 0f

                            // Absolute Screen Centering Logic:
                            // The LargeTopAppBar title slot is horizontally biased by the nav icon area.
                            // navWidth: area on the left (Back Arrow 48dp or Home 16dp).
                            // actionWidth: area on the right (Actions 48dp or balanced spacer 48dp).
                            val left = if (showBackArrow) 48f else 16f
                            val right = if (hasActions || showBackArrow) 48f else 0f

                            // Shift to reach absolute horizontal screen center
                            translationX = (right - left) / 2f
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = titleWeight,
                        fontSize = 19.sp, 
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.offset(y = (-3.5).dp) // Exactly aligned with arrow center
                    )
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
        actions = { 
            // Wrap actions to maintain layout balance
            Row(verticalAlignment = Alignment.CenterVertically) {
                actions()
                if (showBackArrow && !hasActions) {
                   // Spacer for balance if only back arrow is present
                   Spacer(modifier = Modifier.width(48.dp))
                }
            }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}
