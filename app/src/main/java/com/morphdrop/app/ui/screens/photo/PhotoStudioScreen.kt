package com.morphdrop.app.ui.screens.photo

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.morphdrop.app.ui.components.MorphDropTopAppBar
import com.morphdrop.app.ui.components.PrimaryButton
import com.morphdrop.app.ui.components.SectionHeader
import com.morphdrop.app.ui.screens.processing.ProcessingScreenContent
import com.morphdrop.app.ui.screens.processing.ProcessingUiState
import com.morphdrop.app.ui.screens.result.OutputFileItem
import com.morphdrop.app.ui.screens.result.ResultScreenContent
import com.morphdrop.app.ui.screens.result.ResultUiState
import com.morphdrop.app.util.FileHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoStudioScreen(
    onNavigateBack: () -> Unit,
    viewModel: PhotoStudioViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.onImageSelected(uri)
        }
    }

    var hasAutoLaunchedPicker by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hasAutoLaunchedPicker && state.selectedUri == null) {
            hasAutoLaunchedPicker = true
            imagePickerLauncher.launch(arrayOf("image/*"))
        }
    }

    when {
        state.isProcessing -> {
            val processingScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
            ProcessingScreenContent(
                state = ProcessingUiState(
                    progress = 50f,
                    currentStage = if (state.mode == StudioMode.PHOTO) {
                        "Creating your photo..."
                    } else {
                        "Creating your signature..."
                    },
                    fileName = state.fileName
                ),
                scrollBehavior = processingScrollBehavior,
                onCancel = {
                    viewModel.cancelProcessing()
                    onNavigateBack()
                }
            )
        }
        state.isSuccess && state.resultUri != null -> {
            val resultScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
            ResultScreenContent(
                state = ResultUiState(
                    title = when {
                        state.resultNote != null && state.mode == StudioMode.PHOTO ->
                            "Photo Created — Over the Size Limit"
                        state.resultNote != null ->
                            "Signature Created — Over the Size Limit"
                        state.mode == StudioMode.PHOTO -> "Photo Created Successfully!"
                        else -> "Signature Created Successfully!"
                    },
                    subtitle = "1 file created • ${FileHelper.formatFileSize(state.resultSize)} • " +
                        "${state.resultWidth} × ${state.resultHeight} px" +
                        (state.resultNote?.let { "\n$it" } ?: ""),
                    outputFiles = listOf(
                        OutputFileItem(
                            id = state.resultUri.toString(),
                            fileName = state.resultFileName,
                            fileSizeFormatted = FileHelper.formatFileSize(state.resultSize),
                            fileSizeBytes = state.resultSize,
                            extension = "jpg",
                            uri = state.resultUri
                        )
                    )
                ),
                scrollBehavior = resultScrollBehavior,
                onDone = onNavigateBack,
                onConvertAnother = viewModel::resetResult,
                onOpenFile = { file ->
                    file.uri?.let { uri ->
                        context.startActivity(FileHelper.openFile(context, uri))
                    }
                },
                onShareFile = { file ->
                    file.uri?.let { uri ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
                    }
                },
                onOpenFolder = {
                    context.startActivity(FileHelper.openFolderIntent(context, "MorphDrop"))
                },
                onShareAll = {
                    state.resultUri?.let { uri ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
                    }
                }
            )
        }
        else -> {
            PhotoStudioContent(
                state = state,
                onNavigateBack = onNavigateBack,
                onPickImageClick = { imagePickerLauncher.launch(arrayOf("image/*")) },
                onCreateClick = viewModel::createImage,
                onModeChange = viewModel::setMode,
                onPresetChange = viewModel::setPreset,
                onCustomWidthChange = viewModel::setCustomWidth,
                onCustomHeightChange = viewModel::setCustomHeight,
                onFillFrameChange = viewModel::setFillFrame,
                onBackgroundChange = viewModel::setBackground,
                onSizeLimitChange = viewModel::setSizeLimit,
                onCustomSizeKbChange = viewModel::setCustomSizeKb
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PhotoStudioContent(
    state: PhotoStudioUiState,
    onNavigateBack: () -> Unit,
    onPickImageClick: () -> Unit,
    onCreateClick: () -> Unit,
    onModeChange: (StudioMode) -> Unit,
    onPresetChange: (Int) -> Unit,
    onCustomWidthChange: (String) -> Unit,
    onCustomHeightChange: (String) -> Unit,
    onFillFrameChange: (Boolean) -> Unit,
    onBackgroundChange: (Int) -> Unit,
    onSizeLimitChange: (Int) -> Unit,
    onCustomSizeKbChange: (String) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MorphDropTopAppBar(
                title = "Photo & Signature Studio",
                scrollBehavior = scrollBehavior,
                showBackArrow = true,
                onBackClick = onNavigateBack,
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            Surface(shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    if (state.targetWidth > 0 && state.targetHeight > 0) {
                        Text(
                            text = "Output: ${state.targetWidth} × ${state.targetHeight} px · JPG" +
                                (state.targetSizeKb?.let { " · up to $it KB" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    PrimaryButton(
                        text = if (state.mode == StudioMode.PHOTO) "Create Photo" else "Create Signature",
                        onClick = onCreateClick,
                        enabled = state.canCreate,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
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

            // Mode selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = state.mode == StudioMode.PHOTO,
                    onClick = { onModeChange(StudioMode.PHOTO) },
                    label = { Text("Photo") },
                    leadingIcon = {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = state.mode == StudioMode.SIGNATURE,
                    onClick = { onModeChange(StudioMode.SIGNATURE) },
                    label = { Text("Signature") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Draw, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Preview / picker card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val preview = state.previewBitmap
                    if (preview != null) {
                        // The preview box uses the exact output aspect ratio, so
                        // the crop/letterbox shown here is what gets saved. Its
                        // background is the studio background colour: transparent
                        // pixels flatten onto it in the saved JPG, so the preview
                        // must show the same colour (not the theme surface).
                        val frameBackground = Color(
                            StudioBackground.options.getOrNull(state.backgroundIndex)?.color
                                ?: android.graphics.Color.WHITE
                        )
                        val frameRatio = if (state.targetHeight > 0) {
                            (state.targetWidth.toFloat() / state.targetHeight).coerceIn(0.25f, 5f)
                        } else {
                            1f
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(frameRatio)
                                .clip(RoundedCornerShape(12.dp))
                                .background(frameBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = preview.asImageBitmap(),
                                contentDescription = "Selected image preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = if (state.fillFrame) ContentScale.Crop else ContentScale.Fit
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "This frame is exactly what will be saved — ${state.targetWidth} × ${state.targetHeight} px",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = FileHelper.formatFileSize(state.fileSize),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(onClick = onPickImageClick) {
                                Text("Change")
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.UploadFile,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (state.mode == StudioMode.PHOTO) {
                                    "Choose a photo to resize"
                                } else {
                                    "Choose a signature image to resize"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Output is an exact-size JPG, ready for exam and job portals",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            PrimaryButton(text = "Choose Image", onClick = onPickImageClick)
                        }
                    }
                }
            }

            // Size presets
            Column {
                SectionHeader(
                    title = if (state.mode == StudioMode.PHOTO) "Photo Size" else "Signature Size",
                    icon = Icons.Outlined.Badge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.presets.forEachIndexed { index, preset ->
                        FilterChip(
                            selected = state.presetIndex == index,
                            onClick = { onPresetChange(index) },
                            label = { Text("${preset.label} · ${preset.width}×${preset.height}") }
                        )
                    }
                    FilterChip(
                        selected = state.presetIndex == StudioPresets.CUSTOM_PRESET,
                        onClick = { onPresetChange(StudioPresets.CUSTOM_PRESET) },
                        label = { Text("Custom") }
                    )
                }
                if (state.presetIndex == StudioPresets.CUSTOM_PRESET) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = state.customWidth,
                            onValueChange = onCustomWidthChange,
                            label = { Text("Width (px)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = state.customHeight,
                            onValueChange = onCustomHeightChange,
                            label = { Text("Height (px)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Width and height must be between 16 and 8000 px.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Framing
            Column {
                SectionHeader(title = "Framing", icon = Icons.Outlined.Crop)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.fillFrame,
                        onClick = { onFillFrameChange(true) },
                        label = { Text("Fill frame (crop)") }
                    )
                    FilterChip(
                        selected = !state.fillFrame,
                        onClick = { onFillFrameChange(false) },
                        label = { Text("Fit full image") }
                    )
                }
                if (!state.fillFrame) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StudioBackground.options.forEachIndexed { index, background ->
                            FilterChip(
                                selected = state.backgroundIndex == index,
                                onClick = { onBackgroundChange(index) },
                                label = { Text(background.label) }
                            )
                        }
                    }
                }
            }

            // File size limit
            Column {
                SectionHeader(title = "Max File Size", icon = Icons.Outlined.DataUsage)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioPresets.sizeLimitsKb.forEachIndexed { index, kb ->
                        FilterChip(
                            selected = state.sizeLimitIndex == index,
                            onClick = { onSizeLimitChange(index) },
                            label = { Text(if (kb == null) "No limit" else "$kb KB") }
                        )
                    }
                    FilterChip(
                        selected = state.isSizeLimitCustom,
                        onClick = { onSizeLimitChange(StudioPresets.CUSTOM_SIZE_LIMIT) },
                        label = { Text("Custom") }
                    )
                }
                if (state.isSizeLimitCustom) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.customSizeKb,
                        onValueChange = onCustomSizeKbChange,
                        label = { Text("Max size (KB)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The size limit is reached by adjusting quality only — your pixel size never changes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
