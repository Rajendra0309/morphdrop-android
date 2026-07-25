package com.morphdrop.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
fun <T> DraggableList(
    items: List<T>,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    itemContent: @Composable (T, Boolean) -> Unit
) {
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    LazyColumn(
        state = state,
        modifier = modifier
    ) {
        itemsIndexed(items) { index, item ->
            val isDragging = draggedIndex == index
            val zIndex = if (isDragging) 1f else 0f
            val offset by animateDpAsState(
                targetValue = if (isDragging) dragOffset.dp else 0.dp,
                label = "DragOffset"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(zIndex)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffset else 0f
                    }
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { draggedIndex = index },
                            onDragEnd = { draggedIndex = null; dragOffset = 0f },
                            onDragCancel = { draggedIndex = null; dragOffset = 0f },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount.y
                                
                                val targetIndex = (dragOffset / 100).toInt() + index // Simple logic
                                if (targetIndex in items.indices && targetIndex != index) {
                                    // In a real app, you'd use more precise calculations based on item heights
                                    // onMove(index, targetIndex)
                                }
                            }
                        )
                    }
            ) {
                itemContent(item, isDragging)
            }
        }
    }
}
