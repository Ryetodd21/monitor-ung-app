package com.monitorung.app.data.snapshot

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager

/** Rebuild the class total after restart; daily UsageStats buckets include other classes. */
internal fun UsageStatsManager.classUsage(from: Long, to: Long, ownPackage: String): Map<String, Long> {
    if (to <= from) return emptyMap()
    // Include earlier events to recognize an app already open when class starts.
    val events = queryEvents(from - 86_400_000L, to)
    val event = UsageEvents.Event()
    val usage = ClassUsageWindow(from, to)
    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        when (event.eventType) {
            UsageEvents.Event.ACTIVITY_RESUMED -> usage.resume(event.packageName, event.timeStamp)
            UsageEvents.Event.ACTIVITY_PAUSED -> usage.pause(event.packageName, event.timeStamp)
            UsageEvents.Event.SCREEN_NON_INTERACTIVE -> usage.close(event.timeStamp)
        }
    }
    usage.close(to)
    return usage.millis.filterKeys { it != ownPackage }
}

internal class ClassUsageWindow(private val from: Long, private val to: Long) {
    val millis = mutableMapOf<String, Long>()
    private var foreground: String? = null
    private var started = from

    fun resume(pkg: String?, at: Long) {
        if (pkg == foreground) return
        close(at)
        foreground = pkg
        started = at
    }

    fun pause(pkg: String?, at: Long) {
        if (pkg == foreground) close(at)
    }

    fun close(at: Long) {
        foreground?.let { pkg ->
            val duration = (minOf(at, to) - maxOf(started, from)).coerceAtLeast(0)
            millis[pkg] = (millis[pkg] ?: 0) + duration
        }
        foreground = null
    }
}
