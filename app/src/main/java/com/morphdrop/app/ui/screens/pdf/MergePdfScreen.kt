package com.morphdrop.app.ui.screens.pdf

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.ui.components.MorphDropTopAppBar
import com.morphdrop.app.ui.components.PrimaryButton
import com.morphdrop.app.ui.theme.MorphDropTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergePdfScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToProcessing: (conversionTypeId: String, workId: String) -> Unit = { _, _ -> },
    viewModel: MergePdfViewModel = hiltViewModel()
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        viewModel.onFilesSelected(uris)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    MergePdfScreenContent(
        selectedFiles = uiState.selectedFiles,
        outputFileName = uiState.outputFileName,
        scrollBehavior = scrollBehavior,
        onNavigateBack = onNavigateBack,
        onPickFiles = { filePicker.launch(arrayOf("application/pdf")) },
        onRemoveFile = { viewModel.onRemoveFile(it) },
        onOutputFileNameChanged = { viewModel.onOutputFileNameChanged(it) },
        onMerge = {
            val workId = viewModel.startMerge(context)
            if (workId != null) onNavigateToProcessing("merge_pdf", workId.toString())
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergePdfScreenContent(
    selectedFiles: List<FileUriItem>,
    outputFileName: String,
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigateBack: () -> Unit,
    onPickFiles: () -> Unit,
    onRemoveFile: (Uri) -> Unit,
    onOutputFileNameChanged: (String) -> Unit,
    onMerge: () -> Unit
) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MorphDropTopAppBar(
                title = "Merge PDFs",
                scrollBehavior = scrollBehavior,
                showBackArrow = true,
                onBackClick = onNavigateBack
            )
        },
        bottomBar = {
            if (selectedFiles.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    PrimaryButton(
                        text = "Merge ${selectedFiles.size} Files",
                        onClick = onMerge,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) { innerPadding ->
        if (selectedFiles.isEmpty()) {
            EmptyState(onAddFiles = onPickFiles)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                OutlinedTextField(
                    value = outputFileName,
                    onValueChange = onOutputFileNameChanged,
                    label = { Text("Output File Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                FileList(
                    files = selectedFiles,
                    onRemove = onRemoveFile,
                    onAddMore = onPickFiles,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onAddFiles: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.MergeType,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Select PDFs to Merge",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Choose two or more PDF files to combine them into a single document",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onAddFiles,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select PDF Files")
        }
    }
}

@Composable
private fun FileList(
    files: List<FileUriItem>,
    onRemove: (Uri) -> Unit,
    onAddMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(files) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Description, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = item.name,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    IconButton(onClick = { onRemove(item.uri) }) {
                        Icon(
                            imageVector = Icons.Default.RemoveCircleOutline,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = onAddMore,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add More Files")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Light Mode", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun MergePdfScreenLightPreview() {
    MorphDropTheme(darkTheme = false) {
        MergePdfScreenContent(
            selectedFiles = listOf(
                FileUriItem(Uri.parse("file1.pdf"), "Work_Contract.pdf"),
                FileUriItem(Uri.parse("file2.pdf"), "Identity_Proof.pdf")
            ),
            outputFileName = "merged_document.pdf",
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            onNavigateBack = {},
            onPickFiles = {},
            onRemoveFile = {},
            onOutputFileNameChanged = {},
            onMerge = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun MergePdfScreenDarkPreview() {
    MorphDropTheme(darkTheme = true) {
        MergePdfScreenContent(
            selectedFiles = emptyList(),
            outputFileName = "",
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            onNavigateBack = {},
            onPickFiles = {},
            onRemoveFile = {},
            onOutputFileNameChanged = {},
            onMerge = {}
        )
    }
}
