package com.monitorung.app.data.local.entity

import androidx.room.*

/**
 * Per-day app-usage aggregate for a single student.
 *
 * Round 20: this table enforces ONE row per (student_id, log_date) via the
 * UNIQUE INDEX below. The `collectAppUsage()` collector accumulates the
 * day's running totals into the existing row (or inserts on a new day) and
 * the sync layer sends that single row to the server — so the server's
 * "1 day summary" matches what the user sees on Beranda, and a sync replay
 * replaces the day's totals instead of appending a second row.
 *
 * Schema columns mirror the server's `app_logs` aggregate fields
 * (`total_screen_time_minutes`, `social_media_time_minutes`, etc.) so a
 * single DTO row carries the day's cumulative totals.
 */
@Entity(
    tableName = "app_usage_logs",
    indices = [
        Index(value = ["student_id", "log_date", "schedule_id"], unique = true),
        Index(value = ["student_id"]),  // hot path for getRecent / get30Day
    ]
)
data class AppUsageLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "student_id") val studentId: String,
    @ColumnInfo(name = "log_date") val logDate: String,
    @ColumnInfo(name = "log_timestamp") val logTimestamp: Long,
    @ColumnInfo(name = "total_screen_time_minutes") val totalScreenTimeMinutes: Int,
    @ColumnInfo(name = "social_media_time_minutes") val socialMediaTimeMinutes: Int,
    @ColumnInfo(name = "game_time_minutes") val gameTimeMinutes: Int,
    @ColumnInfo(name = "other_app_time_minutes") val otherAppTimeMinutes: Int = 0,
    @ColumnInfo(name = "unlock_count") val unlockCount: Int,
    @ColumnInfo(name = "used_during_class") val usedDuringClass: Boolean,
    @ColumnInfo(name = "schedule_id") val scheduleId: String? = null,
    @ColumnInfo(name = "device_id") val deviceId: String? = null,
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String? = null,
    @ColumnInfo(name = "sync_status") val syncStatus: String = "PENDING"
)

/**
 * Aggregate projection for `AppUsageLogDao.getDayTotals()`. Mirrors the SUM
 * columns in the query — `@ColumnInfo(name = …)` maps the snake_case SQL
 * aliases to idiomatic camelCase Kotlin fields.
 *
 * Lives next to the entity so the @Query and the projection class stay
 * co-located and easy to audit together.
 *
 * Round 20: with single-row-per-day, SUM collapses to the single row's
 * value — but the SUM/MAX query still works for backward compatibility
 * with any pre-migration rows that survived an interrupted upgrade.
 */
data class DayTotalsProjection(
    @ColumnInfo(name = "total_screen_time_minutes") val totalScreenTimeMinutes: Int = 0,
    @ColumnInfo(name = "social_media_time_minutes") val socialMediaTimeMinutes: Int = 0,
    @ColumnInfo(name = "game_time_minutes") val gameTimeMinutes: Int = 0,
    @ColumnInfo(name = "other_app_time_minutes") val otherAppTimeMinutes: Int = 0,
    @ColumnInfo(name = "unlock_count") val unlockCount: Int = 0,
    @ColumnInfo(name = "used_during_class") val usedDuringClass: Int = 0,
)

/** 30-day aggregate projection for `AppUsageLogDao.get30DayAggregate()`. */
data class DayAggregateProjection(
    @ColumnInfo(name = "active_days") val activeDays: Int = 0,
    @ColumnInfo(name = "total_screen_time_minutes") val totalScreenTimeMinutes: Int = 0,
    @ColumnInfo(name = "social_media_time_minutes") val socialMediaTimeMinutes: Int = 0,
    @ColumnInfo(name = "game_time_minutes") val gameTimeMinutes: Int = 0,
    @ColumnInfo(name = "max_unlock_count") val maxUnlockCount: Int = 0,
    @ColumnInfo(name = "used_during_class") val usedDuringClass: Int = 0,
)

@Entity(tableName = "location_logs")
data class LocationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "student_id") val studentId: String,
    @ColumnInfo(name = "recorded_at") val recordedAt: String,
    @ColumnInfo(name = "log_timestamp") val logTimestamp: Long,
    @ColumnInfo(name = "latitude") val latitude: Double? = null,
    @ColumnInfo(name = "longitude") val longitude: Double? = null,
    @ColumnInfo(name = "campus_id") val campusId: String? = null,
    @ColumnInfo(name = "is_on_campus") val isOnCampus: Boolean,
    @ColumnInfo(name = "matches_schedule") val matchesSchedule: Boolean,
    @ColumnInfo(name = "schedule_id") val scheduleId: String? = null,
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String? = null,
    @ColumnInfo(name = "sync_status") val syncStatus: String = "PENDING"
)

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "student_id") val studentId: String,
    @ColumnInfo(name = "day_of_week") val dayOfWeek: Int,
    @ColumnInfo(name = "start_time") val startTime: String,
    @ColumnInfo(name = "end_time") val endTime: String,
    @ColumnInfo(name = "campus_id") val campusId: String?,
    @ColumnInfo(name = "room") val room: String?,
    @ColumnInfo(name = "subject_name_raw") val subjectNameRaw: String?,
    @ColumnInfo(name = "lecturer_name_raw") val lecturerNameRaw: String?,
    @ColumnInfo(name = "subject_id") val subjectId: String?,
    @ColumnInfo(name = "lecturer_id") val lecturerId: String?,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @ColumnInfo(name = "subject_name") val subjectName: String?,
    @ColumnInfo(name = "lecturer_name") val lecturerName: String?
)
