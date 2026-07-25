package com.morphdrop.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morphdrop.app.domain.model.ConversionType
import com.morphdrop.app.ui.theme.LiquidGlassConfig
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquid

@Composable
fun ConversionCard(
    conversionType: ConversionType,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier,
    liquidState: LiquidState? = null,
    isCompact: Boolean = false,
    descriptionPrefix: String = ""
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val shape = RoundedCornerShape(20.dp)
    val interactionSource = remember { MutableInteractionSource() }

    val cardModifier = modifier
        .semantics {
            contentDescription = "$descriptionPrefix Tool: ${conversionType.name}. ${conversionType.description}"
        }
        .clip(shape)
        .then(
            if (liquidState != null) {
                val config = LiquidGlassConfig.CardConfig
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
        )
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )

    Card(
        modifier = cardModifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (liquidState != null) {
                MaterialTheme.colorScheme.surface.copy(alpha = if (isLight) 0.6f else 0.2f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            }
        ),
        border = BorderStroke(
            width = if (isLight) 1.dp else 1.dp,
            color = if (isLight) {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isCompact) 14.dp else 18.dp), // Increased padding to prevent clipping
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp)
        ) {
            // Header: Icon + Heart
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isCompact) 32.dp else 40.dp)
                        .clip(CircleShape)
                        .background(conversionType.inputType.color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = conversionType.icon,
                        contentDescription = null,
                        tint = conversionType.inputType.color,
                        modifier = Modifier.size(if (isCompact) 18.dp else 24.dp)
                    )
                }

                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(if (isCompact) 32.dp else 40.dp)
                ) {
                    Icon(
                        imageVector = if (conversionType.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (conversionType.isFavorite) "Remove ${conversionType.name} from $descriptionPrefix favorites" else "Add ${conversionType.name} to $descriptionPrefix favorites",
                        tint = if (conversionType.isFavorite) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(if (isCompact) 18.dp else 22.dp)
                    )
                }
            }

            // Title
            Text(
                text = conversionType.name,
                fontWeight = FontWeight.Bold,
                fontSize = if (isCompact) 14.sp else 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Description
            if (!isCompact) {
                Text(
                    text = conversionType.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(36.dp)
                )
            }

            // Badges row: input -> output
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                FormatBadge(fileType = conversionType.inputType)

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "to",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(if (isCompact) 10.dp else 12.dp)
                )

                FormatBadge(fileType = conversionType.outputType)
            }
        }
    }
}
