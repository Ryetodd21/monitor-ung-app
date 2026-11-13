package com.monitorung.app.service

import com.monitorung.app.service.NotificationScheduler.Companion.nextClassOccurrence
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Guards when the class reminder (2h before) and class start (5m before) fire.
 * Every case here was a real failure mode: reminders silently skipped on a
 * non-class weekday, and a worker re-arming itself onto the moment it just
 * fired (which would loop).
 *
 * 2026-10-28 is a Wednesday (ISO 3).
 */
class NextClassOccurrenceTest {

    @Test
    fun `rearmed timestamps belong to the new occurrence and classes stay separate`() {
        val first = com.monitorung.app.domain.model.ClassSession(
            scheduleId = "one", courseName = "First", room = "A", dayOfWeek = 3,
            startTime = "08:00", endTime = "10:00", startTimestamp = 1, endTimestamp = 2,
        )
        val start = LocalDateTime.of(2026, 11, 4, 8, 0)
        val next = NotificationScheduler.occurrenceSession(first, start)
        val second = NotificationScheduler.occurrenceSession(
            first.copy(scheduleId = "two", startTime = "10:00", endTime = "12:00"), start.plusHours(2),
        )
        assertEquals(start.atZone(java.time.ZoneId.of("Asia/Makassar")).toInstant().toEpochMilli(), next.startTimestamp)
        assertEquals(2 * 3_600_000L, next.endTimestamp - next.startTimestamp)
        assertEquals(next.endTimestamp, second.startTimestamp)
        assertEquals("two", second.scheduleId)
    }

    @Test
    fun `nightly work targets 22 WITA and rearms tomorrow`() {
        val night = LocalDateTime.of(2026, 11, 13, 22, 0)
        assertEquals(night, NotificationScheduler.nextAutoSync(night.minusHours(12)))
        assertEquals(night.plusDays(1), NotificationScheduler.nextAutoSync(night))
        assertEquals(night.plusDays(1), NotificationScheduler.nextAutoSync(night.plusMinutes(1)))
    }

    private val wednesday: LocalDateTime = LocalDateTime.of(2026, 10, 28, 9, 0)
    private val eight = LocalTime.of(8, 0)

    @Test
    fun `same week day later today`() {
        // Wednesday 09:00, class Wednesday 14:00 → today.
        assertEquals(
            LocalDateTime.of(2026, 10, 28, 14, 0),
            nextClassOccurrence(3, LocalTime.of(14, 0), wednesday),
        )
    }

    @Test
    fun `same week day already past rolls a full week`() {
        // Wednesday 09:00, class Wednesday 08:00 → next Wednesday.
        assertEquals(
            LocalDateTime.of(2026, 11, 4, 8, 0),
            nextClassOccurrence(3, eight, wednesday),
        )
    }

    @Test
    fun `later in the same week`() {
        // Wednesday → Friday (ISO 5) = +2 days.
        assertEquals(
            LocalDateTime.of(2026, 10, 30, 8, 0),
            nextClassOccurrence(5, eight, wednesday),
        )
    }

    @Test
    fun `earlier in the week wraps to next week`() {
        // Wednesday → Monday (ISO 1) = +5 days, not -2.
        assertEquals(
            LocalDateTime.of(2026, 11, 2, 8, 0),
            nextClassOccurrence(1, eight, wednesday),
        )
    }

    @Test
    fun `rearming at its own target moment lands next week`() {
        // The worker wakes at exactly the minute it scheduled. Without the
        // minDaysAhead guard it would resolve to later today and re-fire.
        val noon = LocalTime.of(12, 0)
        assertEquals(
            LocalDateTime.of(2026, 11, 4, 12, 0),
            nextClassOccurrence(3, noon, LocalDateTime.of(2026, 10, 28, 12, 0), minDaysAhead = 7),
        )
    }

    @Test
    fun `scheduling from login leaves a later class today alone`() {
        // Login path passes minDaysAhead = 0: today's 14:00 class stays today.
        assertEquals(
            LocalDateTime.of(2026, 10, 28, 14, 0),
            nextClassOccurrence(3, LocalTime.of(14, 0), wednesday, minDaysAhead = 0),
        )
    }
}
