package com.morphdrop.app.ui.screens.conversion

import android.content.Intent
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.domain.model.ConversionType
import com.morphdrop.app.ui.components.FormatBadge
import com.morphdrop.app.ui.components.GlassCard
import com.morphdrop.app.ui.components.PrimaryButton
import com.morphdrop.app.ui.theme.LocalLiquidState
import com.morphdrop.app.ui.theme.MorphDropTheme
import com.morphdrop.app.ui.theme.NeonEmerald
import com.morphdrop.app.ui.theme.TextPrimary
import com.morphdrop.app.ui.theme.TextSecondary
import com.morphdrop.app.util.FileHelper
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.rememberLiquidState

@Composable
fun ConversionConfigScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToProcessing: (conversionTypeId: String, workId: String) -> Unit = { _, _ -> },
    viewModel: ConversionConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val liquidState = LocalLiquidState.current

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
        liquidState = liquidState,
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
    liquidState: LiquidState,
    onNavigateBack: () -> Unit,
    onPickFile: () -> Unit,
    onOutputFormatChanged: (String) -> Unit,
    onQualityChanged: (Int) -> Unit,
    onPageRangeStartChanged: (String) -> Unit,
    onPageRangeEndChanged: (String) -> Unit,
    onOutputFileNameChanged: (String) -> Unit,
    onConvert: () -> Unit
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.conversionType?.name ?: "Configure",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        color = TextPrimary,
                        letterSpacing = (-0.5).sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(visible = state.errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE53935).copy(alpha = 0.25f))
                        .padding(14.dp)
                ) {
                    Text(
                        text = state.errorMessage ?: "",
                        color = Color(0xFFFF8A80),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            GlassCard(
                liquidState = liquidState,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NeonEmerald.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    if (state.selectedFileUri != null) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.selectedFileName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                color = TextPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = FileHelper.formatFileSize(state.selectedFileSize),
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                        state.conversionType?.inputType?.let { FormatBadge(fileType = it) }
                    } else {
                        Text(
                            text = "No file selected",
                            fontSize = 15.sp,
                            color = TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            GlassCard(
                liquidState = liquidState,
                onClick = onPickFile,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = null,
                        tint = NeonEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (state.selectedFileUri != null) "Change File" else "Select Input File",
                        fontWeight = FontWeight.Bold,
                        color = NeonEmerald
                    )
                }
            }

            if (state.availableOutputFormats.size > 1) {
                Text(
                    text = "Output Format",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = TextPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.availableOutputFormats.forEach { format ->
                        val isSelected = state.outputFormat == format
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) NeonEmerald else Color.White.copy(alpha = 0.25f)
                                )
                                .clickable { onOutputFormatChanged(format) }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = format.uppercase(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isSelected) Color.Black else TextPrimary
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = state.showQualitySlider) {
                GlassCard(
                    liquidState = liquidState,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Quality",
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "${state.quality}%",
                                fontWeight = FontWeight.Bold,
                                color = NeonEmerald
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = state.quality.toFloat(),
                            onValueChange = { onQualityChanged(it.toInt()) },
                            valueRange = 1f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = NeonEmerald,
                                activeTrackColor = NeonEmerald,
                                inactiveTrackColor = NeonEmerald.copy(alpha = 0.24f)
                            )
                        )
                    }
                }
            }

            AnimatedVisibility(visible = state.showPageRange) {
                GlassCard(
                    liquidState = liquidState,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Page Range (Optional)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = state.pageRangeStart,
                                onValueChange = { onPageRangeStartChanged(it.filter { c -> c.isDigit() }) },
                                label = { Text("From", color = TextSecondary) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonEmerald,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                            OutlinedTextField(
                                value = state.pageRangeEnd,
                                onValueChange = { onPageRangeEndChanged(it.filter { c -> c.isDigit() }) },
                                label = { Text("To", color = TextSecondary) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonEmerald,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                        }
                    }
                }
            }

            GlassCard(
                liquidState = liquidState,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Output File Name",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.outputFileName,
                        onValueChange = { onOutputFileNameChanged(it) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonEmerald,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            PrimaryButton(
                text = "Convert",
                enabled = state.isConvertEnabled,
                onClick = onConvert,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ConversionConfigScreenPreview() {
    MorphDropTheme {
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
            liquidState = rememberLiquidState(),
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
