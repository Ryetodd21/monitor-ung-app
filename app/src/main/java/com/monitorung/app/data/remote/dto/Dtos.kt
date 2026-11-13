package com.monitorung.app.data.remote.dto

import com.google.gson.annotations.SerializedName

// Auth DTOs
// Login identifier — server accepts `nim` field and falls back to email lookup
// if the value contains "@". Users can enter NIM or email address here.
data class LoginRequest(
    val nim: String,
    val password: String,
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("device_name") val deviceName: String? = null,
    @SerializedName("user_agent") val userAgent: String? = null,
)

data class LoginResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("expires_in") val expiresIn: Long,
    @SerializedName("student_id") val studentId: String,
    val name: String,
    val email: String
)

data class UserProfileDto(
    val id: String,
    val username: String,
    val email: String,
    @SerializedName("full_name") val fullName: String,
    val jabatan: String,
    val jurusan: String,
    val nip: String,
    val phone: String,
    @SerializedName("avatar_url") val avatarUrl: String,
    val bio: String,
    val role: String,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("created_at") val createdAt: String?
)

data class ProfileUpdateRequest(
    @SerializedName("full_name") val fullName: String?,
    val email: String?,
    val phone: String?,
    @SerializedName("avatar_url") val avatarUrl: String?,
    val bio: String?
)

// Round 27 — FCM device-token registration. Server expects
// `{"token": "..."}`; pass an empty string to clear the stored token
// on sign-out so a returned device doesn't keep receiving push.
data class FcmTokenRequest(
    val token: String,
)

data class PasswordChangeRequest(
    @SerializedName("current_password") val currentPassword: String,
    @SerializedName("new_password") val newPassword: String
)

data class RefreshTokenRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

// Server's /api/auth/refresh returns a raw JSONResponse dict (NOT a Pydantic
// model — see server/app/routers/auth.py:292-298). The body has
// `access_token` + `refresh_token` + `token_type`, NO `expires_in`. The
// previous DTO declared `expires_in` (default 0L), which made
// tokenExpiresAt = System.currentTimeMillis() on every refresh — and the
// rotated `refresh_token` was silently discarded here. TokenAuthenticator
// is the one that persists the rotated refresh_token; this DTO only needs
// to deserialize the body so the refresh call succeeds.
data class RefreshTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("token_type") val tokenType: String = "bearer",
)

// Schedule DTO — aligned with server ScheduleResponse
data class ScheduleDto(
    val id: String,
    @SerializedName("student_id") val studentId: String,
    @SerializedName("day_of_week") val dayOfWeek: Int,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    @SerializedName("campus_id") val campusId: String?,
    val room: String?,
    @SerializedName("subject_name_raw") val subjectNameRaw: String?,
    @SerializedName("lecturer_name_raw") val lecturerNameRaw: String?,
    @SerializedName("subject_id") val subjectId: String?,
    @SerializedName("lecturer_id") val lecturerId: String?,
    @SerializedName("is_active") val isActive: Boolean,
    // Round 26 audit (P1): these two fields lacked @SerializedName so Gson
    // looked for `subjectName` / `lecturerName` in the JSON. The server's
    // ScheduleResponse emits `subject_name` / `lecturer_name` (snake_case
    // via Pydantic alias), so the camelCase property names never
    // matched and both fields were always null. The course-name fallback
    // chain (`subjectName ?: subjectNameRaw`) silently absorbed the
    // nulls, but the displayed `Mata Kuliah` column on the Android
    // Schedule screen was empty for every row.
    @SerializedName("subject_name") val subjectName: String?,
    @SerializedName("lecturer_name") val lecturerName: String?
) {
    val courseName: String get() = subjectName ?: subjectNameRaw ?: ""
    val courseCode: String get() = subjectId ?: ""
    val lecturerDisplay: String get() = lecturerName ?: lecturerNameRaw ?: ""
}


data class ScheduleDirectoryResponse(
    val subjects: List<SubjectItem>,
    val lecturers: List<LecturerItem>
)

data class SubjectItem(
    val id: String,
    val name: String,
    val code: String?,
    // Lokasi kampus mata kuliah (di-bridge dari tabel mata_kuliah.campus_id
    // lewat routers/mata_kuliah._get_or_create_subject). Nullable karena
    // Subject row yang di-seed sebelum round 17 tidak punya campus_id.
    @SerializedName("campus_id") val campusId: String? = null,
    @SerializedName("campus_name") val campusName: String? = null,
)

data class LecturerItem(
    val id: String,
    val name: String,
    val nidn: String?
)

