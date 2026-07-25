package com.morphdrop.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morphdrop.app.ui.theme.LocalLiquidState
import com.morphdrop.app.ui.theme.NeonEmerald

@Composable
fun PageThumbnail(
    pageNumber: Int,
    rotation: Int = 0,
    onRotate: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val liquidState = LocalLiquidState.current

    GlassCard(
        liquidState = liquidState,
        modifier = modifier
            .width(140.dp)
            .height(200.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = NeonEmerald.copy(alpha = 0.7f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Page $pageNumber",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (onRotate != null) {
                IconButton(
                    onClick = onRotate,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RotateRight,
                        contentDescription = "Rotate",
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(NeonEmerald.copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${rotation}°",
                    color = NeonEmerald,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
