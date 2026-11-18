package com.monitorung.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * Regression cover for "Failed to submit request: 422".
 *
 * The Jadwal tab defaults to `selectedDay = 0` ("Semua"). The request form used
 * that value as `initialDay` and forwarded it straight to the server, which
 * rejects `day_of_week = 0` (`server/app/schemas/schedule_request.py`).
 */
class ScheduleRequestDraftTest {

    @Test
    fun `day filter 0 is never sent as day_of_week`() {
        // Friday, filter is "Semua".
        assertEquals(5, ScheduleRequestDefaults.normalizeDayOfWeek(preferred = 0, today = 5))
    }

    @Test
    fun `a concrete selected day is kept`() {
        assertEquals(6, ScheduleRequestDefaults.normalizeDayOfWeek(preferred = 6, today = 5))
    }

    @Test
    fun `out of range days fall back to today`() {
        assertEquals(3, ScheduleRequestDefaults.normalizeDayOfWeek(preferred = 9, today = 3))
        assertEquals(3, ScheduleRequestDefaults.normalizeDayOfWeek(preferred = -1, today = 3))
        // Even today is unusable: stay inside the server's accepted range.
        assertEquals(1, ScheduleRequestDefaults.normalizeDayOfWeek(preferred = 0, today = 0))
    }

    @Test
    fun `draft built from the Semua filter is valid`() {
        val draft = ScheduleRequestDraft(
            dayOfWeek = ScheduleRequestDefaults.normalizeDayOfWeek(preferred = 0, today = 2),
            startTime = "09:30",
            endTime = "11:30",
            subjectNameRaw = "Basis Data",
        )
        assertNull(draft.validationError())
        assertTrue(draft.isValid())
    }

    @Test
    fun `day_of_week 0 is rejected locally instead of round-tripping to a 422`() {
        val draft = ScheduleRequestDraft(
            dayOfWeek = 0,
            startTime = "09:30",
            endTime = "11:30",
            subjectNameRaw = "Basis Data",
        )
        assertEquals("Pilih hari kuliah terlebih dahulu.", draft.validationError())
    }

    @Test
    fun `times must match the server HH colon MM contract`() {
        // The server regex is ^\d{2}:\d{2}$ — "8:30" and "08:30:00" are 422s.
        assertFalse(ScheduleRequestDefaults.isValidTime("8:30"))
        assertFalse(ScheduleRequestDefaults.isValidTime("08:30:00"))
        assertFalse(ScheduleRequestDefaults.isValidTime("24:00"))
        assertFalse(ScheduleRequestDefaults.isValidTime("08:60"))
        assertTrue(ScheduleRequestDefaults.isValidTime("08:30"))
        assertTrue(ScheduleRequestDefaults.isValidTime("00:00"))
        assertTrue(ScheduleRequestDefaults.isValidTime("23:59"))
    }

    @Test
    fun `end time must be after start time`() {
        assertTrue(ScheduleRequestDefaults.isValidTimeRange("09:30", "11:30"))
        assertFalse(ScheduleRequestDefaults.isValidTimeRange("11:30", "09:30"))
        assertFalse(ScheduleRequestDefaults.isValidTimeRange("09:30", "09:30"))
    }

    @Test
    fun `default start time is always two digit and in the future`() {
        // 08:07 rounds up to 08:30, not "8:30".
        assertEquals("08:30", ScheduleRequestDefaults.defaultStartTime(LocalTime.of(8, 7)))
        // Exact half hour still moves forward: the default must not be in the past.
        assertEquals("09:30", ScheduleRequestDefaults.defaultStartTime(LocalTime.of(9, 0)))
        // Late slot wraps inside the day instead of producing "24:00".
        assertEquals("00:00", ScheduleRequestDefaults.defaultStartTime(LocalTime.of(23, 40)))
        // Every produced value must survive the server's validator.
        for (hour in 0..23) {
            for (minute in 0..59) {
                val value = ScheduleRequestDefaults.defaultStartTime(LocalTime.of(hour, minute))
                assertTrue("$hour:$minute -> $value", ScheduleRequestDefaults.isValidTime(value))
            }
        }
    }

    @Test
    fun `default end time is two hours later and stays valid`() {
        assertEquals("10:00", ScheduleRequestDefaults.defaultEndTime("08:00"))
        assertEquals("00:30", ScheduleRequestDefaults.defaultEndTime("22:30"))
        assertTrue(ScheduleRequestDefaults.isValidTime(ScheduleRequestDefaults.defaultEndTime("23:00")))
    }

    @Test
    fun `description and subject are required`() {
        val noSubject = ScheduleRequestDraft(
            dayOfWeek = 1,
            startTime = "09:30",
            endTime = "11:30",
        )
        assertEquals("Mata kuliah wajib diisi.", noSubject.validationError())

        val noDescription = ScheduleRequestDraft(
            dayOfWeek = 1,
            startTime = "09:30",
            endTime = "11:30",
            subjectId = "subject-1",
            description = "   ",
        )
        assertEquals("Deskripsi request tidak boleh kosong.", noDescription.validationError())
    }
}
