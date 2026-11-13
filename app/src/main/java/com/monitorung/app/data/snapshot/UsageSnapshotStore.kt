package com.monitorung.app.data.snapshot

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared singleton holding the per-package cumulative UsageStats snapshot
 * for the entire app.
 *
 * Three collectors (UsageRepositoryImpl, SmartMonitoringManager,
 * BatteryBasedSotManager) all query UsageStatsManager.INTERVAL_DAILY, which
 * returns the day's running cumulative total regardless of the time window
 * passed. Each one used to maintain a private `MutableMap<String, Long>`
 * snapshot and compute deltas against it — so two collectors running the
 * same minute each reported the same total as their own delta, inflating
 * the day's sum by 2-3×. Round 17 fix.
 *
 * Plus, in-memory snapshots were wiped on process death (Android can kill
 * the app overnight), so the next collection after a restart computed
 * `delta = total − 0` = the entire day's worth of usage, dumping it all into
 * the current interval. UsageSnapshotStore also handles the date-rollover
 * case (midnight WITA): when the date string changes, the map is cleared
 * before computing deltas, so each day starts fresh.
 */
@Singleton
class UsageSnapshotStore @Inject constructor() {

    private val mutex = Mutex()
    private val lastSnapshot: MutableMap<String, Long> = mutableMapOf()
    private var lastResetDate: String = ""

    /**
     * Atomically reads the previous snapshot for `today` and updates the map
     * with the new cumulative values. Returns a per-package delta in millis
     * along with a `wasReset` flag — `wasReset = true` means the snapshot map
     * was cleared because the date changed (or this is the very first call
     * after process restart with no persisted date), so callers should
     * expect the delta for the "anchor" packages to be the entire cumulative
     * total rather than an increment.
     */
    suspend fun computeDeltas(
        today: String,
        cumulativeByPackage: Map<String, Long>,
    ): Pair<Map<String, Long>, Boolean> = mutex.withLock {
        val wasReset = lastResetDate != today
        if (wasReset) {
            lastSnapshot.clear()
            lastResetDate = today
        }
        val deltas = mutableMapOf<String, Long>()
        cumulativeByPackage.forEach { (pkg, total) ->
            val prev = lastSnapshot[pkg] ?: 0L
            // Android can occasionally return a slightly smaller total than
            // the previous call (clock corrections, package reinstalls).
            // Treat any drop as a reset for that package, not as a negative
            // delta that would corrupt the running sum.
            val safePrev = if (total < prev) 0L else prev
            deltas[pkg] = (total - safePrev).coerceAtLeast(0L)
            lastSnapshot[pkg] = total
        }
        deltas to wasReset
    }

    /** Drop the entire snapshot. Call on logout so the next user starts clean. */
    fun reset() {
        lastSnapshot.clear()
        lastResetDate = ""
    }

    companion object {
        /** WITA timezone pinned so the rollover fires at 00:00 Asia/Makassar
         *  regardless of the user's device TZ (avoids drift while traveling). */
        val WITA: TimeZone = TimeZone.getTimeZone("Asia/Makassar")
    }
}