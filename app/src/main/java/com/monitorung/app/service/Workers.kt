package com.monitorung.app.service

import androidx.core.content.edit
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.monitorung.app.domain.model.ClassSession
import com.monitorung.app.util.AppConstants
import com.monitorung.app.util.SecurePrefs
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class MorningNotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val monitoringManager: SmartMonitoringManager,
    private val notificationScheduler: NotificationScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        monitoringManager.showMorningNotification()
        notificationScheduler.scheduleMorningNotification()
        return Result.success()
    }
}

@HiltWorker
class ClassReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val monitoringManager: SmartMonitoringManager,
    private val notificationScheduler: NotificationScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val scheduleId = inputData.getString("schedule_id") ?: return Result.failure()
        val session = ClassSession(
            scheduleId = scheduleId,
            courseName = inputData.getString("course_name") ?: "",
            room = inputData.getString("room") ?: "",
            dayOfWeek = inputData.getInt("day_of_week", 1),
            startTime = inputData.getString("start_time") ?: "",
            endTime = inputData.getString("end_time") ?: "",
            startTimestamp = inputData.getLong("start_timestamp", System.currentTimeMillis()),
            endTimestamp = inputData.getLong("end_timestamp", System.currentTimeMillis()),
        )
        monitoringManager.showClassReminderNotification(session)
        // One-shot work never repeats. Re-arm for next week before returning —
        // minDaysAhead=7 because "next occurrence" right now resolves to today.
        notificationScheduler.scheduleClassReminder(session, minDaysAhead = 7)
        return Result.success()
    }
}

@HiltWorker
class ClassStartWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val monitoringManager: SmartMonitoringManager,
    private val notificationScheduler: NotificationScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val scheduleId = inputData.getString("schedule_id") ?: return Result.failure()
        val session = ClassSession(
            scheduleId = scheduleId,
            courseName = inputData.getString("course_name") ?: "",
            room = inputData.getString("room") ?: "",
            dayOfWeek = inputData.getInt("day_of_week", 1),
            startTime = inputData.getString("start_time") ?: "",
            endTime = inputData.getString("end_time") ?: "",
            startTimestamp = inputData.getLong("start_timestamp", System.currentTimeMillis()),
            endTimestamp = inputData.getLong("end_timestamp", System.currentTimeMillis()),
        )
        monitoringManager.startClassMonitoring(session)
        // Re-arm for next week — see ClassReminderWorker.
        notificationScheduler.scheduleClassStart(session, minDaysAhead = 7)
        return Result.success()
    }
}

@HiltWorker
class ClassEndWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val monitoringManager: SmartMonitoringManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val session = ClassSession(
            scheduleId = inputData.getString("schedule_id") ?: return Result.failure(),
            courseName = inputData.getString("course_name") ?: "",
            room = inputData.getString("room") ?: "",
            dayOfWeek = inputData.getInt("day_of_week", 1),
            startTime = inputData.getString("start_time") ?: "",
            endTime = inputData.getString("end_time") ?: "",
            startTimestamp = inputData.getLong("start_timestamp", 0),
            endTimestamp = inputData.getLong("end_timestamp", 0),
        )
        return if (monitoringManager.completeClass(session)) Result.success()
        else if (runAttemptCount >= AppConstants.MAX_WORKER_RETRY_ATTEMPTS) Result.failure()
        else Result.retry()
    }
}

@HiltWorker
class SyncReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val monitoringManager: SmartMonitoringManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pendingCount = inputData.getInt("pending_count", 0)
        monitoringManager.showSyncReminderNotification(pendingCount, minutesUntil22 = 15)
        return Result.success()
    }
}