// Schedule Request DTOs
data class ScheduleRequestCreateDto(
    @SerializedName("day_of_week") val dayOfWeek: Int,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    @SerializedName("room") val room: String?,
    @SerializedName("subject_id") val subjectId: String?,
    @SerializedName("lecturer_id") val lecturerId: String?,
    @SerializedName("subject_name_raw") val subjectNameRaw: String?,
    @SerializedName("lecturer_name_raw") val lecturerNameRaw: String?,
    @SerializedName("campus_id") val campusId: String? = null,
    @SerializedName("description") val description: String = "Request jadwal baru"
)

data class ScheduleRequestDto(
    @SerializedName("id") val id: String,
    @SerializedName("requester_id") val requesterId: String,
    @SerializedName("student_id") val studentId: String?,
    @SerializedName("request_type") val requestType: String,
    @SerializedName("description") val description: String,
    @SerializedName("status") val status: String, // pending | approved | rejected
    @SerializedName("admin_notes") val adminNotes: String?,
    @SerializedName("processed_by") val processedBy: String?,
    @SerializedName("processed_at") val processedAt: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("day_of_week") val dayOfWeek: Int?,
    @SerializedName("start_time") val startTime: String?,
    @SerializedName("end_time") val endTime: String?,
    @SerializedName("room") val room: String?,
    @SerializedName("subject_id") val subjectId: String?,
    @SerializedName("lecturer_id") val lecturerId: String?,
    @SerializedName("subject_name_raw") val subjectNameRaw: String?,
    @SerializedName("lecturer_name_raw") val lecturerNameRaw: String?,
    @SerializedName("campus_id") val campusId: String?,
    @SerializedName("subject_name") val subjectName: String?,
    @SerializedName("lecturer_name") val lecturerName: String?,
    @SerializedName("student_name") val studentName: String?
) {
    val courseName: String get() = subjectName ?: subjectNameRaw ?: description
}

data class ScheduleRequestListDto(
    @SerializedName("requests") val requests: List<ScheduleRequestDto>,
    @SerializedName("total") val total: Int
)

// Batch Log DTO — field names match server/app/schemas/log.py:BatchLogRequest:
//   `logs` and `locations` (mapped via @SerializedName from Kotlin camelCase).
data class BatchLogRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("idempotency_key") val idempotencyKey: String,
    @SerializedName("logs") val appUsageLogs: List<AppUsageLogDto>? = null,
    @SerializedName("locations") val locationLogs: List<LocationLogDto>? = null
)

data class AppUsageLogDto(
    @SerializedName("client_id") val clientId: String? = null,
    @SerializedName("log_date") val logDate: String,
    @SerializedName("total_screen_time_minutes") val totalScreenTimeMinutes: Int,
    @SerializedName("social_media_time_minutes") val socialMediaTimeMinutes: Int,
    @SerializedName("game_time_minutes") val gameTimeMinutes: Int,
    @SerializedName("other_app_time_minutes") val otherAppTimeMinutes: Int = 0,
    @SerializedName("unlock_count") val unlockCount: Int,
    @SerializedName("used_during_class") val usedDuringClass: Boolean,
    @SerializedName("schedule_id") val scheduleId: String? = null
)

data class LocationLogDto(
    @SerializedName("client_id") val clientId: String? = null,
    @SerializedName("recorded_at") val recordedAt: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("campus_id") val campusId: String? = null,
    @SerializedName("is_on_campus") val isOnCampus: Boolean,
    @SerializedName("matches_schedule") val matchesSchedule: Boolean,
    @SerializedName("schedule_id") val scheduleId: String? = null
)

// Server's actual response is SyncStatusResponse — aligned with
// server/app/schemas/log.py:SyncStatusResponse. The previous DTO declared
// a non-existent `synced_ids` field that the server never returned, so
// UsageRepositoryImpl.syncedIds.mapNotNull { it.toLongOrNull() } always
// produced an empty list → markSynced(emptyList()) → pending rows stuck
// PENDING forever. Round 17 fix.
data class BatchLogResponse(
    @SerializedName("idempotency_key") val idempotencyKey: String,
    val status: String,
    @SerializedName("processed_count") val processedCount: Int,
    @SerializedName("synced_client_ids") val syncedClientIds: List<String> = emptyList(),
)

// Campus config DTO
data class CampusConfigDto(
    @SerializedName("campus_id") val campusId: String? = null,
    @SerializedName("campus_lat") val campusLat: Double,
    @SerializedName("campus_lon") val campusLon: Double,
    @SerializedName("geofence_radius_meters") val geofenceRadiusMeters: Int,
    @SerializedName("mqtt_broker_url") val mqttBrokerUrl: String,
    // Round 27: VPS mosquitto as auto-fallback. Default empty = no fallback
    // (backward-compat with older server versions that don't return this
    // field). MqttPushClient tries primary first, on consecutive failures
    // switches to fallback, retries indefinitely.
    @SerializedName("mqtt_broker_url_fallback") val mqttBrokerUrlFallback: String = "",
)

