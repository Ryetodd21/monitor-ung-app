package com.monitorung.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.monitorung.app.data.local.dao.ScheduleDao
import com.monitorung.app.data.local.entity.ScheduleEntity
import com.monitorung.app.data.remote.api.MonitorUNGApi
import com.monitorung.app.data.remote.dto.ScheduleDto
import com.monitorung.app.domain.model.Schedule
import com.monitorung.app.domain.repository.AuthRepository
import com.monitorung.app.domain.repository.LecturerItem
import com.monitorung.app.domain.repository.ScheduleRepository
import com.monitorung.app.domain.repository.SubjectItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

private val Context.configDataStore: DataStore<Preferences> by preferencesDataStore(name = "config_prefs")

// Keyed by the caller's own studentId (users.id), never the server's students.id.
internal fun ScheduleDto.toEntity(ownerId: String) = ScheduleEntity(
    id = id,
    studentId = ownerId,
    dayOfWeek = dayOfWeek,
    startTime = startTime,
    endTime = endTime,
    campusId = campusId,
    room = room,
    subjectNameRaw = subjectNameRaw,
    lecturerNameRaw = lecturerNameRaw,
    subjectId = subjectId,
    lecturerId = lecturerId,
    isActive = isActive,
    subjectName = subjectName,
    lecturerName = lecturerName
)

