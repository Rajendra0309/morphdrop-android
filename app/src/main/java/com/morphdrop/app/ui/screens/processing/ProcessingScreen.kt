package com.morphdrop.app.ui.screens.processing

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morphdrop.app.ui.components.ErrorState
import com.morphdrop.app.ui.components.FormatChip
import com.morphdrop.app.ui.components.MorphButton
import com.morphdrop.app.ui.components.MorphButtonVariant
import com.morphdrop.app.ui.components.MorphDropTopAppBar
import com.morphdrop.app.ui.components.MorphScaffold
import com.morphdrop.app.ui.components.TransformIndicator
import com.morphdrop.app.ui.theme.MorphDropTheme
import com.morphdrop.app.ui.theme.chipColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingScreen(
    onNavigateToResult: () -> Unit = {},
    onCancel: (String) -> Unit = {},
    viewModel: ProcessingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.observeWork(context)
    }

    // Let the user see the completion state (output chip) before navigating.
    LaunchedEffect(state.isCompleted) {
        if (state.isCompleted) {
            delay(900)
            onNavigateToResult()
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    ProcessingScreenContent(
        state = state,
        scrollBehavior = scrollBehavior,
        onCancel = {
            viewModel.cancelConversion(context)
            onCancel(viewModel.workIdString ?: "")
        }
    )
}

/**
 * Shared with the PDF tool screens — keep this signature stable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingScreenContent(
    state: ProcessingUiState,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    onCancel: () -> Unit
) {
    MorphScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MorphDropTopAppBar(
                title = "Processing",
                scrollBehavior = scrollBehavior,
                showBackArrow = false // Don't allow back while processing
            )
        }
    ) { innerPadding ->
        when {
            state.isFailed -> {
                ErrorState(
                    icon = Icons.Default.ErrorOutline,
                    message = state.errorMessage ?: "Something went wrong. Please try again.",
                    retryLabel = "Back",
                    onRetry = onCancel,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
            state.isCancelled -> {
                CancelledState(
                    onBack = onCancel,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
            else -> {
                ProgressContent(
                    state = state,
                    onCancel = onCancel,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun ProgressContent(
    state: ProcessingUiState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val conversionType = state.conversionType
    val chipType = conversionType?.outputType ?: conversionType?.inputType

    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // File card: what is being morphed.
        if (conversionType != null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = scheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (badgeContainer, badgeContent) = conversionType.inputType.chipColors()
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = badgeContainer,
                        contentColor = badgeContent,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = conversionType.icon,
                                contentDescription = null,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.fileName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        TransformIndicator(
                            conversionType = conversionType,
                            compact = true
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        // Progress ring with the format chip at its center.
        Box(
            modifier = Modifier.size(176.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { (state.progress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 12.dp,
                trackColor = scheme.surfaceVariant,
                color = scheme.primary,
                strokeCap = StrokeCap.Round
            )
            if (state.isCompleted) {
                Surface(
                    shape = CircleShape,
                    color = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                    modifier = Modifier.size(88.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Conversion complete",
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            } else if (chipType != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FormatChip(fileType = chipType)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${state.progress.toInt()}%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface
                    )
                }
            } else {
                Text(
                    text = "${state.progress.toInt()}%",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = scheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (state.isCompleted) "Morph complete" else "Morphing your file…",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = state.currentStage,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // Output chip appears on completion, before navigation.
        AnimatedVisibility(
            visible = state.isCompleted && chipType != null,
            enter = fadeIn() + scaleIn()
        ) {
            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Output",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (chipType != null) FormatChip(fileType = chipType)
            }
        }

        if (!state.isCompleted) {
            Spacer(modifier = Modifier.height(48.dp))
            MorphButton(
                text = "Cancel",
                onClick = onCancel,
                variant = MorphButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CancelledState(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = scheme.surfaceContainerHigh,
            contentColor = scheme.onSurfaceVariant,
            modifier = Modifier.size(88.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Conversion cancelled",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "No files were changed. You can start over whenever you're ready.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        MorphButton(
            text = "Back",
            onClick = onBack,
            variant = MorphButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Light Mode", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun ProcessingScreenLightPreview() {
    MorphDropTheme(darkTheme = false) {
        ProcessingScreenContent(
            state = ProcessingUiState(
                fileName = "My_Project_Final_Q3_Report_Draft_v2.xlsx",
                progress = 65f,
                currentStage = "Converting…"
            ),
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            onCancel = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun ProcessingScreenDarkPreview() {
    MorphDropTheme(darkTheme = true) {
        ProcessingScreenContent(
            state = ProcessingUiState(
                fileName = "Vacation_Photos.zip",
                progress = 42f,
                currentStage = "Converting…"
            ),
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            onCancel = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Failed State", showBackground = true, showSystemUi = true, device = Devices.PIXEL_7_PRO)
@Composable
fun ProcessingScreenFailedPreview() {
    MorphDropTheme(darkTheme = false) {
        ProcessingScreenContent(
            state = ProcessingUiState(
                fileName = "report.pdf",
                isFailed = true,
                errorMessage = "The PDF is password-protected and couldn't be read."
            ),
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            onCancel = {}
        )
    }
}
