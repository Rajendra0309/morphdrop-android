package com.morphdrop.app.ui.screens.ocr

import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.domain.model.OcrScript
import com.morphdrop.app.ui.components.MorphDropTopAppBar
import com.morphdrop.app.ui.components.PrimaryButton
import com.morphdrop.app.ui.theme.MorphDropTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBatchOcr: () -> Unit = {},
    viewModel: OcrViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onFileSelected(context, uri)
        }
    }

    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { err ->
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
    }

    OcrScreenContent(
        state = state,
        scrollBehavior = scrollBehavior,
        onNavigateBack = onNavigateBack,
        onNavigateToBatchOcr = onNavigateToBatchOcr,
        onPickFileClick = { filePickerLauncher.launch("*/*") },
        onDismissDisclaimer = { viewModel.dismissDisclaimer() },
        onDismissSaveFileNameDialog = { viewModel.dismissSaveFileNameDialog() },
        onConfirmSaveAsTxt = { viewModel.confirmSaveAsTxt(context) },
        onCustomFileNameChange = { viewModel.onCustomFileNameChange(it) },
        onToggleExtractAllPages = { viewModel.onToggleExtractAllPages(it) },
        onPageSelected = { page -> viewModel.onPageSelected(context, page) },
        onScriptSelected = { script -> viewModel.onScriptSelected(script) },
        onStartExtraction = { viewModel.startExtraction() },
        onCopyToClipboard = { viewModel.copyToClipboard(context) },
        onOpenSaveFileNameDialog = { viewModel.openSaveFileNameDialog() },
        onShareText = { viewModel.shareText(context) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreenContent(
    state: OcrUiState,
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigateBack: () -> Unit,
    onNavigateToBatchOcr: () -> Unit = {},
    onPickFileClick: () -> Unit,
    onDismissDisclaimer: () -> Unit,
    onDismissSaveFileNameDialog: () -> Unit,
    onConfirmSaveAsTxt: () -> Unit,
    onCustomFileNameChange: (String) -> Unit,
    onToggleExtractAllPages: (Boolean) -> Unit,
    onPageSelected: (Int) -> Unit,
    onScriptSelected: (OcrScript) -> Unit,
    onStartExtraction: () -> Unit,
    onCopyToClipboard: () -> Unit,
    onOpenSaveFileNameDialog: () -> Unit,
    onShareText: () -> Unit
) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MorphDropTopAppBar(
                title = "Extract Text (OCR)",
                scrollBehavior = scrollBehavior,
                showBackArrow = true,
                onBackClick = onNavigateBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // First time disclaimer modal (Shows ONCE ONLY on first launch)
            if (state.showDisclaimerDialog) {
                AlertDialog(
                    onDismissRequest = onDismissDisclaimer,
                    icon = { Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = { Text(text = "On-Device Text Recognition") },
                    text = {
                        Text(
                            text = "First time OCR use downloads a ~20MB on-device language model. After downloading, text recognition works 100% offline with zero data sent off your device."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = onDismissDisclaimer) {
                            Text("Got it")
                        }
                    }
                )
            }

            // Save Filename Dialog
            if (state.showSaveFileNameDialog) {
                AlertDialog(
                    onDismissRequest = onDismissSaveFileNameDialog,
                    icon = { Icon(Icons.Outlined.Save, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = { Text("Save Extracted Text") },
                    text = {
                        Column {
                            Text(
                                text = "Enter file name to save in Downloads/MorphDrop:",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = state.customSaveFileName,
                                onValueChange = onCustomFileNameChange,
                                label = { Text("File Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = onConfirmSaveAsTxt) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = onDismissSaveFileNameDialog) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Batch OCR Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToBatchOcr() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Batch OCR (Multiple Files)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Extract text from multiple images or receipts at once",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Button(
                        onClick = onNavigateToBatchOcr,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Open")
                    }
                }
            }

            // 1. File Selection Card & Live Thumbnail Preview
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onPickFileClick() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                if (state.selectedUri == null) {
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
                                text = "Select Image or PDF Document",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap to choose any image format (PNG, JPG, WEBP, BMP, HEIC) or PDF file",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Header details
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (state.isPdf) Icons.Outlined.PictureAsPdf else Icons.Outlined.AddPhotoAlternate,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.fileName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(state.fileSizeFormatted, maxLines = 1) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        modifier = Modifier.height(24.dp)
                                    )
                                    if (state.isPdf) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        AssistChip(
                                            onClick = {},
                                            label = { Text("${state.pdfPageCount} Page(s)", maxLines = 1) },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            ),
                                            modifier = Modifier.height(24.dp)
                                        )
                                    }
                                }
                            }
                            TextButton(onClick = onPickFileClick) {
                                Text("Change")
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Live Thumbnail Preview Container
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 260.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(14.dp)
                                ),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (state.isThumbnailLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                                } else if (state.currentThumbnailBitmap != null) {
                                    Image(
                                        bitmap = state.currentThumbnailBitmap.asImageBitmap(),
                                        contentDescription = "Content Preview",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp)
                                    )
                                } else {
                                    Text(
                                        text = "Preview unavailable",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. PDF Page Options & Live Page Navigator
            if (state.isPdf && state.selectedUri != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "PDF Page Options",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Extract text from ALL pages sequentially",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Switch(
                                checked = state.extractAllPages,
                                onCheckedChange = onToggleExtractAllPages
                            )
                        }

                        if (!state.extractAllPages && state.pdfPageCount > 1) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                IconButton(
                                    onClick = { onPageSelected(state.selectedPage - 1) },
                                    enabled = state.selectedPage > 1
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = "Previous Page")
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "Page ${state.selectedPage} of ${state.pdfPageCount}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                IconButton(
                                    onClick = { onPageSelected(state.selectedPage + 1) },
                                    enabled = state.selectedPage < state.pdfPageCount
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = "Next Page")
                                }
                            }
                        }
                    }
                }
            }

            // 3. Language Selector & Extraction Action Button
            if (state.selectedUri != null) {
                var expanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = state.selectedScript.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Language / Script") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
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
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                PrimaryButton(
                    text = if (state.isExtracting) "Extracting Text..." else "Extract Text (OCR)",
                    onClick = onStartExtraction,
                    enabled = !state.isExtracting,
                    modifier = Modifier.fillMaxWidth()
                )

                if (state.isExtracting) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (state.progressFraction > 0f) {
                            LinearProgressIndicator(
                                progress = { state.progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.progressText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 4. Extracted Result Workbench View
            AnimatedVisibility(
                visible = state.extractedText.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Extracted Text",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "${state.wordCount} words",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "${state.charCount} chars",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        SelectionContainer {
                            OutlinedTextField(
                                value = state.extractedText,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 200.dp, max = 380.dp),
                                shape = RoundedCornerShape(14.dp),
                                textStyle = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Single-Line Action Buttons Bar: Copy, Save .txt, Share
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = onCopyToClipboard,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy", maxLines = 1, softWrap = false)
                            }

                            Button(
                                onClick = onOpenSaveFileNameDialog,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save .txt", maxLines = 1, softWrap = false)
                            }

                            OutlinedButton(
                                onClick = onShareText,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Light Mode", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun OcrScreenLightPreview() {
    MorphDropTheme(darkTheme = false) {
        OcrScreenContent(
            state = OcrUiState(
                fileName = "Scanned_Invoice.pdf",
                fileSizeFormatted = "1.4 MB",
                isPdf = true,
                pdfPageCount = 3,
                selectedUri = Uri.parse("content://mock"),
                extractedText = "Sample Extracted Text\nInvoice #1024\nTotal: $49.99",
                wordCount = 6,
                charCount = 42
            ),
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            onNavigateBack = {},
            onPickFileClick = {},
            onDismissDisclaimer = {},
            onDismissSaveFileNameDialog = {},
            onConfirmSaveAsTxt = {},
            onCustomFileNameChange = {},
            onToggleExtractAllPages = {},
            onPageSelected = {},
            onScriptSelected = {},
            onStartExtraction = {},
            onCopyToClipboard = {},
            onOpenSaveFileNameDialog = {},
            onShareText = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun OcrScreenDarkPreview() {
    MorphDropTheme(darkTheme = true) {
        OcrScreenContent(
            state = OcrUiState(
                fileName = "Scanned_Invoice.pdf",
                fileSizeFormatted = "1.4 MB",
                isPdf = true,
                pdfPageCount = 3,
                selectedUri = Uri.parse("content://mock"),
                extractedText = "Sample Extracted Text\nInvoice #1024\nTotal: $49.99",
                wordCount = 6,
                charCount = 42
            ),
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            onNavigateBack = {},
            onPickFileClick = {},
            onDismissDisclaimer = {},
            onDismissSaveFileNameDialog = {},
            onConfirmSaveAsTxt = {},
            onCustomFileNameChange = {},
            onToggleExtractAllPages = {},
            onPageSelected = {},
            onScriptSelected = {},
            onStartExtraction = {},
            onCopyToClipboard = {},
            onOpenSaveFileNameDialog = {},
            onShareText = {}
        )
    }
}
