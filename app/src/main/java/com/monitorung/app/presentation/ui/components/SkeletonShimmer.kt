package com.monitorung.app.presentation.ui.components

import com.monitorung.app.presentation.ui.theme.*

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * papercard-style skeleton shimmer — animated gradient sweep across a placeholder block.
 * Used while real content is loading. Keeps the screen feeling alive instead of static.
 *
 * FIX N3: skeleton now reads MaterialTheme colors at runtime instead of importing
 * PrimaryBlueLight directly. This keeps the skeleton looking correct in dark/AMOLED
 * themes (where a light-blue shimmer would look wrong on a dark surface).
 */
@Composable
fun rememberShimmerBrush(
    baseColor: Color? = null,
): Brush {
    // Derive shimmer colors from MaterialTheme at runtime so the skeleton adapts
    // to light/dark/AMOLED automatically. Pass baseColor to override for
    // specialized uses (e.g., colored placeholder on a tinted surface).
    val scheme = MaterialTheme.colorScheme
    val shimmerBase = baseColor ?: scheme.surfaceVariant
    val shimmerHighlight = scheme.surface

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-translate",
    )
    val shimmer = remember(translate, shimmerBase, shimmerHighlight) {
        Brush.linearGradient(
            colors = listOf(
                shimmerBase.copy(alpha = 0.4f),
                shimmerHighlight.copy(alpha = 0.7f),
                shimmerBase.copy(alpha = 0.4f),
            ),
            start = Offset(translate - 200f, 0f),
            end = Offset(translate + 200f, 0f),
        )
    }
    return shimmer
}

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    shape: Shape = RoundedCornerShape(RadiusXS),
    baseColor: Color? = null,
) {
    // FIX N3: use MaterialTheme colors at runtime — the shimmer now adapts to
    // light/dark/AMOLED automatically. baseColor param overrides the default
    // surfaceVariant for specialized uses (e.g., tinted placeholder).
    val shimmer = rememberShimmerBrush(baseColor = baseColor)
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(shimmer)
    )
}

@Composable
fun SkeletonCard(modifier: Modifier = Modifier, baseColor: Color? = null) {
    val shimmer = rememberShimmerBrush(baseColor = baseColor)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(RadiusM2))
            .background(shimmer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SkeletonBox(modifier = Modifier.fillMaxWidth(0.6f), height = 18.dp, baseColor = baseColor)
        SkeletonBox(modifier = Modifier.fillMaxWidth(), height = 12.dp, baseColor = baseColor)
        SkeletonBox(modifier = Modifier.fillMaxWidth(0.85f), height = 12.dp, baseColor = baseColor)
    }
}

@Composable
fun SkeletonMetric(modifier: Modifier = Modifier, baseColor: Color? = null) {
    val shimmer = rememberShimmerBrush(baseColor = baseColor)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(RadiusM))
            .background(shimmer)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SkeletonBox(modifier = Modifier.fillMaxWidth(0.5f), height = 12.dp, baseColor = baseColor)
        SkeletonBox(modifier = Modifier.fillMaxWidth(0.7f), height = 22.dp, baseColor = baseColor)
    }
}
