package com.morphdrop.app.ui.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.morphdrop.app.MainActivity
import com.morphdrop.app.PdfViewerActivity
import com.morphdrop.app.R
import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.domain.repository.HistoryRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

class MorphDropWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun historyRepository(): HistoryRepository
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val history = try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java
            )
            entryPoint.historyRepository().getAllHistory().first()
                .filter { it.success && it.outputUris.isNotBlank() }
                .take(8)
        } catch (e: Exception) {
            emptyList()
        }

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                WidgetContainer(context = context, size = size, recentFiles = history)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun WidgetContainer(
    context: Context,
    size: DpSize,
    recentFiles: List<ConversionHistoryEntity>
) {
    val height = size.height
    val width = size.width

    val isUltraCompact = height < 115.dp
    val isNarrow = width < 210.dp

    val maxItems = when {
        height < 115.dp -> 1
        height < 165.dp -> 2
        height < 215.dp -> 3
        height < 265.dp -> 4
        height < 320.dp -> 6
        else -> 8
    }

    val containerPadding = if (isUltraCompact) 8.dp else 12.dp
    val spacingBetween = if (isUltraCompact) 4.dp else 6.dp

    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(containerPadding)
    ) {
        // --- Header: App Icon + Clean Brand Name ---
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 2.dp)
                .clickable(actionStartActivity(openAppIntent)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_app_icon),
                contentDescription = "MorphDrop Icon",
                modifier = GlanceModifier.size(if (isUltraCompact) 18.dp else 20.dp)
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = "MorphDrop",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = if (isUltraCompact) 12.sp else 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(spacingBetween))

        // --- Middle Section: Dynamic Recent Files (Never Empty or Blank Void) ---
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxWidth()
        ) {
            if (recentFiles.isEmpty()) {
                EmptyStateRow(isCompact = isUltraCompact)
            } else {
                val itemsToShow = recentFiles.take(maxItems)
                itemsToShow.forEachIndexed { index, item ->
                    RecentFileRow(
                        context = context,
                        item = item,
                        isCompact = isUltraCompact || maxItems >= 4,
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight()
                    )
                    if (index < itemsToShow.lastIndex) {
                        Spacer(modifier = GlanceModifier.height(3.dp))
                    }
                }
            }
        }

        Spacer(modifier = GlanceModifier.height(spacingBetween))

        // --- Bottom: Responsive Quick Actions (PDF Tools, Image Tools) ---
        QuickActionsRow(
            context = context,
            showTwoButtonsOnly = isUltraCompact || isNarrow,
            buttonHeight = if (isUltraCompact) 28.dp else 34.dp
        )
    }
}

@androidx.compose.runtime.Composable
private fun RecentFileRow(
    context: Context,
    item: ConversionHistoryEntity,
    isCompact: Boolean,
    modifier: GlanceModifier = GlanceModifier
) {
    val firstUriStr = item.outputUris.split(",").firstOrNull()?.trim() ?: ""
    val uri = if (firstUriStr.isNotBlank()) Uri.parse(firstUriStr) else null
    val isPdf = item.conversionType.contains("pdf", ignoreCase = true) || firstUriStr.endsWith(".pdf", ignoreCase = true)
    val isMd = item.conversionType.contains("markdown", ignoreCase = true) || firstUriStr.endsWith(".md", ignoreCase = true)
    val isImage = item.conversionType.contains("image", ignoreCase = true) ||
            firstUriStr.endsWith(".png", ignoreCase = true) ||
            firstUriStr.endsWith(".jpg", ignoreCase = true) ||
            firstUriStr.endsWith(".webp", ignoreCase = true)
    val isOcr = item.conversionType.contains("ocr", ignoreCase = true)

    val clickIntent = when {
        isPdf && uri != null -> {
            Intent(context, PdfViewerActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        isMd && uri != null -> {
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("extra_open_markdown", firstUriStr)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
        else -> {
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("extra_open_history_id", item.id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }

    val iconRes = when {
        isPdf -> R.drawable.ic_shortcut_pdf
        isImage -> R.drawable.ic_shortcut_image
        isOcr -> R.drawable.ic_shortcut_ocr
        else -> R.drawable.ic_shortcut_file
    }

    val displayName = item.displayName.ifBlank {
        item.outputFileNames.split(",").firstOrNull()?.trim() ?: item.inputFileName
    }

    Row(
        modifier = modifier
            .cornerRadius(12.dp)
            .background(GlanceTheme.colors.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = if (isCompact) 4.dp else 6.dp)
            .clickable(actionStartActivity(clickIntent)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            modifier = GlanceModifier.size(if (isCompact) 16.dp else 18.dp)
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = displayName.take(24),
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = if (isCompact) 11.sp else 12.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight()
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = formatRelativeTime(item.timestamp),
            style = TextStyle(
                color = GlanceTheme.colors.outline,
                fontSize = if (isCompact) 9.sp else 10.sp
            )
        )
    }
}

@androidx.compose.runtime.Composable
private fun EmptyStateRow(isCompact: Boolean) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(10.dp)
            .background(GlanceTheme.colors.surfaceVariant)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_empty),
            contentDescription = "Empty",
            modifier = GlanceModifier.size(if (isCompact) 14.dp else 16.dp)
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = "No recent conversions yet",
            style = TextStyle(
                color = GlanceTheme.colors.outline,
                fontSize = if (isCompact) 10.sp else 11.sp
            )
        )
    }
}

@androidx.compose.runtime.Composable
private fun QuickActionsRow(
    context: Context,
    showTwoButtonsOnly: Boolean,
    buttonHeight: androidx.compose.ui.unit.Dp
) {
    fun createNavIntent(route: String): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("extra_navigate_to", route)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            text = if (showTwoButtonsOnly) "PDF Tools" else "PDF",
            onClick = actionStartActivity(createNavIntent("config/images_to_pdf")),
            modifier = GlanceModifier.defaultWeight().height(buttonHeight),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = GlanceTheme.colors.primary,
                contentColor = GlanceTheme.colors.onPrimary
            )
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Button(
            text = if (showTwoButtonsOnly) "Image Tools" else "Image",
            onClick = actionStartActivity(createNavIntent("config/image_converter")),
            modifier = GlanceModifier.defaultWeight().height(buttonHeight),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = GlanceTheme.colors.secondaryContainer,
                contentColor = GlanceTheme.colors.onSecondaryContainer
            )
        )
        if (!showTwoButtonsOnly) {
            Spacer(modifier = GlanceModifier.width(4.dp))
            Button(
                text = "OCR",
                onClick = actionStartActivity(createNavIntent("ocr")),
                modifier = GlanceModifier.defaultWeight().height(buttonHeight),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = GlanceTheme.colors.secondaryContainer,
                    contentColor = GlanceTheme.colors.onSecondaryContainer
                )
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            Button(
                text = "MD",
                onClick = actionStartActivity(createNavIntent("markdown_editor")),
                modifier = GlanceModifier.defaultWeight().height(buttonHeight),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = GlanceTheme.colors.secondaryContainer,
                    contentColor = GlanceTheme.colors.onSecondaryContainer
                )
            )
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L -> "Just now"
        diff < 3600_000L -> "${diff / 60_000L}m ago"
        diff < 86400_000L -> "${diff / 3600_000L}h ago"
        diff < 172800_000L -> "Yesterday"
        else -> "${diff / 86400_000L}d ago"
    }
}
