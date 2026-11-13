package com.monitorung.app.service

import androidx.core.content.edit
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import com.monitorung.app.data.push.MqttPushClient
import com.monitorung.app.domain.model.ClassSession
import com.monitorung.app.domain.model.MonitoringState
import com.monitorung.app.util.SecurePrefs
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SmartMonitoringService : Service() {

    @Inject
    lateinit var monitoringManager: SmartMonitoringManager

    @Inject
    lateinit var batterySotManager: BatteryBasedSotManager

    @Inject
    lateinit var mqttPushClient: MqttPushClient

    companion object {
        const val ACTION_START = "com.monitorung.app.START_SMART_SERVICE"
        const val ACTION_STOP = "com.monitorung.app.STOP_SMART_SERVICE"
        private const val SESSION_PREFS = "monitor_session_prefs"
        private const val KEY_SID = "sid"
        private const val KEY_STATE = "state"
        private const val KEY_END_TS = "end_ts"
        private const val KEY_START_TS = "start_ts"
        private const val KEY_COURSE = "course"
        private const val KEY_ROOM = "room"
        private const val KEY_DOW = "dow"
        private const val KEY_START_TIME = "start_time"
        private const val KEY_END_TIME = "end_time"
    }

    private val sessionPrefs: SharedPreferences by lazy {
        // Persisted via EncryptedSharedPreferences — class schedule (course
        // name, room, time) is sensitive enough to deserve the same treatment
        // as auth tokens. See SecurePrefs.kt for the AES256-GCM scheme.
        SecurePrefs.get(applicationContext, SESSION_PREFS)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                monitoringManager.stopMonitoring()
                mqttPushClient.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                // MQTT push is wired in parallel with the local monitoring loop
                // — the broker URL was already fetched by CampusConfigRepository
                // when the user logged in (see LoginViewModel). We only need the
                // `start()` hook to spin up the subscriber; lifecycle cleanup
                // happens in `onDestroy()`.
                mqttPushClient.start(applicationContext)
                val savedSession = getSavedSession()
                if (savedSession != null) {
                    val state = try {
                        MonitoringState.valueOf(
                            sessionPrefs.getString(KEY_STATE, "IN_CLASS") ?: "IN_CLASS"
                        )
                    } catch (e: Exception) {
                        MonitoringState.IN_CLASS
                    }
                    if (state == MonitoringState.OUTSIDE_CLASS) {
                        monitoringManager.setOutsideClass(savedSession)
                    } else {
                        monitoringManager.startClassMonitoring(savedSession)
                    }
                    // Promote this Service to real foreground using the notification the manager just built.
                    // Without this call Android (API 26+) kills the service after the startForegroundService() deadline.
                    monitoringManager.currentNotification.value?.let { notification ->
                        startForeground(SmartMonitoringManager.NOTIFICATION_ID_FOREGROUND, notification)
                    }
                } else {
                    // No active session to restore — release any pending foreground anchor and exit.
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Restart service after user swipes app away — session state is persisted
        // via SmartMonitoringManager and will be restored in onStartCommand.
        // Guard first: without an active session the restart calls stopSelf() in
        // <5s, which on Android 12+ throws ForegroundServiceDidNotStartInTime
        // and crashes the app. Same guard BootReceiver already uses.
        if (getSavedSession() == null) {
            super.onTaskRemoved(rootIntent)
            return
        }
        val restartIntent = Intent(applicationContext, SmartMonitoringService::class.java).apply {
            action = ACTION_START
        }

        startForegroundService(restartIntent)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // Tear down ancillary managers so their BroadcastReceivers are
        // unregistered and coroutine scopes cancelled. Without this, the
        // receivers stay registered for the lifetime of the process and
        // could continue firing after the service is "stopped" — wasting
        // battery on a class session that already ended.
        monitoringManager.destroy()
        batterySotManager.destroy()
        // MQTT push lives for the service lifetime. `stop()` disconnects and
        // halts the supervisor scope — a new `start()` (e.g. boot restart)
        // will re-establish the connection with fresh credentials.
        mqttPushClient.stop()
        super.onDestroy()
    }

    private fun getSavedSession(): ClassSession? {
        val scheduleId = sessionPrefs.getString(KEY_SID, null) ?: return null
        val endTs = sessionPrefs.getLong(KEY_END_TS, 0L)
        if (endTs > 0 && System.currentTimeMillis() >= endTs) {
            sessionPrefs.edit {
                clear()
            }
            // The class window already closed. Clear the foreground notification —
            // otherwise it stays visible after stopSelf() because the manager's
            // _currentNotification was set by the previous startClassMonitoring().
            monitoringManager.clearForegroundNotification()
            return null
        }
        return ClassSession(
            scheduleId = scheduleId,
            startTimestamp = sessionPrefs.getLong(KEY_START_TS, 0L),
            endTimestamp = endTs,
            courseName = sessionPrefs.getString(KEY_COURSE, "") ?: "",
            room = sessionPrefs.getString(KEY_ROOM, "") ?: "",
            dayOfWeek = sessionPrefs.getInt(KEY_DOW, 1),
            startTime = sessionPrefs.getString(KEY_START_TIME, "") ?: "",
            endTime = sessionPrefs.getString(KEY_END_TIME, "") ?: "",
        )
    }
}
