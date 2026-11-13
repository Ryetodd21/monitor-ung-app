package com.monitorung.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.monitorung.app.data.lock.UnlockTracker

/**
 * Receives system screen-unlock broadcasts and forwards each one to [UnlockTracker].
 *
 * Registered dynamically in `MonitorUNGApplication.onCreate()` rather than the manifest —
 * `ACTION_SCREEN_ON` is only delivered to runtime-registered receivers on Android 7+.
 *
 * Not annotated with @AndroidEntryPoint: this receiver is instantiated manually by the
 * Application at runtime and given its UnlockTracker dependency directly.
 */
class UnlockReceiver(private val unlockTracker: UnlockTracker) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_SCREEN_ON -> unlockTracker.recordUnlock()
        }
    }
}
