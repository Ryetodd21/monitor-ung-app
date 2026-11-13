package com.monitorung.app.presentation.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Circular progress ring with gradient stroke and rounded caps.
 * Animates smoothly from the current value to [progress].
 *
 * Features:
 * - Gradient stroke (primary → secondary)
 * - Rounded stroke caps (softer ends)
 * - Smooth 900ms tween animation
 * - Configurable size, stroke width, and colors
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    strokeWidth: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    gradientColors: List<Color>? = null,
    /** Arc start angle in degrees. Default -90° = top. */
    startAngle: Float = -90f,
) {
    val coerced = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = coerced,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "ring_progress",
    )

    Canvas(modifier = modifier.size(size)) {
        val diameterPx = this.size.minDimension
        val stroke = strokeWidth.toPx()
        val arcSize = Size(diameterPx - stroke, diameterPx - stroke)
        val topLeft = Offset(stroke / 2f, stroke / 2f)

        // Track — full circle with rounded cap
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        // Progress arc — gradient if provided, single color otherwise
        if (gradientColors != null && gradientColors.size >= 2) {
            val sweepAngle = 360f * animated
            drawArc(
                brush = Brush.sweepGradient(
                    colors = gradientColors,
                    center = Offset(diameterPx / 2f, diameterPx / 2f),
                ),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        } else {
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}
