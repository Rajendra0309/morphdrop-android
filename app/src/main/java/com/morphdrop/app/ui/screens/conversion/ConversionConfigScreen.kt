package com.morphdrop.app.ui.screens.conversion

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Surface
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.morphdrop.app.domain.model.ConversionType
import com.morphdrop.app.domain.model.FileMetadata
import com.morphdrop.app.domain.model.FileType
import com.morphdrop.app.domain.model.MetadataEditParams
import com.morphdrop.app.ui.components.ErrorBanner
import com.morphdrop.app.ui.components.FormatChip
import com.morphdrop.app.ui.components.ImageWorkbenchGrid
import com.morphdrop.app.ui.components.InteractiveCropDialog
import com.morphdrop.app.ui.components.MorphButton
import com.morphdrop.app.ui.components.MorphButtonVariant
import com.morphdrop.app.ui.components.MorphDropTopAppBar
import com.morphdrop.app.ui.components.MorphScaffold
import com.morphdrop.app.ui.components.PdfPageOrganizerDialog
import com.morphdrop.app.ui.components.TransformIndicator
import com.morphdrop.app.ui.components.WorkbenchImageItem
import com.morphdrop.app.ui.components.dashedBorder
import com.morphdrop.app.ui.screens.conversion.components.MetadataInspectorView
import com.morphdrop.app.ui.theme.MorphDropTheme
import com.morphdrop.app.ui.theme.morphColors
import com.morphdrop.app.util.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun ConversionConfigScreen(
    initialUri: String? = null,
    /** Long-press quick conversion: auto-start processing once files are picked. */
    autoStart: Boolean = false,
    onNavigateBack: () -> Unit = {},
    onNavigateToProcessing: (conversionTypeId: String, workId: String) -> Unit = { _, _ -> },
    viewModel: ConversionConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var hasAutoLaunchedPicker by rememberSaveable { mutableStateOf(false) }

    val mimeFilter = when (state.conversionType?.id) {
        "excel_to_pdf" -> arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "text/csv")
        "text_to_pdf" -> arrayOf("text/plain")
        "md_to_pdf" -> arrayOf("text/markdown", "text/x-markdown", "text/plain")
        "pdf_to_images", "split_pdf", "compress_pdf", "protect_pdf", "unlock_pdf", "organize_pdf", "merge_pdf" -> arrayOf("application/pdf")
        "images_to_pdf", "compress_images", "image_converter" -> arrayOf("image/*")
        else -> arrayOf("*/*")
    }

    val isImageConversion = when (state.conversionType?.id) {
        "images_to_pdf", "compress_images", "image_converter" -> true
        else -> false
    }

    var isAppendingFiles by remember { mutableStateOf(false) }
    var activeCropItem by remember { mutableStateOf<WorkbenchImageItem?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.onFilesSelected(context, uris, append = isAppendingFiles)
        }
        isAppendingFiles = false
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.onFilesSelected(context, uris, append = isAppendingFiles)
        }
        isAppendingFiles = false
    }

    LaunchedEffect(Unit) {
        val hasIncomingUri = !initialUri.isNullOrBlank() || state.hasInitialUri
        if (!hasAutoLaunchedPicker && !hasIncomingUri && state.selectedFileUris.isEmpty() && state.workbenchImageItems.isEmpty()) {
            hasAutoLaunchedPicker = true
            if (isImageConversion) {
                imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                filePicker.launch(mimeFilter)
            }
        }
    }

    // Long-press quick conversion: once files are staged and the config is
    // valid, start processing immediately using the saved preset, skipping
    // manual configuration. Fires once, and only when a real preset exists;
    // otherwise the user lands on the normal config screen.
    var autoStartConsumed by rememberSaveable { mutableStateOf(false) }
    val hasPreset by viewModel.hasPreset.collectAsStateWithLifecycle()
    LaunchedEffect(
        autoStart,
        hasPreset,
        state.isConvertEnabled,
        state.conversionType?.id,
        state.isPdfLoading,
        state.workbenchPages.size
    ) {
        if (autoStart && !autoStartConsumed && hasPreset && state.isConvertEnabled) {
            val typeId = state.conversionType?.id
            val isPageBasedPdfType = typeId in listOf("merge_pdf", "merge_pdfs", "split_pdf", "organize_pdf")
            if (isPageBasedPdfType && (state.isPdfLoading || state.workbenchPages.isEmpty())) {
                return@LaunchedEffect
            }
            if (typeId != null && typeId != "metadata_editor") {
                autoStartConsumed = true
                val workId = viewModel.startConversion(context)
                if (workId != null) {
                    onNavigateToProcessing(typeId, workId.toString())
                }
            }
        }
    }

    ConversionConfigScreenContent(
        state = state,
        isFolderOutput = viewModel.isFolderOutput(state.conversionType),
        isImageConversion = isImageConversion,
        onNavigateBack = onNavigateBack,
        onPickFile = { append ->
            isAppendingFiles = append
            if (isImageConversion) {
                imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                filePicker.launch(mimeFilter)
            }
        },
        onOutputFormatChanged = viewModel::onOutputFormatChanged,
        onQualityChanged = viewModel::onQualityChanged,
        onResizeOptionSelected = viewModel::onResizeOptionSelected,
        onStripMetadataChanged = viewModel::onStripMetadataChanged,
        onWorkbenchItemClick = { viewModel.onWorkbenchItemClicked(it) },
        onWorkbenchItemLongClick = { viewModel.onWorkbenchItemLongClicked(it) },
        onSelectAllWorkbenchImages = viewModel::onSelectAllWorkbenchImages,
        onDeselectAllWorkbenchImages = viewModel::onDeselectAllWorkbenchImages,
        onCropWorkbenchImage = { item -> activeCropItem = item },
        onRotateWorkbenchImage = { viewModel.onRotateWorkbenchImage(it) },
        onResetWorkbenchImage = { viewModel.onResetWorkbenchImage(context, it) },
        onApplyWorkbenchEdit = { viewModel.onApplyWorkbenchEdit(it) },
        onCancelWorkbenchEdit = { viewModel.onCancelWorkbenchEdit(context, it) },
        onPageRangeStartChanged = viewModel::onPageRangeStartChanged,
        onPageRangeEndChanged = viewModel::onPageRangeEndChanged,
        onOutputFileNameChanged = viewModel::onOutputFileNameChanged,
        onTargetWidthChanged = viewModel::onTargetWidthChanged,
        onTargetHeightChanged = viewModel::onTargetHeightChanged,
        onPaddingColorChanged = viewModel::onPaddingColorChanged,
        onTargetSizeKbChanged = viewModel::onTargetSizeKbChanged,
        onCompressionPresetSelected = viewModel::onCompressionPresetSelected,
        onPdfCompressionPresetSelected = viewModel::onPdfCompressionPresetSelected,
        onAspectRatioPresetSelected = viewModel::onAspectRatioPresetSelected,
        onPreviewUriChanged = viewModel::onPreviewUriChanged,
        onCropRectChanged = viewModel::onCropRectChanged,
        onShowCropDialog = viewModel::setShowCropDialog,
        onShowColorPickerDialog = viewModel::setShowColorPickerDialog,
        onShowOrganizerDialog = viewModel::setShowOrganizerDialog,
        onPdfPasswordChanged = viewModel::onPdfPasswordChanged,
        onAllowPrintingChanged = viewModel::onAllowPrintingChanged,
        onAllowCopyingChanged = viewModel::onAllowCopyingChanged,
        onAllowEditingChanged = viewModel::onAllowEditingChanged,
        onAuthorChange = viewModel::onEditAuthorChanged,
        onTitleChange = viewModel::onEditTitleChanged,
        onSubjectChange = viewModel::onEditSubjectChanged,
        onSoftwareChange = viewModel::onEditSoftwareChanged,
        onCopyrightChange = viewModel::onEditCopyrightChanged,
        onDateCreatedChange = viewModel::onEditDateCreatedChanged,
        onLatitudeChange = viewModel::onEditLatitudeChanged,
        onLongitudeChange = viewModel::onEditLongitudeChanged,
        onCameraMakeChange = viewModel::onEditCameraMakeChanged,
        onCameraModelChange = viewModel::onEditCameraModelChanged,
        onScrubClicked = {
            val workId = viewModel.startMetadataWorker(context, "scrub")
            if (workId != null) {
                onNavigateToProcessing("metadata_editor", workId.toString())
            }
        },
        onApplyEditsClicked = { params ->
            val workId = viewModel.startMetadataWorker(context, "edit", params)
            if (workId != null) {
                onNavigateToProcessing("metadata_editor", workId.toString())
            }
        },
        onConvert = {
            val workId = viewModel.startConversion(context)
            if (workId != null && state.conversionType != null) {
                onNavigateToProcessing(state.conversionType!!.id, workId.toString())
            }
        },
        onClearError = viewModel::clearError,
        onRemoveFiles = viewModel::removeSelectedFiles,
        onReloadMetadata = viewModel::loadMetadata
    )

    if (activeCropItem != null) {
        InteractiveCropDialog(
            imageUri = activeCropItem!!.workingUri,
            initialRotation = activeCropItem!!.rotationDegrees,
            onDismiss = { activeCropItem = null },
            onCropApplied = { left, top, right, bottom, rotation ->
                val targetId = activeCropItem!!.id
                val sourceUri = activeCropItem!!.workingUri
                coroutineScope.launch {
                    val croppedUri = cropAndSaveImageToCache(context, sourceUri, left, top, right, bottom, rotation)
                    if (croppedUri != null) {
                        viewModel.onImageCropped(targetId, croppedUri)
                    }
                }
                activeCropItem = null
            }
        )
    }

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

    if (state.showOrganizerDialog && state.selectedFileUri != null) {
        PdfPageOrganizerDialog(
            pdfUri = state.selectedFileUri!!,
            workbenchPages = state.workbenchPages,
            selectedPageIds = state.selectedWorkbenchPages,
            pageRotations = state.pageRotations,
            toolId = state.conversionType?.id ?: "",
            splitMode = state.splitMode,
            splitEveryN = state.splitEveryN,
            isLoading = state.isPdfLoading,
            onDismiss = { viewModel.setShowOrganizerDialog(false) },
            onToggleSelection = viewModel::togglePageSelection,
            onRotatePage = viewModel::rotatePage,
            onMovePage = viewModel::movePage,
            onSplitModeChanged = viewModel::onSplitModeChanged,
            onSplitEveryNChanged = viewModel::onSplitEveryNChanged,
            onConfirm = { viewModel.setShowOrganizerDialog(false) }
        )
    }
}

