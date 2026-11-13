package com.monitorung.app.service

import android.content.Context
import androidx.work.*
import com.monitorung.app.domain.model.ClassSession
import com.monitorung.app.domain.model.Schedule
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val workManager = WorkManager.getInstance(context)

    companion object {
        const val WORK_MORNING = "work_morning_notification"
        const val WORK_SYNC_REMINDER = "work_sync_reminder"
        const val WORK_SYNC_SEND = "work_sync_send"
        const val WORK_CLASS_REMINDER_PREFIX = "work_class_reminder_"
        const val WORK_CLASS_START_PREFIX = "work_class_start_"
        const val WORK_CLASS_END_PREFIX = "work_class_end_"
        const val WORK_DATA_COLLECT = "work_data_collect"
        const val WORK_KEEP_ALIVE = "work_monitoring_keep_alive"

        /**
         * Next time [dayOfWeek] (ISO 1=MON..7=SUN) falls at [time] on or after
         * [now]. Rolls a week when today matches but [time] already passed, and
         * again when the result is closer than [minDaysAhead] days — a worker
         * re-arming itself fires at its own target moment, where "next
         * occurrence" would otherwise resolve to the present.
         *
         * Pure so it can be unit tested: no Context, no WorkManager, no clock.
         */
        internal fun nextClassOccurrence(
            dayOfWeek: Int,
            time: LocalTime,
            now: LocalDateTime,
            minDaysAhead: Int = 0,
        ): LocalDateTime {
            val todayDow = now.toLocalDate().dayOfWeek.value
            var daysAhead = (dayOfWeek - todayDow + 7) % 7
            if (daysAhead == 0 && now.toLocalTime().isAfter(time)) daysAhead = 7
            if (daysAhead < minDaysAhead) daysAhead += 7
            return now.toLocalDate().plusDays(daysAhead.toLong()).atTime(time)
        }
        internal fun nextAutoSync(now: LocalDateTime): LocalDateTime {
            val target = now.toLocalDate().atTime(22, 0)
            return if (target.isAfter(now)) target else target.plusDays(1)
        }

        internal fun occurrenceSession(session: ClassSession, start: LocalDateTime): ClassSession {
            val zone = ZoneId.of("Asia/Makassar")
            var end = start.toLocalDate().atTime(LocalTime.parse(session.endTime))
            if (!end.isAfter(start)) end = end.plusDays(1)
            return session.copy(
                startTimestamp = start.atZone(zone).toInstant().toEpochMilli(),
                endTimestamp = end.atZone(zone).toInstant().toEpochMilli(),
            )
        }

    }

    // ─── Morning notification at 07:00 WITA ────────────────────────────────────
    fun scheduleMorningNotification() {
        val now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Makassar"))
        var target = now.toLocalDate().atTime(7, 0)

        // WITA = UTC+8, LocalDateTime.now() already adjusted

        if (now.toLocalTime().isAfter(target.toLocalTime())) {
            target = target.plusDays(1)
        }

        val delayMs = java.time.Duration.between(now, target).toMillis()

        val work = OneTimeWorkRequestBuilder<MorningNotificationWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .addTag(WORK_MORNING)
            .build()

        workManager.enqueueUniqueWork(
            WORK_MORNING,
            ExistingWorkPolicy.REPLACE,
            work
        )
    }

    // ─── Class reminder 2h before class ───────────────────────────────────────
    // [minDaysAhead] is 7 when called from ClassReminderWorker itself: the worker
    // fires at the reminder moment, so "next occurrence" would resolve to right
    // now and get dropped by the delayMs<=0 guard. WorkManager one-shot work
    // never repeats, so each worker re-arms itself for the following week.
    fun scheduleClassReminder(session: ClassSession, minDaysAhead: Int = 0) {
        val zone = ZoneId.of("Asia/Makassar")
        val now = java.time.LocalDateTime.now(zone)
        val classTime = runCatching { LocalTime.parse(session.startTime) }.getOrNull() ?: return
        val reminderTime = classTime.minusHours(2)
        // Pick the next occurrence of session.dayOfWeek from today. If today is
        // class day but the reminder time has already passed, roll to next week.
        val reminderDay = if (classTime.hour < 2) (session.dayOfWeek + 5) % 7 + 1 else session.dayOfWeek
        val target = nextOccurrence(reminderDay, reminderTime, zone, minDaysAhead)
        val occurrence = occurrenceSession(session, target.plusHours(2))

        val delayMs = java.time.Duration.between(now, target).toMillis()
        if (delayMs <= 0) return

        val data = workDataOf(
            "schedule_id" to session.scheduleId,
            "course_name" to session.courseName,
            "room" to session.room,
            "start_time" to session.startTime,
            "end_time" to session.endTime,
            "start_timestamp" to occurrence.startTimestamp,
            "end_timestamp" to occurrence.endTimestamp,
            "day_of_week" to session.dayOfWeek
        )

        val work = OneTimeWorkRequestBuilder<ClassReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("${WORK_CLASS_REMINDER_PREFIX}${session.scheduleId}")
            .build()

        workManager.enqueueUniqueWork(
            "${WORK_CLASS_REMINDER_PREFIX}${session.scheduleId}",
            ExistingWorkPolicy.REPLACE,
            work
        )
    }

    // ─── Class start monitoring at class time ──────────────────────────────────
    fun scheduleClassStart(session: ClassSession, minDaysAhead: Int = 0) {
        val zone = ZoneId.of("Asia/Makassar")
        val now = java.time.LocalDateTime.now(zone)
        val classTime = runCatching { LocalTime.parse(session.startTime) }.getOrNull() ?: return
        val endTime = runCatching { LocalTime.parse(session.endTime) }.getOrNull() ?: return
        val activeToday = minDaysAhead == 0 && now.dayOfWeek.value == session.dayOfWeek &&
            !now.toLocalTime().isBefore(classTime) && now.toLocalTime().isBefore(endTime)
        val target = if (activeToday) now.toLocalDate().atTime(classTime)
            else nextOccurrence(session.dayOfWeek, classTime, zone, minDaysAhead)
        val occurrence = occurrenceSession(session, target)

        val delayMs = java.time.Duration.between(now, target).toMillis().coerceAtLeast(0)

        val data = workDataOf(
            "schedule_id" to session.scheduleId,
            "course_name" to session.courseName,
            "room" to session.room,
            "start_time" to session.startTime,
            "end_time" to session.endTime,
            "start_timestamp" to occurrence.startTimestamp,
            "end_timestamp" to occurrence.endTimestamp,
            "day_of_week" to session.dayOfWeek
        )

        val work = OneTimeWorkRequestBuilder<ClassStartWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("${WORK_CLASS_START_PREFIX}${session.scheduleId}")
            .build()

        workManager.enqueueUniqueWork(
            "${WORK_CLASS_START_PREFIX}${session.scheduleId}",
            ExistingWorkPolicy.REPLACE,
            work
        )
        // A durable final collection also runs when Android has killed the timer's process.
        val endWork = OneTimeWorkRequestBuilder<ClassEndWorker>()
            .setInitialDelay((occurrence.endTimestamp - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("${WORK_CLASS_END_PREFIX}${session.scheduleId}")
            .build()
        workManager.enqueueUniqueWork(
            "${WORK_CLASS_END_PREFIX}${session.scheduleId}_${occurrence.startTimestamp}",
            ExistingWorkPolicy.KEEP,
            endWork,
        )
    }

    /**
     * Compute the next LocalDateTime at which [dayOfWeek] (1=MON..7=SUN, java.time
     * convention) falls on or after today at [time]. If today matches and the
     * target time is already past, roll forward a full week.
     */
    private fun nextOccurrence(
        dayOfWeek: Int,
        time: LocalTime,
        zone: ZoneId,
        minDaysAhead: Int = 0,
    ): LocalDateTime =
        nextClassOccurrence(dayOfWeek, time, LocalDateTime.now(zone), minDaysAhead)

    // ─── Sync reminder at 21:45 WITA ────────────────────────────────────────────
    fun scheduleSyncReminder(pendingCount: Int) {
        val now = java.time.LocalDateTime.now(ZoneId.of("Asia/Makassar"))
        var target = now.toLocalDate().atTime(21, 45)

        if (now.toLocalTime().isAfter(target.toLocalTime())) {
            target = target.plusDays(1)
        }

        val delayMs = java.time.Duration.between(now, target).toMillis()

        val work = OneTimeWorkRequestBuilder<SyncReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("pending_count" to pendingCount))
            .addTag(WORK_SYNC_REMINDER)
            .build()

        workManager.enqueueUniqueWork(
            WORK_SYNC_REMINDER,
            ExistingWorkPolicy.REPLACE,
            work
        )
    }

    // ─── Sync at 22:00 WITA ─────────────────────────────────────────────────
    fun scheduleAutoSync() {
        val now = java.time.LocalDateTime.now(ZoneId.of("Asia/Makassar"))
        val target = nextAutoSync(now)

        val delayMs = java.time.Duration.between(now, target).toMillis()

        val work = OneTimeWorkRequestBuilder<AutoSyncWorker>()
            .setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build())
            .addTag(WORK_SYNC_SEND)
            .build()

        workManager.cancelUniqueWork(WORK_SYNC_SEND) // Retire the old immediate/periodic work.
        workManager.enqueueUniqueWork("${WORK_SYNC_SEND}_${target.toLocalDate()}", ExistingWorkPolicy.KEEP, work)
    }

    // ─── Schedule all daily class notifications ───────────────────────────────
    /**
     * Enqueue reminder (2h before) + start-monitoring (at class time) for each row.
     *
     * ClassSession needs wall-clock timestamps that Schedule does not carry —
     * they are derived from dayOfWeek + startTime here. Each worker re-arms
     * itself weekly, so a login is enough to keep the whole semester armed.
     */
    fun scheduleClassNotifications(schedules: List<Schedule>) {
        schedules.mapNotNull { it.toClassSession() }.forEach { session ->
            scheduleClassReminder(session)
            scheduleClassStart(session)
        }
    }

    /**
     * Convert a Schedule row into a ClassSession. Null when the row is inactive
     * or its times don't parse — the same `runCatching` guard the workers use.
     */
    fun Schedule.toClassSession(): ClassSession? {
        if (!isActive) return null
        val zone = ZoneId.of("Asia/Makassar")
        val start = runCatching { LocalTime.parse(startTime) }.getOrNull() ?: return null
        val end = runCatching { LocalTime.parse(endTime) }.getOrNull() ?: return null
        val day = nextOccurrence(dayOfWeek, start, zone)
        val startTs = day.atZone(zone).toInstant().toEpochMilli()
        return ClassSession(
            scheduleId = id,
            courseName = courseName,
            room = room ?: "",
            dayOfWeek = dayOfWeek,
            startTime = startTime,
            endTime = endTime,
            startTimestamp = startTs,
            endTimestamp = day.with(end).atZone(zone).toInstant().toEpochMilli(),
        )
    }

    // ─── Background SOT collection every 4 hours (outside class hours) ─────────
    // Supplements class-hour monitoring with data from outside class windows.
    // Respects battery level — skips if < 15% and not charging.
    fun scheduleBackgroundSotCollection() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicWork = PeriodicWorkRequestBuilder<BackgroundSotWorker>(
            4, TimeUnit.HOURS,
            30, TimeUnit.MINUTES // flex interval
        )
            .setConstraints(constraints)
            .addTag(WORK_DATA_COLLECT)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_DATA_COLLECT,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork
        )
    }

    fun cancelBackgroundSotCollection() {
        workManager.cancelUniqueWork(WORK_DATA_COLLECT)
    }

    // ─── Monitoring keep-alive (every 30 min) ─────────────────────────────────────
    // Bug 3 fix: ensures SmartMonitoringService survives when the OS kills it
    // after the app hasn't been opened for 1 day. The worker wakes up every
    // 30 minutes and restarts the service if an active class session exists.
    // Without this, the service stays dead after a force-kill or low-memory
    // scenario, and usage detection stops entirely.
    fun scheduleMonitoringKeepAlive() {
        val constraints = Constraints.Builder()
            .build()

        val periodicWork = PeriodicWorkRequestBuilder<MonitoringKeepAliveWorker>(
            30, java.util.concurrent.TimeUnit.MINUTES,
            15, java.util.concurrent.TimeUnit.MINUTES // flex interval
        )
            .setConstraints(constraints)
            .addTag(WORK_KEEP_ALIVE)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_KEEP_ALIVE,
            ExistingPeriodicWorkPolicy.REPLACE,
            periodicWork
        )
    }

    fun cancelMonitoringKeepAlive() {
        workManager.cancelUniqueWork(WORK_KEEP_ALIVE)
    }

    // ─── Cancel all ──────────────────────────────────────────────────────────
    fun cancelAll() {
        workManager.cancelAllWork()
    }

    fun cancelClassWork(scheduleId: String) {
        workManager.cancelUniqueWork("${WORK_CLASS_REMINDER_PREFIX}$scheduleId")
        workManager.cancelUniqueWork("${WORK_CLASS_START_PREFIX}$scheduleId")
        workManager.cancelAllWorkByTag("${WORK_CLASS_END_PREFIX}$scheduleId")
    }
}
