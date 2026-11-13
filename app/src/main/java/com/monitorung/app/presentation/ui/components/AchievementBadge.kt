package com.monitorung.app.presentation.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monitorung.app.R
import com.monitorung.app.presentation.ui.theme.*

/**
 * Playful achievement badge — rounded square with gradient fill, icon, and label.
 *
 * - Unlocked: gradient fill + icon with glow + title
 * - Locked: grayscale + lock overlay + "???" title
 * - Unlock animation: scale bounce on first appear
 */
@Composable
fun AchievementBadge(
    icon: ImageVector,
    title: String,
    isUnlocked: Boolean,
    modifier: Modifier = Modifier,
    gradient: List<Color> = GradientBlueTeal,
    tint: Color = PrimaryBlue,
) {
    val scale by animateFloatAsState(
        targetValue = if (isUnlocked) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "badge_scale",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Badge icon container
        Box(
            modifier = Modifier
                .scale(scale)
                .size(52.dp)
                .clip(RoundedCornerShape(RadiusBubble))
                .then(
                    if (isUnlocked) {
                        Modifier
                            .background(Brush.linearGradient(gradient))
                            .border(
                                2.dp,
                                tint.copy(alpha = 0.4f),
                                RoundedCornerShape(RadiusBubble),
                            )
                    } else {
                        Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(RadiusBubble),
                            )
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isUnlocked) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = stringResource(R.string.achievement_locked),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // Title — "???" when locked, real title when unlocked
        Text(
            text = if (isUnlocked) title else "???",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isUnlocked) FontWeight.Medium else FontWeight.Normal,
            color = if (isUnlocked) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 64.dp),
        )
    }
}

/**
 * Compact inline achievement indicator (used in History summary).
 */
@Composable
fun CompactAchievementBadge(
    icon: ImageVector,
    label: String,
    isUnlocked: Boolean,
    gradient: List<Color> = GradientBlueTeal,
    tint: Color = PrimaryBlue,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(RadiusSoft))
            .background(
                if (isUnlocked) {
                    Brush.horizontalGradient(gradient.map { it.copy(alpha = 0.15f) })
                } else {
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surfaceVariant,
                        )
                    )
                }
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        if (isUnlocked) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
