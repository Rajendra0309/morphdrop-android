package com.morphdrop.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morphdrop.app.domain.model.FileType

@Composable
fun FormatBadge(
    fileType: FileType,
    modifier: Modifier = Modifier,
    backgroundColor: Color = fileType.color
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val finalBgColor = if (isLight) {
        // Darken the background more aggressively for better contrast on white
        backgroundColor.copy(alpha = 1f).let {
            Color(
                red = (it.red * 0.65f),
                green = (it.green * 0.65f),
                blue = (it.blue * 0.65f),
                alpha = 1f
            )
        }
    } else {
        backgroundColor
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(finalBgColor)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = fileType.displayName.uppercase(),
            color = if (finalBgColor.luminance() > 0.5f) Color.Black else Color.White,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp
            )
        )
    }
}

@Composable
fun FormatBadge(
    text: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val finalBgColor = if (isLight) {
        backgroundColor.copy(alpha = 1f).let {
            Color(
                red = (it.red * 0.65f),
                green = (it.green * 0.65f),
                blue = (it.blue * 0.65f),
                alpha = 1f
            )
        }
    } else {
        backgroundColor
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(finalBgColor)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = if (finalBgColor.luminance() > 0.5f) Color.Black else Color.White,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp
            )
        )
    }
}
