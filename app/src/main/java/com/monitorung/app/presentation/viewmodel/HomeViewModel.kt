package com.monitorung.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monitorung.app.domain.model.AppUsageLog
import com.monitorung.app.domain.model.Gamification
import com.monitorung.app.domain.model.SyncState
import com.monitorung.app.domain.model.TodayAggregate
import com.monitorung.app.domain.model.ThirtyDayAggregate
import com.monitorung.app.domain.repository.LecturerItem
import com.monitorung.app.domain.repository.ScheduleRepository
import com.monitorung.app.domain.repository.ScheduleRequest
import com.monitorung.app.domain.repository.ScheduleRequestRepository
import com.monitorung.app.domain.repository.SubjectItem
import com.monitorung.app.domain.repository.UsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val todayUsage: TodayAggregate? = null,
    val dailyUsageBreakdown: com.monitorung.app.domain.model.DailyUsageBreakdown = com.monitorung.app.domain.model.DailyUsageBreakdown.EMPTY,
    val thirtyDayAggregate: ThirtyDayAggregate = ThirtyDayAggregate.EMPTY,
    val recentActivity: List<AppUsageLog> = emptyList(),
    val syncState: SyncState = SyncState(),
    val todaySchedules: List<com.monitorung.app.domain.model.Schedule> = emptyList(),
    val recentLocations: List<com.monitorung.app.domain.model.LocationLog> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isOffline: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val usageRepository: UsageRepository,
    private val scheduleRepository: ScheduleRepository,
    private val api: com.monitorung.app.data.remote.api.MonitorUNGApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            usageRepository.getSyncState().collect { syncState ->
                _uiState.update { it.copy(syncState = syncState) }
            }
        }
        // Day totals — not the latest interval delta. Without this the user
        // sees only the most-recent 45-min window, which reads as "screen
        // time tidak sesuai" / "social media tidak terbaca" / "game tidak
        // terbaca" even though the day's totals are healthy.
        viewModelScope.launch {
            usageRepository.getTodayAggregate().collect { aggregate ->
                _uiState.update { it.copy(todayUsage = aggregate) }
            }
        }
        viewModelScope.launch {
            usageRepository.getDailyUsageBreakdown().collect { breakdown ->
                _uiState.update { it.copy(dailyUsageBreakdown = breakdown) }
            }
        }
        // 30-day aggregate for the summary card.
        viewModelScope.launch {
            usageRepository.get30DayAggregate().collect { aggregate ->
                _uiState.update { it.copy(thirtyDayAggregate = aggregate) }
            }
        }
        // Riwayat harian. 30 baris disimpan supaya streak bisa dihitung penuh;
        // Beranda hanya menampilkan 3 terbaru lewat take(3) di HomeScreen.
        viewModelScope.launch {
            usageRepository.getRecentUsage(30).collect { logs ->
                _uiState.update { it.copy(recentActivity = logs) }
            }
        }
        // Most recent geofence checks — backs the "Lokasi terakhir" card on
        // HomeScreen so users can see the location pipeline is running.
        viewModelScope.launch {
            usageRepository.getRecentLocations(5).collect { locations ->
                _uiState.update { it.copy(recentLocations = locations) }
            }
        }
        // Hari bisnis memakai WITA, bukan timezone perangkat. `dayOfWeek.value`
        // sudah ISO (1=Senin…7=Minggu), sama dengan `day_of_week` di Room.
        // Sebelumnya memakai Calendar perangkat: di luar WITA jadwal "hari ini"
        // bisa bergeser sehari dan streak ikut salah di batas tengah malam.
        val todayDow = java.time.LocalDate.now(Gamification.WITA).dayOfWeek.value
        viewModelScope.launch {
            scheduleRepository.getSchedulesByDay(todayDow).collect { schedules ->
                _uiState.update { it.copy(todaySchedules = schedules) }
            }
        }
    }

    suspend fun refreshServerStatus() {
        val reachable = try {
            api.getSystemStatus().isSuccessful
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        _uiState.update { it.copy(isOffline = !reachable) }
    }

    fun collectUsage() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            usageRepository.collectAppUsage()
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    /**
     * Koleksi berkala tanpa mengubah status loading, supaya tombol dan skeleton
     * tidak berkedip tiap menit. Kegagalan tetap disimpan di [HomeUiState.error]
     * agar penyebabnya (mis. izin Usage Access) terlihat, bukan tampak "tanpa data".
     */
    fun refreshUsage() {
        viewModelScope.launch {
            usageRepository.collectAppUsage()
                .onSuccess { _uiState.update { it.copy(error = null) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            usageRepository.syncPendingLogs()
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    // FIX H5: re-query sync state so the pending count badge updates
                    // immediately instead of waiting for the next Flow emission tick.
                    usageRepository.getSyncState().first().let { state ->
                        _uiState.update { it.copy(syncState = state) }
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class HistoryUiState(
    val logs: List<AppUsageLog> = emptyList(),
    /** Sumber XP yang sama dengan Beranda (getTodayAggregate), supaya level cocok. */
    val todayScreenMinutes: Int = 0,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val usageRepository: UsageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            usageRepository.getRecentUsage(30).collect { logs ->
                _uiState.update { it.copy(logs = logs, isLoading = false) }
            }
        }
        viewModelScope.launch {
            usageRepository.getTodayAggregate().collect { aggregate ->
                _uiState.update { it.copy(todayScreenMinutes = aggregate.totalScreenTimeMinutes) }
            }
        }
    }

    /**
     * Pull-to-refresh mengoleksi pemakaian terbaru. Sebelumnya hanya menunda
     * selama beberapa ratus ms lalu mematikan spinner tanpa mengambil data apa pun,
     * jadi daftar histori tidak pernah berubah setelah ditarik.
     */
    fun onRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            usageRepository.collectAppUsage()
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
}

data class ScheduleUiState(
    /** Filtered rows for the selected day, or every row when [selectedDay] is 0. */
    val schedules: List<com.monitorung.app.domain.model.Schedule> = emptyList(),
    /** 0 = show every day (default), 1..7 = ISO day filter. */
    val selectedDay: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val subjects: List<SubjectItem> = emptyList(),
    val lecturers: List<LecturerItem> = emptyList(),
    val isLoadingDirectory: Boolean = false,
    val actionSuccess: String? = null,
    val scheduleRequests: List<ScheduleRequest> = emptyList(),
    val isLoadingRequests: Boolean = false
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val scheduleRequestRepository: ScheduleRequestRepository,
    private val notificationScheduler: com.monitorung.app.service.NotificationScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    // Cancel previous day-list collector on every selectDay() — fixes collector leak.
    private var dayJob: Job? = null

    init {
        loadSchedules()
        loadMyRequests()
        // Start unfiltered: a student must see their whole week without guessing
        // which day chip holds it. The day row only narrows the list afterwards.
        selectDay(0)
    }

    fun loadSchedules() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            scheduleRepository.fetchSchedules()
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    // Re-collect so a pull-to-refresh shows the new rows.
                    selectDay(_uiState.value.selectedDay)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    /** [day] 0 shows every schedule; 1..7 filters to that ISO day. */
    fun selectDay(day: Int) {
        _uiState.update { it.copy(selectedDay = day) }
        dayJob?.cancel()
        dayJob = viewModelScope.launch {
            val source = if (day == 0) scheduleRepository.getAllSchedules()
                         else scheduleRepository.getSchedulesByDay(day)
            source.collect { schedules ->
                _uiState.update { it.copy(schedules = schedules) }
            }
        }
    }

    fun loadDirectory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDirectory = true) }
            scheduleRepository.getDirectory()
                .onSuccess { (subjects, lecturers) ->
                    _uiState.update { it.copy(subjects = subjects, lecturers = lecturers, isLoadingDirectory = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoadingDirectory = false) }
                }
        }
    }

    fun loadMyRequests() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRequests = true) }
            scheduleRequestRepository.getMyRequests()
                .onSuccess { requests ->
                    _uiState.update { it.copy(scheduleRequests = requests, isLoadingRequests = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoadingRequests = false) }
                }
        }
    }

    fun submitScheduleRequest(
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        room: String?,
        subjectId: String?,
        lecturerId: String?,
        subjectNameRaw: String?,
        lecturerNameRaw: String?,
        description: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            scheduleRequestRepository.createRequest(
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                room = room,
                subjectId = subjectId,
                lecturerId = lecturerId,
                subjectNameRaw = subjectNameRaw,
                lecturerNameRaw = lecturerNameRaw,
                description = description
            ).onSuccess {
                _uiState.update { it.copy(isLoading = false, actionSuccess = "Request jadwal berhasil diajukan. Menunggu persetujuan admin.") }
                loadMyRequests()
                loadSchedules()
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearActionSuccess() {
        _uiState.update { it.copy(actionSuccess = null) }
    }

    fun deleteSchedule(scheduleId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            scheduleRepository.deleteSchedule(scheduleId)
                .onSuccess {
                    // Drop the reminder/start work for the deleted row — otherwise
                    // it keeps firing for a schedule the student no longer has.
                    notificationScheduler.cancelClassWork(scheduleId)
                    _uiState.update { it.copy(isLoading = false, actionSuccess = "Jadwal berhasil dihapus.") }
                    loadSchedules()
                }.onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }
}
