package com.monitorung.app.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.monitorung.app.presentation.ui.theme.*

/**
 * GradientCard — PaperCard variant with a gradient background.
 *
 * Uses shadow + border trick: an outer shadow/clip container wraps an inner
 * gradient box so we get both the gradient AND the border/shadow simultaneously.
 *
 * - bg: pastel gradient fill
 * - border: hairline outlineVariant (or gradient border when gradientBorder set)
 * - shadow: 6dp ambient
 * - shape: RadiusPlayful (28dp)
 *
 * @param gradient Gradient brush for the card background
 * @param gradientBorder Optional gradient brush for the border (null = outlineVariant)
 */
@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    gradient: Brush,
    gradientBorder: Brush? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(RadiusPlayful)
    val border = if (gradientBorder != null) {
        BorderStroke(2.dp, gradientBorder)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    // Shadow container wraps gradient container (so shadow follows rounded shape)
    Box(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = shape,
                ambientColor = SoftShadowColorLight,
                spotColor = SoftShadowColorLight,
            )
            .clip(shape)
    ) {
        // Gradient background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
        ) {}

        // Border overlay
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = shape,
            color = Color.Transparent,
            border = border,
            onClick = onClick ?: {},
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (padding > SpaceS) {
                            Modifier.padding(padding)
                        } else {
                            Modifier
                        }
                    ),
            ) {
                content()
            }
        }
    }
}
