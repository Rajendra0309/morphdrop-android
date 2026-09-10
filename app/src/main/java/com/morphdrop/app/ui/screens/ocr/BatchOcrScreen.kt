package com.morphdrop.app.ui.screens.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.morphdrop.app.domain.model.OcrScript
import com.morphdrop.app.ui.components.MorphDropTopAppBar
import com.morphdrop.app.ui.components.PrimaryButton
import com.morphdrop.app.ui.theme.MorphDropTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchOcrScreen(
    onNavigateBack: () -> Unit,
    viewModel: BatchOcrViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }

    val multiplePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        viewModel.onFilesSelected(context, uris)
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessages()
        }
    }

    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessages()
        }
    }

    BatchOcrScreenContent(
        state = state,
        scrollBehavior = scrollBehavior,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onPickMultipleImages = { multiplePicker.launch("image/*") },
        onRemoveFile = { viewModel.onRemoveFile(it) },
        onClearAll = { viewModel.onClearAll() },
        onScriptSelected = { viewModel.onScriptSelected(it) },
        onStartBatchExtraction = { viewModel.startBatchExtraction(context) },
        onCancelBatchExtraction = { viewModel.cancelBatchExtraction() },
        onToggleViewMode = { viewModel.onToggleViewMode(it) },
        onCopyAll = { viewModel.copyAllToClipboard(context) },
        onSaveSingleTxt = { viewModel.saveAsSingleTxt(context) },
        onSaveSeparateFiles = { viewModel.saveAsSeparateFiles(context) },
        onShareAll = { viewModel.shareAll(context) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchOcrScreenContent(
    state: BatchOcrUiState,
    scrollBehavior: TopAppBarScrollBehavior,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onPickMultipleImages: () -> Unit,
    onRemoveFile: (Uri) -> Unit,
    onClearAll: () -> Unit,
    onScriptSelected: (OcrScript) -> Unit,
    onStartBatchExtraction: () -> Unit,
    onCancelBatchExtraction: () -> Unit,
    onToggleViewMode: (BatchOcrViewMode) -> Unit,
    onCopyAll: () -> Unit,
    onSaveSingleTxt: () -> Unit,
    onSaveSeparateFiles: () -> Unit,
    onShareAll: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MorphDropTopAppBar(
                title = "Batch OCR",
                scrollBehavior = scrollBehavior,
                showBackArrow = true,
                onBackClick = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Dropdown Script Selector (Only shown when images are selected)
            if (state.selectedUris.isNotEmpty()) {
                var expandedDropdown by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = expandedDropdown,
                    onExpandedChange = { expandedDropdown = !expandedDropdown },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = state.selectedScript.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Language / Script") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false }
                    ) {
                        OcrScript.entries.forEach { script ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(script.displayName, fontWeight = FontWeight.Bold)
                                        Text(script.description, style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                onClick = {
                                    onScriptSelected(script)
                                    expandedDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            if (state.selectedUris.isEmpty()) {
                // Empty state - File Selection Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onPickMultipleImages() },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.DocumentScanner,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Select Multiple Images for OCR",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap to pick document photos, scanned receipts, or notes to extract text in batch",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else if (state.results.isEmpty() && !state.isProcessing) {
                // File Selection Grid & Start Extraction Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${state.selectedUris.size} Images Selected",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onPickMultipleImages,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add")
                        }
                        OutlinedButton(
                            onClick = onClearAll,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Clear")
                        }
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.selectedUris) { uri ->
                        Box(
                            modifier = Modifier
                                .height(110.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(uri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = { onRemoveFile(uri) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(24.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                PrimaryButton(
                    text = "Extract Text From All (${state.selectedUris.size})",
                    onClick = onStartBatchExtraction,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (state.isProcessing) {
                // Processing view
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Processing Batch OCR...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.progressText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (state.progressTotal > 0) {
                            LinearProgressIndicator(
                                progress = { state.progressCurrent.toFloat() / state.progressTotal.toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        OutlinedButton(onClick = onCancelBatchExtraction) {
                            Text("Cancel Batch")
                        }
                    }
                }
            } else {
                // Results Screen (Combined View vs Individual View)
                TabRow(selectedTabIndex = if (state.viewMode == BatchOcrViewMode.COMBINED) 0 else 1) {
                    Tab(
                        selected = state.viewMode == BatchOcrViewMode.COMBINED,
                        onClick = { onToggleViewMode(BatchOcrViewMode.COMBINED) },
                        text = { Text("Combined View") }
                    )
                    Tab(
                        selected = state.viewMode == BatchOcrViewMode.INDIVIDUAL,
                        onClick = { onToggleViewMode(BatchOcrViewMode.INDIVIDUAL) },
                        text = { Text("Individual View (${state.results.size})") }
                    )
                }

                if (state.viewMode == BatchOcrViewMode.COMBINED) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = state.combinedText.ifBlank { "No text extracted." },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    // Individual View via HorizontalPager with Copy Button per image
                    val pagerState = rememberPagerState(pageCount = { state.results.size })
                    Column(modifier = Modifier.weight(1f)) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val item = state.results.getOrNull(page)
                            if (item != null) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    border = if (!item.isSuccess) BorderStroke(1.5.dp, MaterialTheme.colorScheme.error) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp)
                                    ) {
                                        // Header Row with Copy Button
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.fileName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (item.isSuccess && item.text.isNotBlank()) {
                                                    IconButton(
                                                        onClick = {
                                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                            val clip = ClipData.newPlainText("Extracted Text", item.text)
                                                            clipboard.setPrimaryClip(clip)
                                                            Toast.makeText(context, "Copied text for ${item.fileName}", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.ContentCopy,
                                                            contentDescription = "Copy text for this image",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(4.dp))
                                                if (item.isSuccess) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                } else {
                                                    Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(140.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context).data(item.uri).crossfade(true).build(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            Text(
                                                text = if (item.isSuccess) item.text else (item.errorMessage ?: "Failed to extract text"),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (item.isSuccess) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Actions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCopyAll,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy All", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = onSaveSingleTxt,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Single .txt", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = onSaveSeparateFiles,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Separate", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = onShareAll,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Light Mode - Empty", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun BatchOcrScreenLightEmptyPreview() {
    MorphDropTheme(darkTheme = false) {
        BatchOcrScreenContent(
            state = BatchOcrUiState(),
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onPickMultipleImages = {},
            onRemoveFile = {},
            onClearAll = {},
            onScriptSelected = {},
            onStartBatchExtraction = {},
            onCancelBatchExtraction = {},
            onToggleViewMode = {},
            onCopyAll = {},
            onSaveSingleTxt = {},
            onSaveSeparateFiles = {},
            onShareAll = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Dark Mode - Selection", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun BatchOcrScreenDarkSelectionPreview() {
    MorphDropTheme(darkTheme = true) {
        BatchOcrScreenContent(
            state = BatchOcrUiState(
                selectedUris = listOf(
                    Uri.parse("content://mock/1"),
                    Uri.parse("content://mock/2"),
                    Uri.parse("content://mock/3")
                )
            ),
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onPickMultipleImages = {},
            onRemoveFile = {},
            onClearAll = {},
            onScriptSelected = {},
            onStartBatchExtraction = {},
            onCancelBatchExtraction = {},
            onToggleViewMode = {},
            onCopyAll = {},
            onSaveSingleTxt = {},
            onSaveSeparateFiles = {},
            onShareAll = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Light Mode - Results", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun BatchOcrScreenLightResultsPreview() {
    MorphDropTheme(darkTheme = false) {
        BatchOcrScreenContent(
            state = BatchOcrUiState(
                selectedUris = listOf(
                    Uri.parse("content://mock/1"),
                    Uri.parse("content://mock/2")
                ),
                results = listOf(
                    BatchOcrItemResult(
                        uri = Uri.parse("content://mock/1"),
                        fileName = "receipt_01.jpg",
                        text = "Store #104\nTotal: $12.50\nThank you!",
                        isSuccess = true
                    ),
                    BatchOcrItemResult(
                        uri = Uri.parse("content://mock/2"),
                        fileName = "receipt_02.jpg",
                        text = "",
                        isSuccess = false,
                        errorMessage = "No readable text detected"
                    )
                ),
                combinedText = "--- File 1: receipt_01.jpg ---\nStore #104\nTotal: $12.50\nThank you!"
            ),
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onPickMultipleImages = {},
            onRemoveFile = {},
            onClearAll = {},
            onScriptSelected = {},
            onStartBatchExtraction = {},
            onCancelBatchExtraction = {},
            onToggleViewMode = {},
            onCopyAll = {},
            onSaveSingleTxt = {},
            onSaveSeparateFiles = {},
            onShareAll = {}
        )
    }
}
