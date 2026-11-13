package com.monitorung.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyUsageMathTest {
    @Test
    fun splitsClassAndOutsideMinutes() {
        val (scheduled, outside) = usageMinutesBySchedule(
            intervals = listOf(0L until 120L * 60_000L),
            windows = listOf(60L * 60_000L until 90L * 60_000L),
        )
        assertEquals(30, scheduled)
        assertEquals(90, outside)
    }
}
