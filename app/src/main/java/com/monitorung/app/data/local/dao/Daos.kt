package com.monitorung.app.data.local.dao

import androidx.room.*
import com.monitorung.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppUsageLogDao {
    /**
     * Round 20: the `OnConflictStrategy.REPLACE` here is now load-bearing.
     * The entity declares `@Index(value = ["student_id", "log_date"], unique = true)`,
     * so an INSERT that collides on (student_id, log_date) replaces the existing
     * row. Combined with the [accumulateDay] transaction below, this enforces
     * "one row per student per day" in the local DB - the Android-side mirror
     * of the server's partial UNIQUE INDEX.
     *
     * IMPORTANT: do not call `insert()` directly with a fresh `AppUsageLogEntity`
     * for an existing (student, date) - it overwrites the running counters.
     * Use [accumulateDay] for collector writes; use [insert] only for
     * bulk-import paths that intentionally want to replace the day.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AppUsageLogEntity): Long

    /**
     * Fold [delta] into the existing day's running totals. Round 20 - the
     * collector computes a per-call delta (since last snapshot reset) and
     * we ADD it to the existing row's running counters. If no row exists
     * for `(studentId, logDate)`, insert the delta as the initial day total.
     *
     * Always flips `sync_status` to PENDING on update so the next sync
     * re-sends the day's totals - the server UPSERT then replaces its row
     * in place, keeping the Android-side and server-side summaries in sync.
     *
     * Wrapped in `@Transaction` so the read-modify-write is atomic per
     * (student_id, log_date). The unique index on the table serializes
     * concurrent attempts at the SQLite level, but the transaction keeps
     * the read+write inside one atomic operation - without it, two
     * collectors calling `accumulateDay` simultaneously could both read
     * the same `existing` row and double-add the delta.
     */
    @Transaction
    suspend fun accumulateDay(delta: AppUsageLogEntity) {
        val existing = getByDate(delta.studentId, delta.logDate, delta.scheduleId)
        if (existing == null) {
            // First collection of the day - write the delta as the day's
            // running total directly.
            insert(delta)
        } else {
            // Subsequent collection - add the delta to the existing day's totals.
            updateById(
                id = existing.id,
                logTimestamp = delta.logTimestamp,
                totalScreenTimeMinutes = existing.totalScreenTimeMinutes + delta.totalScreenTimeMinutes,
                socialMediaTimeMinutes = existing.socialMediaTimeMinutes + delta.socialMediaTimeMinutes,
                gameTimeMinutes = existing.gameTimeMinutes + delta.gameTimeMinutes,
                otherAppTimeMinutes = existing.otherAppTimeMinutes + delta.otherAppTimeMinutes,
                // unlock_count is a daily running counter (MAX-style, not a delta)
                unlockCount = maxOf(existing.unlockCount, delta.unlockCount),
                usedDuringClass = existing.usedDuringClass || delta.usedDuringClass,
                syncStatus = "PENDING",
                scheduleId = delta.scheduleId ?: existing.scheduleId,
                deviceId = delta.deviceId ?: existing.deviceId,
            )
        }
    }

    /** Each new absolute snapshot gets a new ID; old upload ACKs cannot acknowledge it. */
    @Transaction
    suspend fun replaceDay(absolute: AppUsageLogEntity) {
        val existing = getByDate(absolute.studentId, absolute.logDate, absolute.scheduleId)
        if (existing != null) {
            if (existing.logTimestamp > absolute.logTimestamp) return
            deleteByPrimaryKey(existing.id)
        }
        insert(absolute)
    }

    @Query("DELETE FROM app_usage_logs WHERE id = :id")
    suspend fun deleteByPrimaryKey(id: Long)

    @Query("SELECT * FROM app_usage_logs WHERE student_id = :studentId AND log_date = :date AND ((schedule_id = :scheduleId) OR (schedule_id IS NULL AND :scheduleId IS NULL)) LIMIT 1")
    suspend fun getByDate(studentId: String, date: String, scheduleId: String?): AppUsageLogEntity?

    /**
     * Raw UPDATE on the day's row. Round 20 - only [accumulateDay] calls this.
     * Updates all day-level aggregate fields in one statement and always
     * flips sync_status to PENDING (new data means another upload is due).
     */
    @Query(
        """
        UPDATE app_usage_logs
        SET log_timestamp = :logTimestamp,
            total_screen_time_minutes = :totalScreenTimeMinutes,
            social_media_time_minutes = :socialMediaTimeMinutes,
            game_time_minutes = :gameTimeMinutes,
            other_app_time_minutes = :otherAppTimeMinutes,
            unlock_count = :unlockCount,
            used_during_class = :usedDuringClass,
            sync_status = :syncStatus,
            schedule_id = :scheduleId,
            device_id = :deviceId
        WHERE id = :id
        """
    )
    suspend fun updateById(
        id: Long,
        logTimestamp: Long,
        totalScreenTimeMinutes: Int,
        socialMediaTimeMinutes: Int,
        gameTimeMinutes: Int,
        otherAppTimeMinutes: Int,
        unlockCount: Int,
        usedDuringClass: Boolean,
        syncStatus: String,
        scheduleId: String?,
        deviceId: String?,
    )

    @Query("SELECT * FROM app_usage_logs WHERE student_id = :studentId ORDER BY log_timestamp DESC LIMIT :limit")
    fun getRecent(studentId: String, limit: Int = 30): Flow<List<AppUsageLogEntity>>

    /**
     * Cumulative day totals across every collection row for a single
     * (studentId, date). Round 20 - with single-row-per-day, the SUM collapses
     * to the single row's value. Kept as SUM/MAX for safety so a partial-failed
     * migration (which left multiple rows per day) still produces correct totals.
     */
    @Query("""
        SELECT
            COALESCE(SUM(total_screen_time_minutes), 0) AS total_screen_time_minutes,
            COALESCE(SUM(social_media_time_minutes), 0) AS social_media_time_minutes,
            COALESCE(SUM(game_time_minutes), 0) AS game_time_minutes,
            COALESCE(SUM(other_app_time_minutes), 0) AS other_app_time_minutes,
            COALESCE(MAX(unlock_count), 0) AS unlock_count,
            MAX(CASE WHEN used_during_class = 1 THEN 1 ELSE 0 END) AS used_during_class
        FROM app_usage_logs
        WHERE student_id = :studentId AND log_date = :date
    """)
    fun getDayTotals(studentId: String, date: String): Flow<DayTotalsProjection>

    /**
     * Round 20: a single row per (student, date) means "all PENDING" already
     * covers all days with unsynced totals. The Android client sends ONE row
     * per day to the server, so `getPending(studentId)` is the canonical
     * "days I need to push" set. The 100-row limit caps burst syncs.
     */
    @Query("SELECT * FROM app_usage_logs WHERE student_id = :studentId AND sync_status = 'PENDING' LIMIT :limit")
    suspend fun getPending(studentId: String, limit: Int = 100): List<AppUsageLogEntity>

    @Query("SELECT COUNT(*) FROM app_usage_logs WHERE student_id = :studentId AND sync_status = 'PENDING'")
    suspend fun getPendingCount(studentId: String): Int

    @Transaction
    suspend fun markSynced(snapshots: List<AppUsageLogEntity>) {
        snapshots.forEach { sent ->
            if (getByDate(sent.studentId, sent.logDate, sent.scheduleId) == sent) {
                markSyncedIds(listOf(sent.id))
            }
        }
    }

    @Query("UPDATE app_usage_logs SET sync_status = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSyncedIds(ids: List<Long>)

    @Transaction
    @Query("DELETE FROM app_usage_logs WHERE student_id = :studentId AND log_timestamp < :threshold AND sync_status = 'PENDING'")
    suspend fun deleteOldPending(studentId: String, threshold: Long)

    @Query("DELETE FROM app_usage_logs WHERE :studentId IS NULL OR student_id = :studentId")
    suspend fun deleteAll(studentId: String? = null)

    @Query("SELECT COUNT(*) FROM app_usage_logs WHERE student_id = :studentId AND log_date = :date")
    suspend fun countByDate(studentId: String, date: String): Int

    /**
     * 30-day aggregate: total and average metrics across all days with at
     * least one collection row. Powers the HomeScreen "30 Day Summary" card so
     * users can see their longer-term trends even when offline.
     */
    @Query("""
        SELECT
            COUNT(DISTINCT log_date) AS active_days,
            COALESCE(SUM(total_screen_time_minutes), 0) AS total_screen_time_minutes,
            COALESCE(SUM(social_media_time_minutes), 0) AS social_media_time_minutes,
            COALESCE(SUM(game_time_minutes), 0) AS game_time_minutes,
            COALESCE(MAX(unlock_count), 0) AS max_unlock_count,
            MAX(CASE WHEN used_during_class = 1 THEN 1 ELSE 0 END) AS used_during_class
        FROM app_usage_logs
        WHERE student_id = :studentId
          AND log_date >= date('now', '-30 days', 'localtime')
    """)
    fun get30DayAggregate(studentId: String): Flow<DayAggregateProjection>
}

@Dao
interface LocationLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: LocationLogEntity): Long

    @Query("SELECT * FROM location_logs WHERE student_id = :studentId AND sync_status = 'PENDING' LIMIT :limit")
    suspend fun getPending(studentId: String, limit: Int = 100): List<LocationLogEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM location_logs WHERE student_id = :studentId AND campus_id = :campusId AND log_timestamp >= :from AND log_timestamp < :to AND is_on_campus = 1)")
    suspend fun hasCampusPresence(studentId: String, campusId: String, from: Long, to: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM location_logs WHERE student_id = :studentId AND schedule_id = :scheduleId AND log_timestamp >= :from AND log_timestamp < :to AND matches_schedule = 1)")
    suspend fun hasSchedulePresence(studentId: String, scheduleId: String, from: Long, to: Long): Boolean

    @Query("SELECT COUNT(*) FROM location_logs WHERE student_id = :studentId AND sync_status = 'PENDING'")
    suspend fun getPendingCount(studentId: String): Int

    @Query("UPDATE location_logs SET sync_status = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    /**
     * Most-recent location entries for a student. Used by HomeScreen to
     * surface a "lokasi terakhir" indicator (when was geofence last
     * checked, was the student on campus) so users can see the location
     * pipeline is actually running.
     */
    @Query("SELECT * FROM location_logs WHERE student_id = :studentId ORDER BY log_timestamp DESC LIMIT :limit")
    fun getRecent(studentId: String, limit: Int): Flow<List<LocationLogEntity>>

    @Query("DELETE FROM location_logs WHERE :studentId IS NULL OR student_id = :studentId")
    suspend fun deleteAll(studentId: String? = null)
}

@Dao
interface ScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<ScheduleEntity>)

    @Query("SELECT * FROM schedules WHERE student_id = :studentId ORDER BY day_of_week, start_time")
    fun getAll(studentId: String): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE student_id = :studentId AND day_of_week = :dayOfWeek ORDER BY start_time")
    fun getByDay(studentId: String, dayOfWeek: Int): Flow<List<ScheduleEntity>>

    @Query("DELETE FROM schedules WHERE student_id = :studentId")
    suspend fun deleteAll(studentId: String)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM schedules")
    suspend fun deleteAllTables()

    /**
     * Atomically replace [studentId]'s schedule set in a single transaction.
     * Without `@Transaction` the two DAO calls could straddle a process kill
     * and leave the user with zero schedules until the next refresh succeeds.
     */
    @Transaction
    suspend fun replaceAllForStudent(studentId: String, schedules: List<ScheduleEntity>) {
        deleteAll(studentId)
        insertAll(schedules)
    }
}

