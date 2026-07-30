package com.morphdrop.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
    
    // Smooth linear scaling: 36sp expanded -> 22sp collapsed
    val fontSize = lerp(36.sp, 22.sp, collapsedFraction)
    
    val isHomeBrand = title == "MorphDrop"
    
    // Smooth transition from left corner (-1.0) to center (0.0)
    val horizontalBias = (-1f + (1f * collapsedFraction)).coerceIn(-1f, 0f)

    // Weight policy: Black for Home brand, Bold for others
    val titleWeight = if (isHomeBrand) FontWeight.Black else FontWeight.Bold

    LargeTopAppBar(
        title = {
            // Smooth motion using BiasAlignment (native Compose behavior)
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = BiasAlignment(horizontalBias, 0f)
            ) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier
                        .padding(
                            start = if (horizontalBias < -0.99f) 16.dp else 0.dp,
                            bottom = (8 * (1f - collapsedFraction)).dp
                        )
                        .offset(
                            x = if (collapsedFraction > 0.1f) {
                                // Absolute centering correction: (ActionsWidth - NavWidth) / 2
                                val navWidth = if (showBackArrow) 48.dp else 0.dp
                                val actionWidth = if (hasActions) 48.dp else 0.dp
                                (actionWidth - navWidth) / 2f * collapsedFraction
                            } else 0.dp
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
                        textAlign = TextAlign.Start
                    )
                    
                    if (showTagline && isHomeBrand) {
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
