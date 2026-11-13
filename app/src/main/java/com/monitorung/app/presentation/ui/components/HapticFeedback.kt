package com.monitorung.app.presentation.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * papercard-style haptic feedback wrapper. Tap anything important and the device will
 * buzz with the right intensity — light taps for navigation, success for milestones,
 * error for failed actions.
 */
class HapticFeedback(private val view: View) {
    fun lightTap() {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun mediumTick() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun success() {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30)
            HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun error() {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30)
            HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
    }
}

@Composable
fun rememberHapticFeedback(): HapticFeedback {
    val view = LocalView.current
    return remember(view) { HapticFeedback(view) }
}
