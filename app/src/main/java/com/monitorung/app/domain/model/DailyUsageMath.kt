package com.monitorung.app.domain.model

internal fun usageMinutesBySchedule(
    intervals: List<LongRange>,
    windows: List<LongRange>,
): Pair<Int, Int> {
    val scheduled = intervals.sumOf { interval ->
        windows.sumOf { window ->
            (minOf(interval.last, window.last) + 1L - maxOf(interval.first, window.first)).coerceAtLeast(0L)
        }
    } / 60_000L
    val total = intervals.sumOf { it.last - it.first + 1L } / 60_000L
    return scheduled.toInt() to (total - scheduled).toInt().coerceAtLeast(0)
}
