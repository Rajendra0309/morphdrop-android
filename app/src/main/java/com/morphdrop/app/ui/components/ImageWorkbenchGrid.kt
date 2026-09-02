package com.morphdrop.app.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

data class WorkbenchImageItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val originalUri: Uri,
    val workingUri: Uri,
    val name: String,
    val size: Long = -1,
    val rotationDegrees: Int = 0,
    val isSelected: Boolean = true,
    val isEdited: Boolean = false
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageWorkbenchGrid(
    items: List<WorkbenchImageItem>,
    expandedItemId: String?,
    onItemClick: (WorkbenchImageItem) -> Unit,
    onItemLongClick: (WorkbenchImageItem) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onCropClick: (WorkbenchImageItem) -> Unit,
    onRotateClick: (WorkbenchImageItem) -> Unit,
    onResetClick: (WorkbenchImageItem) -> Unit,
    onApplyEdit: (WorkbenchImageItem) -> Unit,
    onCancelEdit: (WorkbenchImageItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedCount = items.count { it.isSelected }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(24.dp)
            )
            .padding(16.dp)
    ) {
        // Header Layout
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = "Image Workbench",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (items.isNotEmpty()) "$selectedCount of ${items.size} selected" else "No images selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(
                    onClick = onSelectAll,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Select All",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                TextButton(
                    onClick = onDeselectAll,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Deselect All",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Grid of Thumbnails
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(((items.size + 2) / 3 * 110).coerceIn(110, 330).dp)
        ) {
            items(items, key = { it.id }) { item ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    border = if (item.isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(
                        containerColor = if (item.isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .combinedClickable(
                            onClick = { onItemClick(item) },
                            onLongClick = { onItemLongClick(item) }
                        )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = item.workingUri,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { rotationZ = item.rotationDegrees.toFloat() }
                        )

                        // Selection Checkmark Overlay (Dynamic M3 Theme Colors)
                        if (item.isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(24.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Edited Badge
                        if (item.isEdited) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                                    .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Edited",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Expanded In-Workbench Editing View (Smooth 300ms AnimatedVisibility)
        val expandedItem = items.find { it.id == expandedItemId }
        AnimatedVisibility(
            visible = expandedItem != null,
            enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
        ) {
            if (expandedItem != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = expandedItem.name,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = expandedItem.workingUri,
                                contentDescription = "Expanded Preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { rotationZ = expandedItem.rotationDegrees.toFloat() }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Editing Toolbar Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { onCropClick(expandedItem) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Crop, contentDescription = "Crop", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Crop", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }

                            OutlinedButton(
                                onClick = { onRotateClick(expandedItem) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Rotate", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }

                            OutlinedButton(
                                onClick = { onResetClick(expandedItem) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onCancelEdit(expandedItem) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancel")
                            }

                            Button(
                                onClick = { onApplyEdit(expandedItem) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("Apply")
                            }
                        }
                    }
                }
            }
        }
    }
}