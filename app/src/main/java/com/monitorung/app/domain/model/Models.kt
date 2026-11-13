package com.monitorung.app.domain.model

data class AppUsageLog(
    val id: Long = 0,
    val studentId: String,
    val logDate: String, // yyyy-MM-dd
    val logTimestamp: Long,
    val totalScreenTimeMinutes: Int,
    val socialMediaTimeMinutes: Int,
    val gameTimeMinutes: Int,
    val unlockCount: Int,
    val usedDuringClass: Boolean,
    val syncStatus: SyncStatus = SyncStatus.PENDING
)

/**
 * Day-level aggregate shown on HomeScreen — SUM of every collection row for
 * a single (studentId, date). The previous `HomeUiState.todayUsage` pointed
 * at `getRecent(3).firstOrNull()`, which is the LATEST 45-min interval delta,
 * so the user saw "screen time tidak sesuai" and "social media tidak terbaca"
 * even though the day's totals were healthy in the local DB.
 *
 * `logTimestamp` is set to "now" so the existing UI consumers don't see a
 * zero/stale date; `id` is zero because aggregates aren't rows.
 */
data class TodayAggregate(
    val studentId: String,
    val logDate: String,
    val logTimestamp: Long,
    val totalScreenTimeMinutes: Int,
    val socialMediaTimeMinutes: Int,
    val gameTimeMinutes: Int,
    val unlockCount: Int,
    val usedDuringClass: Boolean,
) {
    companion object {
        val EMPTY = TodayAggregate(
            studentId = "",
            logDate = "",
            logTimestamp = 0L,
            totalScreenTimeMinutes = 0,
            socialMediaTimeMinutes = 0,
            gameTimeMinutes = 0,
            unlockCount = 0,
            usedDuringClass = false,
        )
    }
}

/** 30-day aggregate for the HomeScreen summary card. */
data class ThirtyDayAggregate(
    val activeDays: Int = 0,
    val totalScreenTimeMinutes: Int = 0,
    val totalSocialMediaMinutes: Int = 0,
    val totalGameMinutes: Int = 0,
    val maxUnlockCount: Int = 0,
    val daysUsedDuringClass: Int = 0,
) {
    val avgScreenMinutes: Int get() = if (activeDays > 0) totalScreenTimeMinutes / activeDays else 0
    val avgSocialMinutes: Int get() = if (activeDays > 0) totalSocialMediaMinutes / activeDays else 0
    val avgGameMinutes: Int get() = if (activeDays > 0) totalGameMinutes / activeDays else 0
    companion object {
        val EMPTY = ThirtyDayAggregate()
    }
}

data class LocationLog(
    val id: Long = 0,
    val studentId: String,
    val logTimestamp: Long,
    val isOnCampus: Boolean,
    val matchesSchedule: Boolean,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    /**
     * Coordinates at the time of the geofence check. Null when the device
     * returned a null fix (indoor, GPS off, no permission). HomeScreen
     * shows the latest non-null lat/lon to confirm the location pipeline
     * is running.
     */
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class Schedule(
    val id: String,
    val studentId: String,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val campusId: String?,
    val room: String?,
    val subjectNameRaw: String?,
    val lecturerNameRaw: String?,
    val subjectId: String?,
    val lecturerId: String?,
    val isActive: Boolean,
    val subjectName: String?,
    val lecturerName: String?
) {
    val courseName: String get() = subjectName ?: subjectNameRaw ?: ""
    val lecturerDisplay: String get() = lecturerName ?: lecturerNameRaw ?: ""
}

data class User(
    val studentId: String,
    val name: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val tokenExpiresAt: Long,
    val phone: String = "",
    val avatarUrl: String = "",
    val bio: String = "",
    val role: String = "mahasiswa",
)

enum class SyncStatus {
    PENDING, SYNCED, FAILED
}

data class SyncState(
    val totalCollectedToday: Int = 0,
    val totalSynced: Int = 0,
    val pendingCount: Int = 0,
    val lastSyncTimestamp: Long? = null,
    val isMqttConnected: Boolean = false,
    val batteryLevel: Int = 100,
    val isSyncing: Boolean = false,
)
