package com.morphdrop.app.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morphdrop.app.domain.model.FileType
import com.morphdrop.app.ui.theme.MorphDropTheme
import com.morphdrop.app.ui.theme.formatChipColors

/**
 * Single format chip implementation (replaces FormatBadge).
 *
 * Rules: container color at full opacity, text in the matching on-container
 * color, uppercase monospace, never raw neon color as body text.
 */
@Composable
fun FormatChip(
    format: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = format.uppercase(),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp
            )
        )
    }
}

/**
 * Convenience overload: derive a theme-aware container/content pair from a
 * FileType accent color. The accent is darkened on light themes and lightened
 * on dark themes so the chip always passes contrast.
 */
@Composable
fun FormatChip(
    fileType: FileType,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val (containerColor, contentColor) = formatChipColors(fileType, isDark)
    FormatChip(
        format = fileType.displayName,
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor
    )
}

@Preview(name = "Light Mode", showBackground = true)
@Composable
private fun FormatChipLightPreview() {
    MorphDropTheme(darkTheme = false) {
        Surface {
            FormatChip(fileType = FileType.MD, modifier = Modifier.padding(16.dp))
        }
    }
}

@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun FormatChipDarkPreview() {
    MorphDropTheme(darkTheme = true) {
        Surface {
            FormatChip(fileType = FileType.MD, modifier = Modifier.padding(16.dp))
        }
    }
}
