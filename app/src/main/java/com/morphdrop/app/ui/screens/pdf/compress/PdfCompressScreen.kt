package com.morphdrop.app.ui.screens.pdf.compress

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.morphdrop.app.PdfViewerActivity
import com.morphdrop.app.domain.model.FileType
import com.morphdrop.app.domain.usecase.conversion.CompressionLevel
import com.morphdrop.app.ui.components.FormatBadge
import com.morphdrop.app.ui.components.MorphDropTopAppBar
import com.morphdrop.app.ui.components.PrimaryButton
import com.morphdrop.app.ui.screens.processing.ProcessingScreenContent
import com.morphdrop.app.ui.screens.processing.ProcessingUiState
import com.morphdrop.app.ui.screens.result.OutputFileItem
import com.morphdrop.app.ui.screens.result.ResultScreenContent
import com.morphdrop.app.ui.screens.result.ResultUiState
import com.morphdrop.app.ui.theme.MorphDropTheme
import com.morphdrop.app.util.FileHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfCompressScreen(
    initialUri: Uri? = null,
    onNavigateBack: () -> Unit,
    viewModel: PdfCompressViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(initialUri) {
        if (initialUri != null && state.selectedUri == null) {
            viewModel.onPdfSelected(initialUri)
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.onPdfSelected(uri)
        }
    }

    var hasAutoLaunchedPicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasAutoLaunchedPicker && initialUri == null && state.selectedUri == null) {
            hasAutoLaunchedPicker = true
            pdfPickerLauncher.launch(arrayOf("application/pdf"))
        }
    }

    when {
        state.isProcessing -> {
            val processingScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
            val stage = if (state.isTargetSizeMode && state.currentIteration > 0) {
                "Optimizing image stream... (Pass ${state.currentIteration} of ${state.maxIterations})"
            } else {
                "Compressing PDF resources..."
            }
            val progress = if (state.isTargetSizeMode && state.maxIterations > 0) {
                ((state.currentIteration.toFloat() / state.maxIterations.toFloat()) * 100f).coerceIn(10f, 95f)
            } else {
                50f
            }
            ProcessingScreenContent(
                state = ProcessingUiState(
                    progress = progress,
                    currentStage = stage,
                    fileName = state.fileName
                ),
                scrollBehavior = processingScrollBehavior,
                onCancel = viewModel::cancelProcessing
            )
        }
        state.isSuccess && state.resultUri != null -> {
            val resultScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
            val original = state.resultOriginalSize
            val compressed = state.resultNewSize
            val percentSaved = if (original > 0 && compressed < original) {
                (((original - compressed).toFloat() / original.toFloat()) * 100).toInt()
            } else 0
            val sizeSubtitle = if (percentSaved > 0) {
                "${FileHelper.formatFileSize(compressed)} (-$percentSaved%) • Saved ${FileHelper.formatFileSize(original - compressed)}"
            } else {
                "1 file created • ${FileHelper.formatFileSize(compressed)}"
            }

            ResultScreenContent(
                state = ResultUiState(
                    title = "PDF Compressed Successfully!",
                    subtitle = sizeSubtitle,
                    outputFiles = listOf(
                        OutputFileItem(
                            id = state.resultUri.toString(),
                            fileName = state.resultFileName,
                            fileSizeFormatted = FileHelper.formatFileSize(compressed),
                            extension = "pdf",
                            uri = state.resultUri
                        )
                    )
                ),
                scrollBehavior = resultScrollBehavior,
                onDone = onNavigateBack,
                onShare = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, state.resultUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Compressed PDF"))
                },
                onOpen = {
                    val intent = Intent(context, PdfViewerActivity::class.java).apply {
                        data = state.resultUri
                    }
                    context.startActivity(intent)
                }
            )
        }
        else -> {
            PdfCompressContent(
                state = state,
                onNavigateBack = onNavigateBack,
                onPickPdfClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                onSetTargetSizeMode = viewModel::setTargetSizeMode,
                onSetCompressionLevel = viewModel::setCompressionLevel,
                onSetQualitySlider = viewModel::setQualitySlider,
                onSetTargetSizeInput = viewModel::setTargetSizeInput,
                onSetTargetSizeUnit = viewModel::setTargetSizeUnit,
                onApplyPreset = viewModel::applyPreset,
                onCompressPdf = viewModel::compressPdf
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PdfCompressContent(
    state: PdfCompressUiState,
    onNavigateBack: () -> Unit = {},
    onPickPdfClick: () -> Unit = {},
    onSetTargetSizeMode: (Boolean) -> Unit = {},
    onSetCompressionLevel: (CompressionLevel) -> Unit = {},
    onSetQualitySlider: (Int) -> Unit = {},
    onSetTargetSizeInput: (String) -> Unit = {},
    onSetTargetSizeUnit: (Boolean) -> Unit = {},
    onApplyPreset: (Float) -> Unit = {},
    onCompressPdf: () -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MorphDropTopAppBar(
                title = "Compress PDF",
                scrollBehavior = scrollBehavior,
                showBackArrow = true,
                onBackClick = onNavigateBack,
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            AnimatedVisibility(visible = state.errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    ) {
                        if (state.selectedUri != null && state.previewBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                val pageAspectRatio = (state.previewBitmap.width.toFloat() / state.previewBitmap.height.toFloat()).coerceIn(0.4f, 2.5f)

                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight(0.92f)
                                        .aspectRatio(pageAspectRatio)
                                        .shadow(6.dp, RoundedCornerShape(4.dp))
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.Image(
                                        bitmap = state.previewBitmap.asImageBitmap(),
                                        contentDescription = "PDF Preview Page",
                                        contentScale = ContentScale.FillBounds,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        } else if (state.selectedUri != null && state.isAnalyzing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Outlined.PictureAsPdf,
                                        contentDescription = null,
                                        modifier = Modifier.size(56.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "PDF Document",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (state.selectedUri != null) state.fileName else "No File Selected",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (state.selectedUri != null) {
                                        "${FileHelper.formatFileSize(state.fileSize)} • ${state.pageCount} pages"
                                    } else {
                                        "Tap to select a PDF to reduce its size"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FormatBadge(fileType = FileType.PDF)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = onPickPdfClick,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (state.selectedUri != null) "Change PDF Document" else "Select PDF Document")
                        }
                    }
                }
            }

            if (state.selectedUri != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Document Analysis",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (state.isAnalyzing) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Analyzing images and vector content...", style = MaterialTheme.typography.bodySmall)
                            }
                        } else if (state.analysis != null) {
                            val a = state.analysis!!
                            Text(
                                text = "Contains ${a.imageCount} images (${FileHelper.formatFileSize(a.totalImageBytes)}) and ${FileHelper.formatFileSize(a.nonImageOverheadBytes)} vector/text content.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (a.imageCount == 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = "Text-only PDF: Compression will optimize fonts and stream dictionaries.",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Compression Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !state.isTargetSizeMode,
                            onClick = { onSetTargetSizeMode(false) },
                            label = { Text("Quality Mode") }
                        )
                        FilterChip(
                            selected = state.isTargetSizeMode,
                            onClick = { onSetTargetSizeMode(true) },
                            label = { Text("Target Size Mode (New)") }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!state.isTargetSizeMode) {
                        Text(
                            text = "Compression Preset",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val presets = listOf(
                                Triple(CompressionLevel.HIGH, "Extreme", "30%"),
                                Triple(CompressionLevel.MEDIUM, "Balanced", "60%"),
                                Triple(CompressionLevel.LOW, "Low", "85%")
                            )
                            presets.forEach { (level, name, _) ->
                                val isSelected = state.compressionLevel == level
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSetCompressionLevel(level) },
                                    label = {
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val presetDescription = when (state.compressionLevel) {
                            CompressionLevel.HIGH -> "Extreme • Maximum size reduction (30% quality)"
                            CompressionLevel.MEDIUM -> "Balanced (Recommended) • Best balance of size and quality (60%)"
                            CompressionLevel.LOW -> "Low • High visual fidelity (85% quality)"
                        }
                        Text(
                            text = presetDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Custom Quality: ${state.qualitySlider}%", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = state.qualitySlider.toFloat(),
                            onValueChange = { q -> onSetQualitySlider(q.toInt()) },
                            valueRange = 10f..100f
                        )
                    } else {
                        Text("Target File Size", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = state.targetSizeInput,
                                onValueChange = onSetTargetSizeInput,
                                label = { Text("Target Size") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Row {
                                FilterChip(
                                    selected = state.isTargetSizeMb,
                                    onClick = { onSetTargetSizeUnit(true) },
                                    label = { Text("MB") }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                FilterChip(
                                    selected = !state.isTargetSizeMb,
                                    onClick = { onSetTargetSizeUnit(false) },
                                    label = { Text("KB") }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Quick Presets", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(1f, 2f, 5f, 10f, 25f).forEach { mbVal ->
                                val label = if (mbVal == 25f) "Email (25 MB)" else "${mbVal.toInt()} MB"
                                FilterChip(
                                    selected = state.isTargetSizeMb && state.targetSizeInput == mbVal.toInt().toString(),
                                    onClick = { onApplyPreset(mbVal) },
                                    label = { Text(label) }
                                )
                            }
                        }

                        if (state.isTargetLargerThanOriginal) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(10.dp)
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("File is already smaller than or equal to target size.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else if (state.isTargetBelowMinimum) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(10.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Target is below estimated minimum (${FileHelper.formatFileSize(state.analysis?.estimatedMinBytes ?: 0)}). Compression will apply maximum possible reduction.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            PrimaryButton(
                text = "Compress PDF",
                onClick = onCompressPdf,
                enabled = !state.isProcessing && state.selectedUri != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )
        }
    }
}

// ----------------------------------------------------
// COMPOSE PREVIEWS FOR ANDROID STUDIO
// ----------------------------------------------------

@Preview(name = "Compress PDF - Empty Light", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun PdfCompressScreenEmptyLightPreview() {
    MorphDropTheme(darkTheme = false) {
        PdfCompressContent(
            state = PdfCompressUiState()
        )
    }
}

@Preview(name = "Compress PDF - Empty Dark", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PdfCompressScreenEmptyDarkPreview() {
    MorphDropTheme(darkTheme = true) {
        PdfCompressContent(
            state = PdfCompressUiState()
        )
    }
}

@Preview(name = "Compress PDF - Selected Light", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun PdfCompressScreenSelectedLightPreview() {
    MorphDropTheme(darkTheme = false) {
        PdfCompressContent(
            state = PdfCompressUiState(
                selectedUri = Uri.parse("content://dummy/large_scanned.pdf"),
                fileName = "Large_Scanned_Document.pdf",
                fileSize = 14_850_000L,
                pageCount = 28,
                compressionLevel = CompressionLevel.MEDIUM,
                qualitySlider = 60
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Compress PDF - Result Light", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun PdfCompressScreenResultLightPreview() {
    MorphDropTheme(darkTheme = false) {
        ResultScreenContent(
            state = ResultUiState(
                title = "PDF Compressed Successfully!",
                subtitle = "5.2 MB (-65%) • Saved 9.6 MB",
                outputFiles = listOf(
                    OutputFileItem(
                        id = "1",
                        fileName = "Large_Scanned_Document_compressed.pdf",
                        fileSizeFormatted = "5.2 MB",
                        extension = "pdf",
                        uri = null
                    )
                )
            ),
            scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState()),
            onDone = {},
            onShare = {},
            onOpen = {}
        )
    }
}
