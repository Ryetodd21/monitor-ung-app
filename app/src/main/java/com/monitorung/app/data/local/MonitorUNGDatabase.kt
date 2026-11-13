package com.monitorung.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.monitorung.app.data.local.dao.*
import com.monitorung.app.data.local.entity.*

@Database(
    entities = [
        AppUsageLogEntity::class,
        LocationLogEntity::class,
        ScheduleEntity::class,
    ],
    version = 5,
    exportSchema = false
)
abstract class MonitorUNGDatabase : RoomDatabase() {
    abstract fun appUsageLogDao(): AppUsageLogDao
    abstract fun locationLogDao(): LocationLogDao
    abstract fun scheduleDao(): ScheduleDao
    /**
     * Clears all data for the given [studentId], or all data if [studentId] is null.
     * Called from Settings "Delete All Data" with studentId set (scoped delete).
     *
     * Wrapped in `withTransaction` so the three DAO deletes either all land or
     * none do — a process kill between two of them used to leave the local DB
     * half-wiped, which then re-attached stale rows to the next logged-in user.
     */
    suspend fun clearAllData(studentId: String?) {
        withTransaction {
            appUsageLogDao().deleteAll(studentId)
            locationLogDao().deleteAll(studentId)
            if (studentId != null) {
                scheduleDao().deleteAll(studentId)
            } else {
                scheduleDao().deleteAllTables()
            }
        }
    }

