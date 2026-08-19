package com.morphdrop.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.morphdrop.app.data.local.entity.BookmarkEntity

@Composable
fun PdfFastScroller(
    listState: LazyListState,
    totalPages: Int,
    bookmarks: List<BookmarkEntity> = emptyList(),
    currentScale: Float = 1f,
    onBookmarkToggle: ((Int) -> Unit)? = null,
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
            if (listState.layoutInfo.totalItemsCount == 0) return@derivedStateOf 0f
            val firstVisibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull() ?: return@derivedStateOf 0f
            val itemSize = firstVisibleItem.size.toFloat()
            val viewportSize = listState.layoutInfo.viewportSize.height.toFloat()
            
            // Total height of all items (approximate)
            val totalContentHeight = totalPages * itemSize
            if (totalContentHeight <= viewportSize) return@derivedStateOf 0f
            
            // Current scroll position
            val currentScrollY = (firstVisibleItem.index * itemSize) - firstVisibleItem.offset
            
            // Progress is currentScroll / (totalContentHeight - viewportSize)
            (currentScrollY / (totalContentHeight - viewportSize)).coerceIn(0f, 1f)
        }
    }
    
    var localDragY by remember { mutableStateOf(0f) }

    // Auto-hide logic
    LaunchedEffect(listState.isScrollInProgress, isDragging) {
        if (listState.isScrollInProgress || isDragging) {
            isVisible = true
        } else {
            delay(1500)
            isVisible = false
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val trackHeightDp = maxHeight.value - 56f // 56dp is thumb height
        val trackHeightPx = with(LocalDensity.current) { trackHeightDp.dp.toPx() }
        
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val thumbY = if (isDragging) localDragY else (progress * trackHeightDp).coerceIn(0f, trackHeightDp)
                val topOffsetDp = 28f // Half thumb height

                val activePageIndex = remember(listState.layoutInfo) {
                    listState.layoutInfo.visibleItemsInfo.minByOrNull { 
                        kotlin.math.abs(it.offset + (it.size / 2) - (listState.layoutInfo.viewportSize.height / 2))
                    }?.index ?: listState.firstVisibleItemIndex
                }
                val currentPageNumber = activePageIndex + 1

                val visibleRangeText = remember(listState.layoutInfo, totalPages, currentScale) {
                    if (currentScale > 1.2f) {
                        "${currentPageNumber} / $totalPages"
                    } else {
                        val items = listState.layoutInfo.visibleItemsInfo
                        val viewportStart = listState.layoutInfo.viewportStartOffset
                        val viewportEnd = listState.layoutInfo.viewportEndOffset
                        val viewportSize = viewportEnd - viewportStart
                        
                        val significantItems = items.filter { item ->
                            val itemStart = maxOf(viewportStart, item.offset)
                            val itemEnd = minOf(viewportEnd, item.offset + item.size)
                            val visibleSize = itemEnd - itemStart
                            (visibleSize.toFloat() / viewportSize.toFloat()) > 0.40f // Covers more than 40% of viewport
                        }
                        
                        if (significantItems.isNotEmpty()) {
                            val first = significantItems.first().index + 1
                            val last = significantItems.last().index + 1
                            if (first == last) "$first / $totalPages" else "$first-$last / $totalPages"
                        } else {
                            "${currentPageNumber} / $totalPages"
                        }
                    }
                }

                // Page Number Pill (Separate)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-48).dp, y = (topOffsetDp + thumbY - 20).dp)
                        .shadow(8.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text(
                        text = visibleRangeText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // Bookmark Button (Separate)
                if (onBookmarkToggle != null) {
                    val isBookmarked = bookmarks.any { it.pageNumber == currentPageNumber }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-135).dp, y = (topOffsetDp + thumbY - 20).dp)
                            .size(40.dp)
                            .shadow(8.dp, CircleShape),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                        shadowElevation = 4.dp
                    ) {
                        IconButton(
                            onClick = { onBookmarkToggle(currentPageNumber) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = if (isBookmarked) Color(0xFF008080) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Handle
                Box(
                    modifier = Modifier
                        .offset(y = (topOffsetDp + thumbY - 28).dp)
                        .align(Alignment.TopEnd)
                        .padding(end = 8.dp)
                        .size(width = 32.dp, height = 56.dp)
                        .pointerInput(totalPages) {
                            detectDragGestures(
                                onDragStart = { 
                                    isDragging = true 
                                    localDragY = progress * trackHeightDp
                                },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val dragAmountDp = dragAmount.y / density
                                    localDragY = (localDragY + dragAmountDp).coerceIn(0f, trackHeightDp)
                                    val newProgress = localDragY / trackHeightDp
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
                }
            }
        }
    }
}

