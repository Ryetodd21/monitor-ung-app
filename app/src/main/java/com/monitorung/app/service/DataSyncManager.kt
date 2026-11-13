package com.monitorung.app.service

import androidx.core.content.edit
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import androidx.work.*
import com.monitorung.app.data.local.dao.AppUsageLogDao
import com.monitorung.app.data.local.dao.LocationLogDao
import com.monitorung.app.data.remote.api.MonitorUNGApi
import com.monitorung.app.data.remote.dto.BatchLogRequest
import com.monitorung.app.domain.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataSyncManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: MonitorUNGApi,
    private val authRepository: AuthRepository,
    private val appUsageLogDao: AppUsageLogDao,
    private val locationLogDao: LocationLogDao,
    private val notificationScheduler: NotificationScheduler,
) {
    private val workManager = WorkManager.getInstance(context)

    companion object {
        private const val SYNC_WORK_NAME = "data_sync_pending"
        // Battery thresholds — sync only proceeds if battery >= this level
        private const val BATTERY_THRESHOLD_LOW = 20
        // Sync only proceeds if battery is above threshold, unless charging
        private const val BATTERY_THRESHOLD_NORMAL = 15
    }

    /**
     * Sync all pending data. If not within 22:00-04:00 WITA window,
     * data stays queued. If no internet, queues a network-constrained work.
     * If battery is critically low (< 15%) and device is not charging, defers sync.
     */
    suspend fun syncPendingData(): Boolean {
        val now = LocalTime.now(ZoneId.of("Asia/Makassar"))
        if (now.isBefore(LocalTime.of(22, 0)) && !now.isBefore(LocalTime.of(4, 0))) {
            scheduleAutoSync()
            return true // Deferred to nightly work, not a failed network attempt.
        }

        if (!hasInternet()) {
            scheduleNetworkConstrainedSync()
            return false
        }

        // Battery-aware check: skip sync if battery is critically low and not charging
        val batteryLevel = getBatteryLevel()
        val isCharging = isCharging()
        if (batteryLevel < BATTERY_THRESHOLD_LOW && !isCharging) {
            // Defer sync — device is low on battery and not charging
            scheduleBatteryDeferredSync()
            return false
        }

        return try {
            val token = authRepository.getAccessToken() ?: return false
            val studentId = authRepository.getCurrentUser().first()?.studentId ?: return false
            val pendingUsage = appUsageLogDao.getPending(studentId, 100)
            val pendingLocation = locationLogDao.getPending(studentId, 100)

            if (pendingUsage.isEmpty() && pendingLocation.isEmpty()) {
                return true
            }

            val deviceId = getDeviceId()
            val idempotencyKey = UUID.randomUUID().toString()

            val request = BatchLogRequest(
                deviceId = deviceId,
                idempotencyKey = idempotencyKey,
                appUsageLogs = pendingUsage.map { it.toDto() },
                locationLogs = pendingLocation.map { it.toDto() }
            )

            val response = api.postBatchLogs("Bearer $token", request)
            if (response.isSuccessful) {
                val body = response.body() ?: return false
                val syncedIds = body.syncedClientIds.toSet()
                appUsageLogDao.markSynced(pendingUsage.filter { "usage:${it.id}" in syncedIds })
                locationLogDao.markSynced(pendingLocation.filter { "location:${it.id}" in syncedIds }.map { it.id })
                context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE).edit {
                    putLong("last_sync_${studentId}", System.currentTimeMillis())
                }
                appUsageLogDao.getPendingCount(studentId) == 0 && locationLogDao.getPendingCount(studentId) == 0
            } else if (response.code() == 401) {
                // A transient refresh failure also returns the original 401; keep nightly work.
                if (authRepository.getRefreshToken() == null) cancelAutoSync()
                false
            } else {
                false
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            scheduleNetworkConstrainedSync()
            false
        }
    }

    private fun getDeviceId(): String {
        return com.monitorung.app.util.installationId(context)
    }

    /** Called when network becomes available. */
    fun onNetworkAvailable() {
        WorkManager.getInstance(context)
            .pruneWork()
    }

    /**
     * Network-only sync — no battery constraint since WorkManager will
     * respect system battery-saver mode automatically.
     */
    fun scheduleNetworkConstrainedSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work = OneTimeWorkRequestBuilder<DataSyncWorker>()
            .setConstraints(constraints)
            .addTag("data_sync")
            .build()

        workManager.enqueueUniqueWork(
            SYNC_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            work
        )
    }

    /**
     * Battery-deferred sync: only runs when battery is NOT low.
     * WorkManager automatically skips this when system battery-saver is active.
     */
    private fun scheduleBatteryDeferredSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val work = OneTimeWorkRequestBuilder<DataSyncWorker>()
            .setConstraints(constraints)
            .addTag("data_sync_battery_aware")
            .build()

        workManager.enqueueUniqueWork(
            "data_sync_battery_deferred",
            ExistingWorkPolicy.KEEP,
            work
        )
    }

    fun scheduleSyncReminder(pendingCount: Int) {
        notificationScheduler.scheduleSyncReminder(pendingCount)
    }

    fun scheduleAutoSync() = notificationScheduler.scheduleAutoSync()

    fun cancelAutoSync() {
        workManager.cancelUniqueWork(SYNC_WORK_NAME)
        workManager.cancelUniqueWork("data_sync_battery_deferred")
        workManager.cancelUniqueWork(NotificationScheduler.WORK_SYNC_SEND)
        workManager.cancelAllWorkByTag(NotificationScheduler.WORK_SYNC_SEND)
    }

    private fun hasInternet(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Returns battery percentage (0-100). Returns 100 if unable to determine.
     */
    private fun getBatteryLevel(): Int {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                (level * 100 / scale)
            } else 100
        } catch (e: Exception) {
            100 // Default to safe value
        }
    }

    private fun isCharging(): Boolean {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            false
        }
    }

    private fun com.monitorung.app.data.local.entity.AppUsageLogEntity.toDto() =
        com.monitorung.app.data.remote.dto.AppUsageLogDto(
            clientId = "usage:$id",
            logDate = logDate,
            totalScreenTimeMinutes = totalScreenTimeMinutes,
            socialMediaTimeMinutes = socialMediaTimeMinutes,
            gameTimeMinutes = gameTimeMinutes,
            otherAppTimeMinutes = otherAppTimeMinutes,
            unlockCount = unlockCount,
            usedDuringClass = usedDuringClass,
            scheduleId = scheduleId
        )

    private fun com.monitorung.app.data.local.entity.LocationLogEntity.toDto() =
        com.monitorung.app.data.remote.dto.LocationLogDto(
            clientId = "location:$id",
            recordedAt = recordedAt,
            latitude = latitude ?: 0.0,
            longitude = longitude ?: 0.0,
            campusId = campusId,
            isOnCampus = isOnCampus,
            matchesSchedule = matchesSchedule,
            scheduleId = scheduleId
        )
}
