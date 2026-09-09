package com.morphdrop.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.morphdrop.app.BuildConfig
import com.morphdrop.app.data.updater.DownloadProgress
import com.morphdrop.app.data.updater.DownloadStatus
import com.morphdrop.app.domain.model.UpdateInfo
import com.morphdrop.app.ui.theme.MorphDropTheme
import java.util.Locale

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    downloadProgress: DownloadProgress = DownloadProgress(),
    onDownload: () -> Unit,
    onSkipVersion: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var skipThisVersionChecked by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {
            if (downloadProgress.status != DownloadStatus.DOWNLOADING) {
                if (skipThisVersionChecked) onSkipVersion()
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedVisibility(
            visible = true,
            enter = scaleIn(initialScale = 0.90f) + fadeIn()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(28.dp)),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Icon Header (Circular 48dp Container reflecting state)
                    val isError = downloadProgress.status == DownloadStatus.FAILED
                    val isDownloading = downloadProgress.status == DownloadStatus.DOWNLOADING || downloadProgress.status == DownloadStatus.PENDING

                    val headerBgColor = when {
                        isError -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    }
                    val headerIconTint = when {
                        isError -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                    val headerIcon = when {
                        isError -> Icons.Outlined.ErrorOutline
                        isDownloading -> Icons.Outlined.Downloading
                        else -> Icons.Outlined.SystemUpdate
                    }

                    Surface(
                        shape = CircleShape,
                        color = headerBgColor,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = headerIcon,
                                contentDescription = null,
                                tint = headerIconTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title
                    Text(
                        text = "Update Available",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Unified Version Pill Badge (v1.3.1 -> v2.5.0-preview)
                    val cleanCurrent = BuildConfig.VERSION_NAME.removePrefix("v").removePrefix("V")
                    val cleanNew = updateInfo.versionName.removePrefix("v").removePrefix("V")

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "v$cleanCurrent",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "v$cleanNew",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // "What's New" Release Notes Card
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp, max = 190.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "What's New",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                ReleaseNotesContent(
                                    rawNotes = updateInfo.releaseNotes.ifBlank { "• Performance optimizations and bug fixes" }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // State 1: Prompt State (IDLE or SUCCESSFUL)
                    if (downloadProgress.status == DownloadStatus.IDLE || downloadProgress.status == DownloadStatus.SUCCESSFUL) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { skipThisVersionChecked = !skipThisVersionChecked }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = skipThisVersionChecked,
                                onCheckedChange = { skipThisVersionChecked = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Skip this version",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    if (skipThisVersionChecked) onSkipVersion()
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = "Later",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Button(
                                onClick = onDownload,
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Update Now",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // State 2: Downloading Progress State
                    if (downloadProgress.status == DownloadStatus.DOWNLOADING || downloadProgress.status == DownloadStatus.PENDING || downloadProgress.status == DownloadStatus.PAUSED) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (downloadProgress.status == DownloadStatus.PENDING) "Connecting..."
                                    else if (downloadProgress.status == DownloadStatus.PAUSED) "Download paused"
                                    else "Downloading update...",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${downloadProgress.percentage}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Full Rounded Pill Progress Bar (height 10dp)
                            if (downloadProgress.percentage > 0) {
                                LinearProgressIndicator(
                                    progress = { downloadProgress.percentage / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(50)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                                    strokeCap = StrokeCap.Round
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(50)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            }

                            if (downloadProgress.totalBytes > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${formatBytes(downloadProgress.downloadedBytes)} / ${formatBytes(downloadProgress.totalBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    if (downloadProgress.status == DownloadStatus.PAUSED) {
                                        Text(
                                            text = "Waiting for network",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                FilledTonalButton(
                                    onClick = onDismiss,
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "Download in Background",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // State 3: Error State
                    if (downloadProgress.status == DownloadStatus.FAILED) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // M3 Error Container Callout Box
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .padding(top = 1.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = downloadProgress.statusMessage.ifBlank { "Network connection timed out while downloading package" },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        lineHeight = 18.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Error Action Buttons (Full width stacked pills)
                            Button(
                                onClick = onDownload,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Retry Download",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = "Later",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseNotesContent(rawNotes: String) {
    val lines = rawNotes.trim().lines().filter { it.isNotBlank() }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { rawLine ->
            val trimmedLine = rawLine.trim()

            when {
                trimmedLine.startsWith("---") || trimmedLine.startsWith("***") || trimmedLine.startsWith("___") -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
                trimmedLine.startsWith("#") -> {
                    val headingText = trimmedLine.replace(Regex("^#+\\s*"), "")
                    Text(
                        text = headingText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") || trimmedLine.startsWith("• ") -> {
                    val bulletContent = trimmedLine.substring(2).trim()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp, start = 2.dp)
                        )
                        Text(
                            text = formatInlineMarkdown(bulletContent),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                else -> {
                    Text(
                        text = formatInlineMarkdown(trimmedLine),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            if (i + 1 < text.length && text[i] == '*' && text[i + 1] == '*') {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    val boldContent = text.substring(i + 2, end)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(boldContent)
                    }
                    i = end + 2
                    continue
                }
            } else if (text[i] == '`') {
                val end = text.indexOf("`", i + 1)
                if (end != -1) {
                    val codeContent = text.substring(i + 1, end)
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)) {
                        append(codeContent)
                    }
                    i = end + 1
                    continue
                }
            }

            append(text[i])
            i++
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024f * 1024f)
    return String.format(Locale.getDefault(), "%.1f MB", mb)
}

@Preview(name = "Update Dialog Light", showBackground = true)
@Composable
fun UpdateDialogLightPreview() {
    MorphDropTheme(darkTheme = false) {
        UpdateDialog(
            updateInfo = UpdateInfo(
                versionName = "2.5.0-preview",
                releaseNotes = """
                    * **Responsive home screen widgets** (Combined, History, Tools)
                    * **Material 3 dynamic launcher shortcuts**
                    * **PDF Watermark, Page Numbers & Compression tools**
                    * **Performance optimizations and bug fixes**
                """.trimIndent(),
                downloadUrl = "",
                isUpdateAvailable = true
            ),
            onDownload = {},
            onDismiss = {}
        )
    }
}
