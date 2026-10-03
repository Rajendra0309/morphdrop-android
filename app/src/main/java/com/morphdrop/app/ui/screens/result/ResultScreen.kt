package com.morphdrop.app.ui.screens.result

import android.content.res.Configuration
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.ui.components.ErrorState
import com.morphdrop.app.ui.components.FormatChip
import com.morphdrop.app.ui.components.LoadingState
import com.morphdrop.app.ui.components.MorphButton
import com.morphdrop.app.ui.components.MorphButtonVariant
import com.morphdrop.app.ui.components.MorphDropTopAppBar
import com.morphdrop.app.ui.components.MorphScaffold
import com.morphdrop.app.ui.theme.MorphDropTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    onDone: () -> Unit = {},
    /** Returns to the same tool's config screen. Null when the tool is unknown. */
    onConvertAnother: (() -> Unit)? = null,
    viewModel: ResultViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ResultScreenContent(
        state = state,
        scrollBehavior = scrollBehavior,
        onDone = onDone,
        onConvertAnother = onConvertAnother,
        onOpenFile = { viewModel.openFile(context, it) },
        onShareFile = { viewModel.shareFile(context, it) },
        onOpenFolder = { viewModel.openOutputFolder(context) },
        onShareAll = { viewModel.shareAllFiles(context) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreenContent(
    state: ResultUiState,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    onDone: () -> Unit,
    onConvertAnother: (() -> Unit)? = null,
    onOpenFile: (OutputFileItem) -> Unit,
    onShareFile: (OutputFileItem) -> Unit,
    onOpenFolder: () -> Unit,
    onShareAll: () -> Unit
) {
    MorphScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MorphDropTopAppBar(
                title = "Result",
                scrollBehavior = scrollBehavior,
                showBackArrow = true,
                onBackClick = onDone
            )
        }
    ) { innerPadding ->
        when {
            state.isLoading -> {
                LoadingState(
                    label = "Wrapping up…",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
            state.errorMessage != null -> {
                ErrorState(
                    icon = Icons.Default.ErrorOutline,
                    message = state.errorMessage,
                    retryLabel = "Back",
                    onRetry = onDone,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
            else -> {
                ResultBody(
                    state = state,
                    onDone = onDone,
                    onConvertAnother = onConvertAnother,
                    onOpenFile = onOpenFile,
                    onShareFile = onShareFile,
                    onOpenFolder = onOpenFolder,
                    onShareAll = onShareAll,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun ResultBody(
    state: ResultUiState,
    onDone: () -> Unit,
    onConvertAnother: (() -> Unit)?,
    onOpenFile: (OutputFileItem) -> Unit,
    onShareFile: (OutputFileItem) -> Unit,
    onOpenFolder: () -> Unit,
    onShareAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val isMultiple = state.outputFiles.size > 1

    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Animated check morph: plays on entry via MutableTransitionState.
        val checkVisibleState = remember {
            androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true }
        }
        androidx.compose.animation.AnimatedVisibility(
            visibleState = checkVisibleState,
            enter = scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        ) {
            Surface(
                shape = CircleShape,
                color = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer,
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Conversion complete",
                        modifier = Modifier.size(52.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = state.title.ifBlank { "Conversion complete" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = state.subtitle.ifBlank { "Your files are ready." },
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Before/after size comparison when the worker reported input size.
        if (state.inputSizeFormatted != null && state.outputSizeFormatted != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = scheme.primaryContainer.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${state.inputSizeFormatted} → ${state.outputSizeFormatted}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (state.savingsText != null) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(
                                    text = state.savingsText,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Prominent Share / Open actions for single-file output.
        if (!isMultiple && state.outputFiles.size == 1) {
            val file = state.outputFiles.first()
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MorphButton(
                    text = "Open",
                    onClick = { onOpenFile(file) },
                    variant = MorphButtonVariant.Primary,
                    modifier = Modifier.weight(1f),
                    leadingIcon = Icons.AutoMirrored.Filled.OpenInNew
                )
                MorphButton(
                    text = "Share",
                    onClick = { onShareFile(file) },
                    variant = MorphButtonVariant.Secondary,
                    modifier = Modifier.weight(1f),
                    leadingIcon = Icons.Default.Share
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Folder count card for multi-file output.
            if (isMultiple) {
                item {
                    Card(
                        onClick = onOpenFolder,
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = scheme.primaryContainer.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.folderName ?: "Output folder",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${state.outputFiles.size} files",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open folder",
                                tint = scheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Per-file output cards. Single-file output keeps the card compact —
            // its big Open/Share actions live above. Multi-file keeps per-card actions.
            items(state.outputFiles, key = { it.id }) { file ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = file.fileSizeFormatted,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                            if (file.extension.isNotBlank()) {
                                FormatChip(format = file.extension.uppercase())
                            }
                        }
                        if (isMultiple) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                MorphButton(
                                    text = "Open",
                                    onClick = { onOpenFile(file) },
                                    variant = MorphButtonVariant.Secondary,
                                    modifier = Modifier.weight(1f),
                                    leadingIcon = Icons.AutoMirrored.Filled.OpenInNew
                                )
                                MorphButton(
                                    text = "Share",
                                    onClick = { onShareFile(file) },
                                    variant = MorphButtonVariant.Ghost,
                                    modifier = Modifier.weight(1f),
                                    leadingIcon = Icons.Default.Share
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isMultiple) {
            MorphButton(
                text = "Share all",
                onClick = onShareAll,
                variant = MorphButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Default.Share
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (onConvertAnother != null) {
            MorphButton(
                text = "Convert another",
                onClick = onConvertAnother,
                variant = MorphButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Default.Refresh
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        MorphButton(
            text = "Done",
            onClick = onDone,
            variant = MorphButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Light Mode", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun ResultScreenLightPreview() {
    MorphDropTheme(darkTheme = false) {
        ResultScreenContent(
            state = ResultUiState(
                isLoading = false,
                title = "Conversion complete",
                subtitle = "Saved to MorphDrop folder",
                folderName = "MorphDrop",
                inputSizeFormatted = "8.0 MB",
                outputSizeFormatted = "1.7 MB",
                savingsText = "79% smaller",
                outputFiles = listOf(
                    OutputFileItem(id = "1", fileName = "Converted_Document_1.pdf", fileSizeFormatted = "1.2 MB", fileSizeBytes = 1258291L, extension = "pdf", uri = null),
                    OutputFileItem(id = "2", fileName = "Extracted_Image_Page_2.png", fileSizeFormatted = "450 KB", fileSizeBytes = 460800L, extension = "png", uri = null)
                )
            ),
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            onDone = {},
            onOpenFile = {},
            onShareFile = {},
            onOpenFolder = {},
            onShareAll = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun ResultScreenDarkPreview() {
    MorphDropTheme(darkTheme = true) {
        ResultScreenContent(
            state = ResultUiState(
                isLoading = false,
                title = "Conversion complete",
                subtitle = "1 file created • 4.8 MB",
                inputSizeFormatted = "4.2 MB",
                outputSizeFormatted = "4.8 MB",
                savingsText = "14% larger",
                outputFiles = listOf(
                    OutputFileItem(id = "1", fileName = "Final_Presentation.pdf", fileSizeFormatted = "4.8 MB", fileSizeBytes = 5033165L, extension = "pdf", uri = null)
                )
            ),
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            onDone = {},
            onConvertAnother = {},
            onOpenFile = {},
            onShareFile = {},
            onOpenFolder = {},
            onShareAll = {}
        )
    }
}
