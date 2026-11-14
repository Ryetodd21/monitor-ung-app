package com.monitorung.app.presentation.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun StaggeredEntrance(
    index: Int,
    modifier: Modifier = Modifier,
    staggerDelayMs: Long = 30L,
    durationMs: Int = 240,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(index, staggerDelayMs, durationMs) {
        progress.animateTo(
            1f,
            tween(
                durationMillis = durationMs.coerceAtLeast(0),
                delayMillis = (index.coerceIn(0, 4) * staggerDelayMs.coerceIn(0L, 30L)).toInt(),
                easing = FastOutSlowInEasing,
            ),
        )
    }
    // Reserve layout immediately; Compose also scales the delay for reduced motion.
    Box(modifier.graphicsLayer {
        alpha = progress.value
        translationY = 12.dp.toPx() * (1f - progress.value)
    }) {
        content()
    }
}
