package com.morphdrop.app.ui.screens.pdf.batch

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import android.widget.Toast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.morphdrop.app.PdfViewerActivity
import com.morphdrop.app.domain.model.BatchCompressConfig
import com.morphdrop.app.domain.model.BatchPdfItemResult
import com.morphdrop.app.domain.model.BatchPdfOperation
import com.morphdrop.app.domain.model.PageNumberConfig
import com.morphdrop.app.domain.model.PageNumberFormat
import com.morphdrop.app.domain.model.PageNumberPosition
import com.morphdrop.app.domain.model.RotateScope
import com.morphdrop.app.domain.model.WatermarkConfig
import com.morphdrop.app.domain.model.WatermarkPosition
import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.morphdrop.app.domain.model.WatermarkType
import com.morphdrop.app.ui.components.MorphDropTopAppBar
import com.morphdrop.app.ui.components.PrimaryButton
import com.morphdrop.app.ui.theme.MorphDropTheme
import com.morphdrop.app.util.FileHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchPdfScreen(
    onNavigateBack: () -> Unit,
    viewModel: BatchPdfViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        viewModel.onFilesSelected(uris)
    }

    // System Back Gesture / Hardware Back Button Handler
    BackHandler(enabled = state.currentStep != BatchPdfStep.SELECT_FILES) {
        if (state.currentStep == BatchPdfStep.PROCESSING) {
            viewModel.cancelBatchProcessing()
        } else {
            viewModel.onBackStep()
        }
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

    val topBarTitle = when (state.currentStep) {
        BatchPdfStep.SELECT_FILES -> "Batch PDF Operations"
        BatchPdfStep.SELECT_OPERATION -> "Select Operation"
        BatchPdfStep.CONFIGURE -> state.selectedOperation.displayName
        BatchPdfStep.PROCESSING -> "Processing..."
        BatchPdfStep.RESULTS -> "Batch Complete"
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MorphDropTopAppBar(
                title = topBarTitle,
                scrollBehavior = scrollBehavior,
                showBackArrow = true,
                onBackClick = {
                    if (state.currentStep == BatchPdfStep.SELECT_FILES || state.currentStep == BatchPdfStep.RESULTS) {
                        onNavigateBack()
                    } else if (state.currentStep == BatchPdfStep.PROCESSING) {
                        viewModel.cancelBatchProcessing()
                    } else {
                        viewModel.onBackStep()
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Interactive Step Breadcrumbs
            StepProgressBar(
                currentStep = state.currentStep,
                hasFiles = state.selectedFiles.isNotEmpty(),
                onStepClick = { step -> viewModel.onNavigateToStep(step) }
            )

            AnimatedContent(
                targetState = state.currentStep,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "BatchPdfStepContent"
            ) { step ->
                when (step) {
                    BatchPdfStep.SELECT_FILES -> {
                        SelectFilesStepContent(
                            state = state,
                            onPickFiles = { pdfPickerLauncher.launch("application/pdf") },
                            onRemoveFile = viewModel::onRemoveFile,
                            onClearAll = viewModel::onClearAllFiles,
                            onMoveUp = viewModel::onMoveFileUp,
                            onMoveDown = viewModel::onMoveFileDown,
                            onProceed = viewModel::onProceedToOperationSelection
                        )
                    }

                    BatchPdfStep.SELECT_OPERATION -> {
                        SelectOperationStepContent(
                            state = state,
                            onSelectOperation = viewModel::onSelectOperation
                        )
                    }

                    BatchPdfStep.CONFIGURE -> {
                        ConfigureOperationStepContent(
                            state = state,
                            viewModel = viewModel,
                            onStartProcessing = viewModel::startBatchProcessing
                        )
                    }

                    BatchPdfStep.PROCESSING -> {
                        ProcessingStepContent(
                            state = state,
                            onCancel = viewModel::cancelBatchProcessing
                        )
                    }

                    BatchPdfStep.RESULTS -> {
                        ResultsStepContent(
                            state = state,
                            onOpenFolder = { viewModel.openFolder(context) },
                            onShareAll = { viewModel.shareAll(context) },
                            onOpenFile = { uriString ->
                                try {
                                    val uri = Uri.parse(uriString)
                                    val intent = Intent(context, PdfViewerActivity::class.java).apply {
                                        action = Intent.ACTION_VIEW
                                        data = uri
                                        putExtra("pdf_uri", uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    try {
                                        val uri = Uri.parse(uriString)
                                        val externalIntent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "application/pdf")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(externalIntent, "Open PDF"))
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Cannot open PDF file", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onDone = onNavigateBack
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepProgressBar(
    currentStep: BatchPdfStep,
    hasFiles: Boolean,
    onStepClick: (BatchPdfStep) -> Unit
) {
    val steps = listOf(
        BatchPdfStep.SELECT_FILES to "Files",
        BatchPdfStep.SELECT_OPERATION to "Action",
        BatchPdfStep.CONFIGURE to "Settings",
        BatchPdfStep.RESULTS to "Results"
    )

    val activeIndex = when (currentStep) {
        BatchPdfStep.SELECT_FILES -> 0
        BatchPdfStep.SELECT_OPERATION -> 1
        BatchPdfStep.CONFIGURE -> 2
        BatchPdfStep.PROCESSING, BatchPdfStep.RESULTS -> 3
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            steps.forEachIndexed { index, (stepEnum, label) ->
                val isActive = index <= activeIndex
                val isCurrent = index == activeIndex
                val isClickable = index < activeIndex && currentStep != BatchPdfStep.PROCESSING

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = isClickable) { onStepClick(stepEnum) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (index < activeIndex) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                        }
                        Text(
                            text = "${index + 1}. $label",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            color = when {
                                isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
                                isActive -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectFilesStepContent(
    state: BatchPdfUiState,
    onPickFiles: () -> Unit,
    onRemoveFile: (Uri) -> Unit,
    onClearAll: () -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onProceed: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Selection Summary Card
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${state.selectedFiles.size} PDFs Selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Total Size: ${FileHelper.formatFileSize(state.totalSizeBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onPickFiles,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (state.selectedFiles.isEmpty()) "Select PDFs" else "Add More")
                        }
                        if (state.selectedFiles.isNotEmpty()) {
                            IconButton(onClick = onClearAll) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear All", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (state.selectedFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No PDFs Selected Yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap 'Select PDFs' to choose multiple files from storage",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(state.selectedFiles, key = { _, item -> item.uri.toString() }) { index, file ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.PictureAsPdf,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${index + 1}.  ${FileHelper.formatFileSize(file.sizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row {
                                if (index > 0) {
                                    IconButton(onClick = { onMoveUp(index) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (index < state.selectedFiles.size - 1) {
                                    IconButton(onClick = { onMoveDown(index) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                                    }
                                }
                                IconButton(onClick = { onRemoveFile(file.uri) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            PrimaryButton(
                text = "Next: Choose Operation (${state.selectedFiles.size} PDFs)",
                onClick = onProceed,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SelectOperationStepContent(
    state: BatchPdfUiState,
    onSelectOperation: (BatchPdfOperation) -> Unit
) {
    val operations = BatchPdfOperation.entries

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Choose Operation",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(operations) { op ->
                val icon = when (op) {
                    BatchPdfOperation.COMPRESS -> Icons.Outlined.Compress
                    BatchPdfOperation.WATERMARK -> Icons.Outlined.Layers
                    BatchPdfOperation.PAGE_NUMBERS -> Icons.Outlined.FormatListNumbered
                    BatchPdfOperation.PASSWORD -> Icons.Outlined.Lock
                    BatchPdfOperation.ROTATE -> Icons.AutoMirrored.Outlined.RotateRight
                    BatchPdfOperation.MERGE -> Icons.AutoMirrored.Outlined.MergeType
                }

                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectOperation(op) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = op.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = op.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigureOperationStepContent(
    state: BatchPdfUiState,
    viewModel: BatchPdfViewModel? = null,
    onStartProcessing: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        when (state.selectedOperation) {
            BatchPdfOperation.COMPRESS -> CompressConfigSection(state = state, viewModel = viewModel)
            BatchPdfOperation.WATERMARK -> WatermarkConfigSection(state = state, viewModel = viewModel)
            BatchPdfOperation.PAGE_NUMBERS -> PageNumbersConfigSection(state = state, viewModel = viewModel)
            BatchPdfOperation.PASSWORD -> PasswordConfigSection(state = state, viewModel = viewModel)
            BatchPdfOperation.ROTATE -> RotateConfigSection(state = state, viewModel = viewModel)
            BatchPdfOperation.MERGE -> MergeConfigSection(state = state, viewModel = viewModel)
        }

        PrimaryButton(
            text = "Apply to All (${state.selectedFiles.size} PDFs)",
            onClick = onStartProcessing,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ----------------------------------------------------
// OPERATION CONFIG SECTIONS
// ----------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompressConfigSection(
    state: BatchPdfUiState,
    viewModel: BatchPdfViewModel? = null
) {
    val config = state.compressConfig

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Compression Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !config.isTargetSizeMode,
                    onClick = { viewModel?.updateCompressConfig { it.copy(isTargetSizeMode = false) } },
                    label = { Text("Quality Mode") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = config.isTargetSizeMode,
                    onClick = { viewModel?.updateCompressConfig { it.copy(isTargetSizeMode = true) } },
                    label = { Text("Target Size Mode") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!config.isTargetSizeMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quality Level",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${(config.quality * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = config.quality,
                    onValueChange = { q -> viewModel?.updateCompressConfig { it.copy(quality = q) } },
                    valueRange = 0.2f..0.9f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = config.quality <= 0.35f,
                        onClick = { viewModel?.updateCompressConfig { it.copy(quality = 0.3f) } },
                        label = { Text("Max (-70%)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = config.quality in 0.36f..0.65f,
                        onClick = { viewModel?.updateCompressConfig { it.copy(quality = 0.5f) } },
                        label = { Text("Balanced") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = config.quality > 0.65f,
                        onClick = { viewModel?.updateCompressConfig { it.copy(quality = 0.8f) } },
                        label = { Text("Light") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Size Estimation Card
                val estimatedSize = (state.totalSizeBytes * config.quality).toLong()
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Input Size", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(FileHelper.formatFileSize(state.totalSizeBytes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Text("→", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Estimated Output", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("~${FileHelper.formatFileSize(estimatedSize)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else {
                Text(
                    text = "Target Size per PDF (MB):",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = if (config.targetSizeMb > 0f) config.targetSizeMb.toString() else "",
                    onValueChange = { str ->
                        val floatVal = str.toFloatOrNull() ?: 0f
                        viewModel?.updateCompressConfig { it.copy(targetSizeMb = floatVal) }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    suffix = { Text("MB") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1f, 2f, 5f, 10f, 25f).forEach { preset ->
                        FilterChip(
                            selected = config.targetSizeMb == preset,
                            onClick = { viewModel?.updateCompressConfig { it.copy(targetSizeMb = preset) } },
                            label = { Text(if (preset == 25f) "Email (25 MB)" else "${preset.toInt()} MB") }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WatermarkConfigSection(
    state: BatchPdfUiState,
    viewModel: BatchPdfViewModel? = null
) {
    val config = state.watermarkConfig

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel?.updateWatermarkConfig { it.copy(imageUri = uri.toString()) }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Watermark Type",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = config.type == WatermarkType.TEXT,
                    onClick = { viewModel?.updateWatermarkConfig { it.copy(type = WatermarkType.TEXT) } },
                    label = { Text("Text Watermark") }
                )
                FilterChip(
                    selected = config.type == WatermarkType.IMAGE,
                    onClick = { viewModel?.updateWatermarkConfig { it.copy(type = WatermarkType.IMAGE) } },
                    label = { Text("Image Watermark") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (config.type == WatermarkType.TEXT) {
                OutlinedTextField(
                    value = config.text,
                    onValueChange = { t -> viewModel?.updateWatermarkConfig { it.copy(text = t) } },
                    label = { Text("Watermark Text") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Font Size: ${config.fontSizeSp.toInt()} sp", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = config.fontSizeSp,
                    onValueChange = { sz -> viewModel?.updateWatermarkConfig { it.copy(fontSizeSp = sz) } },
                    valueRange = 14f..72f
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text("Color", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val colors = listOf(
                        0xFF888888 to "Gray",
                        0xFFE53935 to "Red",
                        0xFF1E88E5 to "Blue",
                        0xFF000000 to "Black"
                    )
                    colors.forEach { (colorVal, _) ->
                        val isSelected = config.fontColor == colorVal
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(colorVal))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { viewModel?.updateWatermarkConfig { it.copy(fontColor = colorVal) } }
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { imagePicker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (config.imageUri != null) "Change Selected Image" else "Choose Image from Gallery")
                }

                if (config.imageUri != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = config.imageUri,
                                contentDescription = "Watermark Thumbnail",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Watermark Image Ready",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Scale: ${(config.imageScale * 100).toInt()}% • Rotation: ${config.rotationDegrees.toInt()}°",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { viewModel?.updateWatermarkConfig { it.copy(imageUri = null) } }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove Image",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Image Scale: ${(config.imageScale * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = config.imageScale,
                    onValueChange = { s -> viewModel?.updateWatermarkConfig { it.copy(imageScale = s) } },
                    valueRange = 0.1f..1.0f
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Opacity: ${(config.opacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = config.opacity,
                onValueChange = { o -> viewModel?.updateWatermarkConfig { it.copy(opacity = o) } },
                valueRange = 0.1f..1.0f
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("Rotation: ${config.rotationDegrees.toInt()}°", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = config.rotationDegrees,
                onValueChange = { r -> viewModel?.updateWatermarkConfig { it.copy(rotationDegrees = r) } },
                valueRange = 0f..360f
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("Position", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WatermarkPosition.entries.forEach { pos ->
                    FilterChip(
                        selected = config.position == pos,
                        onClick = { viewModel?.updateWatermarkConfig { it.copy(position = pos) } },
                        label = { Text(pos.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Skip First Page (Cover)", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = config.skipFirstPage,
                    onCheckedChange = { sk -> viewModel?.updateWatermarkConfig { it.copy(skipFirstPage = sk) } }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live Preview Card
            Text("Live Preview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            WatermarkLivePreviewCard(config = config)
        }
    }
}

@Composable
private fun WatermarkLivePreviewCard(config: WatermarkConfig) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        val alignment = when (config.position) {
            WatermarkPosition.CENTER, WatermarkPosition.DIAGONAL -> Alignment.Center
            WatermarkPosition.TOP_LEFT -> Alignment.TopStart
            WatermarkPosition.TOP_RIGHT -> Alignment.TopEnd
            WatermarkPosition.BOTTOM_LEFT -> Alignment.BottomStart
            WatermarkPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = alignment
        ) {
            if (config.type == WatermarkType.TEXT) {
                Text(
                    text = config.text.ifBlank { "CONFIDENTIAL" },
                    fontSize = (config.fontSizeSp * 0.5f).sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(config.fontColor).copy(alpha = config.opacity),
                    modifier = Modifier.rotate(config.rotationDegrees)
                )
            } else {
                if (config.imageUri != null) {
                    AsyncImage(
                        model = config.imageUri,
                        contentDescription = "Image Watermark Live Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size((90 * config.imageScale).coerceAtLeast(30f).dp)
                            .rotate(config.rotationDegrees)
                            .alpha(config.opacity)
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .rotate(config.rotationDegrees)
                            .alpha(config.opacity)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier.size((75 * config.imageScale).coerceAtLeast(40f).dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size((32 * config.imageScale).coerceAtLeast(20f).dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "IMAGE STAMP",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageNumbersConfigSection(
    state: BatchPdfUiState,
    viewModel: BatchPdfViewModel? = null,
    onUpdateConfig: (((PageNumberConfig) -> PageNumberConfig) -> Unit)? = null
) {
    val config = state.pageNumberConfig
    val update: ((PageNumberConfig) -> PageNumberConfig) -> Unit = { fn ->
        if (onUpdateConfig != null) {
            onUpdateConfig(fn)
        } else {
            viewModel?.updatePageNumberConfig(fn)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Page Numbers Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(14.dp))

            Text("Position", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PageNumberPosition.entries.forEach { pos ->
                    FilterChip(
                        selected = config.position == pos,
                        onClick = { update { it.copy(position = pos) } },
                        label = { Text(pos.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("Format", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PageNumberFormat.entries.forEach { fmt ->
                    FilterChip(
                        selected = config.format == fmt,
                        onClick = {
                            update {
                                it.copy(
                                    format = fmt,
                                    customTemplate = if (fmt == PageNumberFormat.CUSTOM) "- {page} -" else it.customTemplate
                                )
                            }
                        },
                        label = { Text(fmt.example) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("Font Size: ${config.fontSizeSp.toInt()} sp", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = config.fontSizeSp,
                onValueChange = { fs -> update { it.copy(fontSizeSp = fs) } },
                valueRange = 8f..28f
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Skip First Page (Cover)", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = config.skipFirstPage,
                    onCheckedChange = { sk -> update { it.copy(skipFirstPage = sk) } }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Skip Last Page", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = config.skipLastPage,
                    onCheckedChange = { sk -> update { it.copy(skipLastPage = sk) } }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Live Preview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            PageNumberLivePreviewCard(config = config)
        }
    }
}

@Composable
private fun PageNumberLivePreviewCard(config: PageNumberConfig) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        val alignment = when (config.position) {
            PageNumberPosition.TOP_LEFT -> Alignment.TopStart
            PageNumberPosition.TOP_CENTER -> Alignment.TopCenter
            PageNumberPosition.TOP_RIGHT -> Alignment.TopEnd
            PageNumberPosition.BOTTOM_LEFT -> Alignment.BottomStart
            PageNumberPosition.BOTTOM_CENTER -> Alignment.BottomCenter
            PageNumberPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
        }

        val sampleText = when (config.format) {
            PageNumberFormat.PAGE_X -> "Page 1"
            PageNumberFormat.NUMBER_ONLY -> "1"
            PageNumberFormat.PAGE_X_OF_Y -> "Page 1 of 10"
            PageNumberFormat.X_OF_Y -> "1/10"
            PageNumberFormat.CUSTOM -> {
                val tmpl = if (config.customTemplate.isNotBlank() && config.customTemplate.contains("{page}")) {
                    config.customTemplate
                } else {
                    "- {page} -"
                }
                tmpl.replace("{page}", "1").replace("{total}", "10")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            contentAlignment = alignment
        ) {
            Text(
                text = sampleText,
                fontSize = (config.fontSizeSp * 0.9f).sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(config.fontColor)
            )
        }
    }
}

@Composable
private fun PasswordConfigSection(
    state: BatchPdfUiState,
    viewModel: BatchPdfViewModel? = null
) {
    val config = state.passwordConfig
    var passwordVisible by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Password Protection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = config.password,
                onValueChange = { p -> viewModel?.updatePasswordConfig { it.copy(password = p) } },
                label = { Text("Enter Document Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Document Permissions", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = config.allowPrinting,
                    onCheckedChange = { pr -> viewModel?.updatePasswordConfig { it.copy(allowPrinting = pr) } }
                )
                Text("Allow Printing", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = config.allowCopying,
                    onCheckedChange = { cp -> viewModel?.updatePasswordConfig { it.copy(allowCopying = cp) } }
                )
                Text("Allow Copying Content", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = config.allowEditing,
                    onCheckedChange = { ed -> viewModel?.updatePasswordConfig { it.copy(allowEditing = ed) } }
                )
                Text("Allow Modifying Pages", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RotateConfigSection(
    state: BatchPdfUiState,
    viewModel: BatchPdfViewModel? = null
) {
    val config = state.rotateConfig

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Rotation Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(14.dp))

            Text("Rotation Angle", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(90, 180, 270).forEach { deg ->
                    FilterChip(
                        selected = config.degrees == deg,
                        onClick = { viewModel?.updateRotateConfig { it.copy(degrees = deg) } },
                        label = { Text("$deg°") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("Apply Rotation To", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RotateScope.entries.forEach { scope ->
                    FilterChip(
                        selected = config.scope == scope,
                        onClick = { viewModel?.updateRotateConfig { it.copy(scope = scope) } },
                        label = { Text(scope.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Visual orientation preview
            Text("Orientation Preview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier
                            .width(60.dp)
                            .height(80.dp)
                            .rotate(config.degrees.toFloat()),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "A4",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MergeConfigSection(
    state: BatchPdfUiState,
    viewModel: BatchPdfViewModel? = null
) {
    val config = state.mergeConfig

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Merge Options",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Clean toggle row with proper layout
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Add Continuous Page Numbers",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Number all pages sequentially in the final merged PDF",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.addPageNumbers,
                        onCheckedChange = { num -> viewModel?.updateMergeConfig { it.copy(addPageNumbers = num) } }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // File merge order sequence list
            Text(
                text = "Merge Order (${state.selectedFiles.size} PDFs)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Files will be concatenated in this order. Use arrows to reorder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.selectedFiles.forEachIndexed { index, file ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = FileHelper.formatFileSize(file.sizeBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (index > 0) {
                                IconButton(onClick = { viewModel?.onMoveFileUp(index) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                                }
                            }
                            if (index < state.selectedFiles.size - 1) {
                                IconButton(onClick = { viewModel?.onMoveFileDown(index) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessingStepContent(
    state: BatchPdfUiState,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Single central circular progress indicator with percentage inside
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(130.dp)
        ) {
            CircularProgressIndicator(
                progress = { state.progressPercent / 100f },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 10.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${state.progressPercent}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Processing ${state.progressCurrent} of ${state.progressTotal} PDFs",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // File Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = state.progressFile.ifBlank { "Preparing documents..." },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedButton(
            onClick = onCancel,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Cancel Batch Operation")
        }
    }
}

@Composable
private fun ResultsStepContent(
    state: BatchPdfUiState,
    onOpenFolder: () -> Unit,
    onShareAll: () -> Unit,
    onOpenFile: (String) -> Unit,
    onDone: () -> Unit
) {
    val successCount = state.results.count { it.isSuccess }
    val failCount = state.results.size - successCount

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Summary Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "$successCount of ${state.results.size} Processed",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (failCount > 0) {
                            Text(
                                text = "$failCount failed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                if (state.outputFolder != null) {
                    val fullPath = "Downloads/${state.outputFolder}"
                    val clipboardManager = LocalClipboardManager.current
                    val ctx = LocalContext.current
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = fullPath,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(fullPath))
                                    Toast.makeText(ctx, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Path",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Processed Files (${state.results.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.results) { res ->
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = res.isSuccess && res.outputUriString != null) {
                            res.outputUriString?.let { onOpenFile(it) }
                        },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (res.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (res.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = res.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (res.isSuccess) {
                                val origFmt = FileHelper.formatFileSize(res.originalSize)
                                val newFmt = FileHelper.formatFileSize(res.newSize)
                                Text(
                                    text = if (res.newSize > 0 && res.newSize != res.originalSize) "$origFmt → $newFmt" else origFmt,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = res.errorMessage ?: "Failed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        if (res.isSuccess && res.outputUriString != null) {
                            FilledTonalButton(
                                onClick = { onOpenFile(res.outputUriString) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Open", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onOpenFolder,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Folder")
            }

            OutlinedButton(
                onClick = onShareAll,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share All")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        PrimaryButton(
            text = "Done",
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ----------------------------------------------------
// COMPOSE PREVIEWS FOR ANDROID STUDIO
// ----------------------------------------------------

@Preview(name = "Page Numbers Settings - Light", showBackground = true)
@Composable
fun PageNumbersConfigSectionLightPreview() {
    MorphDropTheme(darkTheme = false) {
        Surface(modifier = Modifier.padding(16.dp)) {
            PageNumbersConfigSection(
                state = BatchPdfUiState(
                    selectedOperation = BatchPdfOperation.PAGE_NUMBERS,
                    pageNumberConfig = PageNumberConfig(
                        position = PageNumberPosition.BOTTOM_CENTER,
                        format = PageNumberFormat.CUSTOM,
                        customTemplate = "- {page} -"
                    )
                ),
                onUpdateConfig = {}
            )
        }
    }
}

@Preview(name = "Page Numbers Settings - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PageNumbersConfigSectionDarkPreview() {
    MorphDropTheme(darkTheme = true) {
        Surface(modifier = Modifier.padding(16.dp)) {
            PageNumbersConfigSection(
                state = BatchPdfUiState(
                    selectedOperation = BatchPdfOperation.PAGE_NUMBERS,
                    pageNumberConfig = PageNumberConfig(
                        position = PageNumberPosition.BOTTOM_CENTER,
                        format = PageNumberFormat.CUSTOM,
                        customTemplate = "- {page} -"
                    )
                ),
                onUpdateConfig = {}
            )
        }
    }
}

@Preview(name = "Page Numbers Live Preview Card", showBackground = true)
@Composable
fun PageNumberLivePreviewCardPreview() {
    MorphDropTheme(darkTheme = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            PageNumberLivePreviewCard(
                config = PageNumberConfig(
                    position = PageNumberPosition.BOTTOM_CENTER,
                    format = PageNumberFormat.CUSTOM,
                    customTemplate = "- {page} -"
                )
            )
        }
    }
}

@Preview(name = "Watermark Live Preview Card", showBackground = true)
@Composable
fun WatermarkLivePreviewCardPreview() {
    MorphDropTheme(darkTheme = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            WatermarkLivePreviewCard(
                config = WatermarkConfig(
                    type = WatermarkType.TEXT,
                    text = "CONFIDENTIAL",
                    rotationDegrees = 45f,
                    opacity = 0.4f
                )
            )
        }
    }
}

@Preview(name = "Batch PDF - Select Files", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun BatchPdfSelectFilesPreview() {
    MorphDropTheme(darkTheme = false) {
        Surface {
            SelectFilesStepContent(
                state = BatchPdfUiState(
                    selectedFiles = listOf(
                        SelectedPdfFile(
                            uri = Uri.parse("content://mock/1.pdf"),
                            name = "Contract_2026.pdf",
                            sizeBytes = 1024 * 500
                        ),
                        SelectedPdfFile(
                            uri = Uri.parse("content://mock/2.pdf"),
                            name = "Report_Final.pdf",
                            sizeBytes = 1024 * 1200
                        )
                    )
                ),
                onPickFiles = {},
                onRemoveFile = {},
                onClearAll = {},
                onMoveUp = {},
                onMoveDown = {},
                onProceed = {}
            )
        }
    }
}

@Preview(name = "Batch PDF - Choose Operation", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun BatchPdfChooseOperationPreview() {
    MorphDropTheme(darkTheme = false) {
        Surface {
            SelectOperationStepContent(
                state = BatchPdfUiState(),
                onSelectOperation = {}
            )
        }
    }
}

@Preview(name = "Batch PDF - Configure (Page Numbers)", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun BatchPdfConfigurePageNumbersPreview() {
    MorphDropTheme(darkTheme = false) {
        Surface {
            ConfigureOperationStepContent(
                state = BatchPdfUiState(
                    currentStep = BatchPdfStep.CONFIGURE,
                    selectedOperation = BatchPdfOperation.PAGE_NUMBERS,
                    selectedFiles = listOf(
                        SelectedPdfFile(uri = Uri.parse("content://mock/1.pdf"), name = "Contract_2026.pdf", sizeBytes = 1024 * 500),
                        SelectedPdfFile(uri = Uri.parse("content://mock/2.pdf"), name = "Report_Final.pdf", sizeBytes = 1024 * 1200)
                    ),
                    pageNumberConfig = PageNumberConfig(
                        position = PageNumberPosition.BOTTOM_CENTER,
                        format = PageNumberFormat.CUSTOM,
                        customTemplate = "- {page} -"
                    )
                ),
                onStartProcessing = {}
            )
        }
    }
}

@Preview(name = "Batch PDF - Configure (Watermark)", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun BatchPdfConfigureWatermarkPreview() {
    MorphDropTheme(darkTheme = false) {
        Surface {
            ConfigureOperationStepContent(
                state = BatchPdfUiState(
                    currentStep = BatchPdfStep.CONFIGURE,
                    selectedOperation = BatchPdfOperation.WATERMARK,
                    selectedFiles = listOf(
                        SelectedPdfFile(uri = Uri.parse("content://mock/1.pdf"), name = "Document_Confidential.pdf", sizeBytes = 1024 * 800)
                    ),
                    watermarkConfig = WatermarkConfig(
                        type = WatermarkType.TEXT,
                        text = "CONFIDENTIAL",
                        rotationDegrees = 45f,
                        opacity = 0.4f
                    )
                ),
                onStartProcessing = {}
            )
        }
    }
}

@Preview(name = "Batch PDF - Configure (Compress)", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun BatchPdfConfigureCompressPreview() {
    MorphDropTheme(darkTheme = false) {
        Surface {
            ConfigureOperationStepContent(
                state = BatchPdfUiState(
                    currentStep = BatchPdfStep.CONFIGURE,
                    selectedOperation = BatchPdfOperation.COMPRESS,
                    selectedFiles = listOf(
                        SelectedPdfFile(uri = Uri.parse("content://mock/1.pdf"), name = "Large_Catalog.pdf", sizeBytes = 1024 * 1024 * 15)
                    ),
                    compressConfig = BatchCompressConfig(
                        quality = 0.5f,
                        isTargetSizeMode = false
                    )
                ),
                onStartProcessing = {}
            )
        }
    }
}

@Preview(name = "Batch PDF - Processing", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun BatchPdfProcessingPreview() {
    MorphDropTheme(darkTheme = false) {
        Surface {
            ProcessingStepContent(
                state = BatchPdfUiState(
                    currentStep = BatchPdfStep.PROCESSING,
                    progressCurrent = 2,
                    progressTotal = 4,
                    progressPercent = 50,
                    progressFile = "Report_Final_2026.pdf"
                ),
                onCancel = {}
            )
        }
    }
}

@Preview(name = "Batch PDF - Results (Light)", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun BatchPdfResultsPreview() {
    MorphDropTheme(darkTheme = false) {
        Surface {
            ResultsStepContent(
                state = BatchPdfUiState(
                    outputFolder = "MorphDrop/Batch_PDF_PageNumbers_2026-09-08_11-00",
                    results = listOf(
                        BatchPdfItemResult(
                            uriString = "content://mock/1",
                            fileName = "00-START-HERE.pdf",
                            outputUriString = "content://mock/out1",
                            originalSize = 236851,
                            newSize = 241152,
                            isSuccess = true
                        ),
                        BatchPdfItemResult(
                            uriString = "content://mock/2",
                            fileName = "Admit Card NICL 2026.pdf",
                            outputUriString = "content://mock/out2",
                            originalSize = 147558,
                            newSize = 149200,
                            isSuccess = true
                        )
                    )
                ),
                onOpenFolder = {},
                onShareAll = {},
                onOpenFile = {},
                onDone = {}
            )
        }
    }
}

@Preview(name = "Batch PDF - Results (Dark)", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun BatchPdfResultsDarkPreview() {
    MorphDropTheme(darkTheme = true) {
        Surface {
            ResultsStepContent(
                state = BatchPdfUiState(
                    outputFolder = "MorphDrop/Batch_PDF_PageNumbers_2026-09-08_11-00",
                    results = listOf(
                        BatchPdfItemResult(
                            uriString = "content://mock/1",
                            fileName = "00-START-HERE.pdf",
                            outputUriString = "content://mock/out1",
                            originalSize = 236851,
                            newSize = 241152,
                            isSuccess = true
                        ),
                        BatchPdfItemResult(
                            uriString = "content://mock/2",
                            fileName = "Admit Card NICL 2026.pdf",
                            outputUriString = "content://mock/out2",
                            originalSize = 147558,
                            newSize = 149200,
                            isSuccess = true
                        )
                    )
                ),
                onOpenFolder = {},
                onShareAll = {},
                onOpenFile = {},
                onDone = {}
            )
        }
    }
}

