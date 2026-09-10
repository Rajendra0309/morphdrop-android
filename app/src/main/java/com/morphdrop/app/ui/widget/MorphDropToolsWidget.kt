package com.morphdrop.app.ui.widget

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
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
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
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
import com.morphdrop.app.R

class MorphDropToolsWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                ToolsWidgetContainer(context = context, size = size)
            }
        }
    }
}

private data class ToolItem(
    val label: String,
    val route: String,
    val iconRes: Int,
    val isPrimary: Boolean = false
)

@androidx.compose.runtime.Composable
private fun ToolsWidgetContainer(
    context: Context,
    size: DpSize
) {
    val height = size.height
    val width = size.width

    val isUltraCompact = height < 100.dp
    val isWide = width >= 260.dp

    val toolList = listOf(
        ToolItem("PDF to Img", "config/pdf_to_images", R.drawable.ic_widget_pdf, isPrimary = true),
        ToolItem("OCR Text", "ocr", R.drawable.ic_widget_ocr),
        ToolItem("Compress", "compress_pdf", R.drawable.ic_widget_compress),
        ToolItem("Image Tools", "config/image_converter", R.drawable.ic_widget_image),
        ToolItem("Watermark", "watermark_pdf", R.drawable.ic_widget_watermark),
        ToolItem("Batch PDF", "batch_pdf", R.drawable.ic_widget_batch),
        ToolItem("Markdown", "markdown_editor", R.drawable.ic_widget_markdown),
        ToolItem("Page #s", "page_numbers_pdf", R.drawable.ic_widget_page_numbers)
    )

    // Select number of tools based on size:
    // - Ultra compact (2x1): 3 tools in 1 row
    // - Standard (3x2): 6 tools in 3x2 grid
    // - Wide / Expanded (4x2+): 8 tools in 4x2 grid
    val (columns, visibleTools) = when {
        isUltraCompact -> Pair(3, toolList.take(3))
        isWide -> Pair(4, toolList.take(8))
        else -> Pair(3, toolList.take(6))
    }

    // Dynamic responsive sizing based on actual widget dimensions (height AND width):
    val rowCount = if (isUltraCompact) 1 else 2
    val availableHeight = height - (if (isUltraCompact) 32.dp else 42.dp)
    val cellHeight = availableHeight / rowCount
    val cellWidth = (width - 24.dp) / columns
    val minCellDimension = minOf(cellHeight, cellWidth)

    val cardIconSize = when {
        minCellDimension < 52.dp -> 28.dp
        minCellDimension < 68.dp -> 34.dp
        minCellDimension < 85.dp -> 42.dp
        minCellDimension < 105.dp -> 48.dp
        else -> 54.dp
    }

    val cardTextSize = when {
        minCellDimension < 55.dp -> 9.sp
        minCellDimension < 75.dp -> 10.sp
        minCellDimension < 95.dp -> 11.sp
        else -> 12.sp
    }

    val headerIconSize = when {
        height < 100.dp -> 18.dp
        height < 160.dp -> 22.dp
        else -> 24.dp
    }

    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(22.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // --- Header: 4-Square Quick Tools Icon + Quick Actions Title ---
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .clickable(actionStartActivity(openAppIntent)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_quick_tools),
                contentDescription = "Quick Actions Icon",
                modifier = GlanceModifier.size(headerIconSize)
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = "Quick Actions",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = if (isUltraCompact) 12.sp else 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        // --- Grid of Tool Action Cards ---
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxWidth()
        ) {
            val rows = visibleTools.chunked(columns)
            rows.forEachIndexed { rowIndex, rowItems ->
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowItems.forEachIndexed { colIndex, tool ->
                        val navIntent = Intent(context, MainActivity::class.java).apply {
                            action = Intent.ACTION_VIEW
                            putExtra("extra_navigate_to", tool.route)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }

                        ToolActionCard(
                            tool = tool,
                            iconSize = cardIconSize,
                            textSize = cardTextSize,
                            onClickIntent = navIntent,
                            modifier = GlanceModifier
                                .defaultWeight()
                                .fillMaxHeight()
                        )

                        if (colIndex < rowItems.lastIndex) {
                            Spacer(modifier = GlanceModifier.width(6.dp))
                        }
                    }
                }
                if (rowIndex < rows.lastIndex) {
                    Spacer(modifier = GlanceModifier.height(6.dp))
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ToolActionCard(
    tool: ToolItem,
    iconSize: androidx.compose.ui.unit.Dp,
    textSize: androidx.compose.ui.unit.TextUnit,
    onClickIntent: Intent,
    modifier: GlanceModifier
) {
    Box(
        modifier = modifier
            .cornerRadius(14.dp)
            .background(if (tool.isPrimary) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.surfaceVariant)
            .clickable(actionStartActivity(onClickIntent))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(tool.iconRes),
                contentDescription = tool.label,
                modifier = GlanceModifier.size(iconSize)
            )
            Spacer(modifier = GlanceModifier.height(if (iconSize >= 38.dp) 4.dp else 2.dp))
            Text(
                text = tool.label,
                style = TextStyle(
                    color = if (tool.isPrimary) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurfaceVariant,
                    fontSize = textSize,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
        }
    }
}
