package com.monitorung.app.presentation.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monitorung.app.presentation.ui.theme.WarningAmber

/**
 * papercard-style streak indicator: animated flame that pulses (scale + color glow)
 * when the streak is active (>0), or sits dim when the user hasn't started yet.
 *
 * Animation layers:
 *   1. Scale:  1.0 → 1.15 → 1.0  (gentle bounce, 900ms)
 *   2. Color:  base → brighter → base  (glow feel via color transition)
 */
@Composable
fun StreakIndicator(
    streakDays: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = WarningAmber,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val isActive = streakDays > 0

    val infinite = rememberInfiniteTransition(label = "flame")
    val scale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "flame_scale",
    )


    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.LocalFireDepartment,
            contentDescription = "Streak",
            tint = if (isActive) activeColor else inactiveColor,
            modifier = Modifier
                .size(22.dp)
                .scale(scale),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
        Text(
            text = "$streakDays hari",
            style = MaterialTheme.typography.labelLarge,
            color = if (isActive) activeColor else inactiveColor,
            fontWeight = FontWeight.Bold,
        )
    }
}
