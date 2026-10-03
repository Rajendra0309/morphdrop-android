package com.morphdrop.app.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.morphdrop.app.domain.model.ConversionType
import com.morphdrop.app.ui.theme.chipColors

@Composable
fun ConversionCard(
    conversionType: ConversionType,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    /** Long-press: quick conversion with the tool's saved preset. Null = no long-press. */
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    descriptionPrefix: String = ""
) {
    val (badgeContainer, badgeContent) = conversionType.inputType.chipColors()
    val interactionSource = remember { MutableInteractionSource() }

    ElevatedCard(
        modifier = modifier
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics {
                contentDescription =
                    "${conversionType.name}: ${conversionType.inputType.displayName} " +
                        "to ${conversionType.outputType.displayName}" +
                        if (onLongClick != null) ". Long-press for quick conversion." else ""
            },
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: format-aware icon + favorite toggle (48dp touch target)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(if (isCompact) 40.dp else 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = badgeContainer,
                    contentColor = badgeContent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = conversionType.icon,
                            contentDescription = null,
                            modifier = Modifier.size(if (isCompact) 22.dp else 26.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (conversionType.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        // Announce the ACTION, not the current state.
                        contentDescription = if (conversionType.isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (conversionType.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Title & Description — fixed 2-line slots so every card in the grid
            // is exactly the same height no matter how long the text is.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = conversionType.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!isCompact) {
                    Text(
                        text = descriptionPrefix + conversionType.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Signature morph pill: PNG → PDF
            if (conversionType.id == "metadata_editor") {
                FormatChip(
                    format = "ALL FORMATS",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                TransformIndicator(
                    conversionType = conversionType,
                    compact = isCompact
                )
            }
        }
    }
}
