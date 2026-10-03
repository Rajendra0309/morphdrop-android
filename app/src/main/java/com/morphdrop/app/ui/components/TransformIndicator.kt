package com.morphdrop.app.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.morphdrop.app.domain.model.ConversionType
import com.morphdrop.app.ui.theme.MorphDropTheme

/**
 * The signature morph pill: `PNG → PDF`. Input chip, brand-tinted arrow,
 * output chip. Used on tool cards, config headers, history rows and the
 * result screen so every conversion reads as a visible transformation.
 */
@Composable
fun TransformIndicator(
    inputFormat: String,
    outputFormat: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier.semantics {
            contentDescription = "$inputFormat to $outputFormat"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
    ) {
        FormatChip(format = inputFormat)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(if (compact) 12.dp else 14.dp),
        )
        FormatChip(format = outputFormat)
    }
}

/**
 * Convenience overload deriving formats from a [ConversionType]. FileType
 * overloads of [FormatChip] keep per-format container colors.
 */
@Composable
fun TransformIndicator(
    conversionType: ConversionType,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier.semantics {
            contentDescription =
                "${conversionType.inputType.displayName} to ${conversionType.outputType.displayName}"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
    ) {
        FormatChip(fileType = conversionType.inputType)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(if (compact) 12.dp else 14.dp),
        )
        FormatChip(fileType = conversionType.outputType)
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun TransformIndicatorLightPreview() {
    MorphDropTheme(darkTheme = false) {
        Surface {
            TransformIndicator(
                inputFormat = "PNG",
                outputFormat = "PDF",
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun TransformIndicatorDarkPreview() {
    MorphDropTheme(darkTheme = true) {
        Surface {
            TransformIndicator(
                inputFormat = "MD",
                outputFormat = "PDF",
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
