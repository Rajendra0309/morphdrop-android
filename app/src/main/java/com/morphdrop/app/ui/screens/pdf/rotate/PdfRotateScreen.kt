package com.morphdrop.app.ui.screens.pdf.rotate

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.morphdrop.app.ui.theme.MorphDropTheme
import com.morphdrop.app.PdfViewerActivity
import com.morphdrop.app.domain.model.FileType
import com.morphdrop.app.domain.model.RotateScope
import com.morphdrop.app.ui.components.FormatBadge
import com.morphdrop.app.ui.components.MorphDropTopAppBar
import com.morphdrop.app.ui.components.PrimaryButton
import com.morphdrop.app.ui.screens.processing.ProcessingScreenContent
import com.morphdrop.app.ui.screens.processing.ProcessingUiState
import com.morphdrop.app.ui.screens.result.OutputFileItem
import com.morphdrop.app.ui.screens.result.ResultScreenContent
import com.morphdrop.app.ui.screens.result.ResultUiState
import com.morphdrop.app.util.FileHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfRotateScreen(
    initialUri: Uri? = null,
    onNavigateBack: () -> Unit,
    viewModel: PdfRotateViewModel = hiltViewModel()
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
            ProcessingScreenContent(
                state = ProcessingUiState(
                    progress = 50f,
                    currentStage = "Rotating PDF pages...",
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
                    title = "PDF Rotated Successfully!",
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
                    context.startActivity(Intent.createChooser(shareIntent, "Share Rotated PDF"))
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
            PdfRotateContent(
                state = state,
                onNavigateBack = onNavigateBack,
                onPickPdfClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                onSelectDegrees = viewModel::setDegrees,
                onSelectScope = viewModel::setScope,
                onCustomRangeChanged = viewModel::setCustomRangeText,
                onRotatePdf = viewModel::rotatePdf
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PdfRotateContent(
    state: PdfRotateUiState,
    onNavigateBack: () -> Unit = {},
    onPickPdfClick: () -> Unit = {},
    onSelectDegrees: (Int) -> Unit = {},
    onSelectScope: (RotateScope) -> Unit = {},
    onCustomRangeChanged: (String) -> Unit = {},
    onRotatePdf: () -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MorphDropTopAppBar(
                title = "Rotate PDF",
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

            // Error banner if any
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

            // Unified Hero Document Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    ) {
                        if (state.selectedUri != null && state.previewBitmap != null) {
                            val animatedRotation by animateFloatAsState(
                                targetValue = state.degrees.toFloat(),
                                animationSpec = spring(),
                                label = "PageRotationAnimation"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.foundation.Image(
                                    bitmap = state.previewBitmap!!.asImageBitmap(),
                                    contentDescription = "PDF Page Rotation Preview",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize(0.85f)
                                        .rotate(animatedRotation)
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = "+${state.degrees}°",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
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
                                        "Tap to select a PDF to rotate its pages"
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
                            Icon(imageVector = Icons.Default.UploadFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.selectedUri != null) "Change PDF Document" else "Select PDF Document",
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Configuration Panel
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Rotation Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Rotation Angle Chips
                    Text("Rotation Angle", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            90 to "90° Right",
                            180 to "180° Flip",
                            270 to "270° Left"
                        ).forEach { (deg, label) ->
                            FilterChip(
                                selected = state.degrees == deg,
                                onClick = { onSelectDegrees(deg) },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Scope Chips
                    Text("Apply Rotation To", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RotateScope.entries.forEach { sc ->
                            FilterChip(
                                selected = state.scope == sc,
                                onClick = { onSelectScope(sc) },
                                label = { Text(sc.label) }
                            )
                        }
                    }

                    if (state.scope == RotateScope.ALL_PAGES) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.customRangeText,
                            onValueChange = onCustomRangeChanged,
                            label = { Text("Specific Pages (optional, e.g. 1-3, 5)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Action Button
            PrimaryButton(
                text = if (state.isProcessing) "Rotating PDF..." else "Rotate PDF",
                onClick = onRotatePdf,
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

@Preview(name = "Rotate PDF - Empty Light", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun PdfRotateScreenEmptyLightPreview() {
    MorphDropTheme(darkTheme = false) {
        PdfRotateContent(
            state = PdfRotateUiState()
        )
    }
}

@Preview(name = "Rotate PDF - Empty Dark", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PdfRotateScreenEmptyDarkPreview() {
    MorphDropTheme(darkTheme = true) {
        PdfRotateContent(
            state = PdfRotateUiState()
        )
    }
}

@Preview(name = "Rotate PDF - Selected Light", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun PdfRotateScreenSelectedLightPreview() {
    MorphDropTheme(darkTheme = false) {
        PdfRotateContent(
            state = PdfRotateUiState(
                selectedUri = Uri.parse("content://dummy/sample.pdf"),
                fileName = "Annual_Report_2026.pdf",
                fileSize = 4_850_000L,
                pageCount = 16,
                degrees = 90
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Rotate PDF - Result Light", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun PdfRotateScreenResultLightPreview() {
    MorphDropTheme(darkTheme = false) {
        ResultScreenContent(
            state = ResultUiState(
                title = "PDF Rotated Successfully!",
                subtitle = "1 file created • 431.7 KB",
                outputFiles = listOf(
                    OutputFileItem(
                        id = "1",
                        fileName = "interview_rotated.pdf",
                        fileSizeFormatted = "431.7 KB",
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
