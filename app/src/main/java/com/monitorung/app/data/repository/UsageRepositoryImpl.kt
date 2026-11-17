package com.monitorung.app.data.repository

import androidx.core.content.edit
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.monitorung.app.data.local.dao.AppUsageLogDao
import com.monitorung.app.data.local.dao.LocationLogDao
import com.monitorung.app.data.local.entity.AppUsageLogEntity
import com.monitorung.app.data.local.entity.LocationLogEntity
import com.monitorung.app.data.lock.UnlockTracker
import com.monitorung.app.data.remote.api.MonitorUNGApi
import com.monitorung.app.data.remote.dto.AppUsageLogDto
import com.monitorung.app.data.remote.dto.BatchLogRequest
import com.monitorung.app.data.remote.dto.LocationLogDto
import com.monitorung.app.data.snapshot.UsageSnapshotStore
import com.monitorung.app.data.snapshot.classUsage
import com.monitorung.app.domain.model.AppUsageLog
import com.monitorung.app.domain.model.DailyUsageBreakdown
import com.monitorung.app.domain.model.Schedule
import com.monitorung.app.domain.model.SyncState
import com.monitorung.app.domain.model.SyncStatus
import com.monitorung.app.domain.model.TodayAggregate
import com.monitorung.app.domain.model.ThirtyDayAggregate
import com.monitorung.app.domain.repository.AuthRepository
import com.monitorung.app.domain.repository.CampusConfigRepository
import com.monitorung.app.domain.repository.ScheduleRepository
import com.monitorung.app.domain.repository.UsageRepository
import com.monitorung.app.util.AppCategorizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flowOn
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appUsageLogDao: AppUsageLogDao,
    private val locationLogDao: LocationLogDao,
    private val api: MonitorUNGApi,
    private val authRepository: AuthRepository,
    private val scheduleRepository: ScheduleRepository,
    private val campusConfigRepository: CampusConfigRepository,
    private val unlockTracker: UnlockTracker,
    // Shared per-package UsageStats snapshot. Round 17 fix: previous design
    // had three collectors each maintaining their own private snapshot map,
    // causing 2-3× inflation of cumulative totals per day.
    private val snapshotStore: UsageSnapshotStore,
) : UsageRepository {

    private val usageStatsManager: UsageStatsManager by lazy {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    }

    // Pin to WITA (Asia/Makassar) so day-rollover fires at 00:00 WITA
    // regardless of the user's device TZ (avoids drift while traveling).
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = UsageSnapshotStore.WITA
    }

    override suspend fun collectAppUsage(): Result<AppUsageLog> {
        return try {
            if (!hasUsageStatsPermission()) {
                return Result.failure(Exception("PACKAGE_USAGE_STATS permission not granted"))
            }

            val now = System.currentTimeMillis()
            val currentClass = scheduleRepository.getCurrentClass()
            val zone = java.time.ZoneId.of("Asia/Makassar")
            val date = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

            // Tanpa kelas aktif, tetap koleksi pemakaian sepanjang hari. Sebelumnya
            // fungsi ini langsung gagal dengan "No active class schedule", sehingga
            // Beranda tidak pernah menampilkan data apa pun saat mahasiswa memakai HP
            // di luar jam kuliah.
            val usage: Map<String, Long>
            val windowStart: Long
            val windowEnd: Long
            if (currentClass != null) {
                val classStart = date.atTime(parseScheduleTime(currentClass.startTime)
                    ?: return Result.failure(Exception("Invalid class start"))).atZone(zone).toInstant().toEpochMilli()
                val classEnd = date.atTime(parseScheduleTime(currentClass.endTime)
                    ?: return Result.failure(Exception("Invalid class end"))).atZone(zone).toInstant().toEpochMilli()
                windowStart = classStart
                windowEnd = minOf(now, classEnd)
                usage = usageStatsManager.classUsage(windowStart, windowEnd, context.packageName)
            } else {
                windowStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
                windowEnd = now
                usage = usageStatsManager.classUsage(windowStart, windowEnd, context.packageName)
            }
            val total = (usage.values.sum() / 60_000).toInt()
            val social = (usage.filterKeys { AppCategorizer.categorize(it) == "social_media" }.values.sum() / 60_000).toInt()
            val game = (usage.filterKeys { AppCategorizer.categorize(it) == "game" }.values.sum() / 60_000).toInt()
            val studentId = authRepository.getCurrentUser().first()?.studentId
                ?: return Result.failure(Exception("No logged-in user"))
            val inClass = currentClass != null
            val entity = AppUsageLogEntity(
                studentId = studentId,
                logDate = date.toString(),
                logTimestamp = now,
                totalScreenTimeMinutes = total,
                socialMediaTimeMinutes = social,
                gameTimeMinutes = game,
                otherAppTimeMinutes = (total - social - game).coerceAtLeast(0),
                unlockCount = unlockTracker.getTodayCount(),
                usedDuringClass = inClass,
                scheduleId = currentClass?.id,
            )
            appUsageLogDao.replaceDay(entity)
            Result.success((appUsageLogDao.getByDate(studentId, date.toString(), currentClass?.id) ?: entity).toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getRecentUsage(limit: Int): Flow<List<AppUsageLog>> {
        return authRepository.getCurrentUser()
            .map { it?.studentId ?: "" }
            .distinctUntilChanged()
            .flatMapLatest { studentId ->
                appUsageLogDao.getRecent(studentId, limit).map { entities ->
                    entities.map { it.toDomain() }
                }
            }
    }

    /**
     * Day-level aggregate that powers the HomeScreen's metric tiles. Without
     * this, the UI showed `getRecent(3).firstOrNull()` — the LATEST 45-min
     * interval — which read as "screen time tidak sesuai" / "social media tidak
     * terbaca" / "game tidak terbaca" because users expected day totals.
     *
     * Sums the four time-minute columns; takes MAX(unlock_count) (the
     * underlying counter is itself a daily running total, so SUM would
     * double-count); OR-collapses `used_during_class` so a single class-time
     * collection flips the day flag.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getTodayAggregate(): Flow<TodayAggregate> {
        val today = dateFormat.format(Date())
        return authRepository.getCurrentUser()
            .map { it?.studentId ?: "" }
            .distinctUntilChanged()
            .flatMapLatest { studentId ->
                if (studentId.isEmpty()) {
                    kotlinx.coroutines.flow.flowOf(TodayAggregate.EMPTY)
                } else {
                    appUsageLogDao.getDayTotals(studentId, today).map { proj ->
                        if (proj.totalScreenTimeMinutes == 0 &&
                            proj.socialMediaTimeMinutes == 0 &&
                            proj.gameTimeMinutes == 0
                        ) {
                            TodayAggregate.EMPTY
                        } else {
                            TodayAggregate(
                                studentId = studentId,
                                logDate = today,
                                logTimestamp = System.currentTimeMillis(),
                                totalScreenTimeMinutes = proj.totalScreenTimeMinutes,
                                socialMediaTimeMinutes = proj.socialMediaTimeMinutes,
                                gameTimeMinutes = proj.gameTimeMinutes,
                                unlockCount = proj.unlockCount,
                                usedDuringClass = proj.usedDuringClass == 1,
                            )
                        }
                    }
                }
            }
    }

    override suspend fun syncPendingLogs(): Result<Int> {
        return try {
            val token = authRepository.getAccessToken()
                ?: return Result.failure(Exception("No token"))

            val studentId = authRepository.getCurrentUser().first()?.studentId ?: return Result.failure(Exception("No user"))
            val appUsagePending = appUsageLogDao.getPending(studentId, 100)
            val locationPending = locationLogDao.getPending(studentId, 100)

            if (appUsagePending.isEmpty() && locationPending.isEmpty()) {
                return Result.success(0)
            }

            val request = BatchLogRequest(
                deviceId = com.monitorung.app.util.installationId(context),
                idempotencyKey = UUID.randomUUID().toString(),
                appUsageLogs = appUsagePending.map { it.toDto() },
                locationLogs = locationPending.map { it.toDto() }
            )

            val response = api.postBatchLogs("Bearer $token", request)
            if (response.isSuccessful) {
                val body = response.body() ?: return Result.failure(Exception("Post batch logs response body is empty"))
                val syncedIds = body.syncedClientIds.toSet()
                appUsageLogDao.markSynced(appUsagePending.filter { "usage:${it.id}" in syncedIds })
                locationLogDao.markSynced(locationPending.filter { "location:${it.id}" in syncedIds }.map { it.id })

                // Persist last-sync timestamp so Settings UI can show
                // "Tersinkron · X menit lalu". Lives in regular SharedPreferences
                // (not Encrypted) — this is non-sensitive metadata.
                context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                    .edit {
                        putLong("last_sync_${studentId}", System.currentTimeMillis())
                    }

                Result.success(syncedIds.size)
            } else {
                Result.failure(Exception("Sync failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getDailyUsageBreakdown(): Flow<DailyUsageBreakdown> {
        val today = dateFormat.format(Date())
        return authRepository.getCurrentUser()
            .map { it?.studentId ?: "" }
            .distinctUntilChanged()
            .flatMapLatest { studentId ->
                if (studentId.isEmpty()) {
                    kotlinx.coroutines.flow.flowOf(DailyUsageBreakdown.EMPTY)
                } else {
                    appUsageLogDao.getDayTotals(studentId, today).mapLatest {
                        val schedules = scheduleRepository.getSchedulesByDay(currentDayOfWeek()).first()
                        calculateDailyUsageBreakdown(studentId, schedules)
                    }
                }
            }
            .flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    private fun currentDayOfWeek(): Int = Calendar.getInstance(UsageSnapshotStore.WITA).get(Calendar.DAY_OF_WEEK).let {
        if (it == Calendar.SUNDAY) 7 else it - 1
    }

    private suspend fun calculateDailyUsageBreakdown(
        studentId: String,
        schedules: List<Schedule>,
    ): DailyUsageBreakdown {
        val now = System.currentTimeMillis()
        val zone = UsageSnapshotStore.WITA
        val calendar = Calendar.getInstance(zone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dayStart = calendar.timeInMillis
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK).let { if (it == Calendar.SUNDAY) 7 else it - 1 }
        val scheduledClasses = schedules.filter { it.isActive && it.dayOfWeek == dayOfWeek }.mapNotNull { schedule ->
            val start = parseScheduleTime(schedule.startTime) ?: return@mapNotNull null
            val end = parseScheduleTime(schedule.endTime) ?: return@mapNotNull null
            val from = dayStart + start.hour * 3_600_000L + start.minute * 60_000L
            val to = dayStart + end.hour * 3_600_000L + end.minute * 60_000L
            if (to > from) schedule to (from until to) else null
        }
        val windows = scheduledClasses.map { it.second }
        val events = usageStatsManager.queryEvents(dayStart, now)
        val event = android.app.usage.UsageEvents.Event()
        val foreground = mutableMapOf<String, Long>()
        val intervals = mutableListOf<LongRange>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val packageName = event.packageName ?: continue
            when (event.eventType) {
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> foreground[packageName] = event.timeStamp
                android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED -> foreground.remove(packageName)?.let { start ->
                    if (event.timeStamp > start) intervals += start..event.timeStamp
                }
            }
        }
        foreground.values.forEach { start -> if (now > start) intervals += start..now }
        val (scheduledMinutes, outsideMinutes) = com.monitorung.app.domain.model.usageMinutesBySchedule(intervals, windows)
        val attended = scheduledClasses.count { (schedule, window) ->
            if (schedule.campusId != null) {
                locationLogDao.hasCampusPresence(studentId, schedule.campusId, window.first, minOf(window.last + 1, now))
            } else {
                locationLogDao.hasSchedulePresence(studentId, schedule.id, window.first, minOf(window.last + 1, now))
            }
        }
        return DailyUsageBreakdown(
            scheduledMinutes = scheduledMinutes,
            outsideScheduleMinutes = outsideMinutes,
            scheduledClassCount = windows.size,
            attendedClassCount = attended,
            absentClassCount = scheduledClasses.count { (_, window) -> window.last < now } -
                scheduledClasses.count { (schedule, window) ->
                    window.last < now && if (schedule.campusId != null) {
                        locationLogDao.hasCampusPresence(studentId, schedule.campusId, window.first, window.last + 1)
                    } else {
                        locationLogDao.hasSchedulePresence(studentId, schedule.id, window.first, window.last + 1)
                    }
                },
        )
    }

    private fun parseScheduleTime(value: String): java.time.LocalTime? = runCatching {
        java.time.LocalTime.parse(value.replace('.', ':').let { if (it.count { c -> c == ':' } == 1) "$it:00" else it })
    }.getOrNull()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun get30DayAggregate(): Flow<ThirtyDayAggregate> {
        return authRepository.getCurrentUser()
            .map { it?.studentId ?: "" }
            .distinctUntilChanged()
            .flatMapLatest { studentId ->
                if (studentId.isEmpty()) {
                    kotlinx.coroutines.flow.flowOf(ThirtyDayAggregate.EMPTY)
                } else {
                    appUsageLogDao.get30DayAggregate(studentId).map { proj ->
                        ThirtyDayAggregate(
                            activeDays = proj.activeDays,
                            totalScreenTimeMinutes = proj.totalScreenTimeMinutes,
                            totalSocialMediaMinutes = proj.socialMediaTimeMinutes,
                            totalGameMinutes = proj.gameTimeMinutes,
                            maxUnlockCount = proj.maxUnlockCount,
                            daysUsedDuringClass = proj.usedDuringClass,
                        )
                    }
                }
            }
    }

    /**
     * Recent geofence checks for the HomeScreen "Lokasi terakhir" indicator.
     * Backed by Room's Flow-on-table-change — no polling, no manual refresh.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getRecentLocations(limit: Int): Flow<List<com.monitorung.app.domain.model.LocationLog>> {
        return authRepository.getCurrentUser()
            .map { it?.studentId ?: "" }
            .distinctUntilChanged()
            .flatMapLatest { studentId ->
                if (studentId.isEmpty()) {
                    kotlinx.coroutines.flow.flowOf(emptyList())
                } else {
                    locationLogDao.getRecent(studentId, limit).map { entities ->
                        entities.map { it.toLocationDomain() }
                    }
                }
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getSyncState(): Flow<SyncState> {
        return authRepository.getCurrentUser()
            .map { user ->
                val studentId = user?.studentId ?: return@map SyncState(pendingCount = 0)
                val appPending = appUsageLogDao.getPendingCount(studentId)
                val locPending = locationLogDao.getPendingCount(studentId)
                val lastSync = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                    .getLong("last_sync_${studentId}", 0L)
                    .takeIf { it > 0L }
                SyncState(pendingCount = appPending + locPending, lastSyncTimestamp = lastSync)
            }
            .distinctUntilChanged()
    }

    /**
     * Safely parse a `sync_status` column value into [SyncStatus].
     *
     * `SyncStatus.valueOf` throws [IllegalArgumentException] if the string
     * doesn't match an enum constant. If a row in the DB ever holds a
     * value outside the current enum set (e.g. a stale migration, a
     * future enum value seen from a downgraded APK, or manual DB
     * edits), we'd crash during entity → domain mapping and lose the
     * entire row. Fall back to PENDING so the row stays usable and
     * the next sync attempt can re-evaluate.
     */
    private fun safeSyncStatus(value: String?): SyncStatus =
        if (value == null) SyncStatus.PENDING
        else runCatching { SyncStatus.valueOf(value) }.getOrDefault(SyncStatus.PENDING)

    /**
     * Cek izin Usage Access lewat AppOps. Sebelumnya memakai "queryUsageStats
     * kosong = izin tidak ada", yang salah: perangkat baru atau pengguna yang
     * belum membuka aplikasi apa pun juga menghasilkan daftar kosong, sehingga
     * koleksi ditolak padahal izin sudah diberikan.
     */
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        }
        if (mode == android.app.AppOpsManager.MODE_ALLOWED) return true
        // Sebagian OEM mengembalikan MODE_DEFAULT; anggap diberikan bila ada data.
        if (mode != android.app.AppOpsManager.MODE_DEFAULT) return false
        return runCatching {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                System.currentTimeMillis() - 86_400_000L,
                System.currentTimeMillis(),
            ).isNotEmpty()
        }.getOrDefault(false)
    }

    private fun AppUsageLogEntity.toDto() = AppUsageLogDto(
        clientId = "usage:$id",
        logDate = logDate,
        totalScreenTimeMinutes = totalScreenTimeMinutes,
        socialMediaTimeMinutes = socialMediaTimeMinutes,
        gameTimeMinutes = gameTimeMinutes,
        otherAppTimeMinutes = otherAppTimeMinutes,
        unlockCount = unlockCount,
        usedDuringClass = usedDuringClass,
        scheduleId = scheduleId
    )

    private fun LocationLogEntity.toDto() = LocationLogDto(
        clientId = "location:$id",
        recordedAt = recordedAt,
        latitude = latitude ?: 0.0,
        longitude = longitude ?: 0.0,
        campusId = campusId,
        isOnCampus = isOnCampus,
        matchesSchedule = matchesSchedule,
        scheduleId = scheduleId
    )

    private fun AppUsageLogEntity.toDomain() = AppUsageLog(
        id = id,
        studentId = studentId,
        logDate = logDate,
        logTimestamp = logTimestamp,
        totalScreenTimeMinutes = totalScreenTimeMinutes,
        socialMediaTimeMinutes = socialMediaTimeMinutes,
        gameTimeMinutes = gameTimeMinutes,
        unlockCount = unlockCount,
        usedDuringClass = usedDuringClass,
        syncStatus = safeSyncStatus(syncStatus)
    )

    private fun LocationLogEntity.toLocationDomain() = com.monitorung.app.domain.model.LocationLog(
        id = id,
        studentId = studentId,
        logTimestamp = logTimestamp,
        isOnCampus = isOnCampus,
        matchesSchedule = matchesSchedule,
        syncStatus = safeSyncStatus(syncStatus),
        latitude = latitude,
        longitude = longitude,
    )

}
