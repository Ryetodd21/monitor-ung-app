package com.monitorung.app.presentation.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The schedule tab must open unfiltered. A student whose only class is on
 * Saturday saw an empty tab when the default was "today", and concluded the
 * schedule never arrived.
 */
class ScheduleDefaultFilterTest {
    @Test
    fun `tab opens showing every day, not today`() {
        assertEquals(0, ScheduleUiState().selectedDay)
        assertEquals(0, ScheduleUiState().schedules.size)
    }
}