private suspend fun cropAndSaveImageToCache(
    context: Context,
    sourceUri: Uri,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    rotation: Int
): Uri? = withContext(Dispatchers.IO) {
    try {
        var bitmap: android.graphics.Bitmap? = null
        FileHelper.readFileFromUri(context, sourceUri).use {
            bitmap = android.graphics.BitmapFactory.decodeStream(it)
        }
        if (bitmap == null) return@withContext null
        
        var current = bitmap!!
        if (rotation % 360 != 0) {
            val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = android.graphics.Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
            if (rotated != current) {
                current.recycle()
                current = rotated
            }
        }
        
        val safeLeft = left.coerceIn(0, (current.width - 1).coerceAtLeast(0))
        val safeTop = top.coerceIn(0, (current.height - 1).coerceAtLeast(0))
        val safeRight = right.coerceIn(safeLeft + 1, current.width.coerceAtLeast(1))
        val safeBottom = bottom.coerceIn(safeTop + 1, current.height.coerceAtLeast(1))
        
        val cropped = android.graphics.Bitmap.createBitmap(current, safeLeft, safeTop, safeRight - safeLeft, safeBottom - safeTop)
        if (cropped != current) {
            current.recycle()
        }
        
        val destFile = File(context.cacheDir, "cropped_img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg")
        destFile.outputStream().use { out ->
            cropped.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
        }
        cropped.recycle()
        
        Uri.fromFile(destFile)
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversionConfigScreenContent(
    state: ConversionConfigState,
    isFolderOutput: Boolean,
    isImageConversion: Boolean,
    onNavigateBack: () -> Unit,
    onPickFile: (Boolean) -> Unit,
    onOutputFormatChanged: (String) -> Unit,
    onQualityChanged: (Int) -> Unit,
    onResizeOptionSelected: (String) -> Unit,
    onStripMetadataChanged: (Boolean) -> Unit,
    onWorkbenchItemClick: (WorkbenchImageItem) -> Unit,
    onWorkbenchItemLongClick: (WorkbenchImageItem) -> Unit,
    onSelectAllWorkbenchImages: () -> Unit,
    onDeselectAllWorkbenchImages: () -> Unit,
    onCropWorkbenchImage: (WorkbenchImageItem) -> Unit,
    onRotateWorkbenchImage: (WorkbenchImageItem) -> Unit,
    onResetWorkbenchImage: (WorkbenchImageItem) -> Unit,
    onApplyWorkbenchEdit: (WorkbenchImageItem) -> Unit,
    onCancelWorkbenchEdit: (WorkbenchImageItem) -> Unit,
    onPageRangeStartChanged: (String) -> Unit,
    onPageRangeEndChanged: (String) -> Unit,
    onOutputFileNameChanged: (String) -> Unit,
    onTargetWidthChanged: (String) -> Unit,
    onTargetHeightChanged: (String) -> Unit,
    onPaddingColorChanged: (Int) -> Unit,
    onTargetSizeKbChanged: (String) -> Unit,
    onCompressionPresetSelected: (String) -> Unit,
    onPdfCompressionPresetSelected: (String) -> Unit,
    onAspectRatioPresetSelected: (String) -> Unit,
    onPreviewUriChanged: (Uri) -> Unit,
    onCropRectChanged: (Int, Int, Int, Int) -> Unit,
    onShowCropDialog: (Boolean) -> Unit,
    onShowColorPickerDialog: (Boolean) -> Unit,
    onShowOrganizerDialog: (Boolean) -> Unit,
    onPdfPasswordChanged: (String) -> Unit,
    onAllowPrintingChanged: (Boolean) -> Unit,
    onAllowCopyingChanged: (Boolean) -> Unit,
    onAllowEditingChanged: (Boolean) -> Unit,
    onAuthorChange: (String) -> Unit = {},
    onTitleChange: (String) -> Unit = {},
    onSubjectChange: (String) -> Unit = {},
    onSoftwareChange: (String) -> Unit = {},
    onCopyrightChange: (String) -> Unit = {},
    onDateCreatedChange: (String) -> Unit = {},
    onLatitudeChange: (String) -> Unit = {},
    onLongitudeChange: (String) -> Unit = {},
    onCameraMakeChange: (String) -> Unit = {},
    onCameraModelChange: (String) -> Unit = {},
    onScrubClicked: () -> Unit = {},
    onApplyEditsClicked: (MetadataEditParams) -> Unit = {},
    onClearError: () -> Unit = {},
    onRemoveFiles: () -> Unit = {},
    onReloadMetadata: (Context, Uri) -> Unit = { _, _ -> },
    onConvert: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var showAdvancedSettings by remember { mutableStateOf(false) }
    val metaContext = LocalContext.current

    MorphScaffold(
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
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Inline error, dismissible — sits with the content it relates to.
                AnimatedVisibility(visible = state.errorMessage != null) {
                    ErrorBanner(
                        message = state.errorMessage ?: "",
                        onDismiss = onClearError
                    )
                }

            // Image Workbench Grid OR Document Preview Card
            if (isImageConversion && state.workbenchImageItems.isNotEmpty()) {
                ImageWorkbenchGrid(
                    items = state.workbenchImageItems,
                    expandedItemId = state.expandedImageId,
                    onItemClick = onWorkbenchItemClick,
                    onItemLongClick = onWorkbenchItemLongClick,
                    onSelectAll = onSelectAllWorkbenchImages,
                    onDeselectAll = onDeselectAllWorkbenchImages,
                    onCropClick = onCropWorkbenchImage,
                    onRotateClick = onRotateWorkbenchImage,
                    onResetClick = onResetWorkbenchImage,
                    onApplyEdit = onApplyWorkbenchEdit,
                    onCancelEdit = onCancelWorkbenchEdit
                )

                MorphButton(
                    text = "Add More Images",
                    onClick = { onPickFile(true) },
                    variant = MorphButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = Icons.Default.Add
                )
            } else {
                FileDropZone(
                    state = state,
                    onPickFile = onPickFile,
                    onRemoveFiles = onRemoveFiles
                )
            }

            // Metadata Inspector & Editor View
            // Contextual media-location permission: asked ONLY here inside the
            // metadata tool (never at app launch). Android 10+ redacts GPS data
            // from photo/video EXIF unless ACCESS_MEDIA_LOCATION is granted.
            val isMetadataTool = state.conversionType?.id == "metadata_editor"
            var mediaLocationGranted by remember {
                mutableStateOf(hasMediaLocationPermission(metaContext))
            }
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        mediaLocationGranted = hasMediaLocationPermission(metaContext)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            var locationBannerDismissed by rememberSaveable { mutableStateOf(false) }
            val mediaLocationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                mediaLocationGranted = granted
                if (granted) {
                    // Re-read metadata now that EXIF location is unredacted.
                    state.selectedFileUri?.let { onReloadMetadata(metaContext, it) }
                }
            }
            val showLocationBanner = isMetadataTool &&
                state.selectedFileUri != null &&
                !locationBannerDismissed &&
                !mediaLocationGranted &&
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
                isPhotoOrVideoFile(state.selectedFileName)
            androidx.compose.animation.AnimatedVisibility(visible = showLocationBanner) {
                MediaLocationPermissionBanner(
                    onAllow = {
                        mediaLocationLauncher.launch(android.Manifest.permission.ACCESS_MEDIA_LOCATION)
                    },
                    onDismiss = { locationBannerDismissed = true }
                )
            }
            if (state.conversionType?.id == "metadata_editor" && state.selectedFileUri != null) {
                MetadataInspectorView(
                    metadata = state.fileMetadata,
                    isLoading = state.isMetadataLoading,
                    editAuthor = state.editAuthor,
                    editTitle = state.editTitle,
                    editSubject = state.editSubject,
                    editSoftware = state.editSoftware,
                    editCopyright = state.editCopyright,
                    editDateCreated = state.editDateCreated,
                    editLatitude = state.editLatitude,
                    editLongitude = state.editLongitude,
                    editCameraMake = state.editCameraMake,
                    editCameraModel = state.editCameraModel,
                    gpsError = state.gpsError,
                    onAuthorChange = onAuthorChange,
                    onTitleChange = onTitleChange,
                    onSubjectChange = onSubjectChange,
                    onSoftwareChange = onSoftwareChange,
                    onCopyrightChange = onCopyrightChange,
                    onDateCreatedChange = onDateCreatedChange,
                    onLatitudeChange = onLatitudeChange,
                    onLongitudeChange = onLongitudeChange,
                    onCameraMakeChange = onCameraMakeChange,
                    onCameraModelChange = onCameraModelChange,
                    onScrubClicked = onScrubClicked,
                    onApplyEditsClicked = onApplyEditsClicked
                )
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

                // Advanced Settings ExpandableCard for Image Tools
                if (isImageConversion) {
                    OutlinedButton(
                        onClick = { showAdvancedSettings = !showAdvancedSettings },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(if (showAdvancedSettings) "Hide Advanced Settings" else "Show Advanced Settings")
                    }

                    AnimatedVisibility(visible = showAdvancedSettings) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Quality Slider
                            ConfigSection(title = "Compression Quality: ${state.quality}%") {
                                Slider(
                                    value = state.quality.toFloat(),
                                    onValueChange = { onQualityChanged(it.toInt()) },
                                    valueRange = 0f..100f
                                )
                            }

                            // Resize Options
                            ConfigSection(title = "Resize Options") {
                                val resizeOptions = listOf("Original", "50%", "25%", "Custom")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    resizeOptions.forEach { option ->
                                        FilterChip(
                                            selected = state.resizeOption == option,
                                            onClick = { onResizeOptionSelected(option) },
                                            label = { Text(option) }
                                        )
                                    }
                                }

                                if (state.resizeOption == "Custom") {
                                    Spacer(modifier = Modifier.height(8.dp))
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
                                }
                            }

                            // Strip Metadata Checkbox
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onStripMetadataChanged(!state.stripMetadata) }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = state.stripMetadata,
                                    onCheckedChange = { onStripMetadataChanged(it) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Strip Metadata",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Removes EXIF tags, location, and camera details from converted image.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Compress PDF Level
                if (state.conversionType?.id == "compress_pdf") {
                    ConfigSection(title = "Compression Level") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val pdfPresets = listOf(
                                    "Extreme" to "Extreme",
                                    "Recommended" to "Rec.",
                                    "Low" to "Low"
                                )
                                pdfPresets.forEach { (id, label) ->
                                    FilterChip(
                                        selected = state.compressionPreset == id,
                                        onClick = { onPdfCompressionPresetSelected(id) },
                                        label = { Text(label) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Page Range — optional; blank means "all pages". Validated inline
                // against the loaded page count only when the user types.
                AnimatedVisibility(visible = state.showPageRange) {
                    val totalPages = state.pdfPageCount
                    val startError = pageFieldError(state.pageRangeStart, "Start page", totalPages)
                    val endError = pageFieldError(state.pageRangeEnd, "End page", totalPages)
                    val orderError = if (startError == null && endError == null &&
                        state.pageRangeStart.isNotBlank() && state.pageRangeEnd.isNotBlank()
                    ) {
                        val s = state.pageRangeStart.toInt()
                        val e = state.pageRangeEnd.toInt()
                        if (s > e) "Start page must not be after end page" else null
                    } else null

                    ConfigSection(title = "Page Range (Optional)") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = state.pageRangeStart,
                                onValueChange = onPageRangeStartChanged,
                                label = { Text("From Page") },
                                placeholder = { Text("1") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                isError = startError != null || orderError != null,
                                supportingText = {
                                    (startError ?: orderError)?.let {
                                        Text(it, color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            OutlinedTextField(
                                value = state.pageRangeEnd,
                                onValueChange = onPageRangeEndChanged,
                                label = { Text("To Page") },
                                placeholder = { Text("All") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                isError = endError != null || orderError != null,
                                supportingText = {
                                    (endError ?: orderError)?.let {
                                        Text(it, color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                        Text(
                            text = if (totalPages > 0) {
                                "This document has $totalPages pages. Leave empty to convert all pages."
                            } else {
                                "Leave empty to convert all pages."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Security Cockpit
                if (state.conversionType?.id == "protect_pdf") {
                    SecurityCockpit(
                        password = state.pdfPassword,
                        onPasswordChange = onPdfPasswordChanged,
                        allowPrinting = state.allowPrinting,
                        onAllowPrintingChange = onAllowPrintingChanged,
                        allowCopying = state.allowCopying,
                        onAllowCopyingChange = onAllowCopyingChanged,
                        allowEditing = state.allowEditing,
                        onAllowEditingChange = onAllowEditingChanged
                    )
                } else if (state.conversionType?.id == "unlock_pdf") {
                    UnlockCockpit(
                        password = state.pdfPassword,
                        onPasswordChange = onPdfPasswordChanged
                    )
                }

                // Dynamic Output Label: "Output Folder Name" if folder output, "Output File Name" if single file
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

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Sticky bottom Convert CTA — always reachable, no scroll needed.
        if (state.conversionType?.id != "metadata_editor") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                MorphButton(
                    text = "Convert",
                    onClick = onConvert,
                    enabled = state.isConvertEnabled,
                    variant = MorphButtonVariant.Primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentDescription = state.conversionType?.let {
                        "Convert to ${it.outputType.displayName}"
                    }
                )
            }
        }
    }
}
}

/** Returns a validation message for a page-range field, or null when valid. */
private fun pageFieldError(value: String, label: String, totalPages: Int): String? {
    // The page range is optional: blank means "all pages", so never an error.
    if (value.isBlank()) return null
    val n = value.toIntOrNull() ?: return "Enter a page number"
    if (n < 1) return "Must be at least 1"
    if (totalPages > 0 && n > totalPages) return "Document has $totalPages pages"
    return null
}

/**
 * Dashed drop-zone input card with two states:
 * - empty: tappable dashed area prompting file selection
 * - filled: preview (image / themed Excel / generic icon), file name + size,
 *   format chip, and Change / Add-more / Remove actions.
 */
@Composable
private fun FileDropZone(
    state: ConversionConfigState,
    onPickFile: (Boolean) -> Unit,
    onRemoveFiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val hasFile = state.selectedFileUris.isNotEmpty()
    val ext = state.selectedFileName.substringAfterLast('.', "").lowercase()
    val isExcel = state.conversionType?.id == "excel_to_pdf" || ext in listOf("xlsx", "xls", "csv")

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceContainerLow
    ) {
        Column {
            // Preview area — or the empty drop-zone prompt.
            if (!hasFile) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(16.dp)
                        .dashedBorder(2.dp, scheme.primary.copy(alpha = 0.6f), 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button, onClick = { onPickFile(false) }),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = scheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isExcel) "Select Excel Spreadsheet (.xlsx, .xls, .csv)"
                            else "Tap to choose a file",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.conversionType?.let {
                                "Accepted: ${it.inputType.displayName}"
                            } ?: "Your file never leaves this device",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                ) {
                    if (isExcel) {
                        ExcelPreviewCard(
                            fileName = state.selectedFileName,
                            isExcelToPdf = state.conversionType?.id == "excel_to_pdf"
                        )
                    } else if (state.selectedPreviewUri != null) {
                        AsyncImage(
                            model = state.selectedPreviewUri,
                            contentDescription = "Selected file preview",
                            modifier = Modifier
                                .fillMaxSize()
                                .background(scheme.surfaceContainerHigh),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(scheme.surfaceContainerHigh),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = getContentIconForFileName(state.selectedFileName),
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = scheme.primary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = getContentLabelForFileName(state.selectedFileName),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.selectedFileName.ifBlank { "No File Selected" },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (hasFile) {
                            Text(
                                text = FileHelper.formatFileSize(state.selectedFileSize),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                    if (state.conversionType?.id == "metadata_editor" && state.fileMetadata != null) {
                        if (state.fileMetadata.fileType != null) {
                            FormatChip(fileType = state.fileMetadata.fileType)
                        } else {
                            FormatChip(
                                format = state.fileMetadata.fileExtension,
                                containerColor = scheme.primaryContainer,
                                contentColor = scheme.onPrimaryContainer
                            )
                        }
                    } else if (state.conversionType != null) {
                        TransformIndicator(
                            conversionType = state.conversionType!!,
                            compact = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MorphButton(
                        text = if (hasFile) "Change" else "Select File",
                        onClick = { onPickFile(false) },
                        variant = MorphButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                        leadingIcon = Icons.Default.UploadFile
                    )
                    if (state.conversionType?.id == "merge_pdf" && hasFile) {
                        MorphButton(
                            text = "Add More",
                            onClick = { onPickFile(true) },
                            variant = MorphButtonVariant.Secondary,
                            modifier = Modifier.weight(1f),
                            leadingIcon = Icons.Default.Add
                        )
                    }
                    if (hasFile) {
                        MorphButton(
                            text = "Remove",
                            onClick = onRemoveFiles,
                            variant = MorphButtonVariant.Ghost,
                            contentDescription = "Remove selected file"
                        )
                    }
                }
            }
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

@Composable
private fun SecurityCockpit(
    password: String,
    onPasswordChange: (String) -> Unit,
    allowPrinting: Boolean,
    onAllowPrintingChange: (Boolean) -> Unit,
    allowCopying: Boolean,
    onAllowCopyingChange: (Boolean) -> Unit,
    allowEditing: Boolean,
    onAllowEditingChange: (Boolean) -> Unit
) {
    ConfigSection(title = "Security Cockpit") {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                var passwordVisible by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("PDF Password") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = null)
                        }
                    }
                )

                val strength = calculatePasswordStrength(password)
                val strengthColor = when {
                    strength <= 0f -> MaterialTheme.colorScheme.outline
                    strength <= 0.3f -> MaterialTheme.colorScheme.error
                    strength <= 0.7f -> MaterialTheme.morphColors.warning
                    else -> MaterialTheme.morphColors.success
                }
                val strengthLabel = when {
                    strength <= 0f -> "No Password"
                    strength <= 0.3f -> "Weak"
                    strength <= 0.7f -> "Medium"
                    else -> "Strong"
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Password Strength", style = MaterialTheme.typography.labelMedium)
                        Text(strengthLabel, style = MaterialTheme.typography.labelMedium, color = strengthColor, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { strength },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = strengthColor,
                        trackColor = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                Text("Permissions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                
                PermissionToggle(
                    label = "Allow Printing",
                    icon = Icons.Default.Print,
                    checked = allowPrinting,
                    onCheckedChange = onAllowPrintingChange
                )
                PermissionToggle(
                    label = "Allow Copying",
                    icon = Icons.Default.ContentCopy,
                    checked = allowCopying,
                    onCheckedChange = onAllowCopyingChange
                )
                PermissionToggle(
                    label = "Allow Editing",
                    icon = Icons.Default.Edit,
                    checked = allowEditing,
                    onCheckedChange = onAllowEditingChange
                )
            }
        }
    }
}

@Composable
private fun UnlockCockpit(
    password: String,
    onPasswordChange: (String) -> Unit
) {
    ConfigSection(title = "Unlock Settings") {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                var passwordVisible by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("PDF Password") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = null)
                        }
                    }
                )
                Text(
                    text = "Please provide the current password to permanently unlock this PDF.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PermissionToggle(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun isPreviewableImage(fileName: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return ext in listOf("jpg", "jpeg", "png", "webp", "bmp", "heic", "gif")
}

@Composable
private fun ExcelPreviewCard(
    fileName: String,
    isExcelToPdf: Boolean = true,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val ext = fileName.substringAfterLast('.', "").uppercase().ifBlank { "XLSX" }
    val isCsv = ext == "CSV"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        // Subtle spreadsheet grid in theme outline tones — no gradients.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 28.dp.toPx()
            val gridColor = scheme.outlineVariant
            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f,
                    alpha = 0.6f
                )
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                    alpha = 0.6f
                )
                y += step
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = scheme.secondaryContainer,
                contentColor = scheme.onSecondaryContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.TableChart,
                        contentDescription = "Spreadsheet",
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isCsv) "Delimited Data Table" else "Microsoft Excel Spreadsheet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = ext,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isExcelToPdf) "• Ready for PDF conversion" else "• Spreadsheet selected",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

private fun getContentIconForFileName(fileName: String): androidx.compose.ui.graphics.vector.ImageVector {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "pdf" -> Icons.Outlined.PictureAsPdf
        "mp4", "mkv", "avi", "mov", "3gp", "webm", "m4v" -> Icons.Outlined.Movie
        "mp3", "flac", "wav", "aac", "ogg", "m4a" -> Icons.Outlined.AudioFile
        "xlsx", "xls", "csv" -> Icons.Outlined.TableChart
        "txt", "md", "markdown" -> Icons.AutoMirrored.Outlined.Article
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}

private fun getContentLabelForFileName(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "pdf" -> "PDF Document"
        "mp4", "mkv", "avi", "mov", "3gp", "webm", "m4v" -> "Video File"
        "mp3", "flac", "wav", "aac", "ogg", "m4a" -> "Audio File"
        "xlsx", "xls", "csv" -> "Spreadsheet"
        "txt", "md", "markdown" -> "Text Document"
        "" -> "File"
        else -> "${ext.uppercase()} File"
    }
}

private fun calculatePasswordStrength(password: String): Float {
    if (password.isEmpty()) return 0f
    var score = 0f
    if (password.length >= 8) score += 0.25f
    if (password.any { it.isDigit() }) score += 0.25f
    if (password.any { it.isUpperCase() }) score += 0.25f
    if (password.any { !it.isLetterOrDigit() }) score += 0.25f
    return score
}

@Preview(name = "Light Mode - Image Conversion", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
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
            isImageConversion = true,
            onNavigateBack = {},
            onPickFile = {},
            onOutputFormatChanged = {},
            onQualityChanged = {},
            onResizeOptionSelected = {},
            onStripMetadataChanged = {},
            onWorkbenchItemClick = {},
            onWorkbenchItemLongClick = {},
            onSelectAllWorkbenchImages = {},
            onDeselectAllWorkbenchImages = {},
            onCropWorkbenchImage = {},
            onRotateWorkbenchImage = {},
            onResetWorkbenchImage = {},
            onApplyWorkbenchEdit = {},
            onCancelWorkbenchEdit = {},
            onPageRangeStartChanged = {},
            onPageRangeEndChanged = {},
            onOutputFileNameChanged = {},
            onTargetWidthChanged = {},
            onTargetHeightChanged = {},
            onPaddingColorChanged = {},
            onTargetSizeKbChanged = {},
            onCompressionPresetSelected = {},
            onPdfCompressionPresetSelected = {},
            onAspectRatioPresetSelected = {},
            onPreviewUriChanged = {},
            onCropRectChanged = { _, _, _, _ -> },
            onShowCropDialog = {},
            onShowColorPickerDialog = {},
            onShowOrganizerDialog = {},
            onPdfPasswordChanged = {},
            onAllowPrintingChanged = {},
            onAllowCopyingChanged = {},
            onAllowEditingChanged = {},
            onConvert = {}
        )
    }
}

@Preview(name = "Dark Mode - Image Conversion", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun ConversionConfigScreenDarkPreview() {
    MorphDropTheme(darkTheme = true) {
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
            isImageConversion = true,
            onNavigateBack = {},
            onPickFile = {},
            onOutputFormatChanged = {},
            onQualityChanged = {},
            onResizeOptionSelected = {},
            onStripMetadataChanged = {},
            onWorkbenchItemClick = {},
            onWorkbenchItemLongClick = {},
            onSelectAllWorkbenchImages = {},
            onDeselectAllWorkbenchImages = {},
            onCropWorkbenchImage = {},
            onRotateWorkbenchImage = {},
            onResetWorkbenchImage = {},
            onApplyWorkbenchEdit = {},
            onCancelWorkbenchEdit = {},
            onPageRangeStartChanged = {},
            onPageRangeEndChanged = {},
            onOutputFileNameChanged = {},
            onTargetWidthChanged = {},
            onTargetHeightChanged = {},
            onPaddingColorChanged = {},
            onTargetSizeKbChanged = {},
            onCompressionPresetSelected = {},
            onPdfCompressionPresetSelected = {},
            onAspectRatioPresetSelected = {},
            onPreviewUriChanged = {},
            onCropRectChanged = { _, _, _, _ -> },
            onShowCropDialog = {},
            onShowColorPickerDialog = {},
            onShowOrganizerDialog = {},
            onPdfPasswordChanged = {},
            onAllowPrintingChanged = {},
            onAllowCopyingChanged = {},
            onAllowEditingChanged = {},
            onConvert = {}
        )
    }
}

@Preview(name = "Light Mode - Metadata Editor", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun ConversionConfigScreen_MetadataEditor_LightPreview() {
    val metaType = ConversionType.defaultList.firstOrNull { it.id == "metadata_editor" } ?: ConversionType.defaultList.first()
    MorphDropTheme(darkTheme = false) {
        ConversionConfigScreenContent(
            state = ConversionConfigState(
                conversionType = metaType,
                selectedFileUris = listOf(Uri.parse("content://mock")),
                selectedFileNames = listOf("IMG_20240512_143000.jpg"),
                selectedFileSize = 3450000L,
                fileMetadata = FileMetadata(
                    fileName = "IMG_20240512_143000.jpg",
                    fileSize = 3450000L,
                    fileSizeFormatted = "3.4 MB",
                    mimeType = "image/jpeg",
                    fileExtension = "jpg",
                    fileType = FileType.JPG,
                    cameraMake = "Google",
                    cameraModel = "Pixel 7 Pro",
                    author = "MorphDrop User",
                    title = "Golden Gate Bridge",
                    hasMetadata = true,
                    isScrubbable = true,
                    isEditable = true
                ),
                editAuthor = "MorphDrop User",
                editTitle = "Golden Gate Bridge",
                outputFileName = "IMG_20240512_143000_edited",
                isConvertEnabled = true
            ),
            isFolderOutput = false,
            isImageConversion = false,
            onNavigateBack = {},
            onPickFile = {},
            onOutputFormatChanged = {},
            onQualityChanged = {},
            onResizeOptionSelected = {},
            onStripMetadataChanged = {},
            onWorkbenchItemClick = {},
            onWorkbenchItemLongClick = {},
            onSelectAllWorkbenchImages = {},
            onDeselectAllWorkbenchImages = {},
            onCropWorkbenchImage = {},
            onRotateWorkbenchImage = {},
            onResetWorkbenchImage = {},
            onApplyWorkbenchEdit = {},
            onCancelWorkbenchEdit = {},
            onPageRangeStartChanged = {},
            onPageRangeEndChanged = {},
            onOutputFileNameChanged = {},
            onTargetWidthChanged = {},
            onTargetHeightChanged = {},
            onPaddingColorChanged = {},
            onTargetSizeKbChanged = {},
            onCompressionPresetSelected = {},
            onPdfCompressionPresetSelected = {},
            onAspectRatioPresetSelected = {},
            onPreviewUriChanged = {},
            onCropRectChanged = { _, _, _, _ -> },
            onShowCropDialog = {},
            onShowColorPickerDialog = {},
            onShowOrganizerDialog = {},
            onPdfPasswordChanged = {},
            onAllowPrintingChanged = {},
            onAllowCopyingChanged = {},
            onAllowEditingChanged = {},
            onConvert = {}
        )
    }
}

@Preview(name = "Dark Mode - Metadata Editor", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun ConversionConfigScreen_MetadataEditor_DarkPreview() {
    val metaType = ConversionType.defaultList.firstOrNull { it.id == "metadata_editor" } ?: ConversionType.defaultList.first()
    MorphDropTheme(darkTheme = true) {
        ConversionConfigScreenContent(
            state = ConversionConfigState(
                conversionType = metaType,
                selectedFileUris = listOf(Uri.parse("content://mock")),
                selectedFileNames = listOf("IMG_20240512_143000.jpg"),
                selectedFileSize = 3450000L,
                fileMetadata = FileMetadata(
                    fileName = "IMG_20240512_143000.jpg",
                    fileSize = 3450000L,
                    fileSizeFormatted = "3.4 MB",
                    mimeType = "image/jpeg",
                    fileExtension = "jpg",
                    fileType = FileType.JPG,
                    cameraMake = "Google",
                    cameraModel = "Pixel 7 Pro",
                    author = "MorphDrop User",
                    title = "Golden Gate Bridge",
                    hasMetadata = true,
                    isScrubbable = true,
                    isEditable = true
                ),
                editAuthor = "MorphDrop User",
                editTitle = "Golden Gate Bridge",
                outputFileName = "IMG_20240512_143000_edited",
                isConvertEnabled = true
            ),
            isFolderOutput = false,
            isImageConversion = false,
            onNavigateBack = {},
            onPickFile = {},
            onOutputFormatChanged = {},
            onQualityChanged = {},
            onResizeOptionSelected = {},
            onStripMetadataChanged = {},
            onWorkbenchItemClick = {},
            onWorkbenchItemLongClick = {},
            onSelectAllWorkbenchImages = {},
            onDeselectAllWorkbenchImages = {},
            onCropWorkbenchImage = {},
            onRotateWorkbenchImage = {},
            onResetWorkbenchImage = {},
            onApplyWorkbenchEdit = {},
            onCancelWorkbenchEdit = {},
            onPageRangeStartChanged = {},
            onPageRangeEndChanged = {},
            onOutputFileNameChanged = {},
            onTargetWidthChanged = {},
            onTargetHeightChanged = {},
            onPaddingColorChanged = {},
            onTargetSizeKbChanged = {},
            onCompressionPresetSelected = {},
            onPdfCompressionPresetSelected = {},
            onAspectRatioPresetSelected = {},
            onPreviewUriChanged = {},
            onCropRectChanged = { _, _, _, _ -> },
            onShowCropDialog = {},
            onShowColorPickerDialog = {},
            onShowOrganizerDialog = {},
            onPdfPasswordChanged = {},
            onAllowPrintingChanged = {},
            onAllowCopyingChanged = {},
            onAllowEditingChanged = {},
            onConvert = {}
        )
    }
}
/** True when the app may read unredacted EXIF GPS on this device (no runtime ask below Android 10). */
private fun hasMediaLocationPermission(context: Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return true
    return androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_MEDIA_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

private fun isPhotoOrVideoFile(fileName: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return ext in setOf(
        "jpg", "jpeg", "png", "heic", "heif", "webp", "bmp", "gif",
        "tiff", "tif", "dng", "arw", "cr2", "nef", "rw2",
        "mp4", "mkv", "mov", "avi", "3gp", "webm", "m4v"
    )
}

/**
 * Inline, dismissible rationale shown ONLY inside the metadata tool — never at
 * app launch. Explains why media-location access is useful (GPS from photo EXIF)
 * and lets the user skip it without losing anything else.
 */
@Composable
private fun MediaLocationPermissionBanner(
    onAllow: () -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.secondaryContainer.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = scheme.secondary.copy(alpha = 0.16f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = scheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "See photo location",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Allow access to media location so GPS data saved inside your photos and videos can be shown here. Optional — everything else works without it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MorphButton(
                        text = "Allow",
                        onClick = onAllow,
                        variant = MorphButtonVariant.Primary,
                        modifier = Modifier.height(40.dp)
                    )
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(40.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Not now")
                    }
                }
            }
        }
    }
}
