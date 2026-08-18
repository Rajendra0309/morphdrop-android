package com.morphdrop.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PdfFastScroller(
    listState: LazyListState,
    totalPages: Int,
    modifier: Modifier = Modifier
) {
    if (totalPages <= 1) return

    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }
    val density = LocalDensity.current.density
    
    // Exact progress calculation including item offset
    val progress by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) 0f
            else {
                val firstItem = visibleItems.first()
                val totalItems = totalPages
                val itemProgress = -firstItem.offset.toFloat() / firstItem.size.toFloat()
                ((firstItem.index + itemProgress) / (totalItems - 1)).coerceIn(0f, 1f)
            }
        }
    }
    
    var localDragY by remember { mutableStateOf(0f) }

    LaunchedEffect(listState.isScrollInProgress, isDragging) {
        if (listState.isScrollInProgress || isDragging) {
            isVisible = true
        } else {
            delay(2000)
            isVisible = false
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(64.dp)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val maxHeightDp = maxHeight.value
        val scrollbarHeightDp = maxHeightDp * 0.8f
        val topOffsetDp = (maxHeightDp - scrollbarHeightDp) / 2
        
        val thumbY = if (isDragging) localDragY else progress * scrollbarHeightDp

        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Handle
                Box(
                    modifier = Modifier
                        .offset(y = (topOffsetDp + thumbY - 28).dp)
                        .align(Alignment.TopEnd)
                        .padding(end = 8.dp)
                        .size(width = 32.dp, height = 56.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { 
                                    isDragging = true 
                                    localDragY = progress * scrollbarHeightDp
                                },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    // Use local density to convert pixel drag distance to dp
                                    val dragAmountDp = dragAmount.y / density
                                    localDragY = (localDragY + dragAmountDp).coerceIn(0f, scrollbarHeightDp)
                                    val newProgress = localDragY / scrollbarHeightDp
                                    val exactIndex = newProgress * (totalPages - 1)
                                    val targetIndex = exactIndex.toInt().coerceIn(0, totalPages - 1)
                                    val fractionalProgress = exactIndex - targetIndex
                                    
                                    coroutineScope.launch {
                                        val visibleItems = listState.layoutInfo.visibleItemsInfo
                                        val itemSize = visibleItems.find { it.index == targetIndex }?.size 
                                            ?: visibleItems.firstOrNull()?.size ?: 1000
                                        listState.scrollToItem(targetIndex, (fractionalProgress * itemSize).toInt())
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 4.dp, bottomEnd = 4.dp),
                        color = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        shadowElevation = 6.dp,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                repeat(3) {
                                    Box(modifier = Modifier.size(width = 12.dp, height = 2.dp).background(
                                        if (isDragging) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ))
                                }
                            }
                        }
                    }

                    if (isDragging) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 48.dp)
                                .shadow(8.dp, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                text = "${listState.firstVisibleItemIndex + 1}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
