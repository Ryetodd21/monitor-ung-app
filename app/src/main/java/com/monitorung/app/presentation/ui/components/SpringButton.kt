package com.monitorung.app.presentation.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.monitorung.app.presentation.ui.theme.GradientBlueTeal
import com.monitorung.app.presentation.ui.theme.RadiusL

// ─── Shared spring spec tokens ────────────────────────────────────────────────

/** Spring for button press — fast press-in, springy release with overshoot. */
private val PressSpring = spring<Float>(
    dampingRatio = 0.4f,
    stiffness = 850f,
)

/** Spring for button release — gentle settle with subtle bounce. */
private val ReleaseSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessLow,
)

/**
 * Reusable press/release spring scale animation.
 * Press: snap to `pressedScale` quickly.
 * Release: spring to 1f with bounce overshoot (Animatable's natural behavior).
 *
 * Returns the current scale [Float] to be applied via [Modifier.scale].
 */
@Composable
private fun rememberPressScale(
    pressedScale: Float = 0.94f,
): Animatable<Float, *> {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            scale.animateTo(pressedScale, animationSpec = PressSpring)
        } else {
            scale.animateTo(1f, animationSpec = ReleaseSpring)
        }
    }

    return scale
}

// ─── Solid Spring Button ──────────────────────────────────────────────────────

/**
 * papercard-style bouncy button — scales down on press and springs back when released.
 * Pairs with [rememberHapticFeedback] for tap feedback.
 *
 * The release animation uses a low-damping-ratio spring so the button slightly
 * overshoots 1.0x (≈1.04x) before settling, giving it that satisfying squish-back feel.
 */
@Composable
fun SpringButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = RadiusL,
    pressedScale: Float = 0.94f,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            scale.animateTo(pressedScale, animationSpec = PressSpring)
        } else {
            scale.animateTo(1f, animationSpec = ReleaseSpring)
        }
    }

    val bgModifier = if (enabled) {
        Modifier
            .scale(scale.value)
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
    } else {
        Modifier
            .scale(scale.value)
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor.copy(alpha = 0.4f))
    }

    RowContentContainer(
        modifier = modifier.then(bgModifier),
        contentColor = contentColor,
        content = content,
    )
}

// ─── Gradient Spring Button ────────────────────────────────────────────────────

/**
 * Bouncy button with gradient background — papercard-style celebration CTA.
 * Used for login button, achievement unlocks, daily challenge completion.
 *
 * @param gradient Gradient brush for the button background
 * @param contentColor Color of text/icon inside the button
 * @param pressedScale How far the button compresses on press (default 0.94x)
 */
@Composable
fun GradientSpringButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    gradient: Brush = Brush.horizontalGradient(GradientBlueTeal),
    contentColor: Color = Color.White,
    cornerRadius: Dp = RadiusL,
    pressedScale: Float = 0.94f,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            scale.animateTo(pressedScale, animationSpec = PressSpring)
        } else {
            scale.animateTo(1f, animationSpec = ReleaseSpring)
        }
    }

    val bgModifier = if (enabled) {
        Modifier
            .scale(scale.value)
            .clip(RoundedCornerShape(cornerRadius))
            .background(gradient)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
    } else {
        Modifier
            .scale(scale.value)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.Gray.copy(alpha = 0.4f))
    }

    Box(
        modifier = modifier.then(bgModifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) contentColor else contentColor.copy(alpha = 0.4f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
        )
    }
}

// ─── Primary convenience button ─────────────────────────────────────────────────

/**
 * Convenience: bouncy primary CTA button with text label.
 */
@Composable
fun SpringPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SpringButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ─── Row content helper ────────────────────────────────────────────────────────

@Composable
private fun RowContentContainer(
    modifier: Modifier,
    contentColor: Color,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides contentColor,
        ) {
            content()
        }
    }
}
