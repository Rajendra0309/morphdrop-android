package com.morphdrop.app.ui.screens.conversion

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.morphdrop.app.domain.model.ConversionType
import com.morphdrop.app.domain.model.FileType
import com.morphdrop.app.ui.components.FormatBadge
import com.morphdrop.app.ui.components.InteractiveCropDialog
import com.morphdrop.app.ui.components.MorphDropTopAppBar
import com.morphdrop.app.ui.theme.MorphDropTheme
import com.morphdrop.app.util.FileHelper

@Composable
fun ConversionConfigScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToProcessing: (conversionTypeId: String, workId: String) -> Unit = { _, _ -> },
    viewModel: ConversionConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val mimeFilter = when (state.conversionType?.id) {
        "excel_to_pdf" -> arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "text/csv")
        "text_to_pdf" -> arrayOf("text/plain")
        "md_to_pdf" -> arrayOf("text/markdown", "text/x-markdown", "text/plain")
        "pdf_to_images", "split_pdf", "compress_pdf", "protect_pdf", "organize_pdf", "merge_pdf" -> arrayOf("application/pdf")
        "images_to_pdf", "compress_images", "image_converter" -> arrayOf("image/*")
        else -> arrayOf("*/*")
    }

    val isImageConversion = when (state.conversionType?.id) {
        "images_to_pdf", "compress_images", "image_converter" -> true
        else -> false
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.onFilesSelected(context, uris)
            if (state.conversionType?.id == "image_converter") {
                viewModel.setShowCropDialog(true)
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.onFilesSelected(context, uris)
            if (state.conversionType?.id == "image_converter") {
                viewModel.setShowCropDialog(true)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (state.selectedFileUris.isEmpty()) {
            if (isImageConversion) {
                imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                filePicker.launch(mimeFilter)
            }
        }
    }

    ConversionConfigScreenContent(
        state = state,
        isFolderOutput = viewModel.isFolderOutput(state.conversionType),
        onNavigateBack = onNavigateBack,
        onPickFile = {
            if (isImageConversion) {
                imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                filePicker.launch(mimeFilter)
            }
        },
        onOutputFormatChanged = viewModel::onOutputFormatChanged,
        onQualityChanged = viewModel::onQualityChanged,
        onPageRangeStartChanged = viewModel::onPageRangeStartChanged,
        onPageRangeEndChanged = viewModel::onPageRangeEndChanged,
        onOutputFileNameChanged = viewModel::onOutputFileNameChanged,
        onTargetWidthChanged = viewModel::onTargetWidthChanged,
        onTargetHeightChanged = viewModel::onTargetHeightChanged,
        onPaddingColorChanged = viewModel::onPaddingColorChanged,
        onTargetSizeKbChanged = viewModel::onTargetSizeKbChanged,
        onCompressionPresetSelected = viewModel::onCompressionPresetSelected,
        onAspectRatioPresetSelected = viewModel::onAspectRatioPresetSelected,
        onPreviewUriChanged = viewModel::onPreviewUriChanged,
        onCropRectChanged = viewModel::onCropRectChanged,
        onShowCropDialog = viewModel::setShowCropDialog,
        onShowColorPickerDialog = viewModel::setShowColorPickerDialog,
        onConvert = {
            val workId = viewModel.startConversion(context)
            if (workId != null && state.conversionType != null) {
                onNavigateToProcessing(state.conversionType!!.id, workId.toString())
            }
        }
    )

    if (state.showColorPickerDialog) {
        com.morphdrop.app.ui.components.ColorWheelDialog(
            initialColor = state.paddingColor,
            onDismiss = { viewModel.setShowColorPickerDialog(false) },
            onColorSelected = { color ->
                viewModel.onPaddingColorChanged(color)
                viewModel.setShowColorPickerDialog(false)
            }
        )
    }

    if (state.showCropDialog && state.selectedPreviewUri != null) {
        InteractiveCropDialog(
            imageUri = state.selectedPreviewUri!!,
            initialCropRect = if (state.cropRectLeft != -1) {
                android.graphics.Rect(state.cropRectLeft, state.cropRectTop, state.cropRectRight, state.cropRectBottom)
            } else null,
            initialRotation = state.rotationDegrees,
            onDismiss = { viewModel.setShowCropDialog(false) },
            onCropApplied = { l, t, r, b, rotation ->
                viewModel.onCropRectChanged(l, t, r, b)
                viewModel.onRotationChanged(rotation)
                viewModel.setShowCropDialog(false)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversionConfigScreenContent(
    state: ConversionConfigState,
    isFolderOutput: Boolean,
    onNavigateBack: () -> Unit,
    onPickFile: () -> Unit,
    onOutputFormatChanged: (String) -> Unit,
    onQualityChanged: (Int) -> Unit,
    onPageRangeStartChanged: (String) -> Unit,
    onPageRangeEndChanged: (String) -> Unit,
    onOutputFileNameChanged: (String) -> Unit,
    onTargetWidthChanged: (String) -> Unit,
    onTargetHeightChanged: (String) -> Unit,
    onPaddingColorChanged: (Int) -> Unit,
    onTargetSizeKbChanged: (String) -> Unit,
    onCompressionPresetSelected: (String) -> Unit,
    onAspectRatioPresetSelected: (String) -> Unit,
    onPreviewUriChanged: (Uri) -> Unit,
    onCropRectChanged: (Int, Int, Int, Int) -> Unit,
    onShowCropDialog: (Boolean) -> Unit,
    onShowColorPickerDialog: (Boolean) -> Unit,
    onConvert: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var showAdvancedSettings by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MorphDropTopAppBar(
                title = state.conversionType?.name ?: "Configure",
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
                .imePadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Error Message
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

            // File Information & Preview
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                        if (state.selectedPreviewUri != null && state.conversionType?.inputType in listOf(FileType.PNG, FileType.JPG, FileType.WEBP, FileType.BMP, FileType.PDF)) {
                            var previewImageRequest by remember(state.selectedPreviewUri, state.cropRectLeft, state.cropRectTop, state.cropRectRight, state.cropRectBottom, state.rotationDegrees) { 
                                mutableStateOf<coil.request.ImageRequest?>(null) 
                            }
                            
                            val context = LocalContext.current
                            LaunchedEffect(state.selectedPreviewUri, state.cropRectLeft, state.cropRectTop, state.cropRectRight, state.cropRectBottom, state.rotationDegrees) {
                                if (state.selectedPreviewUri != null) {
                                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        val uri = state.selectedPreviewUri!!
                                        var originalWidth = 1
                                        var originalHeight = 1
                                        
                                        try {
                                            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                            com.morphdrop.app.util.FileHelper.readFileFromUri(context, uri).use { 
                                                android.graphics.BitmapFactory.decodeStream(it, null, options) 
                                            }
                                            var exifOrientation = android.media.ExifInterface.ORIENTATION_NORMAL
                                            com.morphdrop.app.util.FileHelper.readFileFromUri(context, uri).use {
                                                exifOrientation = android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
                                            }
                                            val isSwappedByExif = (exifOrientation == android.media.ExifInterface.ORIENTATION_ROTATE_90 || exifOrientation == android.media.ExifInterface.ORIENTATION_ROTATE_270)
                                            val isSwappedByManual = (state.rotationDegrees % 180 != 0)
                                            val isSwappedTotal = isSwappedByExif xor isSwappedByManual
                                            originalWidth = if (isSwappedTotal) options.outHeight else options.outWidth
                                            originalHeight = if (isSwappedTotal) options.outWidth else options.outHeight
                                        } catch (e: Exception) { }
                                        
                                        val req = coil.request.ImageRequest.Builder(context)
                                            .data(uri)
                                            .transformations(
                                                object : coil.transform.Transformation {
                                                    override val cacheKey = "crop_${uri}_${state.cropRectLeft}_${state.cropRectTop}_${state.cropRectRight}_${state.cropRectBottom}_${state.rotationDegrees}"
                                                    override suspend fun transform(input: android.graphics.Bitmap, size: coil.size.Size): android.graphics.Bitmap {
                                                        var current = input
                                                        if (state.rotationDegrees % 360 != 0) {
                                                            val matrix = android.graphics.Matrix().apply { postRotate(state.rotationDegrees.toFloat()) }
                                                            val rotated = android.graphics.Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
                                                            if (rotated != current) current = rotated
                                                        }
                                                        if (state.cropRectLeft != -1) {
                                                            val scaleX = current.width.toFloat() / originalWidth.coerceAtLeast(1)
                                                            val scaleY = current.height.toFloat() / originalHeight.coerceAtLeast(1)
                                                            
                                                            val safeLeft = kotlin.math.max(0, (state.cropRectLeft * scaleX).toInt())
                                                            val safeTop = kotlin.math.max(0, (state.cropRectTop * scaleY).toInt())
                                                            val safeRight = kotlin.math.min(current.width, (state.cropRectRight * scaleX).toInt())
                                                            val safeBottom = kotlin.math.min(current.height, (state.cropRectBottom * scaleY).toInt())
                                                            
                                                            if (safeLeft < safeRight && safeTop < safeBottom) {
                                                                val cropped = android.graphics.Bitmap.createBitmap(current, safeLeft, safeTop, safeRight - safeLeft, safeBottom - safeTop)
                                                                if (cropped != current) current = cropped
                                                            }
                                                        }
                                                        return current
                                                    }
                                                }
                                            )
                                            .build()
                                        previewImageRequest = req
                                    }
                                }
                            }

                            AsyncImage(
                                model = previewImageRequest ?: state.selectedPreviewUri,
                                contentDescription = "Preview",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Fit
                            )
                            
                            // Crop Button Overlay
                            if (state.conversionType?.id in listOf("image_converter", "compress_images")) {
                                IconButton(
                                    onClick = { onShowCropDialog(true) },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Crop,
                                        contentDescription = "Crop",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.selectedFileName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            if (state.selectedFileUris.isNotEmpty()) {
                                Text(
                                    text = FileHelper.formatFileSize(state.selectedFileSize),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        state.conversionType?.inputType?.let { FormatBadge(fileType = it) }
                    }

                    if (state.selectedFileUris.size > 1) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.selectedFileUris) { uri ->
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = if (state.selectedPreviewUri == uri) 2.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onPreviewUriChanged(uri) },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = onPickFile,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (state.selectedFileUris.isNotEmpty()) "Change Selection" else "Select Input File")
                    }
                }
            }

            // Options
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Output Format
                if (state.availableOutputFormats.size > 1) {
                    ConfigSection(title = "Output Format") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.availableOutputFormats.forEach { format ->
                                val isSelected = state.outputFormat == format
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onOutputFormatChanged(format) },
                                    label = { Text(format.uppercase()) }
                                )
                            }
                        }
                    }
                }

                // Quick Presets
                if (state.conversionType?.id in listOf("compress_images", "image_converter")) {
                    ConfigSection(title = "Compression Presets") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val presets = listOf("Balanced", "Under 500KB", "Under 1MB", "Profile Picture (256x256)", "HD (1920x1080)", "4K (3840x2160)")
                            items(presets) { preset ->
                                FilterChip(
                                    selected = state.compressionPreset == preset,
                                    onClick = { onCompressionPresetSelected(preset) },
                                    label = { Text(preset) },
                                    leadingIcon = {
                                        if (state.compressionPreset == preset) {
                                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Quality Slider
                AnimatedVisibility(visible = state.showQualitySlider) {
                    ConfigSection(title = "Quality: ${state.quality}%") {
                        Slider(
                            value = state.quality.toFloat(),
                            onValueChange = { onQualityChanged(it.toInt()) },
                            valueRange = 1f..100f
                        )
                    }
                }
                
                // Advanced Image Settings
                if (state.conversionType?.id in listOf("image_converter", "compress_images")) {
                    OutlinedButton(
                        onClick = { showAdvancedSettings = !showAdvancedSettings },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(if (showAdvancedSettings) "Hide Advanced Settings" else "Show Advanced Settings")
                    }

                    AnimatedVisibility(visible = showAdvancedSettings) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ConfigSection(title = "Manual Resize") {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(
                                        value = state.targetWidth,
                                        onValueChange = onTargetWidthChanged,
                                        label = { Text("Width (px)") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = state.targetHeight,
                                        onValueChange = onTargetHeightChanged,
                                        label = { Text("Height (px)") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Canvas Padding Color", style = MaterialTheme.typography.bodySmall)
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val presetColors = listOf(
                                        android.graphics.Color.BLACK,
                                        android.graphics.Color.WHITE,
                                        android.graphics.Color.TRANSPARENT,
                                        android.graphics.Color.RED,
                                        android.graphics.Color.BLUE,
                                        android.graphics.Color.GREEN
                                    )
                                    val colors = if (state.paddingColor !in presetColors) {
                                        presetColors + state.paddingColor
                                    } else {
                                        presetColors
                                    }
                                    items(colors) { colorInt ->
                                        val isSelected = state.paddingColor == colorInt
                                        val bgColor = if (colorInt == android.graphics.Color.TRANSPARENT) Color.LightGray else Color(colorInt)
                                        
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(bgColor)
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                    shape = CircleShape
                                                )
                                                .clickable { onPaddingColorChanged(colorInt) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                val checkColor = if (colorInt == android.graphics.Color.WHITE || colorInt == android.graphics.Color.TRANSPARENT || colorInt == android.graphics.Color.GREEN) Color.Black else Color.White
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = checkColor,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            } else if (colorInt == android.graphics.Color.TRANSPARENT) {
                                                Text("T", color = Color.Black, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                    item {
                                        IconButton(onClick = { onShowColorPickerDialog(true) }) {
                                            Icon(imageVector = Icons.Default.Palette, contentDescription = "Custom Color")
                                        }
                                    }
                                }
                            }

                            ConfigSection(title = "Target File Size") {
                                OutlinedTextField(
                                    value = state.targetSizeKb,
                                    onValueChange = onTargetSizeKbChanged,
                                    label = { Text("Maximum Size (KB)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                Text(
                                    text = "Intelligent compression will attempt to meet this target.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                        }
                    }
                }

                // Page Range
                AnimatedVisibility(visible = state.showPageRange) {
                    ConfigSection(title = "Page Range") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = state.pageRangeStart,
                                onValueChange = onPageRangeStartChanged,
                                label = { Text("From Page") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = state.pageRangeEnd,
                                onValueChange = onPageRangeEndChanged,
                                label = { Text("To Page") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                        }
                    }
                }

                // Output Name
                val nameLabel = if (isFolderOutput) "Output Folder Name" else "Output File Name"
                ConfigSection(title = nameLabel) {
                    OutlinedTextField(
                        value = state.outputFileName,
                        onValueChange = onOutputFileNameChanged,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
            ) {
                Button(
                    onClick = onConvert,
                    enabled = state.isConvertEnabled,
                    modifier = Modifier.fillMaxSize(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text(
                        text = "Convert",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (state.isConvertEnabled) Color.White else Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConfigSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        content()
    }
}

@Preview(name = "Light Mode", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun ConversionConfigScreenLightPreview() {
    MorphDropTheme(darkTheme = false) {
        ConversionConfigScreenContent(
            state = ConversionConfigState(
                conversionType = ConversionType.defaultList.first(),
                selectedFileUris = listOf(Uri.parse("content://mock")),
                selectedFileNames = listOf("Sample_Image.jpg"),
                selectedFileSize = 1024L * 1024L * 2,
                availableOutputFormats = listOf("png", "jpg", "webp"),
                outputFormat = "png",
                showQualitySlider = true,
                quality = 85,
                showPageRange = false,
                outputFileName = "Sample_Image_Converted",
                isConvertEnabled = true,
                selectedPreviewUri = Uri.parse("content://mock")
            ),
            isFolderOutput = false,
            onNavigateBack = {},
            onPickFile = {},
            onOutputFormatChanged = {},
            onQualityChanged = {},
            onPageRangeStartChanged = {},
            onPageRangeEndChanged = {},
            onOutputFileNameChanged = {},
            onTargetWidthChanged = {},
            onTargetHeightChanged = {},
            onPaddingColorChanged = {},
            onTargetSizeKbChanged = {},
            onCompressionPresetSelected = {},
            onAspectRatioPresetSelected = {},
            onPreviewUriChanged = {},
            onCropRectChanged = { _, _, _, _ -> },
            onShowCropDialog = {},
            onShowColorPickerDialog = {},
            onConvert = {}
        )
    }
}
