package com.monitorung.app.presentation.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import com.monitorung.app.presentation.ui.theme.ChartAmber
import com.monitorung.app.presentation.ui.theme.ChartBlue
import com.monitorung.app.presentation.ui.theme.ChartPurple
import com.monitorung.app.presentation.ui.theme.ChartTeal
import com.monitorung.app.presentation.ui.theme.ErrorFuchsia
import com.monitorung.app.presentation.ui.theme.WarningAmber
import kotlin.random.Random

private enum class ParticleShape { CIRCLE, RECT, PILL }

/**
 * One-shot confetti burst with varied shapes and natural physics.
 *
 * Features:
 * - 32 particles across 3 shapes: circles, rectangles, pills (long rectangles)
 * - Wider horizontal spread (burst centered at 30% from top, not top edge)
 * - Quadratic gravity curve — particles accelerate downward naturally
 * - Per-shape motion trail (shorter pill = longer trail, bigger circle = shorter trail)
 * - Colorblind-safe Okabe-Ito palette
 * - Re-fires cleanly per [trigger] change
 */
@Composable
fun ConfettiBurst(
    trigger: Boolean,
    modifier: Modifier = Modifier,
    particleCount: Int = 32,
    durationMs: Int = 1600,
    onFinished: () -> Unit = {},
) {
    if (!trigger) return

    // Extended Okabe-Ito palette with warm accents
    val palette = listOf(
        ChartBlue, ChartTeal, ChartAmber, ChartPurple,
        ErrorFuchsia, WarningAmber,
    )

    val particles = remember(trigger) {
        List(particleCount) {
            val shape = ParticleShape.entries[Random.nextInt(ParticleShape.entries.size)]
            ConfettiParticle(
                startXFraction = 0.1f + Random.nextFloat() * 0.8f,  // wider spread
                startYFraction = 0.25f + Random.nextFloat() * 0.1f,   // burst from 25-35%
                endYFraction = 0.7f + Random.nextFloat() * 0.3f,      // fall to 70-100%
                // Wider horizontal burst: bigger random kick left/right
                driftX = (Random.nextFloat() - 0.5f) * 0.55f,
                driftY = 0.5f + Random.nextFloat() * 0.5f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 1080f,
                sizePx = 5f + Random.nextFloat() * 8f,
                color = palette[Random.nextInt(palette.size)],
                initialRotation = Random.nextFloat() * 360f,
                shape = shape,
            )
        }
    }

    val animation = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) {
        animation.snapTo(0f)
        animation.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMs, easing = LinearOutSlowInEasing),
        )
        onFinished()
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val progress = animation.value
        val w = size.width
        val h = size.height

        particles.forEach { p ->
            // Quadratic gravity: v(t) = g*t² — natural acceleration
            val gravityY = 0.5f * h * progress * progress

            val x = (p.startXFraction + p.driftX * progress) * w
            val baseY = p.startYFraction * h + (p.endYFraction - p.startYFraction) * h * progress
            val y = baseY + gravityY * p.driftY

            val alpha = (1f - progress).coerceIn(0f, 1f)
            val rotationDeg = p.initialRotation + p.rotationSpeed * progress

            // Shape-dependent trail length: pills have longer trails
            val trailFraction = when (p.shape) {
                ParticleShape.PILL -> 0.08f
                ParticleShape.RECT -> 0.05f
                ParticleShape.CIRCLE -> 0.03f
            }
            val trailX = x - p.driftX * w * trailFraction
            val trailY = y - p.driftY * h * trailFraction

            rotate(degrees = rotationDeg, pivot = Offset(x, y)) {
                val color = p.color.copy(alpha = alpha)
                val trailColor = p.color.copy(alpha = alpha * 0.35f)
                val cx = x; val cy = y
                val s = p.sizePx

                // Draw trail first (behind main shape)
                when (p.shape) {
                    ParticleShape.CIRCLE -> {
                        drawCircle(color = trailColor, radius = s * 0.45f, center = Offset(trailX, trailY))
                        drawCircle(color = color, radius = s, center = Offset(cx, cy))
                    }
                    ParticleShape.RECT -> {
                        drawRoundRect(
                            color = trailColor,
                            topLeft = Offset(trailX - s * 0.35f, trailY - s * 0.35f),
                            size = Size(s * 0.7f, s * 0.7f),
                            cornerRadius = CornerRadius(s * 0.15f, s * 0.15f),
                        )
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(cx - s * 0.5f, cy - s * 0.5f),
                            size = Size(s, s),
                            cornerRadius = CornerRadius(s * 0.2f, s * 0.2f),
                        )
                    }
                    ParticleShape.PILL -> {
                        // Trail: short rounded rect
                        drawRoundRect(
                            color = trailColor,
                            topLeft = Offset(trailX - s * 0.25f, trailY - s * 0.12f),
                            size = Size(s * 0.5f, s * 0.24f),
                            cornerRadius = CornerRadius(s * 0.12f, s * 0.12f),
                        )
                        // Main: tall pill
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(cx - s * 0.35f, cy - s * 0.7f),
                            size = Size(s * 0.7f, s * 1.4f),
                            cornerRadius = CornerRadius(s * 0.35f, s * 0.35f),
                        )
                    }
                }
            }
        }
    }
}

private data class ConfettiParticle(
    val startXFraction: Float,
    val startYFraction: Float,
    val endYFraction: Float,
    val driftX: Float,
    val driftY: Float,
    val rotationSpeed: Float,
    val sizePx: Float,
    val color: Color,
    val initialRotation: Float,
    val shape: ParticleShape,
)
