package com.morphdrop.app.ui.screens.markdown

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardTab
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.morphdrop.app.domain.model.ReadingMode
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownEditorScreen(
    uriString: String? = null,
    isNew: Boolean = false,
    onNavigateBack: () -> Unit = {},
    viewModel: MarkdownEditorViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val state by viewModel.uiState.collectAsState()

    val screenWidth = configuration.screenWidthDp
    val isTabletOrLandscape = screenWidth >= 600 || configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Initial load
    LaunchedEffect(uriString, isNew) {
        viewModel.initDocument(uriString, isNew)
    }

    // Default view mode: split on tablets, edit only on phones
    LaunchedEffect(isTabletOrLandscape) {
        if (isTabletOrLandscape && state.viewMode == EditorViewMode.EDIT_ONLY) {
            viewModel.setViewMode(EditorViewMode.SPLIT_VIEW)
        }
    }

    // SAF Save As launcher
    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { targetUri ->
        if (targetUri != null) {
            viewModel.saveDocumentAs(targetUri) { success ->
                val msg = if (success) "Saved successfully" else "Failed to save file"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // SAF Open File launcher with Markdown (.md) validation
    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { sourceUri ->
        if (sourceUri != null) {
            var pickedName = sourceUri.lastPathSegment?.substringAfterLast("/") ?: ""
            if (sourceUri.scheme == "content") {
                try {
                    context.contentResolver.query(
                        sourceUri,
                        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx != -1) {
                                val name = cursor.getString(idx)
                                if (!name.isNullOrBlank()) pickedName = name
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            val isMarkdown = pickedName.endsWith(".md", ignoreCase = true) ||
                    pickedName.endsWith(".markdown", ignoreCase = true) ||
                    pickedName.endsWith(".mdown", ignoreCase = true) ||
                    pickedName.endsWith(".mkd", ignoreCase = true)

            if (!isMarkdown) {
                Toast.makeText(
                    context,
                    "Invalid file ($pickedName). Only Markdown (.md) files can be opened.",
                    Toast.LENGTH_LONG
                ).show()
                return@rememberLauncherForActivityResult
            }

            viewModel.loadFromUri(sourceUri)
        }
    }

    // Back press handling with unsaved changes confirmation
    BackHandler(enabled = state.hasUnsavedChanges) {
        viewModel.setUnsavedDialogVisible(true)
    }

    // Error toast
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearErrorMessage()
        }
    }

    // Live preview debounce: 300ms
    var debouncedMarkdown by remember { mutableStateOf(state.textFieldValue.text) }
    LaunchedEffect(state.textFieldValue.text) {
        delay(300L)
        debouncedMarkdown = state.textFieldValue.text
    }

    Scaffold(
        topBar = {
            EditorTopAppBar(
                state = state,
                isTablet = isTabletOrLandscape,
                onBack = {
                    if (state.hasUnsavedChanges) {
                        viewModel.setUnsavedDialogVisible(true)
                    } else {
                        onNavigateBack()
                    }
                },
                onSave = {
                    if (state.fileUri != null) {
                        viewModel.saveDocument { success ->
                            val msg = if (success) "File saved" else "Failed to save file"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        saveAsLauncher.launch(state.fileName)
                    }
                },
                onShare = {
                    try {
                        val shareFile = File(context.cacheDir, state.fileName)
                        shareFile.writeText(state.textFieldValue.text, Charsets.UTF_8)
                        val fileUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            shareFile
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/markdown"
                            putExtra(Intent.EXTRA_STREAM, fileUri)
                            putExtra(Intent.EXTRA_SUBJECT, state.fileName)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Markdown File"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error sharing file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                },
                onViewModeSelected = viewModel::setViewMode,
                onRequestRename = { viewModel.setRenameDialogVisible(true) },
                onNewDocument = viewModel::createNewDocument,
                onOpenFile = {
                    openFileLauncher.launch(arrayOf("text/markdown", "text/plain", "text/*", "*/*"))
                },
                onShowStats = { viewModel.setStatsDialogVisible(true) },
                onToggleLineNumbers = viewModel::toggleLineNumbers,
                onToggleWordWrap = viewModel::toggleWordWrap
            )
        },
        bottomBar = {
            // Only show formatting bar in edit or split modes
            if (state.viewMode != EditorViewMode.PREVIEW_ONLY) {
                FormattingToolbar(
                    viewModel = viewModel,
                    modifier = Modifier.imePadding()
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                isTabletOrLandscape || state.viewMode == EditorViewMode.SPLIT_VIEW -> {
                    // Split View: Side-by-side on wide screens, Top/Bottom on phones
                    if (isTabletOrLandscape) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                MarkdownEditorPane(
                                    state = state,
                                    onTextChanged = viewModel::onTextChanged,
                                    onToggleWordWrap = viewModel::toggleWordWrap,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                MarkdownPreviewPane(
                                    markdownText = debouncedMarkdown,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                MarkdownEditorPane(
                                    state = state,
                                    onTextChanged = viewModel::onTextChanged,
                                    onToggleWordWrap = viewModel::toggleWordWrap,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                MarkdownPreviewPane(
                                    markdownText = debouncedMarkdown,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
                state.viewMode == EditorViewMode.PREVIEW_ONLY -> {
                    MarkdownPreviewPane(
                        markdownText = debouncedMarkdown,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                else -> {
                    // Edit Only
                    MarkdownEditorPane(
                        state = state,
                        onTextChanged = viewModel::onTextChanged,
                        onToggleWordWrap = viewModel::toggleWordWrap,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Large file performance warning banner
            AnimatedVisibility(
                visible = state.showLargeFileWarning,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Large document detected (>1MB). Syntax highlighting is disabled for peak responsiveness.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = viewModel::dismissLargeFileWarning,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Rename Document Dialog (M3 Rounded Modal Dialog)
    if (state.showRenameDialog) {
        var tempName by remember(state.fileName) {
            mutableStateOf(state.fileName.removeSuffix(".md"))
        }

        AlertDialog(
            onDismissRequest = { viewModel.setRenameDialogVisible(false) },
            shape = RoundedCornerShape(24.dp),
            icon = {
                Icon(
                    imageVector = Icons.Default.EditNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Rename Document",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter a new name for this Markdown document:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        singleLine = true,
                        label = { Text("File Name") },
                        suffix = { Text(".md") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = tempName.trim()
                        if (clean.isNotEmpty()) {
                            viewModel.setFileName("$clean.md")
                        }
                        viewModel.setRenameDialogVisible(false)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.setRenameDialogVisible(false) }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Unsaved changes dialog
    if (state.showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setUnsavedDialogVisible(false) },
            shape = RoundedCornerShape(24.dp),
            icon = {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Unsaved Changes", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "You have unsaved changes in '${state.fileName}'. Would you like to save them before leaving?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setUnsavedDialogVisible(false)
                        if (state.fileUri != null) {
                            viewModel.saveDocument { success ->
                                if (success) onNavigateBack()
                            }
                        } else {
                            saveAsLauncher.launch(state.fileName)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save & Exit")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            viewModel.setUnsavedDialogVisible(false)
                            onNavigateBack()
                        }
                    ) {
                        Text("Discard", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = { viewModel.setUnsavedDialogVisible(false) }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Document statistics dialog
    if (state.showStatsDialog) {
        val readTimeMinutes = (state.wordCount / 200).coerceAtLeast(1)
        AlertDialog(
            onDismissRequest = { viewModel.setStatsDialogVisible(false) },
            shape = RoundedCornerShape(24.dp),
            icon = {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Document Statistics", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatRow(label = "Words", value = state.wordCount.toString())
                    StatRow(label = "Characters", value = state.charCount.toString())
                    StatRow(label = "Lines", value = state.lineCount.toString())
                    StatRow(label = "Word Wrap", value = if (state.isWordWrapEnabled) "ON" else "OFF (Horizontal scroll)")
                    StatRow(label = "Estimated Reading Time", value = "~$readTimeMinutes min")
                    StatRow(label = "Status", value = if (state.hasUnsavedChanges) "Modified (Unsaved)" else "Saved")
                    if (state.lastSavedMessage.isNotEmpty()) {
                        StatRow(label = "Last Saved", value = state.lastSavedMessage)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setStatsDialogVisible(false) }) {
                    Text("Close")
                }
            }
        )
    }

    // Insert Table Dialog
    if (state.showTableDialog) {
        var rowsText by remember { mutableStateOf("3") }
        var colsText by remember { mutableStateOf("3") }

        AlertDialog(
            onDismissRequest = { viewModel.setTableDialogVisible(false) },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Insert Markdown Table") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = rowsText,
                        onValueChange = { rowsText = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Rows (1-20)") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = colsText,
                        onValueChange = { colsText = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Columns (1-10)") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val r = rowsText.toIntOrNull() ?: 3
                        val c = colsText.toIntOrNull() ?: 3
                        viewModel.insertTable(r, c)
                        viewModel.setTableDialogVisible(false)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Insert")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setTableDialogVisible(false) }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Insert Link Dialog
    if (state.showLinkDialog) {
        var linkTitle by remember { mutableStateOf("") }
        var linkUrl by remember { mutableStateOf("https://") }

        AlertDialog(
            onDismissRequest = { viewModel.setLinkDialogVisible(false) },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Insert Link") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = linkTitle,
                        onValueChange = { linkTitle = it },
                        label = { Text("Link Text") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = linkUrl,
                        onValueChange = { linkUrl = it },
                        label = { Text("URL") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.insertLink(linkTitle, linkUrl)
                        viewModel.setLinkDialogVisible(false)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Insert")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setLinkDialogVisible(false) }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Insert Image Dialog
    if (state.showImageDialog) {
        var altText by remember { mutableStateOf("") }
        var imageUrl by remember { mutableStateOf("https://") }

        AlertDialog(
            onDismissRequest = { viewModel.setImageDialogVisible(false) },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Insert Image") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = altText,
                        onValueChange = { altText = it },
                        label = { Text("Description / Alt Text") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = imageUrl,
                        onValueChange = { imageUrl = it },
                        label = { Text("Image URL") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.insertImage(altText, imageUrl)
                        viewModel.setImageDialogVisible(false)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Insert")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setImageDialogVisible(false) }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopAppBar(
    state: MarkdownEditorState,
    isTablet: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onViewModeSelected: (EditorViewMode) -> Unit,
    onRequestRename: () -> Unit,
    onNewDocument: () -> Unit,
    onOpenFile: () -> Unit,
    onShowStats: () -> Unit,
    onToggleLineNumbers: () -> Unit,
    onToggleWordWrap: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    TopAppBar(
        title = {
            // Elegant M3 Pill for Document Title (tap to rename, hold for complete name toast)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .combinedClickable(
                        onClick = onRequestRename,
                        onLongClick = {
                            Toast.makeText(context, state.fileName, Toast.LENGTH_SHORT).show()
                        }
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = state.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = if (state.hasUnsavedChanges) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (state.hasUnsavedChanges) "Modified" else ".md",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (state.hasUnsavedChanges) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Rename",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            // View Mode Toggle (Edit / Split / Preview)
            if (!isTablet) {
                IconButton(
                    onClick = {
                        val nextMode = when (state.viewMode) {
                            EditorViewMode.EDIT_ONLY -> EditorViewMode.SPLIT_VIEW
                            EditorViewMode.SPLIT_VIEW -> EditorViewMode.PREVIEW_ONLY
                            EditorViewMode.PREVIEW_ONLY -> EditorViewMode.EDIT_ONLY
                        }
                        onViewModeSelected(nextMode)
                    }
                ) {
                    Icon(
                        imageVector = when (state.viewMode) {
                            EditorViewMode.EDIT_ONLY -> Icons.Default.Edit
                            EditorViewMode.SPLIT_VIEW -> Icons.Default.ViewAgenda
                            EditorViewMode.PREVIEW_ONLY -> Icons.Default.Visibility
                        },
                        contentDescription = "Toggle View Mode (${state.viewMode.name})"
                    )
                }
            }

            // Save action
            IconButton(onClick = onSave, enabled = !state.isSaving) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save",
                        tint = if (state.hasUnsavedChanges) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Overflow Menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More Options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Open File...") },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onOpenFile()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("New Markdown File") },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onNewDocument()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share .md File") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onShare()
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (state.isWordWrapEnabled) "Disable Word Wrap" else "Enable Word Wrap") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.WrapText, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onToggleWordWrap()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (state.showLineNumbers) "Hide Line Numbers" else "Show Line Numbers") },
                        leadingIcon = { Icon(Icons.Default.FormatListNumbered, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onToggleLineNumbers()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Document Info & Stats") },
                        leadingIcon = { Icon(Icons.Default.Analytics, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onShowStats()
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun MarkdownEditorPane(
    state: MarkdownEditorState,
    onTextChanged: (TextFieldValue) -> Unit,
    onToggleWordWrap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }

    val primaryColor = MaterialTheme.colorScheme.primary
    val codeContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val codeTextColor = MaterialTheme.colorScheme.onSurface
    val isLarge = state.isLargeFile

    val visualTransformation = remember(isLarge, primaryColor, codeContainerColor) {
        if (isLarge) {
            VisualTransformation.None
        } else {
            MarkdownSyntaxVisualTransformation(
                primaryColor = primaryColor,
                codeContainerColor = codeContainerColor,
                codeTextColor = codeTextColor
            )
        }
    }

    Column(modifier = modifier) {
        // Status & Sub-header Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Ln ${state.cursorLine}, Col ${state.cursorCol}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "${state.wordCount} words  •  ${state.charCount} chars",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onToggleWordWrap)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.WrapText,
                        contentDescription = "Word Wrap",
                        tint = if (state.isWordWrapEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (state.isWordWrapEnabled) "Wrap" else "Scroll X",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.isWordWrapEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Editor Canvas with optional horizontal scrolling and vertical scrolling
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(verticalScrollState)
                .padding(top = 8.dp, bottom = 16.dp)
        ) {
            // Line numbers column
            if (state.showLineNumbers) {
                val lineCount = state.lineCount.coerceAtLeast(1)
                val lineNumbersString = remember(lineCount) {
                    (1..lineCount).joinToString("\n")
                }

                Text(
                    text = lineNumbersString,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .width(42.dp)
                        .padding(end = 8.dp)
                )

                VerticalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    modifier = Modifier.height(IntrinsicSize.Max),
                    thickness = 1.dp
                )
            }

            // Editor TextField Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (!state.isWordWrapEnabled) {
                            Modifier.horizontalScroll(horizontalScrollState)
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 12.dp)
            ) {
                BasicTextField(
                    value = state.textFieldValue,
                    onValueChange = onTextChanged,
                    modifier = Modifier
                        .then(if (!state.isWordWrapEnabled) Modifier.width(IntrinsicSize.Max).defaultMinSize(minWidth = 500.dp) else Modifier.fillMaxWidth())
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    visualTransformation = visualTransformation,
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (state.textFieldValue.text.isEmpty()) {
                                Text(
                                    text = "Start typing markdown...",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp,
                                        lineHeight = 22.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MarkdownPreviewPane(
    markdownText: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isThemeDark = (MaterialTheme.colorScheme.surface.red * 0.299f + MaterialTheme.colorScheme.surface.green * 0.587f + MaterialTheme.colorScheme.surface.blue * 0.114f) < 0.5f
    val isDark = isSystemDark || isThemeDark

    val textColor = MaterialTheme.colorScheme.onBackground
    val codeCardBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val codeHeaderBg = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val clipboardManager = LocalClipboardManager.current

    val markwon = remember(isDark) {
        val density = context.resources.displayMetrics.density
        val codeBg = if (isDark) AndroidColor.parseColor("#21262D") else AndroidColor.parseColor("#F6F8FA")
        val codeFg = if (isDark) AndroidColor.parseColor("#E6EDF3") else AndroidColor.parseColor("#24292F")

        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(CoilImagesPlugin.create(context))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .codeBlockBackgroundColor(codeBg)
                        .codeBlockTextColor(codeFg)
                        .codeBackgroundColor(codeBg)
                        .codeTextColor(codeFg)
                        .codeTypeface(Typeface.MONOSPACE)
                        .codeBlockMargin((8 * density).toInt())
                }
            })
            .build()
    }

    val parsedBlocks = remember(markdownText) {
        parseMarkdownToBlocks(markdownText)
    }

    Column(modifier = modifier) {
        // Preview Header
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Live Preview",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (markdownText.isBlank()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Live preview will appear here...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(parsedBlocks.size, key = { parsedBlocks[it].id }) { idx ->
                    when (val block = parsedBlocks[idx]) {
                        is ParsedBlock.Header -> {
                            MarkdownHeaderItem(
                                level = block.level,
                                title = block.title,
                                textColor = textColor,
                                baseTextSizeSp = 16f
                            )
                        }
                        is ParsedBlock.Code -> {
                            MarkdownCodeCard(
                                language = block.language,
                                code = block.code,
                                backgroundColor = codeCardBg,
                                headerColor = codeHeaderBg,
                                borderColor = borderColor,
                                textColor = textColor,
                                textSizeSp = 14f,
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(block.code))
                                    Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        is ParsedBlock.Table -> {
                            MarkdownTableCard(
                                headers = block.headers,
                                rows = block.rows,
                                borderColor = borderColor,
                                textColor = textColor,
                                headerBg = codeHeaderBg,
                                readingMode = ReadingMode.DEFAULT,
                                textSizeSp = 14f,
                                isDark = isDark
                            )
                        }
                        is ParsedBlock.RichText -> {
                            MarkdownRichTextItem(
                                markdown = block.markdown,
                                markwon = markwon,
                                textColor = textColor,
                                textSizeSp = 15f
                            )
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun FormattingToolbar(
    viewModel: MarkdownEditorViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Undo (Right above keyboard where typing happens!)
            ToolbarButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                description = "Undo",
                enabled = state.canUndo,
                onClick = viewModel::undo
            )

            // Redo
            ToolbarButton(
                icon = Icons.AutoMirrored.Filled.Redo,
                description = "Redo",
                enabled = state.canRedo,
                onClick = viewModel::redo
            )

            VerticalDivider(
                modifier = Modifier
                    .height(24.dp)
                    .padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Bold
            ToolbarButton(
                icon = Icons.Default.FormatBold,
                description = "Bold (**text**)",
                onClick = { viewModel.applyInlineFormat("**", "**", "bold text") }
            )

            // Italic
            ToolbarButton(
                icon = Icons.Default.FormatItalic,
                description = "Italic (*text*)",
                onClick = { viewModel.applyInlineFormat("*", "*", "italic text") }
            )

            // Heading
            ToolbarButton(
                icon = Icons.Default.Title,
                description = "Heading (#)",
                onClick = { viewModel.applyLinePrefix("# ") }
            )

            // Strikethrough
            ToolbarButton(
                icon = Icons.Default.FormatStrikethrough,
                description = "Strikethrough (~~text~~)",
                onClick = { viewModel.applyInlineFormat("~~", "~~", "strikethrough") }
            )

            VerticalDivider(
                modifier = Modifier
                    .height(24.dp)
                    .padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Unordered List
            ToolbarButton(
                icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                description = "Bullet List (- )",
                onClick = { viewModel.applyLinePrefix("- ") }
            )

            // Ordered List
            ToolbarButton(
                icon = Icons.Default.FormatListNumbered,
                description = "Numbered List (1. )",
                onClick = { viewModel.applyLinePrefix("1. ") }
            )

            // Task list
            ToolbarButton(
                icon = Icons.Default.Checklist,
                description = "Task List (- [ ] )",
                onClick = { viewModel.applyLinePrefix("- [ ] ") }
            )

            // Blockquote
            ToolbarButton(
                icon = Icons.Default.FormatQuote,
                description = "Quote (> )",
                onClick = { viewModel.applyLinePrefix("> ") }
            )

            VerticalDivider(
                modifier = Modifier
                    .height(24.dp)
                    .padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Inline Code
            ToolbarButton(
                icon = Icons.Default.Code,
                description = "Inline Code (`code`)",
                onClick = { viewModel.applyInlineFormat("`", "`", "code") }
            )

            // Code Block
            ToolbarButton(
                icon = Icons.Default.IntegrationInstructions,
                description = "Code Block (```)",
                onClick = { viewModel.insertText("\n```\n// code here\n```\n") }
            )

            // Link
            ToolbarButton(
                icon = Icons.Default.Link,
                description = "Insert Link",
                onClick = { viewModel.setLinkDialogVisible(true) }
            )

            // Image
            ToolbarButton(
                icon = Icons.Default.Image,
                description = "Insert Image",
                onClick = { viewModel.setImageDialogVisible(true) }
            )

            // Table
            ToolbarButton(
                icon = Icons.Default.TableChart,
                description = "Insert Table",
                onClick = { viewModel.setTableDialogVisible(true) }
            )

            // Horizontal Rule
            ToolbarButton(
                icon = Icons.Default.HorizontalRule,
                description = "Horizontal Rule (---)",
                onClick = viewModel::insertHorizontalRule
            )

            // Tab / 4 spaces
            ToolbarButton(
                icon = Icons.AutoMirrored.Filled.KeyboardTab,
                description = "Indent (4 spaces)",
                onClick = viewModel::insertTabSpaces
            )
        }
    }
}

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier.size(38.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Lightweight VisualTransformation for basic Markdown syntax highlighting in Compose.
 * Applies rich Material 3 themed colors to headings, bold, italic, code, quotes, and links.
 */
class MarkdownSyntaxVisualTransformation(
    private val primaryColor: Color,
    private val codeContainerColor: Color,
    private val codeTextColor: Color
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val builder = AnnotatedString.Builder(raw)

        // Headings (#, ##, ###)
        val headingRegex = Regex("""^(#{1,6})\s+(.*)$""", RegexOption.MULTILINE)
        headingRegex.findAll(raw).forEach { match ->
            val range = match.range
            builder.addStyle(
                SpanStyle(
                    color = primaryColor,
                    fontWeight = FontWeight.Bold
                ),
                range.first,
                range.last + 1
            )
        }

        // Bold (**text** or __text__)
        val boldRegex = Regex("""(\*\*|__)(.*?)\1""")
        boldRegex.findAll(raw).forEach { match ->
            builder.addStyle(
                SpanStyle(fontWeight = FontWeight.Bold),
                match.range.first,
                match.range.last + 1
            )
        }

        // Italic (*text* or _text_)
        val italicRegex = Regex("""(?<!\*|\w)(\*|_)([^*_\n]+)\1(?!\*|\w)""")
        italicRegex.findAll(raw).forEach { match ->
            builder.addStyle(
                SpanStyle(fontStyle = FontStyle.Italic),
                match.range.first,
                match.range.last + 1
            )
        }

        // Strikethrough (~~text~~)
        val strikeRegex = Regex("""~~(.*?)~~""")
        strikeRegex.findAll(raw).forEach { match ->
            builder.addStyle(
                SpanStyle(textDecoration = TextDecoration.LineThrough),
                match.range.first,
                match.range.last + 1
            )
        }

        // Inline Code (`code`)
        val codeRegex = Regex("""`([^`\n]+)`""")
        codeRegex.findAll(raw).forEach { match ->
            builder.addStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = codeContainerColor,
                    color = codeTextColor
                ),
                match.range.first,
                match.range.last + 1
            )
        }

        // Links ([text](url))
        val linkRegex = Regex("""\[([^\]]+)\]\(([^)]+)\)""")
        linkRegex.findAll(raw).forEach { match ->
            builder.addStyle(
                SpanStyle(
                    color = primaryColor,
                    textDecoration = TextDecoration.Underline
                ),
                match.range.first,
                match.range.last + 1
            )
        }

        // Blockquotes (> quote)
        val quoteRegex = Regex("""^>\s+(.*)$""", RegexOption.MULTILINE)
        quoteRegex.findAll(raw).forEach { match ->
            builder.addStyle(
                SpanStyle(
                    fontStyle = FontStyle.Italic,
                    color = primaryColor.copy(alpha = 0.8f)
                ),
                match.range.first,
                match.range.last + 1
            )
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
