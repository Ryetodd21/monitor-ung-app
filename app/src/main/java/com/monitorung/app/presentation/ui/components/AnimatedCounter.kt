package com.monitorung.app.presentation.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

/** Show the first measurement immediately; ease subsequent changes without overshoot. */
@Composable
fun AnimatedCounter(
    targetValue: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    val animated = remember { Animatable(targetValue.toFloat()) }
    LaunchedEffect(targetValue) {
        animated.animateTo(targetValue.toFloat(), tween(320, easing = FastOutSlowInEasing))
    }

    Text(
        text = animated.value.toInt().toString(),
        modifier = modifier,
        style = style,
    )
}
