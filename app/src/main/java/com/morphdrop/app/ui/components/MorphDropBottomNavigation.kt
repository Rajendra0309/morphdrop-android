package com.morphdrop.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morphdrop.app.ui.theme.LiquidGlassConfig
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquid

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Home : BottomNavItem("home", "Home", Icons.Default.Home)
    data object History : BottomNavItem("history", "History", Icons.Default.History)
    data object Settings : BottomNavItem("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun MorphDropBottomNavigation(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    liquidState: LiquidState,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.History,
        BottomNavItem.Settings
    )

    val shape = RoundedCornerShape(32.dp)
    val config = LiquidGlassConfig.NavBarConfig
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .height(72.dp) // Slightly more compact
            .clip(shape)
            .then(
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
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
            .background(
                MaterialTheme.colorScheme.surface.copy(
                    alpha = if (isLight) 0.6f else 0.2f
                )
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                val interactionSource = remember { MutableInteractionSource() }

                val indicatorWidth by animateDpAsState(
                    targetValue = if (selected) 12.dp else 0.dp,
                    animationSpec = tween(durationMillis = 250),
                    label = "NavIndicatorWidth"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onNavigate(item.route) }
                        )
                        .padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = item.title,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .height(2.dp)
                            .width(indicatorWidth)
                            .clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .let {
                                if (selected) it.blur(4.dp) else it
                            }
                    )
                }
            }
        }
    }
}
