package com.monitorung.app.domain.model

/** Daily screen-time split against today's class windows. */
data class DailyUsageBreakdown(
    val scheduledMinutes: Int = 0,
    val outsideScheduleMinutes: Int = 0,
    val scheduledClassCount: Int = 0,
    val attendedClassCount: Int = 0,
    val absentClassCount: Int = 0,
) {
    companion object { val EMPTY = DailyUsageBreakdown() }
}
