package com.morphdrop.app.ui.components

import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.morphdrop.app.ui.screens.conversion.WorkbenchPage
import com.morphdrop.app.util.PdfThumbnailHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPageOrganizerDialog(
    pdfUri: Uri,
    workbenchPages: List<WorkbenchPage>,
    selectedPageIds: Set<String>,
    pageRotations: Map<String, Int>,
    toolId: String,
    splitMode: String,
    splitEveryN: Int,
    isLoading: Boolean = false,
    onDismiss: () -> Unit,
    onToggleSelection: (String) -> Unit,
    onRotatePage: (String) -> Unit,
    onMovePage: (Int, Int) -> Unit,
    onSplitModeChanged: (String) -> Unit,
    onSplitEveryNChanged: (Int) -> Unit,
    onConfirm: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(when(toolId) {
                        "split_pdf" -> "Split PDF"
                        "merge_pdf" -> "Master Merge"
                        else -> "Organize Pages"
                    }) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        TextButton(onClick = onConfirm, enabled = !isLoading && workbenchPages.isNotEmpty()) {
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            },
            bottomBar = {
                if (toolId == "split_pdf" && workbenchPages.isNotEmpty()) {
                    Surface(
                        tonalElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Split Strategy", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val modes = listOf("selection" to "Selection", "every_n" to "Every N", "all" to "All Pages")
                                modes.forEach { (id, label) ->
                                    FilterChip(
                                        selected = splitMode == id,
                                        onClick = { 
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSplitModeChanged(id) 
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }
                            if (splitMode == "every_n") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Split every", style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    IconButton(onClick = { onSplitEveryNChanged(splitEveryN - 1) }) {
                                        Icon(Icons.Default.Remove, contentDescription = null)
                                    }
                                    Text("$splitEveryN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { onSplitEveryNChanged(splitEveryN + 1) }) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                    }
                                    Text("pages", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                if (isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Reading PDF Pages...", style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (workbenchPages.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No pages found or could not read file.", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onDismiss) {
                            Text("Go Back")
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = if (toolId == "merge_pdf") "Arrange pages from all files into your final document." else "Tap to exclude pages. Drag arrows to reorder.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(150.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(workbenchPages, key = { _, page -> page.id }) { index, page ->
                                PageThumbnailCard(
                                    pdfUri = page.uri,
                                    pageIndex = page.originalIndex,
                                    displayIndex = index + 1,
                                    sourceFileName = page.sourceFileName,
                                    isSelected = selectedPageIds.contains(page.id),
                                    rotation = pageRotations[page.id] ?: 0,
                                    onToggleSelection = { 
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onToggleSelection(page.id) 
                                    },
                                    onRotate = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onRotatePage(page.id)
                                    },
                                    onMoveLeft = if (index > 0) { {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onMovePage(index, index - 1)
                                    } } else null,
                                    onMoveRight = if (index < workbenchPages.size - 1) { {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onMovePage(index, index + 1)
                                    } } else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageThumbnailCard(
    pdfUri: Uri,
    pageIndex: Int,
    displayIndex: Int,
    sourceFileName: String,
    isSelected: Boolean,
    rotation: Int,
    onToggleSelection: () -> Unit,
    onRotate: () -> Unit,
    onMoveLeft: (() -> Unit)?,
    onMoveRight: (() -> Unit)?
) {
    val context = LocalContext.current
    var thumbnailUri by remember(pdfUri, pageIndex) { mutableStateOf<Uri?>(null) }

    LaunchedEffect(pdfUri, pageIndex) {
        thumbnailUri = PdfThumbnailHelper.getThumbnailUri(context, pdfUri, pageIndex)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .alpha(if (isSelected) 1f else 0.4f)
                .clickable { onToggleSelection() }
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = thumbnailUri ?: pdfUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(rotation.toFloat()),
                    contentScale = ContentScale.Fit
                )
                
                // Page Number Badge
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(24.dp)
                        .align(Alignment.TopStart)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "$displayIndex",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Source File Badge
                val fileColors = remember {
                    listOf(
                        Color(0xFF2196F3), // Blue
                        Color(0xFF4CAF50), // Green
                        Color(0xFFFF9800), // Orange
                        Color(0xFFE91E63), // Pink
                        Color(0xFF9C27B0), // Purple
                        Color(0xFF00BCD4)  // Cyan
                    )
                }
                // Deterministic color based on URI
                val colorIndex = remember(pdfUri) { 
                    (pdfUri.toString().hashCode().let { if (it < 0) -it else it }) % fileColors.size 
                }
                
                Surface(
                    color = fileColors[colorIndex].copy(alpha = 0.9f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.BottomStart)
                ) {
                    Text(
                        text = sourceFileName.take(12) + if(sourceFileName.length > 12) "..." else "",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                if (!isSelected) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMoveLeft ?: {}, enabled = onMoveLeft != null) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Move Left", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onRotate) {
                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onMoveRight ?: {}, enabled = onMoveRight != null) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Move Right", modifier = Modifier.size(18.dp))
            }
        }
    }
}
