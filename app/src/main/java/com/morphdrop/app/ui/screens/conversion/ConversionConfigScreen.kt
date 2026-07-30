package com.morphdrop.app.ui.screens.conversion

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.domain.model.ConversionType
import com.morphdrop.app.ui.components.FormatBadge
import com.morphdrop.app.ui.components.MorphDropTopAppBar
import com.morphdrop.app.ui.components.PrimaryButton
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
        "word_to_pdf" -> arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/msword")
        "excel_to_pdf" -> arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "text/csv")
        "ppt_to_pdf" -> arrayOf("application/vnd.openxmlformats-officedocument.presentationml.presentation", "application/vnd.ms-powerpoint")
        "text_to_pdf" -> arrayOf("text/plain")
        "md_to_pdf" -> arrayOf("text/markdown", "text/x-markdown", "text/plain")
        "pdf_to_images", "split_pdf", "compress_pdf", "protect_pdf", "organize_pdf", "merge_pdf" -> arrayOf("application/pdf")
        "images_to_pdf", "compress_images", "image_converter" -> arrayOf("image/*")
        else -> arrayOf("*/*")
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.onFilesSelected(context, uris)
        }
    }

    LaunchedEffect(Unit) {
        if (state.selectedFileUris.isEmpty()) {
            filePicker.launch(mimeFilter)
        }
    }

    ConversionConfigScreenContent(
        state = state,
        isFolderOutput = viewModel.isFolderOutput(state.conversionType),
        onNavigateBack = onNavigateBack,
        onPickFile = { filePicker.launch(mimeFilter) },
        onOutputFormatChanged = viewModel::onOutputFormatChanged,
        onQualityChanged = viewModel::onQualityChanged,
        onPageRangeStartChanged = viewModel::onPageRangeStartChanged,
        onPageRangeEndChanged = viewModel::onPageRangeEndChanged,
        onOutputFileNameChanged = viewModel::onOutputFileNameChanged,
        onConvert = {
            val workId = viewModel.startConversion(context)
            if (workId != null && state.conversionType != null) {
                onNavigateToProcessing(state.conversionType!!.id, workId.toString())
            }
        }
    )
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
    onConvert: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

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

            // File Information
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val fileName = if (state.selectedFileUris.isNotEmpty()) state.selectedFileNames.first() else "No file selected"
                            Text(
                                text = fileName,
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
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = onPickFile,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (state.selectedFileUris.isNotEmpty()) "Change File" else "Select Input File")
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

                // Page Range
                AnimatedVisibility(visible = state.showPageRange) {
                    ConfigSection(title = "Page Range") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = state.pageRangeStart,
                                onValueChange = { onPageRangeStartChanged(it.filter { c -> c.isDigit() }) },
                                label = { Text("From") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = state.pageRangeEnd,
                                onValueChange = { onPageRangeEndChanged(it.filter { c -> c.isDigit() }) },
                                label = { Text("To") },
                                modifier = Modifier.weight(1f),
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
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            PrimaryButton(
                text = "Convert",
                enabled = state.isConvertEnabled,
                onClick = onConvert,
                modifier = Modifier.fillMaxWidth()
            )

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
                selectedFileNames = listOf("Sample_Document.pdf"),
                selectedFileSize = 1024L * 1024L * 2,
                availableOutputFormats = listOf("png", "jpg"),
                outputFormat = "png",
                showQualitySlider = true,
                quality = 85,
                showPageRange = true,
                pageRangeStart = "1",
                pageRangeEnd = "10",
                outputFileName = "Sample_Document_Converted",
                isConvertEnabled = true
            ),
            isFolderOutput = false,
            onNavigateBack = {},
            onPickFile = {},
            onOutputFormatChanged = {},
            onQualityChanged = {},
            onPageRangeStartChanged = {},
            onPageRangeEndChanged = {},
            onOutputFileNameChanged = {},
            onConvert = {}
        )
    }
}

@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun ConversionConfigScreenDarkPreview() {
    MorphDropTheme(darkTheme = true) {
        ConversionConfigScreenContent(
            state = ConversionConfigState(
                conversionType = ConversionType.defaultList.first(),
                selectedFileUris = listOf(Uri.parse("content://mock")),
                selectedFileNames = listOf("Sample_Document.pdf"),
                selectedFileSize = 1024L * 1024L * 2,
                availableOutputFormats = listOf("png", "jpg"),
                outputFormat = "png",
                showQualitySlider = true,
                quality = 85,
                showPageRange = true,
                pageRangeStart = "1",
                pageRangeEnd = "10",
                outputFileName = "Sample_Document_Converted",
                isConvertEnabled = true
            ),
            isFolderOutput = false,
            onNavigateBack = {},
            onPickFile = {},
            onOutputFormatChanged = {},
            onQualityChanged = {},
            onPageRangeStartChanged = {},
            onPageRangeEndChanged = {},
            onOutputFileNameChanged = {},
            onConvert = {}
        )
    }
}
