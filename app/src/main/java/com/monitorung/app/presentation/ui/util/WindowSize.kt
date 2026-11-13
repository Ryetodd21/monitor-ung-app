package com.monitorung.app.presentation.ui.util

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

/**
 * Device form factor detection for responsive layouts.
 */
enum class DeviceFormFactor {
    PHONE,           // compact width < 600dp
    TABLET_PORTRAIT, // medium width 600-840dp
    TABLET_LANDSCAPE, // expanded width >= 840dp
    FOLDABLE,        // special case, treat as expanded
}

fun WindowSizeClass.formFactor(): DeviceFormFactor = when (widthSizeClass) {
    WindowWidthSizeClass.Compact -> DeviceFormFactor.PHONE
    WindowWidthSizeClass.Medium -> DeviceFormFactor.TABLET_PORTRAIT
    WindowWidthSizeClass.Expanded -> DeviceFormFactor.TABLET_LANDSCAPE
    else -> DeviceFormFactor.PHONE
}

/**
 * Returns the number of grid columns for current form factor.
 */
fun DeviceFormFactor.gridColumns(): Int = when (this) {
    DeviceFormFactor.PHONE -> 1
    DeviceFormFactor.TABLET_PORTRAIT -> 2
    DeviceFormFactor.TABLET_LANDSCAPE -> 3
    DeviceFormFactor.FOLDABLE -> 2
}

/**
 * Returns the content max width for centering layouts on large screens.
 */
fun DeviceFormFactor.contentMaxWidthDp(): Int = when (this) {
    DeviceFormFactor.PHONE -> Int.MAX_VALUE
    DeviceFormFactor.TABLET_PORTRAIT -> 720
    DeviceFormFactor.TABLET_LANDSCAPE -> 960
    DeviceFormFactor.FOLDABLE -> 600
}
