package com.monitorung.app.presentation.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.monitorung.app.R
import com.monitorung.app.presentation.ui.theme.*

/**
 * Daily challenge widget — papercard gamified style.
 *
 * Shows today's active challenge with:
 * - Gradient border card
 * - Challenge icon + title + description
 * - PlayfulProgressBar showing completion
 * - Confetti on completion (caller handles via onComplete callback)
 *
 * Challenge types:
 * - REDUCE_SCREEN_TIME: "Kurangi screen time 30 menit hari ini"
 * - NO_SOSMED: "Hari ini tanpa sosmed"
 * - EARLY_START: "Mulai belajar sebelum jam 9"
 * - STREAK: "Jaga streak-mu!"
 */
enum class DailyChallengeType {
    REDUCE_SCREEN_TIME,
    NO_SOSMED,
    EARLY_START,
    STREAK,
}

@Composable
fun DailyChallengeWidget(
    challenge: DailyChallengeType,
    progress: Float, // 0..1
    isCompleted: Boolean,
    modifier: Modifier = Modifier,
) {
    val (icon, title, desc, gradient, tint) = when (challenge) {
        DailyChallengeType.REDUCE_SCREEN_TIME -> listOf(
            Icons.Default.Timer,
            stringResource(R.string.challenge_reduce_title),
            stringResource(R.string.challenge_reduce_desc),
            GradientBlueTeal,
            PrimaryBlue,
        )
        DailyChallengeType.NO_SOSMED -> listOf(
            Icons.Default.School,
            stringResource(R.string.challenge_nososmed_title),
            stringResource(R.string.challenge_nososmed_desc),
            GradientPurplePink,
            ChartPurple,
        )
        DailyChallengeType.EARLY_START -> listOf(
            Icons.Default.Star,
            stringResource(R.string.challenge_early_title),
            stringResource(R.string.challenge_early_desc),
            GradientAmberGold,
            WarningAmber,
        )
        DailyChallengeType.STREAK -> listOf(
            Icons.Default.LocalFireDepartment,
            stringResource(R.string.challenge_streak_title),
            stringResource(R.string.challenge_streak_desc),
            GradientAmberPeach,
            WarningAmber,
        )
    }

    @Suppress("UNCHECKED_CAST")
    val iconVec = icon as androidx.compose.ui.graphics.vector.ImageVector
    @Suppress("UNCHECKED_CAST")
    val titleStr = title as String
    @Suppress("UNCHECKED_CAST")
    val descStr = desc as String
    @Suppress("UNCHECKED_CAST")
    val gradList = gradient as List<Color>
    @Suppress("UNCHECKED_CAST")
    val tintColor = tint as Color

    // Pulsing glow when completed
    val infiniteTransition = rememberInfiniteTransition(label = "challenge_pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow_alpha",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusPlayful))
            .border(
                2.dp,
                if (isCompleted) {
                    tintColor.copy(alpha = glowAlpha)
                } else {
                    gradList.first().copy(alpha = 0.5f)
                },
                RoundedCornerShape(RadiusPlayful),
            )
            .background(
                Brush.linearGradient(
                    listOf(
                        gradList.first().copy(alpha = 0.08f),
                        gradList.last().copy(alpha = 0.04f),
                    )
                )
            )
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Challenge icon in gradient circle
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(RadiusSoft))
                        .background(Brush.linearGradient(gradList))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            imageVector = iconVec,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titleStr,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = descStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (isCompleted) {
                    Text(
                        text = stringResource(R.string.challenge_done),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = tintColor,
                    )
                }
            }

            // Progress bar
            PlayfulProgressBar(
                progress = progress,
                gradient = Brush.horizontalGradient(gradList),
            )

            Text(
                text = if (isCompleted) {
                    stringResource(R.string.challenge_completed_label)
                } else {
                    stringResource(R.string.challenge_progress_label, (progress * 100).toInt())
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
