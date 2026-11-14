package com.monitorung.app.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    gradient: Brush,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    PaperCard(modifier = modifier, padding = 0.dp, onClick = onClick) {
        // A light wash over the theme surface keeps dark/AMOLED text readable.
        Column(Modifier.drawBehind { drawRect(gradient, alpha = 0.12f) }.padding(padding)) {
            content()
        }
    }
}
