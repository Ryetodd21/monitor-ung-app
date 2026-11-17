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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Satu-satunya permukaan kartu di aplikasi: bentuk [RadiusPlayful], border 1 dp,
 * elevasi 1 dp, warna dari tema. [containerColor]/[borderColor] hanya untuk
 * kartu callout berwarna; keduanya default ke token tema agar tampilan seragam.
 * `padding = 0.dp` membuat konten mengatur spacing sendiri.
 */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    containerColor: Color? = null,
    borderColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(RadiusPlayful)
    val border = BorderStroke(1.dp, borderColor ?: MaterialTheme.colorScheme.outlineVariant)
    val color = containerColor ?: MaterialTheme.colorScheme.surface
    val inner: @Composable () -> Unit = {
        if (padding > 0.dp) {
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
            color = color,
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
            border = border,
            content = inner,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = color,
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
            border = border,
            content = inner,
        )
    }
}
