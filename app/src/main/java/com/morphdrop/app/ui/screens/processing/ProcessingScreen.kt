package com.morphdrop.app.ui.screens.processing

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.ui.components.GlassCard
import com.morphdrop.app.ui.theme.LocalLiquidState
import com.morphdrop.app.ui.theme.NeonEmerald
import com.morphdrop.app.ui.theme.TextPrimary
import com.morphdrop.app.ui.theme.TextSecondary
import com.morphdrop.app.ui.utils.rememberHapticHelper

@Composable
fun ProcessingScreen(
    onNavigateBack: () -> Unit = {},
    onConversionFinished: (String) -> Unit = {},
    viewModel: ProcessingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val liquidState = LocalLiquidState.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = rememberHapticHelper()

    LaunchedEffect(Unit) {
        viewModel.observeWork(context)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = uiState.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "progress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted && viewModel.workIdString != null) {
            haptic.success()
            onConversionFinished(viewModel.workIdString!!)
        }
    }

    LaunchedEffect(uiState.isCancelled) {
        if (uiState.isCancelled) {
            haptic.selection()
            onNavigateBack()
        }
    }

    val floatAnim by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floating"
    )

    Scaffold(
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Large Circular Liquid Glass Ring Container
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .graphicsLayer { translationY = floatAnim },
                contentAlignment = Alignment.Center
            ) {
                // Outer Progress Canvas Ring
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Track circle
                    drawCircle(
                        color = Color.White.copy(alpha = 0.3f),
                        radius = size.minDimension / 2 - 8.dp.toPx(),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Progress arc
                    drawArc(
                        color = NeonEmerald,
                        startAngle = -90f,
                        sweepAngle = animatedProgress * 360f,
                        useCenter = false,
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Inner Glass Core Circle
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    // Ambient glow
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(pulseAlpha)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(NeonEmerald.copy(alpha = 0.3f), Color.Transparent)
                                )
                            )
                    )

                    // Animated Center Icons
                    Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(56.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = NeonEmerald.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(28.dp)
                                .align(Alignment.BottomEnd)
                                .rotate(rotationAngle)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Status Title
            Text(
                text = "Converting...",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = TextPrimary,
                modifier = Modifier.alpha(pulseAlpha)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = uiState.conversionType?.name ?: "Processing Document",
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Linear Progress & Details Box
            GlassCard(
                liquidState = liquidState,
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PROGRESS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${(uiState.progress * 100).toInt()}%",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = NeonEmerald
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress bar track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.4f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(NeonEmerald, NeonEmerald.copy(alpha = 0.8f))
                                    )
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = uiState.currentStage,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Cancel Button
            GlassCard(
                liquidState = liquidState,
                onClick = { viewModel.cancelConversion(context) },
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cancel",
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
