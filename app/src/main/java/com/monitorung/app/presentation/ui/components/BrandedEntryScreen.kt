package com.monitorung.app.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/**
 * Branded entry animation — shows a full-screen branded splash that
 * fades and slides in when the loading state resolves.
 *
 * On first app launch the branded splash stays visible while the
 * app resolves auth state; then the real content slides in.
 */
@Composable
fun BrandedEntryScreen(
    isLoading: Boolean,
    content: @Composable () -> Unit,
) {
    var showContent by remember { mutableStateOf(false) }

    // Once loading resolves, trigger the content reveal
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            // Brief pause so the branded splash is visible for a moment
            delay(300)
            showContent = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Real app content — slides in from top when ready
        AnimatedVisibility(
            visible = showContent,
            enter = slideInVertically(
                initialOffsetY = { -it / 4 },
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            ) + fadeIn(
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            ),
        ) {
            content()
        }
    }
}
