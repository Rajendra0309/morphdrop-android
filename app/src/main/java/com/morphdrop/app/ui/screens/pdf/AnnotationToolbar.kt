package com.morphdrop.app.ui.screens.pdf

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AnnotationBottomBar(
    isVisible: Boolean,
    currentTool: AnnotationTool,
    currentColor: Color,
    currentStrokeWidth: Float,
    showAnnotations: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToolSelected: (AnnotationTool) -> Unit,
    onColorSelected: (Color) -> Unit,
    onStrokeWidthSelected: (Float) -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSettingsForTool by remember { mutableStateOf<AnnotationTool?>(null) }
    var showFullPalette by remember { mutableStateOf(false) }

    val colors = listOf(
        Color(0xFFF48FB1), Color(0xFFF06292), Color(0xFFE91E63), Color(0xFFC2185B), Color(0xFFBF360C),
        Color(0xFFFF9800), Color(0xFFFFB74D), Color(0xFFFFC107), Color(0xFFFFEB3B), Color(0xFFFFFF00),
        Color(0xFF00B0FF), Color(0xFF00E5FF), Color(0xFF00E676), Color(0xFF76FF03), Color(0xFFC6FF00),
        Color(0xFFAA00FF), Color(0xFFD500F9), Color(0xFF000000), Color(0xFF757575), Color(0xFFFFFFFF)
    )
    
    val quickColors = listOf(Color.Red, Color.Black, Color.Blue, Color.Green)
    val strokeWidths = listOf(2f, 4f, 8f, 12f, 24f)

    // Reset settings if tool changes externally
    LaunchedEffect(currentTool) {
        if (showSettingsForTool != currentTool) {
            showSettingsForTool = null
            showFullPalette = false
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Full Palette grid
            AnimatedVisibility(
                visible = showFullPalette && showSettingsForTool != null && showAnnotations,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val chunkedColors = colors.chunked(5)
                        chunkedColors.forEach { rowColors ->
                            Row(
                                modifier = Modifier.padding(bottom = if (rowColors != chunkedColors.last()) 12.dp else 0.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowColors.forEach { color ->
                                    ColorDot(
                                        color = color,
                                        isSelected = currentColor == color,
                                        onClick = { 
                                            onColorSelected(color)
                                            showFullPalette = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Settings Bubble (Colors and Strokes)
            AnimatedVisibility(
                visible = showSettingsForTool != null && showSettingsForTool != AnnotationTool.ERASE && showAnnotations,
                enter = scaleIn(transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)) + fadeIn(),
                exit = scaleOut(transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)) + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).width(IntrinsicSize.Min),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Stroke Size Selection
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            
                            strokeWidths.forEach { width ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(if (currentStrokeWidth == width) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .clickable { onStrokeWidthSelected(width) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size((width * 0.8f).dp.coerceAtLeast(4.dp).coerceAtMost(20.dp))
                                            .clip(CircleShape)
                                            .background(if (currentStrokeWidth == width) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                    )
                                }
                            }
                        }
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        
                        // Quick Colors row
                        Row(
                            modifier = Modifier.padding(top = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            quickColors.forEach { color ->
                                ColorDot(
                                    color = color,
                                    isSelected = currentColor == color && !showFullPalette,
                                    onClick = { onColorSelected(color) }
                                )
                            }
                            
                            // Plus icon to expand full palette
                            IconButton(
                                onClick = { showFullPalette = !showFullPalette },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(if (showFullPalette) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Add, 
                                    contentDescription = "More colors",
                                    modifier = Modifier.size(20.dp),
                                    tint = if (showFullPalette) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Main Pill Toolbar
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f),
                shape = CircleShape,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolButton(
                        icon = Icons.Default.Edit,
                        label = "Pen",
                        isSelected = currentTool == AnnotationTool.DRAW,
                        enabled = showAnnotations,
                        onClick = {
                            if (currentTool == AnnotationTool.DRAW) {
                                showSettingsForTool = if (showSettingsForTool == null) AnnotationTool.DRAW else null
                            } else {
                                onToolSelected(AnnotationTool.DRAW)
                                showSettingsForTool = null
                            }
                        }
                    )
                    ToolButton(
                        icon = Icons.Default.HistoryEdu, // Highlighter-like icon
                        label = "Highlighter",
                        isSelected = currentTool == AnnotationTool.HIGHLIGHT,
                        enabled = showAnnotations,
                        onClick = {
                            if (currentTool == AnnotationTool.HIGHLIGHT) {
                                showSettingsForTool = if (showSettingsForTool == null) AnnotationTool.HIGHLIGHT else null
                            } else {
                                onToolSelected(AnnotationTool.HIGHLIGHT)
                                showSettingsForTool = null
                            }
                        }
                    )
                    ToolButton(
                        icon = Icons.Default.AutoFixNormal, // Eraser-like icon
                        label = "Eraser",
                        isSelected = currentTool == AnnotationTool.ERASE,
                        enabled = showAnnotations,
                        onClick = {
                            onToolSelected(AnnotationTool.ERASE)
                            showSettingsForTool = null
                        }
                    )
                    
                    VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
                    
                    IconButton(onClick = onUndo, enabled = canUndo && showAnnotations) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = onRedo, enabled = canRedo && showAnnotations) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    
                    VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
                    
                    IconButton(onClick = onHide) {
                        Icon(
                            if (showAnnotations) Icons.Default.Visibility else Icons.Default.VisibilityOff, 
                            contentDescription = "Hide/Show Annotations"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .background(
                if (isSelected && enabled) MaterialTheme.colorScheme.primaryContainer 
                else Color.Transparent, 
                CircleShape
            )
    ) {
        Icon(
            icon, 
            contentDescription = label,
            tint = if (isSelected && enabled) MaterialTheme.colorScheme.onPrimaryContainer 
                   else if (enabled) MaterialTheme.colorScheme.onSurface
                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}

@Composable
private fun ColorDot(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val contentDescription = "Color: ${color.toArgb()}" // Could be improved with name mapping
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .semantics { this.contentDescription = contentDescription }
            .clickable(onClickLabel = "Select color") { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (color == Color.White || color == MaterialTheme.colorScheme.surface) 
                        Modifier.background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), CircleShape)
                    else Modifier
                )
        )
        if (isSelected) {
            Icon(
                Icons.Default.Check, 
                contentDescription = "Selected", 
                modifier = Modifier.size(16.dp),
                tint = if (color.luminance() > 0.5f) Color.Black else Color.White
            )
        }
    }
}

private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}
