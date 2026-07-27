package com.morphdrop.app.ui.screens.pdf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.ui.components.EmptyStateAnimation
import com.morphdrop.app.ui.components.GlassCard
import com.morphdrop.app.ui.components.GradientBackground
import com.morphdrop.app.ui.components.PrimaryButton
import com.morphdrop.app.ui.theme.LocalLiquidState
import com.morphdrop.app.ui.theme.MorphDropTheme
import com.morphdrop.app.ui.theme.NeonEmerald
import io.github.fletchmckee.liquid.rememberLiquidState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergePdfScreen(
    onNavigateBack: () -> Unit,
    onMergeStarted: (workId: String) -> Unit,
    viewModel: MergePdfViewModel = hiltViewModel()
) {
    val liquidState = LocalLiquidState.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pdfMime = arrayOf("application/pdf")
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            for (uri in uris) {
                try {
                    val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                } catch (_: Exception) {}
            }
            viewModel.onFilesSelected(uris)
        }
    }

    GradientBackground(liquidState = liquidState) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Merge PDFs", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = "Combine multiple PDF files into one document in your preferred order.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Box(modifier = Modifier.weight(1f)) {
                    if (state.selectedFiles.isEmpty()) {
                        EmptyState(onAddFiles = { launcher.launch(pdfMime) })
                    } else {
                        FileList(
                            files = state.selectedFiles,
                            onRemove = viewModel::onRemoveFile,
                            onAddFiles = { launcher.launch(pdfMime) }
                        )
                    }
                }

                PrimaryButton(
                    text = "Merge Files",
                    onClick = { 
                        viewModel.startMerge(context)?.let { workId ->
                            onMergeStarted(workId.toString())
                        }
                    },
                    enabled = state.selectedFiles.size >= 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onAddFiles: () -> Unit) {
    val liquidState = LocalLiquidState.current
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlassCard(
            liquidState = liquidState,
            onClick = onAddFiles,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                EmptyStateAnimation(
                    icon = Icons.Default.PictureAsPdf,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Select PDF Files",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Tap here to choose at least 2 PDF files",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun FileList(
    files: List<Uri>,
    onRemove: (Uri) -> Unit,
    onAddFiles: () -> Unit
) {
    val liquidState = LocalLiquidState.current
    val context = androidx.compose.ui.platform.LocalContext.current

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(files, key = { it.toString() }) { uri ->
            GlassCard(
                liquidState = liquidState,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = com.morphdrop.app.util.FileHelper.getFileName(context, uri),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(onClick = { onRemove(uri) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove file",
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onAddFiles,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonEmerald),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add More Files")
            }
        }
    }
}

@Preview
@Composable
fun MergePdfScreenPreview() {
    MorphDropTheme {
        MergePdfScreen(onNavigateBack = {}, onMergeStarted = {})
    }
}