    companion object {
        /**
         * Explicit schema migrations.
         *
         * Each entry is a (from, to) pair. Add a new `Migration(N, N+1)` here
         * whenever the schema changes — DO NOT enable
         * `fallbackToDestructiveMigration()` because `pending_logs` holds
         * encrypted rows that haven't been synced to the server yet; a silent
         * table drop loses user data permanently.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(
            // MIGRATION_1_2 was applied via destructive fallback before this
            // list was introduced. Starting from this list, migrations are the only
            // supported upgrade path — destructive fallback is disabled for v3+.
            // v2→v3: remove pending_logs table (PendingLogDao removed — dead code)
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("DROP TABLE IF EXISTS pending_logs")
                }
            },
            // v3→v4: enforce single-row-per-day on app_usage_logs.
            // The pre-v4 collector inserted one row per 45-min interval, so a
            // single day could have 10+ rows. Post-v4 we keep one row per
            // (student_id, log_date) — accumulates the day's totals, syncs
            // a single row, and matches what the server expects.
            //
            // Migration strategy:
            //   1. Rename the old table to free up the name.
            //   2. Create the new v4 table with the same column layout.
            //   3. Copy one aggregated row per (student_id, log_date) from
            //      the old table into the new one.
            //   4. Drop the old table.
            //   5. Create the UNIQUE INDEX that enforces the new invariant.
            //
            // This preserves the running totals that the Beranda tile shows
            // (it's a SUM query anyway) so the UI is identical after upgrade.
            //
            // Schema column names + types mirror Room's auto-generated CREATE
            // TABLE so `validateMigration` stays happy (Room uses backticks
            // and `INTEGER NOT NULL` for every column).
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Step 1: rename the old table.
                    db.execSQL("ALTER TABLE `app_usage_logs` RENAME TO `app_usage_logs__old`")

                    // Step 2: create the new v4 table (mirrors Room's auto-CREATE).
                    db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `app_usage_logs` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `student_id` TEXT NOT NULL,
                                `log_date` TEXT NOT NULL,
                                `log_timestamp` INTEGER NOT NULL,
                                `total_screen_time_minutes` INTEGER NOT NULL,
                                `social_media_time_minutes` INTEGER NOT NULL,
                                `game_time_minutes` INTEGER NOT NULL,
                                `other_app_time_minutes` INTEGER NOT NULL,
                                `unlock_count` INTEGER NOT NULL,
                                `used_during_class` INTEGER NOT NULL,
                                `schedule_id` TEXT,
                                `device_id` TEXT,
                                `idempotency_key` TEXT,
                                `sync_status` TEXT NOT NULL
                            )
                            """.trimIndent()
                    )

                    // Step 3: copy aggregated row per (student_id, log_date).
                    // SUM the running totals, MAX the unlock counter
                    // (unlock_count is a daily running counter, not a delta),
                    // MAX the boolean used_during_class flag.
                    db.execSQL(
                            """
                            INSERT INTO `app_usage_logs` (
                                id, student_id, log_date, log_timestamp,
                                total_screen_time_minutes, social_media_time_minutes,
                                game_time_minutes, other_app_time_minutes,
                                unlock_count, used_during_class,
                                schedule_id, device_id, idempotency_key, sync_status
                            )
                            SELECT
                                -- Pick the latest row's id so the auto-increment
                                -- sequence stays close to what it was.
                                (SELECT id FROM `app_usage_logs__old` a2
                                 WHERE a2.student_id = a.student_id
                                   AND a2.log_date = a.log_date
                                 ORDER BY a2.log_timestamp DESC, a2.id DESC
                                 LIMIT 1) AS id,
                                a.student_id,
                                a.log_date,
                                MAX(a.log_timestamp) AS log_timestamp,
                                SUM(a.total_screen_time_minutes) AS total_screen_time_minutes,
                                SUM(a.social_media_time_minutes) AS social_media_time_minutes,
                                SUM(a.game_time_minutes) AS game_time_minutes,
                                SUM(a.other_app_time_minutes) AS other_app_time_minutes,
                                MAX(a.unlock_count) AS unlock_count,
                                MAX(a.used_during_class) AS used_during_class,
                                -- schedule_id/device_id from the latest row
                                -- (any value is fine for the day-level aggregate).
                                (SELECT schedule_id FROM `app_usage_logs__old` a3
                                 WHERE a3.student_id = a.student_id
                                   AND a3.log_date = a.log_date
                                 ORDER BY a3.log_timestamp DESC, a3.id DESC
                                 LIMIT 1) AS schedule_id,
                                (SELECT device_id FROM `app_usage_logs__old` a4
                                 WHERE a4.student_id = a.student_id
                                   AND a4.log_date = a.log_date
                                 ORDER BY a4.log_timestamp DESC, a4.id DESC
                                 LIMIT 1) AS device_id,
                                -- Mark all folded rows PENDING so the post-migration
                                -- sync sends the corrected totals to the server.
                                'PENDING' AS sync_status,
                                (SELECT idempotency_key FROM `app_usage_logs__old` a5
                                 WHERE a5.student_id = a.student_id
                                   AND a5.log_date = a.log_date
                                 ORDER BY a5.log_timestamp DESC, a5.id DESC
                                 LIMIT 1) AS idempotency_key
                            FROM `app_usage_logs__old` a
                            GROUP BY a.student_id, a.log_date
                            """.trimIndent()
                    )

                    // Step 4: drop the old table.
                    db.execSQL("DROP TABLE `app_usage_logs__old`")

                    // Step 5: create the UNIQUE INDEX that enforces the new
                    // "one row per day" invariant. Room's @Index annotation
                    // would do this on a fresh install; the explicit CREATE
                    // INDEX here applies it during migration.
                    db.execSQL(
                            "CREATE UNIQUE INDEX IF NOT EXISTS `index_app_usage_logs_student_id_log_date` " +
                                    "ON `app_usage_logs` (`student_id`, `log_date`)"
                    )
                    db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_app_usage_logs_student_id` " +
                                    "ON `app_usage_logs` (`student_id`)"
                    )
                }
            },
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("DROP INDEX IF EXISTS `index_app_usage_logs_student_id_log_date`")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_app_usage_logs_student_id_log_date_schedule_id` ON `app_usage_logs` (`student_id`, `log_date`, `schedule_id`)")
                }
            }
        )
    }
}