@HiltWorker
class AutoSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dataSyncManager: DataSyncManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // EncryptedSharedPreferences — same scheme as auth/session prefs.
        // The `auto_sync_enabled` flag isn't sensitive on its own but this
        // brings `monitor_prefs` in line with the rest of the storage stack.
        val prefs = SecurePrefs.get(applicationContext, "monitor_prefs")
        val autoSync = prefs.getBoolean("auto_sync_enabled", true)
        if (!autoSync) return Result.success()
        dataSyncManager.scheduleAutoSync()
        return if (dataSyncManager.syncPendingData()) Result.success()
        else if (runAttemptCount >= AppConstants.MAX_WORKER_RETRY_ATTEMPTS) Result.failure()
        else Result.retry()
    }
}

@HiltWorker
class DataSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dataSyncManager: DataSyncManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = dataSyncManager.syncPendingData()
        // FIX H2: cap retries at 3 to prevent infinite loop on persistent failure.
        // Without this, a server outage or malformed response causes WorkManager
        // to retry forever (~5h exponential backoff cap), draining battery.
        return if (result) Result.success() else {
            if (runAttemptCount >= AppConstants.MAX_WORKER_RETRY_ATTEMPTS) Result.failure() else Result.retry()
        }
    }
}

@HiltWorker
class MonitoringKeepAliveWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Read the session prefs directly — SmartMonitoringService.onStartCommand
        // does the same check and will restore the monitoring cycle from there.
        // Starting the service wakes it up if the OS killed it (e.g. after 1 day
        // of the app not being opened). onTaskRemoved() already handles the
        // swiped-away case; this worker handles the system-killed case.
        val sessionPrefs = SecurePrefs.get(applicationContext, "monitor_session_prefs")
        val scheduleId = sessionPrefs.getString("sid", null)
        val endTs = sessionPrefs.getLong("end_ts", 0L)

        if (scheduleId.isNullOrEmpty()) {
            // No active session — nothing to restore
            return Result.success()
        }
        if (endTs > 0 && System.currentTimeMillis() >= endTs) {
            // Session has already ended — clear prefs so we don't keep waking the service
            sessionPrefs.edit {
                clear()
            }
            return Result.success()
        }

        // Session is active — start/restart the service so it restores monitoring.
        // START_STICKY tells the OS to recreate the service if it was killed.
        val intent = android.content.Intent(applicationContext, SmartMonitoringService::class.java).apply {
            action = SmartMonitoringService.ACTION_START
        }

        applicationContext.startForegroundService(intent)
        return Result.success()
    }
}

/**
 * Background SOT collector — runs every 4 hours when class is NOT in session.
 * Uses BatteryBasedSotManager for lightweight collection (screen-on/off tracking
 * + targeted app usage for sosmed/games only).
 * This supplements class-hour monitoring with data from outside class windows.
 */
@HiltWorker
class BackgroundSotWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val batteryBasedSotManager: BatteryBasedSotManager,
    private val scheduleRepository: com.monitorung.app.domain.repository.ScheduleRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Usage is sent only by SmartMonitoringManager during an active class.
        return Result.success()

        // Battery check: skip if critically low battery
        val batteryLevel = getBatteryLevel()
        val isCharging = isCharging()
        if (batteryLevel < 15 && !isCharging) {
            // FIX H2: cap retries at 3 — battery condition may persist indefinitely
            // (device unplugged). Don't hammer WorkManager forever.
            return if (runAttemptCount >= AppConstants.MAX_WORKER_RETRY_ATTEMPTS) Result.success() else Result.retry()
        }

        return try {
            batteryBasedSotManager.collectAndPersistOutsideClass()
            Result.success()
        } catch (e: Exception) {
            // FIX H2: cap retries at 3 to prevent infinite loop on DB failures.
            if (runAttemptCount >= AppConstants.MAX_WORKER_RETRY_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    private fun getBatteryLevel(): Int {
        return try {
            val intent = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED).let {
                applicationContext.registerReceiver(null, it)
            }
            val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) level * 100 / scale else 100
        } catch (e: Exception) { 100 }
    }

    private fun isCharging(): Boolean {
        return try {
            val intent = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED).let {
                applicationContext.registerReceiver(null, it)
            }
            val status = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) { false }
    }
}
