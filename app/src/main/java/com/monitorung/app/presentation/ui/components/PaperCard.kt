package com.monitorung.app.presentation.ui.components

import com.monitorung.app.presentation.ui.theme.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * papercard-style solid paper card. Replaces the old GlassCard which used
 * frosted blur plus gradient border. PaperCard is a flat surface with a single
 * hairline border, sitting on the page background like paper.
 *
 * Gamified update: corners are now RadiusPlayful (28dp) for a softer, friendlier
 * feel. Shadow is lifted to 6dp for a "floating" effect that matches the playful
 * papercard aesthetic. Border is still a 1dp hairline.
 *
 * - color   = MaterialTheme.colorScheme.surface (theme-aware: light, dark, AMOLED)
 * - border  = 1dp hairline outlineVariant (no gradient, no glow)
 * - shape   = RadiusPlayful (28dp rounded — softer than the old 20dp)
 * - shadow  = 6dp warm elevation (slightly lifted for floating effect)
 *
 * Pass onClick to make it interactive. The padding parameter wraps the content
 * in a Column with that much inner padding. Pass padding = 0.dp when your
 * content already provides its own padding.
 */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(RadiusPlayful)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val inner: @Composable () -> Unit = {
        if (padding > SpaceS) {
            Column(modifier = Modifier.padding(padding)) { content() }
        } else {
            content()
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
            border = border,
            content = inner,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
            border = border,
            content = inner,
        )
    }
}
