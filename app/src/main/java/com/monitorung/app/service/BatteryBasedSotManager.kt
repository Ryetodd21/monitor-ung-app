package com.monitorung.app.service

import androidx.core.content.edit
import android.content.BroadcastReceiver
import android.util.Log
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.monitorung.app.data.local.dao.AppUsageLogDao
import com.monitorung.app.data.local.entity.AppUsageLogEntity
import com.monitorung.app.data.lock.UnlockTracker
import com.monitorung.app.data.snapshot.UsageSnapshotStore
import com.monitorung.app.domain.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import com.monitorung.app.util.AppCategorizer

/**
 * Battery-based screen time tracking — lightweight alternative to full UsageStatsManager scanning.
 *
 * Android's `BATTERY_STATS` doesn't expose total screen-on time per day directly,
 * but we can infer it from `ACTION_SCREEN_ON/OFF` broadcast events accumulated
 * during the active window (07:00 - 22:00 WITA). This is more battery-efficient
 * than `UsageStatsManager.queryUsageStats` which scans all apps.
 *
 * Plus, we collect app usage via `UsageStatsManager` ONLY for the apps we care
 * about (social media + games), not all apps — saving resources.
 */
@Singleton
class BatteryBasedSotManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appUsageLogDao: AppUsageLogDao,
    private val authRepository: AuthRepository,
    private val unlockTracker: UnlockTracker,
    // Shared per-package UsageStats snapshot. Round 17 fix.
    private val snapshotStore: UsageSnapshotStore,
) {
    companion object {
        const val ACTIVE_WINDOW_START_HOUR = 7 // 07:00 WITA
        const val ACTIVE_WINDOW_END_HOUR = 22 // 22:00 WITA
        private const val PREFS = "battery_sot_prefs"
        private const val KEY_SCREEN_ON_MS = "screen_on_ms"
        private const val KEY_DATE = "date"
    }

    // screenOnTodayMs used to live only in memory, so every OS process kill
    // (common overnight) reset the day's total to zero. Persist it alongside
    // its date, same lazy-reset scheme as UnlockTracker: a stored date that
    // doesn't match today means the counter starts at 0.
    private val sotPrefs: android.content.SharedPreferences by lazy {
        com.monitorung.app.util.SecurePrefs.get(context, PREFS)
    }

    // Round 29 audit (P2 unaudited): same class of bug as Round 26
    // P0 #3 in `SmartMonitoringManager`. The previous `private val scope`
    // was safe today (no `scope.launch` callers) but if a future
    // contributor adds one, the second logout would reuse a dead
    // scope and silently fail. `@Volatile private var` + lazy
    // `ensureScope()` + `cancelChildren()` (not `cancel()`) makes
    // the pattern safe to grow into.
    @Volatile
    private var scope: CoroutineScope? = CoroutineScope(Dispatchers.Default + SupervisorJob())
    // Pin to WITA so day-rollover fires at 00:00 Asia/Makassar.
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = UsageSnapshotStore.WITA
    }
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _currentStatus = MutableStateFlow(BatterySotStatus())
    val status: StateFlow<BatterySotStatus> = _currentStatus.asStateFlow()

    private val screenOnTimestamps = mutableListOf<Long>() // timestamps when screen turned on
    private var lastScreenOnAt: Long? = null
    /** Date the persisted screen-turn-on total belongs to (null = none stored). */
    private val persistedDate: String?
        get() = sotPrefs.getString(KEY_DATE, null)

    /** Raw stored total, ignoring whether its date is today (used for rollover). */
    private val rawPersistedScreenOnMs: Long
        get() = sotPrefs.getLong(KEY_SCREEN_ON_MS, 0L)

    /** Today's accumulated screen-on millis, rehydrated from disk on first read. */
    private var screenOnTodayMs: Long
        get() {
            val today = dateFormat.format(Date())
            if (sotPrefs.getString(KEY_DATE, null) != today) return 0L
            return sotPrefs.getLong(KEY_SCREEN_ON_MS, 0L)
        }
        set(value) {
            sotPrefs.edit {
                putString(KEY_DATE, dateFormat.format(Date()))
                putLong(KEY_SCREEN_ON_MS, value)
            }
        }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    val now = System.currentTimeMillis()
                    lastScreenOnAt = now
                    screenOnTimestamps.add(now)
                    _currentStatus.value = _currentStatus.value.copy(
                        isScreenOn = true,
                        lastScreenOnAt = now,
                        todayScreenOnMs = calculateTodayScreenOnMs(now)
                    )
                }
                Intent.ACTION_SCREEN_OFF -> {
                    val now = System.currentTimeMillis()
                    val onAt = lastScreenOnAt
                    if (onAt != null) {
                        val duration = now - onAt
                        if (isWithinActiveWindow(onAt, now)) {
                            screenOnTodayMs += duration
                        }
                    }
                    lastScreenOnAt = null
                    _currentStatus.value = _currentStatus.value.copy(
                        isScreenOn = false,
                        lastScreenOnAt = null,
                        todayScreenOnMs = calculateTodayScreenOnMs(now)
                    )
                }
            }
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
                val charging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
                _currentStatus.value = _currentStatus.value.copy(
                    batteryLevel = pct,
                    isCharging = charging
                )
            }
        }
    }

    // Guard against double registration on process restart
    @Volatile
    private var receiversRegistered = false

    init {
        synchronized(this) {
            if (!receiversRegistered) {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                }
                context.registerReceiver(screenReceiver, filter)

                val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                context.registerReceiver(batteryReceiver, batteryFilter)

                receiversRegistered = true
            }
        }
    }

    /**
     * Collect screen-on-time + app usage for apps we care about (sosmed + games)
     * for the entire active window (07:00 - 22:00).
     * Returns screen-on time in minutes for the active window.
     */
    suspend fun collectSotForActiveWindow(): BatterySotResult {
        val today = dateFormat.format(Date())
        val now = System.currentTimeMillis()

        // Midnight crossing: persist yesterday's accumulated time before resetting.
        // Without this, any screenOnTodayMs built up before midnight is lost when
        // the date changes (e.g. BackgroundSotWorker fires at 00:30 and calls this).
        // Read the raw stored value, not `screenOnTodayMs` — the getter already
        // returns 0 once the date is stale, which would flush a zero.
        val staleDate = persistedDate
        val staleScreenOnMs = rawPersistedScreenOnMs
        if (staleDate != null && staleDate != today && staleScreenOnMs > 0) {
            persistDayData(staleDate, staleScreenOnMs, 0, 0, 0)
        }

        // Reset daily counter for the new day
        if (staleDate != today) {
            screenOnTodayMs = 0L // the date-keyed setter stamps today's date
            screenOnTimestamps.clear()
            lastScreenOnAt = null
        }

        // Close current session if screen is still on
        lastScreenOnAt?.let { onAt ->
            val duration = now - onAt
            if (isWithinActiveWindow(onAt, now)) {
                screenOnTodayMs += duration
            }
        }

        // Now collect app-level usage (only for sosmed + games — much less data)
        val (sosmedMin, gameMin, unlockCount) = collectTargetedAppUsage()

        return BatterySotResult(
            totalScreenOnMinutes = (screenOnTodayMs / 60_000).toInt(),
            socialMediaMinutes = sosmedMin,
            gameMinutes = gameMin,
            unlockCount = unlockCount,
            collectedAt = now
        )
    }

    /**
     * Persist one day's SOT data to the local DB under the given date.
     * Called when midnight is crossed so yesterday's accumulated screen-on time
     * is not lost. Uses the same idempotency-key pattern as the foreground collector.
     */
    private suspend fun persistDayData(
        logDate: String,
        screenOnMs: Long,
        sosmedMin: Int,
        gameMin: Int,
        unlockCount: Int,
    ) {
        val studentId = try {
            authRepository.getCurrentUser().first()?.studentId ?: return
        } catch (_: Exception) {
            return
        }
        val now = System.currentTimeMillis()
        // Round 26 audit (P0 #5): the midnight-crossing path used to call
        // appUsageLogDao.insert(entity) which has OnConflictStrategy.REPLACE.
        // For an existing (studentId, logDate) row that meant the running
        // sosmed/game/unlock counters from earlier collections were
        // overwritten with the new partial totals (only screenOnMs from
        // this battery manager; sosmed/game/unlock were passed as 0).
        // Switch to accumulateDay() so the day's totals add correctly
        // across collectors — matches the contract documented at
        // Daos.kt:17-20 ("do not call insert() directly with a fresh
        // AppUsageLogEntity for an existing (student, date)").
        val entity = AppUsageLogEntity(
            studentId = studentId,
            logDate = logDate,
            logTimestamp = now,
            totalScreenTimeMinutes = (screenOnMs / 60_000).toInt(),
            socialMediaTimeMinutes = sosmedMin,
            gameTimeMinutes = gameMin,
            otherAppTimeMinutes = ((screenOnMs / 60_000).toInt() - sosmedMin - gameMin).coerceAtLeast(0),
            unlockCount = unlockCount,
            usedDuringClass = false,
            scheduleId = null,
            deviceId = null,
            idempotencyKey = "$studentId|$logDate|$now",
            syncStatus = "PENDING"
        )
        try {
            // accumulateDay() reads existing day row, adds delta, and UPSERTs
            // in a single @Transaction. If no row exists, inserts the delta
            // as the initial day total. REPLACE semantics are not safe here.
            appUsageLogDao.accumulateDay(entity)
        } catch (e: Exception) {
            Log.e("BatteryBasedSotManager", "collectAndPersistOutsideClass accumulateDay failed", e)
        }
    }

    /**
     * Collect and persist SOT data for outside-class hours.
     * Folds a lightweight AppUsageLogEntity with usedDuringClass=false into
     * the day row. Called by BackgroundSotWorker every 4 hours outside class.
     */
    suspend fun collectAndPersistOutsideClass() {
        val result = collectSotForActiveWindow()
        val now = System.currentTimeMillis()
        // FIX C1: skip insert if no user is logged in — empty studentId creates orphan
        // records that cannot be filtered or synced.
        val studentId = try {
            authRepository.getCurrentUser().first()?.studentId ?: return
        } catch (_: Exception) {
            return
        }
        val today = dateFormat.format(Date(now))

        val entity = AppUsageLogEntity(
            studentId = studentId,
            logDate = today,
            logTimestamp = now,
            totalScreenTimeMinutes = result.totalScreenOnMinutes,
            socialMediaTimeMinutes = result.socialMediaMinutes,
            gameTimeMinutes = result.gameMinutes,
            otherAppTimeMinutes = (result.totalScreenOnMinutes - result.socialMediaMinutes - result.gameMinutes)
                .coerceAtLeast(0),
            unlockCount = result.unlockCount,
            usedDuringClass = false,
            scheduleId = null,
            deviceId = null,
            // Per-row key — same as SmartMonitoringManager — so dedup works regardless of
            // which collector (foreground service vs BackgroundSotWorker) inserted the row.
            idempotencyKey = "$studentId|$today|$now",
            syncStatus = "PENDING"
        )

        // accumulateDay, not insert(): insert() is REPLACE-on-conflict and the
        // table has a unique (student_id, log_date) index, so every 4-hour run
        // of BackgroundSotWorker would overwrite the whole day's totals with
        // this one collection instead of adding to them.
        try {
            appUsageLogDao.accumulateDay(entity)
        } catch (e: Exception) {
            Log.e("BatteryBasedSotManager", "collectAndPersistOutsideClass accumulateDay failed", e)
        }
    }

    /**
     * Collect app usage for ONLY social media + games packages (not all apps).
     * Uses UsageStatsManager scoped to our allowlist, with the shared
     * UsageSnapshotStore providing per-package DELTAS so we don't double-
     * count usage that UsageRepositoryImpl / SmartMonitoringManager already
     * captured this interval. Round 17 fix.
     */
    private suspend fun collectTargetedAppUsage(): Triple<Int, Int, Int> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager

        // Check permission
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val test = usageStatsManager.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_DAILY,
            cal.timeInMillis,
            System.currentTimeMillis()
        )
        if (test.isEmpty()) return Triple(0, 0, 0)

        val startMs = todayStartMillis() + ACTIVE_WINDOW_START_HOUR * 3600_000L
        val endMs = minOf(nowOrWindowEndMillis(), System.currentTimeMillis())

        val stats = usageStatsManager.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_DAILY,
            startMs,
            endMs
        )

        val sosmedPackages = AppCategorizer.getSocialMediaPackages()
        val gamePackages = AppCategorizer.getGamePackages()

        val today = dateFormat.format(Date())
        // Delegate delta computation to the shared snapshot store so this
        // collector doesn't race the other two against the same cumulative
        // totals.
        val cumulative = stats.associate { it.packageName to it.totalTimeInForeground }
        val (deltas, _) = snapshotStore.computeDeltas(today, cumulative)

        var sosmedMs = 0L
        var gameMs = 0L

        deltas.forEach { (pkg, deltaMs) ->
            val minutes = (deltaMs / 60_000).toInt()
            if (minutes == 0) return@forEach

            when {
                sosmedPackages.any { pkg.startsWith(it) } -> sosmedMs += deltaMs
                gamePackages.any { pkg.startsWith(it) } -> gameMs += deltaMs
            }
        }

        val unlocks = unlockTracker.getTodayCount()

        return Triple((sosmedMs / 60_000).toInt(), (gameMs / 60_000).toInt(), unlocks)
    }

    private fun todayStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun nowOrWindowEndMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, ACTIVE_WINDOW_END_HOUR)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun calculateTodayScreenOnMs(now: Long): Long {
        var total = screenOnTodayMs
        lastScreenOnAt?.let {
            total += (now - it)
        }
        return total
    }

    private fun isWithinActiveWindow(start: Long, end: Long): Boolean {
        // Active window is [startHour, endHour] inclusive on both ends. The old
        // `in START until END` was half-open at END — a SCREEN_OFF at exactly
        // 22:00 (the upper bound) dropped the entire accumulated session, and
        // any 22:30-23:00 session was fully excluded since neither endpoint
        // landed inside [7, 22).
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        val startHour = cal.get(Calendar.HOUR_OF_DAY)
        val calEnd = Calendar.getInstance().apply { timeInMillis = end }
        val endHour = calEnd.get(Calendar.HOUR_OF_DAY)
        return startHour in ACTIVE_WINDOW_START_HOUR..ACTIVE_WINDOW_END_HOUR ||
               endHour in ACTIVE_WINDOW_START_HOUR..ACTIVE_WINDOW_END_HOUR
    }

    fun destroy() {
        try {
            context.unregisterReceiver(screenReceiver)
            context.unregisterReceiver(batteryReceiver)
        } catch (e: IllegalArgumentException) {
            // Expected when unregisterReceiver is called twice (e.g. app process
            // killed while receivers are registered, then destroy() called again).
        }
        receiversRegistered = false
        // Round 29: cancelChildren() (not cancel()) so a future logout
        // can re-launch jobs on the same scope. Lazy re-init via
        // ensureScope() on first use.
        scope?.coroutineContext?.cancelChildren()
    }

    /**
     * Round 29: lazy scope initializer. Returns a live scope, creating
     * one if previous destroy()/logout() left it null (shouldn't
     * happen today since we only call cancelChildren, but kept for
     * defensive parity with SmartMonitoringManager's pattern).
     */
    private fun ensureScope(): CoroutineScope {
        scope?.let { return it }
        val newScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = newScope
        return newScope
    }

    /** Called on logout — equivalent to destroy() since all state is tied to user session. */
    fun logout() = destroy()
}

data class BatterySotStatus(
    val isScreenOn: Boolean = false,
    val lastScreenOnAt: Long? = null,
    val todayScreenOnMs: Long = 0L,
    val batteryLevel: Int = -1,
    val isCharging: Boolean = false,
)

data class BatterySotResult(
    val totalScreenOnMinutes: Int,
    val socialMediaMinutes: Int,
    val gameMinutes: Int,
    val unlockCount: Int,
    val collectedAt: Long,
)
