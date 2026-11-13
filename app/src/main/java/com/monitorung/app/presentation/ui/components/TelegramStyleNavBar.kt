package com.monitorung.app.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monitorung.app.presentation.ui.theme.*

/**
 * Telegram-style compact bottom navigation.
 *
 * - Pill-shaped floating glass container
 * - Rounded highlight on active tab (pill inside pill)
 * - Compact icon + label
 * - Smooth color animation on selection
 */
@Composable
fun TelegramStyleNavBar(
    items: List<NavBarItem>,
    currentRoute: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val isAmoled = scheme.background == AmoledBlack
    val isDark = scheme.background == SurfaceDark || isAmoled

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Outer paper container
        PaperCard(
            modifier = Modifier.fillMaxWidth(),
            padding = 6.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    NavBarTab(
                        item = item,
                        selected = item.route == currentRoute,
                        onClick = { onItemSelected(item.route) },
                        modifier = Modifier.weight(1f),
                        isAmoled = isAmoled,
                    )
                }
            }
        }
    }
}

@Composable
private fun NavBarTab(
    item: NavBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isAmoled: Boolean = false,
) {
    val targetColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val color by animateColorAsState(targetColor, label = "nav_color")

    val targetWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
    val targetSize = if (selected) 11.sp else 10.sp

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(RadiusXL))
            .let { mod ->
                if (selected) {
                    // FIX N1: use MaterialTheme colors instead of hardcoded White,
                    // so the pill highlight adapts to dark/AMOLED themes.
                    mod.background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            )
                        )
                    )
                } else {
                    mod.background(Color.Transparent)
                }
            }
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = item.label,
                    tint = color,
                    modifier = Modifier.size(22.dp),
                )
                if (item.badgeCount > 0) {
                    Box(
                        modifier = Modifier
                            .offset(x = 6.dp, y = (-3).dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Red500),
                    )
                }
            }
            Text(
                text = item.label,
                color = color,
                fontWeight = targetWeight,
                fontSize = targetSize,
                maxLines = 1,
            )
        }
    }
}

data class NavBarItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badgeCount: Int = 0,
)
