package com.monitorung.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.monitorung.app.service.SmartMonitoringService
import com.monitorung.app.util.SecurePrefs
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            // Only restart the foreground service if a session is persisted. Otherwise
            // startForegroundService → onStartCommand → stopSelf runs in <5s, which on
            // Android 12+ triggers ForegroundServiceDidNotStartInTimeException.
            val sessionPrefs = SecurePrefs.get(context, "monitor_session_prefs")
            if (sessionPrefs.getString("sid", null).isNullOrEmpty()) return

            // Wave 3 fix #13: also guard against an already-ended class. Without
            // this, a device that reboots AFTER end_ts (but before the service
            // ran its end-of-class cleanup) starts the service, hits stopSelf(),
            // and crashes with ForegroundServiceDidNotStartInTimeException.
            val endTs = sessionPrefs.getLong("end_ts", 0)
            if (endTs > 0 && System.currentTimeMillis() >= endTs) return

            // Use the session-aware SmartMonitoringService so an active class session is
            // restored from SharedPreferences after reboot. (MonitoringService is a static
            // notification-only stub and would not pick up the persisted session state.)
            val serviceIntent = Intent(context, SmartMonitoringService::class.java).apply {
                action = SmartMonitoringService.ACTION_START
            }

            context.startForegroundService(serviceIntent)
        }
    }
}
