package com.morphdrop.app.ui.screens.conversion

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.ui.components.FormatBadge
import com.morphdrop.app.util.FileHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversionConfigScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToProcessing: (conversionTypeId: String) -> Unit = {},
    viewModel: ConversionConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val mimeFilter = when (state.conversionType?.inputType?.extension) {
        "pdf" -> arrayOf("application/pdf")
        "docx" -> arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        "xlsx" -> arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        "pptx" -> arrayOf("application/vnd.openxmlformats-officedocument.presentationml.presentation")
        "txt" -> arrayOf("text/plain")
        "png" -> arrayOf("image/png", "image/jpeg", "image/webp", "image/*")
        "jpg" -> arrayOf("image/jpeg", "image/png", "image/webp", "image/*")
        else -> arrayOf("*/*")
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, flags)
            val name = FileHelper.getFileName(context, it)
            val size = FileHelper.getFileSize(context, it)
            viewModel.onFileSelected(it, name, size)
        }
    }

    // Auto-launch file picker on first entry
    LaunchedEffect(Unit) {
        if (state.selectedFileUri == null) {
            filePicker.launch(mimeFilter)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.conversionType?.name ?: "Configure",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
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

            // File Preview Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        tint = state.conversionType?.inputType?.color ?: MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    if (state.selectedFileUri != null) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.selectedFileName,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1
                            )
                            Text(
                                text = FileHelper.formatFileSize(state.selectedFileSize),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        state.conversionType?.inputType?.let { FormatBadge(fileType = it) }
                    } else {
                        Text(
                            text = "No file selected",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Pick file button
            Button(
                onClick = { filePicker.launch(mimeFilter) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (state.selectedFileUri != null) "Change File" else "Select File")
            }

            // Output Format Chips
            if (state.availableOutputFormats.size > 1) {
                Text(
                    text = "Output Format",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.availableOutputFormats.forEach { format ->
                        FilterChip(
                            selected = state.outputFormat == format,
                            onClick = { viewModel.onOutputFormatChanged(format) },
                            label = { Text(format.uppercase()) }
                        )
                    }
                }
            }

            // Quality Slider
            AnimatedVisibility(visible = state.showQualitySlider) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Quality",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${state.quality}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = state.quality.toFloat(),
                        onValueChange = { viewModel.onQualityChanged(it.toInt()) },
                        valueRange = 1f..100f,
                        steps = 0
                    )
                }
            }

            // Page Range
            AnimatedVisibility(visible = state.showPageRange) {
                Column {
                    Text(
                        text = "Page Range (optional)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = state.pageRangeStart,
                            onValueChange = { viewModel.onPageRangeStartChanged(it.filter { c -> c.isDigit() }) },
                            label = { Text("From") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = state.pageRangeEnd,
                            onValueChange = { viewModel.onPageRangeEndChanged(it.filter { c -> c.isDigit() }) },
                            label = { Text("To") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }
                }
            }

            // Output File Name
            OutlinedTextField(
                value = state.outputFileName,
                onValueChange = { viewModel.onOutputFileNameChanged(it) },
                label = { Text("Output File Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Convert Button
            Button(
                onClick = {
                    state.conversionType?.id?.let { onNavigateToProcessing(it) }
                },
                enabled = state.isConvertEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Convert",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
