package com.morphdrop.app.ui.screens.result

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.ui.components.GlassCard
import com.morphdrop.app.ui.components.PrimaryButton
import com.morphdrop.app.ui.theme.LocalLiquidState
import com.morphdrop.app.ui.theme.NeonEmerald
import com.morphdrop.app.ui.theme.TextPrimary
import com.morphdrop.app.ui.theme.TextSecondary
import com.morphdrop.app.ui.utils.rememberHapticHelper

@Composable
fun ResultScreen(
    onNavigateToHome: () -> Unit = {},
    viewModel: ResultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val liquidState = LocalLiquidState.current
    val context = LocalContext.current
    val haptic = rememberHapticHelper()

    LaunchedEffect(Unit) {
        haptic.success()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "celebration")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Scaffold(
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Celebration Circle
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = NeonEmerald,
                    modifier = Modifier.size(72.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = uiState.title,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp,
                color = TextPrimary,
                letterSpacing = (-1).sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = uiState.subtitle,
                fontSize = 14.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Converted Files List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.outputFiles, key = { it.id }) { item ->
                    GlassCard(
                        liquidState = liquidState,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(NeonEmerald.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                    contentDescription = null,
                                    tint = NeonEmerald,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.fileName,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    text = item.fileSizeFormatted,
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassCard(
                    liquidState = liquidState,
                    onClick = {
                        uiState.outputFiles.firstOrNull()?.let { viewModel.openFile(context, it) }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Open",
                            fontWeight = FontWeight.Bold,
                            color = NeonEmerald,
                            fontSize = 15.sp
                        )
                    }
                }

                GlassCard(
                    liquidState = liquidState,
                    onClick = {
                        uiState.outputFiles.firstOrNull()?.let { viewModel.shareFile(context, it) }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Share",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Convert Another Primary Button
            PrimaryButton(
                text = "Convert Another",
                onClick = onNavigateToHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
