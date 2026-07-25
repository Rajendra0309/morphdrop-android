package com.morphdrop.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.morphdrop.app.ui.theme.LiquidGlassConfig
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquid

@Composable
fun MorphDropSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    liquidState: LiquidState? = null,
    placeholderText: String = "Search conversion tools..."
) {
    val shape = RoundedCornerShape(24.dp)
    val config = LiquidGlassConfig.SearchBarConfig

    Box(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 440.dp)
            .then(
                if (liquidState != null) {
                    Modifier
                        .clip(shape)
                        .liquid(liquidState) {
                            frost = config.frost
                            refraction = config.refraction
                            curve = config.curve
                            edge = 0f // Set to 0 to avoid double border with OutlinedTextField
                            tint = config.tint
                            saturation = config.saturation
                            dispersion = config.dispersion
                            contrast = config.contrast
                            this.shape = shape
                        }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .widthIn(max = 440.dp) // Constraint directly on the field
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            placeholder = {
                Text(
                    text = placeholderText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            shape = shape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color.Transparent, // Let liquid handle background
                unfocusedContainerColor = Color.Transparent
            )
        )
    }
}
