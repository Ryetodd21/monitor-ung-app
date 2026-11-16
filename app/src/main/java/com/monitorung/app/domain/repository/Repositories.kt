package com.monitorung.app.domain.repository

import com.monitorung.app.domain.model.*
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(nim: String, password: String): Result<User>
    suspend fun autoLoginWithSavedCredentials(): Result<User>
    fun getSavedCredentials(): Pair<String, String>?
    suspend fun refreshToken(): Result<String>
    suspend fun logout()
    fun isLoggedIn(): Flow<Boolean>
    fun getCurrentUser(): Flow<User?>
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    suspend fun offlineAccessExpiresAt(): Long?
    suspend fun fetchProfile(): Result<User>
    suspend fun updateProfile(
        fullName: String?,
        email: String?,
        phone: String?,
        avatarUrl: String?,
        bio: String?
    ): Result<User>
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>
    suspend fun setBiometricEnabled(enabled: Boolean)
    fun isBiometricEnabled(): Flow<Boolean>
    /** Cancel background coroutine scopes. Call when repository is being destroyed. */
    fun close()
}

interface ScheduleRepository {
    suspend fun fetchSchedules(): Result<List<Schedule>>
    /** Every stored schedule for the signed-in student, ordered by day then start time. */
    fun getAllSchedules(): Flow<List<Schedule>>
    fun getSchedulesByDay(dayOfWeek: Int): Flow<List<Schedule>>
    suspend fun getCurrentClass(): Schedule?
    suspend fun isCurrentlyInClass(): Boolean
    suspend fun getDirectory(): Result<Pair<List<SubjectItem>, List<LecturerItem>>>
    suspend fun deleteSchedule(scheduleId: String): Result<Unit>
}

data class SubjectItem(
    val id: String,
    val name: String,
    val code: String?,
    // Lokasi kampus mata kuliah (di-bridge dari tabel mata_kuliah.campus_id
    // lewat routers/mata_kuliah._get_or_create_subject). Nullable karena
    // legacy Subject rows yang di-seed sebelum round 17 tidak punya campus_id.
    val campusId: String? = null,
    val campusName: String? = null,
)

data class LecturerItem(
    val id: String,
    val name: String,
    val nidn: String?
)

interface UsageRepository {
    suspend fun collectAppUsage(): Result<AppUsageLog>
    fun getRecentUsage(limit: Int = 30): Flow<List<AppUsageLog>>
    /**
     * Cumulative day totals across every collection row for the current
     * (studentId, today). Replaces the prior `getRecent(3).firstOrNull()`
     * pattern on HomeScreen — that surfaced only the latest 45-min interval
     * delta, not the day total. Emits `TodayAggregate.EMPTY` before any row
     * has been collected so the UI can render a clean zero state.
     */
    fun getTodayAggregate(): Flow<TodayAggregate>
    fun getDailyUsageBreakdown(): Flow<DailyUsageBreakdown>
    /**
     * 30-day aggregate used by the HomeScreen summary card.
     * Shows active days, totals, and per-day averages so users can
     * see longer-term trends even when offline.
     */
    fun get30DayAggregate(): Flow<ThirtyDayAggregate>
    /**
     * Most recent geofence checks. HomeScreen renders a "Lokasi terakhir"
     * indicator from this so users can see whether the location pipeline
     * is actually running (and where the device was when it ran).
     */
    fun getRecentLocations(limit: Int = 5): Flow<List<LocationLog>>
    suspend fun syncPendingLogs(): Result<Int>
    fun getSyncState(): Flow<SyncState>
}

interface CampusConfigRepository {
    suspend fun fetchConfig(): Result<Unit>
    fun getCampusLat(): Double
    fun getCampusLon(): Double
    fun getGeofenceRadiusMeters(): Int
    fun getMqttBrokerUrl(): String
    /**
     * Round 27: VPS mosquitto fallback URL. Empty if not configured. MqttPushClient
     * tries this after N consecutive failures on the primary broker.
     */
    fun getMqttBrokerUrlFallback(): String
    fun getCampusId(): String?
}

data class ScheduleRequest(
    val id: String,
    val requesterId: String,
    val studentId: String?,
    val requestType: String,
    val description: String,
    val status: String,
    val adminNotes: String?,
    val processedBy: String?,
    val processedAt: String?,
    val createdAt: String,
    val dayOfWeek: Int?,
    val startTime: String?,
    val endTime: String?,
    val room: String?,
    val subjectId: String?,
    val lecturerId: String?,
    val subjectNameRaw: String?,
    val lecturerNameRaw: String?,
    val campusId: String?,
    val subjectName: String?,
    val lecturerName: String?,
    val studentName: String?
) {
    val courseName: String get() = subjectName ?: subjectNameRaw ?: description
}

interface ScheduleRequestRepository {
    suspend fun createRequest(
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        room: String?,
        subjectId: String?,
        lecturerId: String?,
        subjectNameRaw: String?,
        lecturerNameRaw: String?,
        description: String
    ): Result<ScheduleRequest>
    suspend fun getMyRequests(status: String? = null): Result<List<ScheduleRequest>>
}
