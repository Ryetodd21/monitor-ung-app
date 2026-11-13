package com.monitorung.app.presentation.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.monitorung.app.presentation.ui.theme.ChartBlue
import com.monitorung.app.presentation.ui.theme.ChartTeal

/**
 * papercard-style refresh icon — colorful circular arrow with gradient.
 * Replaces the plain Material `Icons.Default.Sync` for a more playful feel.
 *
 * @param spinning when true, the arrow rotates continuously (sync in progress)
 * @param size overall size of the icon
 * @param strokeWidth thickness of the arrow stroke
 */
@Composable
fun PapercardRefreshIcon(
    spinning: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    strokeWidth: Dp = 2.5.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "refresh_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "refresh_rotation",
    )

    Canvas(modifier = modifier.size(size)) {
        val diameterPx = this.size.minDimension
        val sw = strokeWidth.toPx()
        val halfD = diameterPx / 2f
        val center = Offset(halfD, halfD)
        val radius = halfD - sw

        // Rotate when spinning
        rotate(if (spinning) rotation else 0f, pivot = center) {
            // Draw the circular arc (the track)
            drawArc(
                color = Color.LightGray.copy(alpha = 0.35f),
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(sw / 2f, sw / 2f),
                size = Size(diameterPx - sw, diameterPx - sw),
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )

            // Draw the gradient arc (the arrow head)
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(ChartBlue, ChartTeal, ChartBlue),
                    center = center,
                ),
                startAngle = 0f,
                sweepAngle = 200f,
                useCenter = false,
                topLeft = Offset(sw / 2f, sw / 2f),
                size = Size(diameterPx - sw, diameterPx - sw),
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )

            // Arrow head — small triangle at the end of the arc
            val arrowAngle = Math.toRadians((0 + 200 - 15).toDouble())
            val ax = center.x + radius * kotlin.math.cos(arrowAngle).toFloat()
            val ay = center.y + radius * kotlin.math.sin(arrowAngle).toFloat()
            val arrowPath = Path().apply {
                moveTo(ax, ay)
                lineTo(
                    ax + (-radius * 0.25f * kotlin.math.cos(arrowAngle + Math.PI / 2).toFloat()),
                    ay + (-radius * 0.25f * kotlin.math.sin(arrowAngle + Math.PI / 2).toFloat()),
                )
                lineTo(
                    ax + (-radius * 0.25f * kotlin.math.cos(arrowAngle - Math.PI / 2).toFloat()),
                    ay + (-radius * 0.25f * kotlin.math.sin(arrowAngle - Math.PI / 2).toFloat()),
                )
                close()
            }
            drawPath(arrowPath, brush = Brush.sweepGradient(
                colors = listOf(ChartTeal, ChartBlue, ChartTeal),
                center = center,
            ))
        }
    }
}
