package com.morphdrop.app.ui.screens.history

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.ui.components.EmptyStateAnimation
import com.morphdrop.app.ui.components.GlassCard
import com.morphdrop.app.ui.theme.LocalLiquidState
import com.morphdrop.app.ui.theme.NeonEmerald
import com.morphdrop.app.ui.theme.TextPrimary
import com.morphdrop.app.ui.theme.TextSecondary
import io.github.fletchmckee.liquid.LiquidState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val historyList by viewModel.historyList.collectAsStateWithLifecycle()
    val liquidState = LocalLiquidState.current
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear History") },
            text = { Text("Are you sure you want to delete all conversion history logs?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearDialog = false
                }) {
                    Text("Clear All", color = Color(0xFFBA1A1A))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "History",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        color = TextPrimary,
                        letterSpacing = (-0.5).sp
                    )
                },
                actions = {
                    if (historyList.isNotEmpty()) {
                        TextButton(onClick = { showClearDialog = true }) {
                            Text(
                                text = "Clear All",
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFBA1A1A)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                GlassCard(
                    liquidState = liquidState,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        EmptyStateAnimation(
                            icon = Icons.Default.History,
                            modifier = Modifier.size(120.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "No Conversion History",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Converted files will appear here",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 128.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(historyList, key = { it.id }) { item ->
                    HistoryItemCard(
                        item = item,
                        onDelete = { viewModel.deleteItem(item) },
                        liquidState = liquidState
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryItemCard(
    item: ConversionHistoryEntity,
    onDelete: () -> Unit,
    liquidState: LiquidState
) {
    val relativeTime = remember(item.timestamp) {
        DateUtils.getRelativeTimeSpanString(
            item.timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }

    GlassCard(
        liquidState = liquidState,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                        if (item.success) NeonEmerald.copy(alpha = 0.15f)
                        else Color(0xFFBA1A1A).copy(alpha = 0.15f)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = if (item.success) NeonEmerald else Color(0xFFBA1A1A),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.conversionType.uppercase(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonEmerald
                    )
                    Text(
                        text = item.inputFileName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = relativeTime,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Icon(
                        imageVector = if (item.success) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (item.success) NeonEmerald else Color(0xFFBA1A1A),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.3f))
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Output: ${item.outputFileNames}",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFBA1A1A),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
