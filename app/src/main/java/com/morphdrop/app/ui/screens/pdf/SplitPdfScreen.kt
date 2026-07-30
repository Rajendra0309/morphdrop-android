package com.morphdrop.app.ui.screens.pdf

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.morphdrop.app.ui.components.MorphDropTopAppBar
import com.morphdrop.app.ui.components.PrimaryButton
import com.morphdrop.app.ui.theme.MorphDropTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitPdfScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToProcessing: (conversionTypeId: String, workId: String) -> Unit = { _, _ -> },
    viewModel: SplitPdfViewModel = hiltViewModel()
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        viewModel.onFileSelected(uri)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    SplitPdfScreenContent(
        state = uiState,
        scrollBehavior = scrollBehavior,
        onNavigateBack = onNavigateBack,
        onPickFile = { filePicker.launch(arrayOf("application/pdf")) },
        onPageRangesChanged = { viewModel.onPageRangesChanged(it) },
        onOutputFolderNameChanged = { viewModel.onOutputFolderNameChanged(it) },
        onSplit = {
            val workId = viewModel.startSplit(context)
            if (workId != null) onNavigateToProcessing("split_pdf", workId.toString())
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitPdfScreenContent(
    state: SplitPdfState,
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigateBack: () -> Unit,
    onPickFile: () -> Unit,
    onPageRangesChanged: (String) -> Unit,
    onOutputFolderNameChanged: (String) -> Unit,
    onSplit: () -> Unit
) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MorphDropTopAppBar(
                title = "Split PDF",
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.CallSplit,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Extract Pages",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Divide your PDF into multiple documents",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // File Selection
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
                        Text(
                            text = state.fileName.ifBlank { "No file selected" },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = onPickFile,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (state.selectedFile != null) "Change File" else "Select PDF File")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = state.pageRanges,
                onValueChange = onPageRangesChanged,
                label = { Text("Page Ranges") },
                placeholder = { Text("e.g. 1-5, 8, 11-13") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                supportingText = { Text("Use commas for multiple ranges") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = state.outputFolderName,
                onValueChange = onOutputFolderNameChanged,
                label = { Text("Output Folder Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(32.dp))

            PrimaryButton(
                text = "Split PDF",
                onClick = onSplit,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.selectedFile != null && state.pageRanges.isNotEmpty()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Light Mode", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun SplitPdfScreenLightPreview() {
    MorphDropTheme(darkTheme = false) {
        SplitPdfScreenContent(
            state = SplitPdfState(
                selectedFile = Uri.parse("document.pdf"),
                fileName = "Project_Proposal.pdf",
                pageRanges = "1-3",
                outputFolderName = "Project_Proposal_Pages"
            ),
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            onNavigateBack = {},
            onPickFile = {},
            onPageRangesChanged = {},
            onOutputFolderNameChanged = {},
            onSplit = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun SplitPdfScreenDarkPreview() {
    MorphDropTheme(darkTheme = true) {
        SplitPdfScreenContent(
            state = SplitPdfState(
                selectedFile = null,
                fileName = "",
                pageRanges = "",
                outputFolderName = ""
            ),
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            onNavigateBack = {},
            onPickFile = {},
            onPageRangesChanged = {},
            onOutputFolderNameChanged = {},
            onSplit = {}
        )
    }
}
