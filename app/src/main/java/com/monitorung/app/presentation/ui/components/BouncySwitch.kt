package com.monitorung.app.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monitorung.app.presentation.ui.theme.ChartTeal
import com.monitorung.app.presentation.ui.theme.PrimaryBlue
import com.monitorung.app.presentation.ui.theme.RadiusPill

/**
 * A spring-animated toggle switch with a satisfying bouncy feel.
 *
 * Uses two independent springs:
 *   - Thumb: DampingRatioMediumBouncy → natural overshoot when toggling
 *   - Track:  smooth crossfade between off/on background colors
 *
 * No coerceIn hacks — spring naturally limits range through stiffness + damping.
 */
@Composable
fun BouncySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val interaction = remember { MutableInteractionSource() }

    // Track color crossfade — smooth Material-style color transition
    val trackColor by animateColorAsState(
        targetValue = if (checked) ChartTeal else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "track_color",
    )

    val trackWidth = 52.dp
    val thumbSize = 24.dp
    val padding = 2.dp
    val travelPx = (trackWidth - thumbSize - padding * 2).value
    val thumbOffsetX by animateFloatAsState(
        targetValue = if (checked) travelPx else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "thumb_offset",
    )

    // Track press feedback: subtle scale press on click
    val trackScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessHigh),
        label = "track_scale",
    )

    val contentColor = if (checked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val alpha = if (enabled) 1f else 0.5f

    Row(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch,
                interactionSource = interaction, indication = null, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(trackWidth)
                .scale(trackScale),
            contentAlignment = Alignment.CenterStart,
        ) {
            // Track background
            Box(
                modifier = Modifier
                    .size(width = trackWidth, height = 30.dp)
                    .shadow(2.dp, RoundedCornerShape(RadiusPill))
                    .clip(RoundedCornerShape(RadiusPill))
                    .background(trackColor.copy(alpha = alpha)),
            )

            // Thumb — spring-animated X offset, no coerceIn
            Box(
                modifier = Modifier
                    .padding(start = padding + thumbOffsetX.dp, top = padding, bottom = padding)
                    .size(thumbSize)
                    .shadow(3.dp, CircleShape)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = alpha)),
            )
        }

        if (label != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = labelColor.copy(alpha = alpha),
                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
