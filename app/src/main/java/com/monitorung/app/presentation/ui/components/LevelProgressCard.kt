package com.monitorung.app.presentation.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monitorung.app.R
import com.monitorung.app.presentation.ui.theme.*
import com.monitorung.app.presentation.ui.theme.GradientBlueTeal

/**
 * XP/Level progress card — papercard gamified style.
 * Shows current level, XP progress bar toward next level, and streak info.
 *
 * Gradient fill on progress bar, animated on appear.
 * Level-up triggers confetti (caller handles via onLevelUp callback).
 *
 * Level formula: level = 1 + (totalXp / 1000) — each level needs 1000 XP.
 * XP = streak_days * 10 + avg_screen_time_minutes_today.
 */
@Composable
fun LevelProgressCard(
    level: Int,
    currentXp: Int,
    xpToNextLevel: Int,
    streakDays: Int,
    modifier: Modifier = Modifier,
    gradient: List<Color> = GradientBlueTeal,
) {
    val progress = if (xpToNextLevel > 0) {
        (currentXp.toFloat() / (currentXp + xpToNextLevel)).coerceIn(0f, 1f)
    } else 0f

    // Animate progress bar on first composition
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "level_progress",
    )

    PaperCard(modifier = modifier.fillMaxWidth(), padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header row: Level badge + streak
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Level badge — rounded square with gradient
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(RadiusSoft))
                            .background(
                                Brush.horizontalGradient(gradient),
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.level_badge, level),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                    Text(
                        text = stringResource(R.string.level_xp_label, currentXp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Streak flame
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    StreakIndicator(streakDays = streakDays)
                }
            }

            // Progress bar
            PlayfulProgressBar(
                progress = animatedProgress,
                modifier = Modifier.fillMaxWidth(),
                gradient = Brush.horizontalGradient(gradient),
            )

            // XP to next level
            Text(
                text = stringResource(R.string.level_xp_to_next, xpToNextLevel),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
