package com.morphdrop.app.ui.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

class MorphDropHistoryWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HistoryWidgetEntryPoint {
        fun historyRepository(): HistoryRepository
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val history = try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                HistoryWidgetEntryPoint::class.java
            )
            entryPoint.historyRepository().getAllHistory().first()
                .filter { it.success && it.outputUris.isNotBlank() }
                .take(10)
        } catch (e: Exception) {
            emptyList()
        }

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                HistoryWidgetContainer(context = context, size = size, recentFiles = history)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun HistoryWidgetContainer(
    context: Context,
    size: DpSize,
    recentFiles: List<ConversionHistoryEntity>
) {
    val height = size.height
    val isUltraCompact = height < 110.dp

    val maxItems = when {
        height < 100.dp -> 2
        height < 155.dp -> 3
        height < 205.dp -> 4
        height < 260.dp -> 6
        else -> 8
    }

    val openHistoryIntent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra("extra_navigate_to", "history")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(22.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(if (isUltraCompact) 8.dp else 12.dp)
    ) {
        // --- Header: History Icon + Title with vertical padding to prevent text clipping ---
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 4.dp)
                .clickable(actionStartActivity(openHistoryIntent)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_history),
                contentDescription = "History Icon",
                modifier = GlanceModifier.size(if (isUltraCompact) 18.dp else 20.dp)
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = "Recent Conversions",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = if (isUltraCompact) 12.sp else 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(if (isUltraCompact) 4.dp else 6.dp))

        // --- File List (Fills full widget area smoothly without void) ---
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxWidth()
        ) {
            if (recentFiles.isEmpty()) {
                HistoryEmptyState()
            } else {
                val itemsToShow = recentFiles.take(maxItems)
                itemsToShow.forEachIndexed { index, item ->
                    HistoryItemRow(
                        context = context,
                        item = item,
                        isCompact = isUltraCompact || maxItems >= 5,
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight()
                    )
                    if (index < itemsToShow.lastIndex) {
                        Spacer(modifier = GlanceModifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun HistoryItemRow(
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
            text = displayName.take(28),
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
private fun HistoryEmptyState() {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(10.dp)
            .background(GlanceTheme.colors.surfaceVariant)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_empty),
            contentDescription = "Empty",
            modifier = GlanceModifier.size(16.dp)
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = "No recent conversions yet",
            style = TextStyle(
                color = GlanceTheme.colors.outline,
                fontSize = 11.sp
            )
        )
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
