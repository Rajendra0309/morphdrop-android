package com.morphdrop.app.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.morphdrop.app.BuildConfig
import com.morphdrop.app.domain.model.UpdateInfo
import com.morphdrop.app.ui.theme.MorphDropTheme

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
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
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SystemUpdate,
                        contentDescription = "Update Available",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = "Update Available",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Version Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = "Current: v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "➜",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "New: v${updateInfo.versionName}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Release Notes Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NewReleases,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "What's New",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Release Notes Content
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        val formattedNotes = formatReleaseNotes(
                            rawNotes = updateInfo.releaseNotes.ifBlank { "Performance improvements and bug fixes." }
                        )
                        Text(
                            text = formattedNotes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions
                Button(
                    onClick = onDownload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "Download Update",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Later",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Parses raw GitHub release notes Markdown and converts it into a formatted AnnotatedString
 * with proper bullet points, bold styling, inline code blocks, and clean line breaks.
 */
@Composable
private fun formatReleaseNotes(rawNotes: String): AnnotatedString {
    val lines = rawNotes.trim().split("\n")
    return buildAnnotatedString {
        lines.forEachIndexed { index, line ->
            val trimmedLine = line.trim()
            
            // Skip horizontal rules
            if (trimmedLine.startsWith("---") || trimmedLine.startsWith("***") || trimmedLine.startsWith("___")) {
                return@forEachIndexed
            }

            // Headings (#, ##, ###)
            val isHeading = trimmedLine.startsWith("#")
            val cleanLine = if (isHeading) {
                trimmedLine.replace(Regex("^#+\\s*"), "")
            } else if (trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") || trimmedLine.startsWith("• ")) {
                "• " + trimmedLine.substring(2).trim()
            } else {
                trimmedLine
            }

            if (isHeading) {
                if (length > 0) append("\n")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.titleSmall.fontSize)) {
                    append(cleanLine)
                }
                append("\n")
            } else {
                if (index > 0 && length > 0) append("\n")
                
                // Parse inline **bold** and `code`
                parseInlineMarkdown(cleanLine)
            }
        }
    }
}

private fun AnnotatedString.Builder.parseInlineMarkdown(text: String) {
    var i = 0
    while (i < text.length) {
        if (i + 1 < text.length && text[i] == '*' && text[i + 1] == '*') {
            // Bold tag **text**
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
            // Code tag `code`
            val end = text.indexOf("`", i + 1)
            if (end != -1) {
                val codeContent = text.substring(i + 1, end)
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
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

@Preview(name = "Update Dialog Light", showBackground = true)
@Composable
fun UpdateDialogLightPreview() {
    MorphDropTheme(darkTheme = false) {
        UpdateDialog(
            updateInfo = UpdateInfo(
                versionName = "1.4.0",
                releaseNotes = """
                    ### Feature Highlights
                    * **Batch OCR**: Process multiple images at once
                    * **Markdown Editor**: Live preview with `split-view` support
                    * **PDF Watermarking**: Add custom text or image stamps
                    
                    ### Fixes & Improvements
                    - Fixed OCR extraction bug in release builds
                    - Optimized PDF compression algorithm
                """.trimIndent(),
                downloadUrl = "",
                isUpdateAvailable = true
            ),
            onDownload = {},
            onDismiss = {}
        )
    }
}

@Preview(name = "Update Dialog Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun UpdateDialogDarkPreview() {
    MorphDropTheme(darkTheme = true) {
        UpdateDialog(
            updateInfo = UpdateInfo(
                versionName = "1.4.0",
                releaseNotes = """
                    ### Feature Highlights
                    * **Batch OCR**: Process multiple images at once
                    * **Markdown Editor**: Live preview with `split-view` support
                    * **PDF Watermarking**: Add custom text or image stamps
                    
                    ### Fixes & Improvements
                    - Fixed OCR extraction bug in release builds
                    - Optimized PDF compression algorithm
                """.trimIndent(),
                downloadUrl = "",
                isUpdateAvailable = true
            ),
            onDownload = {},
            onDismiss = {}
        )
    }
}
