package com.morphdrop.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morphdrop.app.data.local.entity.ConversionHistoryEntity
import com.morphdrop.app.ui.theme.LiquidGlassConfig
import com.morphdrop.app.ui.theme.NeonEmerald
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquid
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecentConversionCard(
    history: ConversionHistoryEntity,
    modifier: Modifier = Modifier,
    liquidState: LiquidState? = null
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    val dateString = dateFormat.format(Date(history.timestamp))
    val shape = RoundedCornerShape(12.dp)

    val cardContent = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Icon
            Icon(
                imageVector = if (history.success) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = if (history.success) "Success" else "Failed",
                tint = if (history.success) MaterialTheme.colorScheme.primary else Color.Red,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = history.inputFileName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${history.conversionType} • $dateString",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            if (history.success) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Done",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    Card(
        modifier = modifier
            .clip(shape)
            .then(
                if (liquidState != null) {
                    val config = LiquidGlassConfig.NavBarConfig
                    Modifier.liquid(liquidState) {
                        frost = config.frost
                        refraction = config.refraction
                        curve = config.curve
                        edge = config.edge
                        tint = config.tint
                        saturation = config.saturation
                        dispersion = config.dispersion
                        contrast = config.contrast
                        this.shape = shape
                    }
                } else Modifier
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (liquidState != null) {
                MaterialTheme.colorScheme.surface.copy(alpha = if (isLight) 0.6f else 0.4f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            }
        ),
        border = BorderStroke(
            width = if (isLight) 1.dp else 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        cardContent()
    }
}
