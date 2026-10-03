package com.morphdrop.app.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.morphdrop.app.ui.theme.MorphDropTheme
import com.morphdrop.app.ui.utils.rememberHapticHelper

/**
 * The one button component. Variants: Primary (filled, 56dp), Secondary
 * (outlined, 48dp), Ghost (text-like, 48dp). Single 16dp shape language,
 * Role.Button semantics, haptic feedback on tap, and a quiet disabled state
 * (flat surface tones — never a gradient).
 */
enum class MorphButtonVariant { Primary, Secondary, Ghost }

@Composable
fun MorphButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: MorphButtonVariant = MorphButtonVariant.Primary,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    contentDescription: String? = null,
) {
    val haptic = rememberHapticHelper()
    val shape = RoundedCornerShape(16.dp)
    val minHeight = if (variant == MorphButtonVariant.Primary) 56.dp else 48.dp
    val scheme = MaterialTheme.colorScheme

    val containerColor: Color
    val contentColor: Color
    val border: BorderStroke?
    when (variant) {
        MorphButtonVariant.Primary -> {
            // Quiet disabled state: flat surface tones, no gradient, no shout.
            containerColor = if (enabled) scheme.primary else scheme.surfaceVariant
            contentColor = if (enabled) scheme.onPrimary else scheme.onSurfaceVariant
            border = null
        }
        MorphButtonVariant.Secondary -> {
            containerColor = Color.Transparent
            contentColor = if (enabled) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.38f)
            border = BorderStroke(1.dp, if (enabled) scheme.outline else scheme.outlineVariant)
        }
        MorphButtonVariant.Ghost -> {
            containerColor = Color.Transparent
            contentColor = if (enabled) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.38f)
            border = null
        }
    }

    Surface(
        onClick = {
            haptic.click()
            onClick()
        },
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .semantics { role = Role.Button }
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = border,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun MorphButtonLightPreview() {
    MorphDropTheme(darkTheme = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MorphButton(text = "Convert", onClick = {}, modifier = Modifier.fillMaxWidth(), leadingIcon = Icons.Default.Check)
            MorphButton(text = "Share", onClick = {}, variant = MorphButtonVariant.Secondary, modifier = Modifier.fillMaxWidth())
            MorphButton(text = "Back to home", onClick = {}, variant = MorphButtonVariant.Ghost, modifier = Modifier.fillMaxWidth())
            MorphButton(text = "Disabled", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun MorphButtonDarkPreview() {
    MorphDropTheme(darkTheme = true) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MorphButton(text = "Convert", onClick = {}, modifier = Modifier.fillMaxWidth())
            MorphButton(text = "Share", onClick = {}, variant = MorphButtonVariant.Secondary, modifier = Modifier.fillMaxWidth())
            MorphButton(text = "Disabled", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
