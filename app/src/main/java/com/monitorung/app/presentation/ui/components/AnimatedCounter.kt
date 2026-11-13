package com.monitorung.app.presentation.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

/**
 * Smooth counter that animates from the previous value to [targetValue].
 *
 * - Small deltas (≤10): spring-based with a subtle overshoot (slot-machine tick feel)
 * - Large deltas (>10): FastOutSlowIn easing for snappy but smooth acceleration
 *
 * Duration scales with the absolute delta: `min(max(delta, 1) * 30, 1200) ms`
 */
@Composable
fun AnimatedCounter(
    targetValue: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    val animated = remember { Animatable(0f) }

    LaunchedEffect(targetValue) {
        val delta = kotlin.math.abs(targetValue - animated.value).toInt()
        val duration = (delta.coerceAtLeast(1) * 30).coerceAtMost(1200)

        if (delta <= 10) {
            // Spring-based: fast tick with natural overshoot bounce
            animated.animateTo(
                targetValue = targetValue.toFloat(),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        } else {
            // Eased: quick start, smooth landing
            animated.animateTo(
                targetValue = targetValue.toFloat(),
                animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing),
            )
        }
    }

    Text(
        text = animated.value.toInt().toString(),
        modifier = modifier,
        style = style,
    )
}
