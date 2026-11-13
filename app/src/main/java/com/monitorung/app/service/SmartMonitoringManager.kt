package com.monitorung.app.service

import androidx.core.content.edit
import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import com.monitorung.app.data.local.dao.AppUsageLogDao
import com.monitorung.app.data.local.dao.LocationLogDao
import com.monitorung.app.data.local.entity.AppUsageLogEntity
import com.monitorung.app.data.local.entity.LocationLogEntity
import com.monitorung.app.data.lock.UnlockTracker
import com.monitorung.app.data.push.MqttPushClient
import com.monitorung.app.data.snapshot.UsageSnapshotStore
import com.monitorung.app.data.snapshot.classUsage
import com.monitorung.app.domain.model.ClassSession
import com.monitorung.app.domain.model.MonitoringState
import com.monitorung.app.domain.model.MonitoringStatus
import com.monitorung.app.domain.repository.AuthRepository
import com.monitorung.app.domain.repository.CampusConfigRepository
import com.monitorung.app.domain.repository.ScheduleRepository
import com.monitorung.app.presentation.MainActivity
import com.monitorung.app.util.AppCategorizer
import com.monitorung.app.util.SecurePrefs
import com.monitorung.app.util.formatDuration
import android.app.usage.UsageStatsManager
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartMonitoringManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val scheduleRepository: ScheduleRepository,
    private val campusConfigRepository: CampusConfigRepository,
    private val authRepository: AuthRepository,
    private val appUsageLogDao: AppUsageLogDao,
    private val locationLogDao: LocationLogDao,
    private val unlockTracker: UnlockTracker,
    private val snapshotStore: UsageSnapshotStore,
    private val mqttPushClient: MqttPushClient,
) {
    private val gson = Gson()

    // MQTT payload — serialized via Gson to avoid injection vulnerabilities
    // from string interpolation (e.g. a scheduleId containing a double-quote).
    private data class MqttLogPayload(
        val log_date: String,
        val total_screen_time_minutes: Long,
        val social_media_time_minutes: Long,
        val game_time_minutes: Long,
        val other_app_time_minutes: Long,
        val unlock_count: Long,
        val used_during_class: Boolean,
        val device_id: String?,
        val schedule_id: String?,
    )
    companion object {
        const val NOTIFICATION_CHANNEL_MONITORING = "monitor_ung_monitoring"
        const val NOTIFICATION_CHANNEL_REMINDER = "monitor_ung_reminder"
        const val NOTIFICATION_CHANNEL_SYNC = "monitor_ung_sync"
        private const val SESSION_PREFS = "monitor_session_prefs"
        private const val KEY_SID = "sid"
        private const val KEY_STATE = "state"
        private const val KEY_END_TS = "end_ts"
        private const val KEY_START_TS = "start_ts"
        private const val KEY_COURSE = "course"
        private const val KEY_ROOM = "room"
        private const val KEY_DOW = "dow"
        private const val KEY_START_TIME = "start_time"
        private const val KEY_END_TIME = "end_time"
        const val NOTIFICATION_ID_FOREGROUND = 1001
        const val NOTIFICATION_ID_MORNING = 1002
        const val NOTIFICATION_ID_CLASS_REMINDER = 1003
        const val NOTIFICATION_ID_SYNC_REMINDER = 1004
        const val NOTIFICATION_ID_LOCATION_ALERT = 1005
        // Location checks during class: every 45 min. Balanced for accuracy vs battery.
        // Round 28: kept as a FALLBACK path only — primary detection is now the
        // Android Geofencing API (hardware-assisted, no periodic wakeup).
        const val LOCATION_CHECK_INTERVAL_MS = 45 * 60 * 1000L // 45 min
        const val LOCATION_END_CHECK_MS = 5 * 60 * 1000L // 5 min before class ends
        // SOT query window must match the collection interval to avoid overlap/double-counting.
        const val SOT_CHECK_INTERVAL_MS = LOCATION_CHECK_INTERVAL_MS
        // Skip location checks when battery is below this threshold (unless charging)
        private const val BATTERY_THRESHOLD_LOCATION_MS = 15
        // Round 28: Geofence request ID for the active class. Single geofence
        // (the campus circle) covers the whole session. Replaced on every
        // class start so the radius picks up the latest server-side value
        // (admin can adjust campus geofence_radius_meters without app update).
        private const val GEOFENCE_REQUEST_ID = "monitor_ung_campus_geofence"
        // Geofence fallback expiration used when the session carries no future
        // endTimestamp. The normal path sizes the geofence to the session itself
        // (see registerGeofence) — a fixed 2h silently stopped reporting ENTER/EXIT
        // for the third hour of a longer lab block.
        private const val GEOFENCE_EXPIRATION_MS = 2 * 60 * 60 * 1000L
        // Geofence PendingIntent request code. Distinct from NOTIFICATION_ID_*
        // to avoid PendingIntent reuse collisions.
        private const val GEOFENCE_PENDING_INTENT_REQUEST_CODE = 2001
    }

    private val sessionPrefs: SharedPreferences by lazy {
        // EncryptedSharedPreferences — same scheme as auth tokens and DB
        // passphrase. Class schedule (course + room + timestamps) is sensitive
        // enough to warrant at-rest encryption on disk.
        SecurePrefs.get(context, SESSION_PREFS)
    }

    // Round 26 (P0 #7): scope is now @Volatile + nullable so destroy()/logout()
    // can cancel children without killing the manager's ability to launch
    // new jobs on the next login. ensureScope() lazily reinitializes on
    // first use after a cancel-children.
    @Volatile
    private var scope: CoroutineScope? = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var sotJob: Job? = null
    private var endOfClassJob: Job? = null
    private var locationCheckJob: Job? = null

    // Round 28: tracks whether the campus geofence is currently registered
    // with the Android Geofencing API. When true, `locationCheckJob` stays
    // null and the 45-minute polling loop is NOT started — geofence
    // transitions are event-driven via [GeofenceReceiver]. When false
    // (registration failed: permission denied, location services off,
    // etc.), the manager falls back to the legacy 45-min polling path.
    @Volatile
    private var geofenceActive: Boolean = false

    private val _status = MutableStateFlow(MonitoringStatus())
    val status: StateFlow<MonitoringStatus> = _status.asStateFlow()

    // Latest foreground-service notification built by this manager. The hosting Service reads
    // this and calls the real Service.startForeground() with it. Updating the value re-publishes
    // the same notification so the service can refresh its foreground anchor if needed.
    private val _currentNotification = MutableStateFlow<Notification?>(null)
    val currentNotification: StateFlow<Notification?> = _currentNotification.asStateFlow()

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // Round 28: GeofencingClient for the campus perimeter. Same package
    // (com.google.android.gms.location) as fusedLocationClient — no new
    // dependency. addGeofences() / removeGeofences() are async Tasks; we
    // attach listeners but don't block startClassMonitoring on success
    // because the 45-min polling fallback can pick up any missed events.
    private val geofencingClient: GeofencingClient by lazy {
        LocationServices.getGeofencingClient(context)
    }

    private val locationRequest: LocationRequest by lazy {
        LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, LOCATION_CHECK_INTERVAL_MS)
            .setMinUpdateIntervalMillis(LOCATION_CHECK_INTERVAL_MS)
            .build()
    }

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    // Pin to WITA (Asia/Makassar) so day-rollover fires at 00:00 WITA
    // regardless of the user's device TZ (avoids drift while traveling).
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = UsageSnapshotStore.WITA
    }
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    private val logDateTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {

        // FIX M5/L2: skip if channels already exist to avoid redundant work on every
        // manager init (Singleton is constructed once per process). Android's
        // createNotificationChannel is idempotent (no-op if exists) but checking first
        // avoids the IPC overhead of the system IPC call.
        val existingChannels = notificationManager.notificationChannels
        listOf(
            NotificationChannel(NOTIFICATION_CHANNEL_MONITORING, "Monitoring Aktif", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Status monitoring aplikasi" },
            NotificationChannel(NOTIFICATION_CHANNEL_REMINDER, "Pengingat Jadwal", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Pengingat jadwal kuliah" },
            NotificationChannel(NOTIFICATION_CHANNEL_SYNC, "Sinkronisasi Data", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Pengingat sinkronisasi data" },
        ).forEach { channel ->
            if (existingChannels.none { it.id == channel.id }) {
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    // ─── Morning notification at 07:00 WITA ──────────────────────────────────────
    fun showMorningNotification() {
        ensureScope().launch {
            val todaySchedules = scheduleRepository.getSchedulesByDay(getCurrentDayOfWeek()).first()
            if (todaySchedules.isEmpty()) {
                showReminderNotification(
                    NOTIFICATION_ID_MORNING,
                    "Selamat Pagi!",
                    "Tidak ada jadwal kuliah hari ini. Tetap semangat!",
                    NotificationCompat.PRIORITY_DEFAULT
                )
                return@launch
            }

            val firstClass = todaySchedules.firstOrNull()
            val locationInfo = if (!firstClass?.room.isNullOrEmpty()) {
                "Ruangan ${firstClass.room}"
            } else {
                ""
            }
            val timeInfo = if (firstClass != null) {
                "${firstClass.startTime} - ${firstClass.endTime}"
            } else {
                ""
            }

            showReminderNotification(
                NOTIFICATION_ID_MORNING,
                "Selamat Pagi!",
                "${firstClass?.courseName ?: "Jadwal"}\n$locationInfo $timeInfo",
                NotificationCompat.PRIORITY_DEFAULT
            )
        }
    }

    // ─── Class reminder 2h before ──────────────────────────────────────────────
    fun showClassReminderNotification(session: ClassSession) {
        showReminderNotification(
            NOTIFICATION_ID_CLASS_REMINDER,
            "Jadwal Kuliah Mendatang",
            "${session.courseName}\nRuangan ${session.room}\n${session.startTime} - ${session.endTime}",
            NotificationCompat.PRIORITY_HIGH
        )
    }

    // ─── Start monitoring during the class window ──────────────────────────────
    @Synchronized
    fun startClassMonitoring(session: ClassSession) {
        val now = System.currentTimeMillis()
        if (now < session.startTimestamp || now >= session.endTimestamp) return
        if (_status.value.currentSession == session && sotJob?.isActive == true) return
        val previous = _status.value.currentSession
        stopAll()
        // Flush the previous class before its last periodic tick, without touching the new session.
        if (previous != null && previous != session) ensureScope().launch { collectSotData(previous) }
        _status.update {
            it.copy(
                state = MonitoringState.IN_CLASS,
                currentSession = session,
                lastLocationCheck = System.currentTimeMillis()
            )
        }
        // Persist session so service can auto-restart after process death (swipe-away)
        sessionPrefs.edit().apply {
            putString(KEY_SID, session.scheduleId)
            putString(KEY_STATE, MonitoringState.IN_CLASS.name)
            putLong(KEY_END_TS, session.endTimestamp)
            putLong(KEY_START_TS, session.startTimestamp)
            putString(KEY_COURSE, session.courseName)
            putString(KEY_ROOM, session.room)
            putInt(KEY_DOW, session.dayOfWeek)
            putString(KEY_START_TIME, session.startTime)
            putString(KEY_END_TIME, session.endTime)
            apply()
        }
        val notification = createMonitoringNotification(session)
        _currentNotification.value = notification
        notificationManager.notify(NOTIFICATION_ID_FOREGROUND, notification)

        // Collect SOT on a repeating interval for the whole class. Storing the
        // Job so stopAll() can cancel it — otherwise a rapid start/stop/start
        // cycle leaves orphan coroutines running and the SOT collection rate
        // doubles (or worse). Without the loop this ran exactly once per class
        // session, so a 2-hour class reported only the last 45-minute window.
        sotJob?.cancel()
        sotJob = ensureScope().launch {
            while (isActive && _status.value.currentSession == session && System.currentTimeMillis() < session.endTimestamp) {
                collectSotData(session)
                delay(SOT_CHECK_INTERVAL_MS)
            }
        }

        // Coarse location is the declared permission; platform geofences require fine location.
        startPollingFallback(session)
        scheduleEndOfClassCheck(session)
    }

    /**
     * Build the PendingIntent that the OS will fire on every
     * geofence transition. Re-used across register/remove calls so
     * removeGeofences() targets the exact same intent — otherwise we'd
     * leak geofences on every class cycle.
     */
    private fun geofencePendingIntent(): PendingIntent {
        val intent = Intent(context, com.monitorung.app.receiver.GeofenceReceiver::class.java)
        // FLAG_UPDATE_CURRENT + FLAG_MUTABLE is the recommended combo
        // for API 31+ (Android 12+ requires explicit mutable/immutable).
        return PendingIntent.getBroadcast(
            context,
            GEOFENCE_PENDING_INTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    /**
     * Public entry point called by [GeofenceReceiver] when Android
     * reports a geofence transition. Updates state, logs the location
     * row, and shows the OUTSIDE_CLASS alert if the user left campus.
     */
    fun onGeofenceEnter(location: android.location.Location?) {
        // Round 30 audit (P2): guard with geofenceActive. If a stray
        // broadcast arrives after stopAll() (e.g. Android delivered a
        // queued event after we tore down the receiver registration),
        // early-return instead of processing it. Otherwise the user
        // would see a phantom "back on campus" notification.
        if (!geofenceActive) return
        val session = _status.value.currentSession ?: return
        if (session.scheduleId != getPersistedScheduleId()) return
        android.util.Log.i("SmartMonitoringManager", "onGeofenceEnter: on campus")
        val androidLoc = location
        if (androidLoc != null) {
            logLocation(isOnCampus = true, matchesSchedule = true, session, androidLoc)
        }
        _status.update {
            it.copy(
                lastLocationCheck = System.currentTimeMillis(),
                wasOnCampus = true,
            )
        }
        if (_status.value.state == MonitoringState.OUTSIDE_CLASS) {
            _status.update { it.copy(state = MonitoringState.IN_CLASS) }
            updateForegroundNotification(session)
        }
    }

    fun onGeofenceExit(location: android.location.Location?) {
        // Round 30 audit (P2): see onGeofenceEnter — guard with geofenceActive.
        if (!geofenceActive) return
        val session = _status.value.currentSession ?: return
        if (session.scheduleId != getPersistedScheduleId()) return
        android.util.Log.i("SmartMonitoringManager", "onGeofenceExit: off campus")
        val androidLoc = location
        if (androidLoc != null) {
            logLocation(isOnCampus = false, matchesSchedule = false, session, androidLoc)
        }
        _status.update {
            it.copy(
                lastLocationCheck = System.currentTimeMillis(),
                wasOnCampus = false,
            )
        }
        if (_status.value.state == MonitoringState.IN_CLASS) {
            _status.update { it.copy(state = MonitoringState.OUTSIDE_CLASS) }
            updateForegroundNotification(session.copy(courseName = "${session.courseName} ⚠️"))
            showLocationAlert(session, isOnCampus = false)
        }
    }

    private fun getPersistedScheduleId(): String? =
        sessionPrefs.getString(KEY_SID, null)

    /** Coarse-location polling: initial check, then every 45 minutes. */
    private fun startPollingFallback(session: ClassSession) {
        if (locationCheckJob?.isActive == true) return
        locationCheckJob = ensureScope().launch {
            while (isActive && _status.value.currentSession == session && System.currentTimeMillis() < session.endTimestamp) {
                checkLocationDuringClass(session)
                delay(LOCATION_CHECK_INTERVAL_MS)
            }
        }
    }

    // ─── Location check during class ───────────────────────────────────────────
    private suspend fun checkLocationDuringClass(session: ClassSession) {
        // Battery-aware: skip location check if battery critically low and not charging.
        // SOT collection still runs — monitoring core functionality preserved.
        val batteryLevel = getBatteryLevel()
        val isCharging = isCharging()
        val skipLocation = batteryLevel < BATTERY_THRESHOLD_LOCATION_MS && !isCharging

        if (skipLocation) {
            // Still collect SOT but skip GPS to save battery
            collectSotData(session)
            return
        }

        val location = getCurrentLocation() ?: run {
            collectSotData(session)
            return
        }

        if (_status.value.currentSession != session || System.currentTimeMillis() >= session.endTimestamp) return

        val distance = calculateDistance(
            location.latitude, location.longitude,
            campusConfigRepository.getCampusLat(),
            campusConfigRepository.getCampusLon()
        )
        val isOnCampus = distance <= campusConfigRepository.getGeofenceRadiusMeters()

        // Pass the already-retrieved location so we don't re-fetch (H5 fix).
        // Include nanoTime in idempotency key when GPS is null to avoid collisions (H8 fix).
        logLocation(isOnCampus, matchesSchedule = isOnCampus, session, location)

        _status.update { it.copy(lastLocationCheck = System.currentTimeMillis(), wasOnCampus = isOnCampus) }

        if (!isOnCampus && _status.value.state == MonitoringState.IN_CLASS) {
            _status.update { it.copy(state = MonitoringState.OUTSIDE_CLASS) }
            updateForegroundNotification(session.copy(courseName = "${session.courseName} ⚠️"))
            showLocationAlert(session, isOnCampus)
        } else if (isOnCampus && _status.value.state == MonitoringState.OUTSIDE_CLASS) {
            _status.update { it.copy(state = MonitoringState.IN_CLASS) }
            updateForegroundNotification(session)
        }

        // Collect SOT every location check
        collectSotData(session)

    }

    private fun scheduleEndOfClassCheck(session: ClassSession) {
        endOfClassJob?.cancel()
        endOfClassJob = ensureScope().launch {
            delay((session.endTimestamp - LOCATION_END_CHECK_MS - System.currentTimeMillis()).coerceAtLeast(0))
            if (_status.value.currentSession != session) return@launch
            withTimeoutOrNull(10_000) { checkLocationDuringClass(session) }
            delay((session.endTimestamp - System.currentTimeMillis()).coerceAtLeast(0))
            if (_status.value.currentSession != session) return@launch
            collectSotData(session)
            finishSession(session)
        }
    }

    suspend fun completeClass(session: ClassSession): Boolean {
        if (session.startTimestamp <= 0 || System.currentTimeMillis() < session.endTimestamp) return false
        val collected = collectSotData(session)
        finishSession(session)
        return collected
    }

    @Synchronized
    private fun finishSession(session: ClassSession) {
        if (_status.value.currentSession != session) return
        endOfClassJob = null
        stopMonitoring()
    }

    fun clearForegroundNotification() {
        notificationManager.cancel(NOTIFICATION_ID_FOREGROUND)
    }

    // ─── Outside class — SOT only ────────────────────────────────────────────
    fun setOutsideClass(session: ClassSession) {
        startClassMonitoring(session)
        if (_status.value.currentSession == session) {
            _status.update { it.copy(state = MonitoringState.OUTSIDE_CLASS) }
            sessionPrefs.edit {
                putString(KEY_STATE, MonitoringState.OUTSIDE_CLASS.name)
            }
        }
    }

    // ─── Stop monitoring ────────────────────────────────────────────────────────
    @Synchronized
    fun stopMonitoring() {
        stopAll()
        sessionPrefs.edit {
            clear()
        }
        _currentNotification.value = null
        _status.update { MonitoringStatus() }
    }

    private fun stopAll() {
        endOfClassJob?.cancel()
        endOfClassJob = null
        sotJob?.cancel()
        sotJob = null
        locationCheckJob?.cancel()
        locationCheckJob = null
        // Round 28: also clear the Android Geofencing API registration
        // when the service stops. removeGeofences(pendingIntent) targets
        // the same PendingIntent we built in geofencePendingIntent() —
        // passing a different intent would leak the registration. Done
        // fire-and-forget because we don't need to block stopAll() on
        // the OS callback (the geofence is best-effort cleanup).
        if (geofenceActive) {
            geofenceActive = false
            try {
                geofencingClient.removeGeofences(geofencePendingIntent())
            } catch (e: SecurityException) {
                // Permission was revoked between registration and stop.
                // Nothing we can do — the OS drops the geofence on its own
                // when the requesting app loses permission.
                android.util.Log.w("SmartMonitoringManager", "removeGeofences: ${e.message}")
            }
        }
        notificationManager.cancel(NOTIFICATION_ID_FOREGROUND)
    }

    // ─── SOT collection (always runs) ────────────────────────────────────────
    private suspend fun collectSotData(session: ClassSession): Boolean {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            if (!hasUsageStatsPermission(usageStatsManager)) return false

            val now = System.currentTimeMillis()
            val end = minOf(now, session.endTimestamp)
            if (end <= session.startTimestamp) return false
            val usage = usageStatsManager.classUsage(session.startTimestamp, end, context.packageName)
            val today = java.time.Instant.ofEpochMilli(session.startTimestamp)
                .atZone(java.time.ZoneId.of("Asia/Makassar")).toLocalDate().toString()
            val totalScreenTime = usage.values.sum() / 60_000
            val socialMediaTime = usage.filterKeys { AppCategorizer.categorize(it) == "social_media" }.values.sum() / 60_000
            val gameTime = usage.filterKeys { AppCategorizer.categorize(it) == "game" }.values.sum() / 60_000

            val unlockCount = unlockTracker.getTodayCount()

            val studentId = authRepository.getCurrentUser().first()?.studentId ?: return false

            val entity = AppUsageLogEntity(
                studentId = studentId,
                logDate = today,
                logTimestamp = now,
                totalScreenTimeMinutes = totalScreenTime.toInt(),
                socialMediaTimeMinutes = socialMediaTime.toInt(),
                gameTimeMinutes = gameTime.toInt(),
                otherAppTimeMinutes = (totalScreenTime - socialMediaTime - gameTime).toInt().coerceAtLeast(0),
                unlockCount = unlockCount,
                usedDuringClass = true,
                scheduleId = session.scheduleId,
                deviceId = null,
                // Server dedups by idempotency_key on /api/logs/batch. Format
                // studentId|logDate|logTimestamp — same row re-inserted after a
                // partial-failed sync gets the same key and dedups server-side.
                idempotencyKey = "$studentId|$today|$now",
                syncStatus = "PENDING"
            )
            currentCoroutineContext().ensureActive()
            appUsageLogDao.replaceDay(entity)

            // Real-time: push to MQTT so the website dashboard receives data
            // without waiting for the next REST /api/logs/batch sync.
            val logDateTime = today
            val otherAppMinutes = (totalScreenTime - socialMediaTime - gameTime).coerceAtLeast(0L)
            val inClass = true
            val payload = MqttLogPayload(
                log_date = logDateTime,
                total_screen_time_minutes = totalScreenTime,
                social_media_time_minutes = socialMediaTime,
                game_time_minutes = gameTime,
                other_app_time_minutes = otherAppMinutes,
                unlock_count = unlockCount.toLong(),
                used_during_class = inClass,
                device_id = null,
                schedule_id = entity.scheduleId,
            )
            mqttPushClient.publish("students/$studentId/logs", gson.toJson(payload))

            _status.update { if (it.currentSession == session) it.copy(lastSotCollectedAt = now) else it }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("SmartMonitoringManager", "collectSotData insert failed", e)
            false
        }
    }

    private fun logLocation(isOnCampus: Boolean, matchesSchedule: Boolean, session: ClassSession? = null, location: Location? = null) {
        ensureScope().launch {
            try {
                val studentId = authRepository.getCurrentUser().first()?.studentId ?: return@launch
                val now = System.currentTimeMillis()
                val recordedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(now))
                // Include nanoTime in idempotency key when coordinates are null to avoid
                // collisions from multiple failed location attempts all using 0.0/0.0.
                val idemKey = if (location != null) {
                    "$studentId|$recordedAt|${location.latitude}|${location.longitude}"
                } else {
                    "$studentId|$recordedAt|null|${System.nanoTime()}"
                }
                val entity = LocationLogEntity(
                    studentId = studentId,
                    recordedAt = recordedAt,
                    logTimestamp = now,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    campusId = campusConfigRepository.getCampusId(),
                    isOnCampus = isOnCampus,
                    matchesSchedule = matchesSchedule,
                    scheduleId = session?.scheduleId,
                    idempotencyKey = idemKey,
                    syncStatus = "PENDING"
                )
                locationLogDao.insert(entity)

                // Real-time: push to MQTT so the website dashboard receives data
                // without waiting for the next REST /api/logs/batch sync.
                // Round 26 (P2) audit: previously this was hand-rolled string
                // interpolation `"""..."""` with `campusId` and `recordedAt`
                // embedded directly. If either field ever contained a
                // double-quote, backslash, or control character, the resulting
                // JSON would be malformed (parse error on the server) or
                // worse — a hookable value if any field flows into SQL via
                // a misconfigured JSONB column. Use Gson (already imported,
                // class field `gson` is the singleton) for proper escaping.
                val campusId = campusConfigRepository.getCampusId() ?: "unknown"
                val locPayload = gson.toJson(
                    mapOf(
                        "recorded_at" to recordedAt,
                        "latitude" to location?.latitude,
                        "longitude" to location?.longitude,
                        "campus_id" to campusId,
                        "is_on_campus" to isOnCampus,
                        "matches_schedule" to matchesSchedule,
                    )
                )
                mqttPushClient.publish("students/$studentId/location", locPayload)
            } catch (e: Exception) {
                android.util.Log.e("SmartMonitoringManager", "logLocation insert failed", e)
            }
        }
    }

    // ─── Notifications ────────────────────────────────────────────────────────
    fun showSyncReminderNotification(pendingCount: Int, minutesUntil22: Int) {
        val title = if (minutesUntil22 <= 15) "Kirim Data Sekarang!" else "Pengingat Sinkronisasi"
        val text = if (pendingCount > 0) {
            "$pendingCount data menunggu di-sync. Kirim sebelum jam 22:00 WITA."
        } else {
            "Semua data sudah tersinkron."
        }
        showReminderNotification(
            NOTIFICATION_ID_SYNC_REMINDER,
            title,
            text,
            NotificationCompat.PRIORITY_HIGH
        )
    }

    private fun showLocationAlert(session: ClassSession, isOnCampus: Boolean) {
        val text = if (!isOnCampus) {
            "Anda terdeteksi di luar kampus saat jam kuliah ${session.courseName}"
        } else {
            "Anda kembali ke kampus saat jam kuliah ${session.courseName}"
        }
        showReminderNotification(
            NOTIFICATION_ID_LOCATION_ALERT,
            if (!isOnCampus) "Alert Lokasi" else "Kembali ke Kampus",
            text,
            NotificationCompat.PRIORITY_HIGH
        )
    }

    private fun showReminderNotification(id: Int, title: String, text: String, priority: Int) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_REMINDER)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setPriority(priority)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .build()

        notificationManager.notify(id, notification)
    }

    private fun createMonitoringNotification(session: ClassSession): Notification {
        val stateText = when (_status.value.state) {
            MonitoringState.IN_CLASS -> "Monitoring aktif, di kampus"
            MonitoringState.OUTSIDE_CLASS -> "Peringatan: Monitoring aktif, di luar kampus"
            else -> "Monitoring aktif"
        }

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = Intent(context, SmartMonitoringService::class.java).apply {
            action = SmartMonitoringService.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_MONITORING)
            .setContentTitle(session.courseName)
            .setContentText("$stateText\nRuangan ${session.room} · ${session.startTime}-${session.endTime}")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$stateText\nRuangan ${session.room} · ${session.startTime}-${session.endTime}"))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateForegroundNotification(session: ClassSession) {
        if (_status.value.state == MonitoringState.IDLE) return
        val notification = createMonitoringNotification(session)
        _currentNotification.value = notification
        notificationManager.notify(NOTIFICATION_ID_FOREGROUND, notification)
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────
    private fun getCurrentDayOfWeek(): Int {
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_WEEK)
        return if (day == Calendar.SUNDAY) 7 else day - 1
    }

    private suspend fun getCurrentLocation(): Location? {
        // Coarse accuracy depends on the device and can exceed the campus radius.
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return null

        return try {
            // Fast path: use the device's cached last-known fix first. Most
            // Android devices keep a fresh Location from system services
            // (e.g. cell-tower / wifi) that's good enough for a 500m geofence
            // and is returned synchronously without spinning up GPS. This
            // avoids the 1-3s blocking wait of `getCurrentLocation()` on
            // cold-start or when GPS is disabled indoors.
            val cached = fusedLocationClient.lastLocation.awaitResult()
            if (cached != null && (System.currentTimeMillis() - cached.time) < 5 * 60 * 1000) {
                return cached
            }
            // Slow path: request a fresh fix. Only used when cache is empty
            // or older than 5 minutes.
            val cancellationToken = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationToken.token
            ).awaitResult() ?: cached  // last-resort: return stale cache
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) { null }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    private fun hasUsageStatsPermission(usageStatsManager: UsageStatsManager): Boolean {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            calendar.timeInMillis,
            System.currentTimeMillis()
        )
        return stats.isNotEmpty()
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitResult(): T? {
        return try {
            this.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) { null }
    }

    /**
     * Returns battery percentage (0-100). Defaults to 100 if unavailable.
     * Safe to call from any thread.
     */
    private fun getBatteryLevel(): Int {
        return try {
            val intent = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED).let { filter ->
                context.registerReceiver(null, filter)
            }
            val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) level * 100 / scale else 100
        } catch (e: Exception) {
            100
        }
    }

    private fun isCharging(): Boolean {
        return try {
            val intent = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED).let { filter ->
                context.registerReceiver(null, filter)
            }
            val status = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            false
        }
    }

    fun destroy() {
        // Round 26 audit (P0 #7): scope.cancel() permanently kills the
        // CoroutineScope, and since SmartMonitoringManager is a @Singleton,
        // the next login reuses the same instance with a dead scope —
        // every ensureScope().launch { collectSotData(...) } then silently fails.
        // Mirrors the AuthRepositoryImpl.close() fix from round 9: cancel
        // children only, not the whole scope, and recreate the scope on
        // the next login via ensureScope().
        ensureScope().coroutineContext.cancelChildren()
    }

    /**
     * Called on logout. Stops all monitoring, clears session prefs,
     * and cancels active coroutine jobs so no orphan work continues.
     * Scope itself is preserved (cancelChildren, not cancel) so the
     * next login can launch new jobs on a still-alive scope.
     */
    fun logout() {
        stopAll()
        sessionPrefs.edit {
            clear()
        }
        _currentNotification.value = null
        _status.update { MonitoringStatus() }
        // Snapshot reset is handled centrally by LoginViewModel via
        // UsageSnapshotStore.reset() — calling it here would race against
        // any in-flight collectSotData() coroutine still draining its map.
        // Round 26 (P0 #7): same cancelChildren pattern as destroy() above.
        ensureScope().coroutineContext.cancelChildren()
    }

    /**
     * Returns the current scope, creating it on first use. Hilt keeps this
     * manager as a @Singleton, so on second-and-later logins the existing
     * scope is reused (and stay-alive is the whole point of the round-26
     * cancelChildren fix).
     */
    private fun ensureScope(): CoroutineScope {
        if (scope == null) {
            scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        }
        return scope!!
    }
}
