package com.monitorung.app.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Wraps a child in a staggered entrance animation: scale (0.88→1) + fade + slide-up.
 *
 * Each item with a different `index` waits `index * staggerDelayMs` before appearing,
 * producing a cascading cascade effect. The slot stays invisible while waiting so it
 * doesn't affect layout — safe inside [Column] (Home/Settings/Profile) and
 * [LazyColumn] (Schedule/History).
 *
 * Animation layers (all run simultaneously after the delay):
 *   1. Scale:  0.88 → 1.0  (FastOutSlowInEasing, overshoot-feel)
 *   2. Fade:   0.0  → 1.0  (same easing)
 *   3. Slide:  +40dp → 0   (same easing)
 */
@Composable
fun StaggeredEntrance(
    index: Int,
    modifier: Modifier = Modifier,
    /** Delay per item in ms. 40ms gives smooth cascade without feeling slow. */
    staggerDelayMs: Long = 40L,
    /** Duration per animation layer in ms. */
    durationMs: Int = 280,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * staggerDelayMs)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing),
        ) + slideInVertically(
            initialOffsetY = { it / 3 },
            animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing),
        ),
        modifier = modifier,
    ) {
        content()
    }
}
