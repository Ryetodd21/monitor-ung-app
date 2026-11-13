package com.monitorung.app.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monitorung.app.presentation.ui.theme.GradientBlueTeal
import com.monitorung.app.presentation.ui.theme.PastelBlueLight
import com.monitorung.app.presentation.ui.theme.RadiusBubble
import com.monitorung.app.presentation.ui.theme.RadiusPlayful
import com.monitorung.app.presentation.ui.theme.RadiusSoft

/**
 * papercard-style playful bottom navigation.
 *
 * Replaces TelegramStyleNavBar (pill-in-pill) with floating bubble cards.
 * Each tab is a soft rounded surface with:
 * - Bouncy spring scale(1.0 → 1.12) on active
 * - Gradient blob background on active
 * - 22dp icon + 10sp label
 * - Badge dot (rounded square, playful)
 *
 * Container: floating paper card with soft shadow, 28dp corners.
 */
@Composable
fun BubbleNavBar(
    items: List<NavBarItem>,
    currentRoute: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Outer container: floating paper card with 28dp corners
        PaperCard(
            modifier = Modifier.fillMaxWidth(),
            padding = 8.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    BubbleNavTab(
                        item = item,
                        selected = item.route == currentRoute,
                        onClick = { onItemSelected(item.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BubbleNavTab(
    item: NavBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "bubble_scale",
    )

    val iconColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "bubble_icon_color",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(RadiusBubble))
            .let { mod ->
                if (selected) {
                    // Gradient blob on active tab — papercard playful fill
                    mod.background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            ),
                        ),
                    )
                } else {
                    mod.background(Color.Transparent)
                }
            }
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
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
                    tint = iconColor,
                    modifier = Modifier.size(24.dp),
                )
                if (item.badgeCount > 0) {
                    // Playful rounded square badge (not circle)
                    Box(
                        modifier = Modifier
                            .offset(x = 6.dp, y = (-3).dp)
                            .size(8.dp)
                            .clip(RoundedCornerShape(RadiusSoft))
                            .background(MaterialTheme.colorScheme.error),
                    )
                }
            }
            Text(
                text = item.label,
                color = iconColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
    }
}
