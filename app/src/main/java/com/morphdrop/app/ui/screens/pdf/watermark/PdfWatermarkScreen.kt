package com.morphdrop.app.ui.screens.pdf.watermark

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.morphdrop.app.PdfViewerActivity
import com.morphdrop.app.domain.model.FileType
import com.morphdrop.app.domain.model.WatermarkConfig
import com.morphdrop.app.domain.model.WatermarkPosition
import com.morphdrop.app.domain.model.WatermarkType
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
fun PdfWatermarkScreen(
    initialUri: Uri? = null,
    onNavigateBack: () -> Unit,
    viewModel: PdfWatermarkViewModel = hiltViewModel()
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

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.updateConfig { it.copy(imageUri = uri.toString()) }
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
            ProcessingScreenContent(
                state = ProcessingUiState(
                    progress = 50f,
                    currentStage = "Applying watermark to PDF...",
                    fileName = state.fileName
                ),
                scrollBehavior = processingScrollBehavior,
                onCancel = viewModel::cancelProcessing
            )
        }
        state.isSuccess && state.resultUri != null -> {
            val resultScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
            ResultScreenContent(
                state = ResultUiState(
                    title = "Watermark Applied Successfully!",
                    subtitle = "1 file created • ${FileHelper.formatFileSize(state.resultFileSize)}",
                    outputFiles = listOf(
                        OutputFileItem(
                            id = state.resultUri.toString(),
                            fileName = state.resultFileName,
                            fileSizeFormatted = FileHelper.formatFileSize(state.resultFileSize),
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
                    context.startActivity(Intent.createChooser(shareIntent, "Share Watermarked PDF"))
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
            PdfWatermarkContent(
                state = state,
                onNavigateBack = onNavigateBack,
                onPickPdfClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                onPickImageClick = {
                    imagePickerLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onUpdateConfig = viewModel::updateConfig,
                onCustomRangeChanged = viewModel::onCustomRangeTextChanged,
                onApplyWatermark = viewModel::applyWatermark
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PdfWatermarkContent(
    state: PdfWatermarkUiState,
    onNavigateBack: () -> Unit = {},
    onPickPdfClick: () -> Unit = {},
    onPickImageClick: () -> Unit = {},
    onUpdateConfig: ((WatermarkConfig) -> WatermarkConfig) -> Unit = {},
    onCustomRangeChanged: (String) -> Unit = {},
    onApplyWatermark: () -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MorphDropTopAppBar(
                title = "Add Watermark",
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
                                val pageAspectRatio = (state.previewBitmap!!.width.toFloat() / state.previewBitmap!!.height.toFloat()).coerceIn(0.4f, 2.5f)

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
                                        bitmap = state.previewBitmap!!.asImageBitmap(),
                                        contentDescription = "PDF Preview Page",
                                        contentScale = ContentScale.FillBounds,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    WatermarkOverlayLayer(config = state.config)
                                }
                            }
                        } else if (state.selectedUri != null && state.isPreviewLoading) {
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
                                        "Tap to select a PDF to add watermark"
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

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Watermark Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.config.type == WatermarkType.TEXT,
                            onClick = { onUpdateConfig { it.copy(type = WatermarkType.TEXT) } },
                            label = { Text("Text Watermark") }
                        )
                        FilterChip(
                            selected = state.config.type == WatermarkType.IMAGE,
                            onClick = { onUpdateConfig { it.copy(type = WatermarkType.IMAGE) } },
                            label = { Text("Image Watermark") }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    when (state.config.type) {
                        WatermarkType.TEXT -> {
                            OutlinedTextField(
                                value = state.config.text,
                                onValueChange = { t -> onUpdateConfig { it.copy(text = t) } },
                                label = { Text("Watermark Text") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Font Size", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("${state.config.fontSizeSp.toInt()} sp", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = state.config.fontSizeSp,
                                onValueChange = { s -> onUpdateConfig { it.copy(fontSizeSp = s) } },
                                valueRange = 12f..72f,
                                steps = 14
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text("Text Color", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            val colors = listOf(
                                "Dark Gray" to 0xFF424242,
                                "Black" to 0xFF000000,
                                "Red" to 0xFFD32F2F,
                                "Blue" to 0xFF1976D2,
                                "Light Gray" to 0xFF9E9E9E
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                colors.forEach { (name, colorVal) ->
                                    val isSelected = state.config.fontColor == colorVal
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(colorVal),
                                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clickable { onUpdateConfig { it.copy(fontColor = colorVal) } }
                                    ) {}
                                }
                            }
                        }
                        WatermarkType.IMAGE -> {
                            Text("Watermark Stamp Image", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))

                            if (state.config.imageUri != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = state.config.imageUri,
                                        contentDescription = "Selected Image",
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    OutlinedButton(
                                        onClick = onPickImageClick,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Change Image")
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onPickImageClick,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Outlined.Image, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Pick Image from Gallery")
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Text("Image Scale: ${(state.config.imageScale * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                            Slider(
                                value = state.config.imageScale,
                                onValueChange = { sc -> onUpdateConfig { it.copy(imageScale = sc) } },
                                valueRange = 0.1f..1.0f
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Position", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WatermarkPosition.entries.forEach { pos ->
                            FilterChip(
                                selected = state.config.position == pos,
                                onClick = { onUpdateConfig { it.copy(position = pos) } },
                                label = { Text(pos.label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Opacity: ${(state.config.opacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = state.config.opacity,
                        onValueChange = { op -> onUpdateConfig { it.copy(opacity = op) } },
                        valueRange = 0.05f..1.0f
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Rotation: ${state.config.rotationDegrees.toInt()}°", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(
                            onClick = { onUpdateConfig { it.copy(rotationDegrees = 45f) } },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("45° Diagonal")
                        }
                    }
                    Slider(
                        value = state.config.rotationDegrees,
                        onValueChange = { rot -> onUpdateConfig { it.copy(rotationDegrees = rot) } },
                        valueRange = 0f..360f
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Skip Cover Page", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Do not stamp watermark on page 1", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.config.skipFirstPage,
                            onCheckedChange = { sk -> onUpdateConfig { it.copy(skipFirstPage = sk) } }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = state.customRangeText,
                        onValueChange = onCustomRangeChanged,
                        label = { Text("Target Pages (optional, e.g. 1-3, 5)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            PrimaryButton(
                text = if (state.isProcessing) "Applying Watermark..." else "Apply Watermark",
                onClick = onApplyWatermark,
                enabled = !state.isProcessing && state.selectedUri != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun WatermarkOverlayLayer(config: WatermarkConfig) {
    val alignment = when (config.position) {
        WatermarkPosition.CENTER, WatermarkPosition.DIAGONAL -> Alignment.Center
        WatermarkPosition.TOP_LEFT -> Alignment.TopStart
        WatermarkPosition.TOP_RIGHT -> Alignment.TopEnd
        WatermarkPosition.BOTTOM_LEFT -> Alignment.BottomStart
        WatermarkPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = alignment
    ) {
        if (config.type == WatermarkType.TEXT) {
            val textScaleFactor = (maxWidth.value / 550f).coerceIn(0.2f, 0.55f)
            Text(
                text = config.text.ifBlank { "CONFIDENTIAL" },
                fontSize = (config.fontSizeSp * textScaleFactor).sp,
                fontWeight = FontWeight.Bold,
                color = Color(config.fontColor).copy(alpha = config.opacity),
                modifier = Modifier.rotate(-config.rotationDegrees)
            )
        } else {
            if (config.imageUri != null) {
                val targetPreviewWidth = maxWidth * config.imageScale.coerceIn(0.1f, 1.0f)
                AsyncImage(
                    model = config.imageUri,
                    contentDescription = "Watermark Stamp",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(targetPreviewWidth)
                        .rotate(-config.rotationDegrees)
                        .alpha(config.opacity)
                )
            } else {
                val placeholderWidth = (maxWidth * 0.35f).coerceIn(36.dp, 80.dp)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(placeholderWidth)
                        .rotate(-config.rotationDegrees)
                        .alpha(config.opacity)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "WATERMARK",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Preview(name = "Watermark - Empty Light", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun PdfWatermarkScreenEmptyLightPreview() {
    MorphDropTheme(darkTheme = false) {
        PdfWatermarkContent(
            state = PdfWatermarkUiState()
        )
    }
}

@Preview(name = "Watermark - Empty Dark", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PdfWatermarkScreenEmptyDarkPreview() {
    MorphDropTheme(darkTheme = true) {
        PdfWatermarkContent(
            state = PdfWatermarkUiState()
        )
    }
}

@Preview(name = "Watermark - Selected Light", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun PdfWatermarkScreenSelectedLightPreview() {
    MorphDropTheme(darkTheme = false) {
        PdfWatermarkContent(
            state = PdfWatermarkUiState(
                selectedUri = Uri.parse("content://dummy/confidential.pdf"),
                fileName = "Confidential_Agreement.pdf",
                fileSize = 4_100_000L,
                pageCount = 8,
                config = WatermarkConfig(text = "CONFIDENTIAL", rotationDegrees = 45f, opacity = 0.35f)
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Watermark - Result Light", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun PdfWatermarkScreenResultLightPreview() {
    MorphDropTheme(darkTheme = false) {
        ResultScreenContent(
            state = ResultUiState(
                title = "Watermark Applied Successfully!",
                subtitle = "1 file created • 4.1 MB",
                outputFiles = listOf(
                    OutputFileItem(
                        id = "1",
                        fileName = "Confidential_Agreement_watermarked.pdf",
                        fileSizeFormatted = "4.1 MB",
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