@Singleton
class ScheduleRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val scheduleDao: ScheduleDao,
    private val api: MonitorUNGApi,
    private val authRepository: AuthRepository
) : ScheduleRepository {

    // getCurrentUser() hydrates from DataStore in the background, so a plain
    // first() on a cold start returns null and the fetch silently fails.
    private suspend fun getStudentId(): String? =
        withTimeoutOrNull(5_000) { authRepository.getCurrentUser().filterNotNull().first() }?.studentId

    override suspend fun fetchSchedules(): Result<List<Schedule>> {
        return try {
            val token = authRepository.getAccessToken() ?: return Result.failure(Exception("No token"))
            // FIX C1 + M2: skip if no user — empty studentId would orphan schedules.
            val studentId = getStudentId() ?: return Result.failure(Exception("No logged-in user"))
            val response = api.getSchedules("Bearer $token")
            if (response.isSuccessful) {
                val dtos = response.body() ?: emptyList()
                // Room key must be the app's own studentId (users.id from login), not the
                // server's students.id — reads and deletes filter by the auth studentId.
                val entities = dtos.map { it.toEntity(studentId) }
                // Atomic: scheduleDao().replaceAllForStudent runs deleteAll +
                // insertAll inside a single Room @Transaction so a process kill
                // between the two calls can no longer leave the user with zero
                // schedules.
                scheduleDao.replaceAllForStudent(studentId, entities)
                Result.success(dtos.map { it.toDomain() })
            } else {
                Result.failure(Exception("Failed to fetch schedules: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAllSchedules(): Flow<List<Schedule>> {
        return authRepository.getCurrentUser()
            .map { it?.studentId ?: "" }
            .distinctUntilChanged()
            .flatMapLatest { studentId ->
                scheduleDao.getAll(studentId).map { entities -> entities.map { it.toDomain() } }
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getSchedulesByDay(dayOfWeek: Int): Flow<List<Schedule>> {
        return authRepository.getCurrentUser()
            .map { it?.studentId ?: "" }
            .distinctUntilChanged()
            .flatMapLatest { studentId ->
                scheduleDao.getByDay(studentId, dayOfWeek).map { entities ->
                    entities.map { it.toDomain() }
                }
            }
    }

    override suspend fun getDirectory(): Result<Pair<List<SubjectItem>, List<LecturerItem>>> {
        return try {
            val token = authRepository.getAccessToken() ?: return Result.failure(Exception("No token"))
            val response = api.getScheduleDirectory("Bearer $token")
            if (response.isSuccessful) {
                val body = response.body() ?: return Result.failure(Exception("Schedule directory response body is empty"))
                val subjects = body.subjects.map { SubjectItem(it.id, it.name, it.code, it.campusId, it.campusName) }
                val lecturers = body.lecturers.map { LecturerItem(it.id, it.name, it.nidn) }
                Result.success(Pair(subjects, lecturers))
            } else {
                Result.failure(Exception("Failed to fetch directory: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteSchedule(scheduleId: String): Result<Unit> {
        return try {
            val token = authRepository.getAccessToken() ?: return Result.failure(Exception("No token"))
            val response = api.deleteSchedule("Bearer $token", scheduleId)
            if (response.isSuccessful) {
                scheduleDao.deleteById(scheduleId)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete schedule: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCurrentClass(): Schedule? {
        val studentId = getStudentId() ?: return null
        val now = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Makassar"))
        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
        val currentTime = LocalTime.now(java.time.ZoneId.of("Asia/Makassar"))
        val schedules = scheduleDao.getByDay(studentId, if (dayOfWeek == Calendar.SUNDAY) 7 else dayOfWeek - 1)
            .first()

        val match = schedules.firstOrNull { schedule ->
            val start = parseTime(schedule.startTime) ?: return@firstOrNull false
            val end = parseTime(schedule.endTime) ?: return@firstOrNull false
            schedule.isActive && !currentTime.isBefore(start) && currentTime.isBefore(end)
        } ?: return null
        return match.toDomain()
    }

    override suspend fun isCurrentlyInClass(): Boolean = getCurrentClass() != null

    private fun parseTime(value: String): LocalTime? = runCatching {
        LocalTime.parse(value.replace('.', ':').let { if (it.count { c -> c == ':' } == 1) "$it:00" else it })
    }.getOrNull()

    private fun ScheduleDto.toDomain() = Schedule(
        id = id,
        studentId = studentId,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        campusId = campusId,
        room = room,
        subjectNameRaw = subjectNameRaw,
        lecturerNameRaw = lecturerNameRaw,
        subjectId = subjectId,
        lecturerId = lecturerId,
        isActive = isActive,
        subjectName = subjectName,
        lecturerName = lecturerName
    )

    private fun ScheduleEntity.toDomain() = Schedule(
        id = id,
        studentId = studentId,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        campusId = campusId,
        room = room,
        subjectNameRaw = subjectNameRaw,
        lecturerNameRaw = lecturerNameRaw,
        subjectId = subjectId,
        lecturerId = lecturerId,
        isActive = isActive,
        subjectName = subjectName,
        lecturerName = lecturerName
    )
}

@Singleton
class CampusConfigRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: MonitorUNGApi,
    private val authRepository: AuthRepository
) : com.monitorung.app.domain.repository.CampusConfigRepository {

    private object ConfigKeys {
        val CAMPUS_LAT = doublePreferencesKey("campus_lat")
        val CAMPUS_LON = doublePreferencesKey("campus_lon")
        val GEOFENCE_RADIUS = intPreferencesKey("geofence_radius")
        val MQTT_BROKER_URL = stringPreferencesKey("mqtt_broker_url")
        val MQTT_BROKER_URL_FALLBACK = stringPreferencesKey("mqtt_broker_url_fallback")
        val CAMPUS_ID = stringPreferencesKey("campus_id")
    }

    private val dataStore = context.configDataStore

    // Defaults — UNG Kampus Utama. Used as fallback when DataStore is empty
    // (first launch, before `fetchConfig()` has completed at least once).
    @Volatile private var cachedCampusLat: Double = -0.5436
    @Volatile private var cachedCampusLon: Double = 123.0618
    @Volatile private var cachedGeofenceRadius: Int = 500
    @Volatile private var cachedMqttBrokerUrl: String = "tcp://10.0.2.2:1883"
    // Round 27: VPS mosquitto as auto-fallback. Empty = no fallback (older
    // servers, or admin chose to disable). MqttPushClient retries primary
    // first, on N consecutive failures switches to fallback.
    @Volatile private var cachedMqttBrokerFallbackUrl: String = ""
    @Volatile private var cachedCampusId: String? = null

    // Background hydration of the in-memory cache from DataStore. Uses a
    // SupervisorJob scope so a launch failure cannot tear down the repository.
    // The previous `runBlocking(IO)` blocked whatever thread Hilt happened to
    // construct this @Singleton on — on a slow-keystore device that meant a
    // measurable MainActivity startup hitch.
    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()
    )

    init {
        scope.launch {
            try {
                val prefs = dataStore.data.first()
                prefs[ConfigKeys.CAMPUS_LAT]?.let { cachedCampusLat = it }
                prefs[ConfigKeys.CAMPUS_LON]?.let { cachedCampusLon = it }
                prefs[ConfigKeys.GEOFENCE_RADIUS]?.let { cachedGeofenceRadius = it }
                prefs[ConfigKeys.MQTT_BROKER_URL]?.let { cachedMqttBrokerUrl = it }
                prefs[ConfigKeys.MQTT_BROKER_URL_FALLBACK]?.let { cachedMqttBrokerFallbackUrl = it }
                prefs[ConfigKeys.CAMPUS_ID]?.let { cachedCampusId = it }
            } catch (_: Exception) {
                // DataStore read failure → keep hardcoded defaults. fetchConfig()
                // will overwrite when the network round-trip lands.
            }
        }
    }

    override suspend fun fetchConfig(): Result<Unit> {
        return try {
            val token = authRepository.getAccessToken() ?: return Result.failure(Exception("No token"))
            val response = api.getCampusConfig("Bearer $token")
            if (response.isSuccessful) {
                val body = response.body() ?: return Result.failure(Exception("Campus config response body is empty"))
                dataStore.edit { prefs ->
                    prefs[ConfigKeys.CAMPUS_LAT] = body.campusLat
                    prefs[ConfigKeys.CAMPUS_LON] = body.campusLon
                    prefs[ConfigKeys.GEOFENCE_RADIUS] = body.geofenceRadiusMeters
                    prefs[ConfigKeys.MQTT_BROKER_URL] = body.mqttBrokerUrl
                    prefs[ConfigKeys.MQTT_BROKER_URL_FALLBACK] = body.mqttBrokerUrlFallback
                    if (body.campusId != null) {
                        prefs[ConfigKeys.CAMPUS_ID] = body.campusId
                    }
                }
                // Update in-memory cache so subsequent sync reads return the
                // fresh server-side values without waiting for DataStore to
                // re-emit. The DataStore write above is what persists them.
                cachedCampusLat = body.campusLat
                cachedCampusLon = body.campusLon
                cachedGeofenceRadius = body.geofenceRadiusMeters
                cachedMqttBrokerUrl = body.mqttBrokerUrl
                cachedMqttBrokerFallbackUrl = body.mqttBrokerUrlFallback
                cachedCampusId = body.campusId
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to fetch config: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getCampusLat(): Double = cachedCampusLat
    override fun getCampusLon(): Double = cachedCampusLon
    override fun getGeofenceRadiusMeters(): Int = cachedGeofenceRadius
    override fun getMqttBrokerUrl(): String = cachedMqttBrokerUrl

    /**
     * Round 27: VPS mosquitto fallback. Returns empty string if not configured
     * (server too old to return the field, or admin disabled).
     */
    override fun getMqttBrokerUrlFallback(): String = cachedMqttBrokerFallbackUrl
    override fun getCampusId(): String? = cachedCampusId
}
