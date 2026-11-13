package com.monitorung.app.presentation.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.monitorung.app.presentation.ui.theme.GradientBlueTeal
import com.monitorung.app.presentation.ui.theme.RadiusPill

/**
 * Playful gradient progress bar with rounded pill ends and a subtle shimmer sweep.
 *
 * The shimmer is a very faint light streak that sweeps left-to-right across the
 * fill, giving the bar a sense of depth and polish without being distracting.
 * It only activates when [animateShimmer] is true (default: progress > 0).
 */
@Composable
fun PlayfulProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    gradient: Brush = Brush.horizontalGradient(GradientBlueTeal),
    height: Dp = 10.dp,
    animateShimmer: Boolean = true,
) {
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "progress_fill",
    )

    // Shimmer sweep: faint white streak moving left→right continuously
    val shimmerOffset by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (animateShimmer && progress > 0f) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "shimmer_visibility",
    )

    val shimmerBrush = if (shimmerOffset > 0f) {
        val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
        val sweep by infinite.animateFloat(
            initialValue = -0.6f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "shimmer_sweep",
        )
        Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.18f),
                Color.Transparent,
            ),
            startX = sweep - 0.3f,
            endX = sweep + 0.3f,
        )
    } else null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(RadiusPill))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .clip(RoundedCornerShape(RadiusPill))
                .background(gradient),
        )
        // Shimmer overlay on the fill
        shimmerBrush?.let {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(RoundedCornerShape(RadiusPill))
                    .background(it),
            )
        }
    }
}
