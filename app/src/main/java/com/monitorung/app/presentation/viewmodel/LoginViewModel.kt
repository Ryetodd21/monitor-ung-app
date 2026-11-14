package com.monitorung.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monitorung.app.data.snapshot.UsageSnapshotStore
import com.monitorung.app.domain.model.User
import com.monitorung.app.domain.model.OfflineAccess
import com.monitorung.app.domain.repository.AuthRepository
import com.monitorung.app.domain.repository.CampusConfigRepository
import com.monitorung.app.domain.repository.ScheduleRepository
import com.monitorung.app.service.BatteryBasedSotManager
import com.monitorung.app.service.DataSyncManager
import com.monitorung.app.service.NotificationScheduler
import com.monitorung.app.service.SmartMonitoringManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import javax.inject.Inject

data class LoginUiState(
    val nim: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val user: User? = null,
    val hasAttemptedAutoLogin: Boolean = false,
    val biometricEnabled: Boolean = false,
    val showBiometricSetupDialog: Boolean = false,
)

enum class AppAccessState {
    /** Still checking credentials on launch */
    LOADING,
    /** Needs biometric verification before showing app */
    LOCKED,
    /** Biometric passed or no auth needed — show app */
    UNLOCKED,
    /** No saved credentials — show login screen */
    NEEDS_LOGIN,
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val notificationScheduler: NotificationScheduler,
    private val campusConfigRepository: CampusConfigRepository,
    private val scheduleRepository: ScheduleRepository,
    private val dataSyncManager: DataSyncManager,
    private val smartMonitoringManager: SmartMonitoringManager,
    private val batteryBasedSotManager: BatteryBasedSotManager,
    private val usageSnapshotStore: UsageSnapshotStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _biometricPassed = MutableStateFlow(false)
    val biometricPassed: StateFlow<Boolean> = _biometricPassed.asStateFlow()

    private val _appAccessState = MutableStateFlow(AppAccessState.LOADING)
    val appAccessState: StateFlow<AppAccessState> = _appAccessState.asStateFlow()
    private var offlineExpiryJob: Job? = null
    private var autoLoginJob: Job? = null

    val isLoggedIn: StateFlow<Boolean?> = authRepository.isLoggedIn()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            authRepository.isLoggedIn().collect { loggedIn ->
                _uiState.update { it.copy(isLoggedIn = loggedIn) }
            }
        }
        viewModelScope.launch {
            authRepository.getCurrentUser().collect { user ->
                _uiState.update { it.copy(user = user) }
            }
        }
        // Track biometric preference
        viewModelScope.launch {
            authRepository.isBiometricEnabled().collect { enabled ->
                _uiState.update { it.copy(biometricEnabled = enabled) }
            }
        }
        // Attempt silent auto-login if we have saved credentials
        attemptAutoLogin()
    }

    private fun attemptAutoLogin() {
        if (autoLoginJob?.isActive == true) return
        autoLoginJob = viewModelScope.launch {
            val credentials = authRepository.getSavedCredentials()
            if (credentials != null) {
                _uiState.update { it.copy(hasAttemptedAutoLogin = true, isLoading = true) }
                _appAccessState.value = AppAccessState.LOADING
                val result = authRepository.autoLoginWithSavedCredentials()
                coroutineContext.ensureActive()
                result
                    .onSuccess {
                        watchSessionExpiry()
                        viewModelScope.launch { setupBackgroundTasks() }
                        refreshCampusConfig()
                        _uiState.update { it.copy(isLoading = false, isLoggedIn = true, nim = credentials.first) }
                        resolveAppAccess()
                    }
                    .onFailure { e ->
                        // If we have saved credentials but the server is unreachable, let the user
                        // proceed with cached data. They can view local Room data offline and sync
                        // when the server comes back. Show a warning state instead of blocking access.
                        val offlineDeadline = if (OfflineAccess.isTemporaryFailure(e)) {
                            authRepository.offlineAccessExpiresAt()
                        } else null
                        if (offlineDeadline != null) {
                            viewModelScope.launch { setupBackgroundTasks(useCache = true) }
                            watchSessionExpiry()
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    isLoggedIn = true,
                                    nim = credentials.first,
                                )
                            }
                            resolveAppAccess()
                        } else {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    hasAttemptedAutoLogin = false,
                                    nim = credentials.first,
                                    error = if (OfflineAccess.isTemporaryFailure(e))
                                        "Akses offline maksimal 24 jam. Hubungkan ke server untuk memvalidasi sesi kembali."
                                    else "Sesi tidak dapat diverifikasi. Silakan masuk kembali.",
                                )
                            }
                            _appAccessState.value = AppAccessState.NEEDS_LOGIN
                        }
                    }
            } else {
                _uiState.update { it.copy(hasAttemptedAutoLogin = true) }
                _appAccessState.value = AppAccessState.NEEDS_LOGIN
            }
        }
    }

    private fun resolveAppAccess() {
        viewModelScope.launch {
            if (!checkOfflineAccess()) return@launch
            val biometricEnabled = authRepository.isBiometricEnabled().first()
            if (biometricEnabled) {
                _appAccessState.value = AppAccessState.LOCKED
            } else {
                _biometricPassed.value = true
                _appAccessState.value = AppAccessState.UNLOCKED
            }
        }
    }

    fun onNimChange(nim: String) {
        _uiState.update { it.copy(nim = nim, error = null) }
    }

    fun login(password: String) {
        val state = _uiState.value

        if (state.nim.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "Username dan password harus diisi") }
            return
        }

        viewModelScope.launch {
            autoLoginJob?.cancel()
            offlineExpiryJob?.cancel()
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.login(state.nim, password)
                .onSuccess {
                    watchSessionExpiry()
                    viewModelScope.launch { setupBackgroundTasks() }
                    refreshCampusConfig()
                    _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
                    resolveAppAccess()
                    // Prompt biometric setup if not yet enabled and device supports it
                    _uiState.update { it.copy(showBiometricSetupDialog = !it.biometricEnabled) }
                }
                .onFailure { e ->
                    val msg = e.message ?: "Login gagal"
                    val isNetworkError = msg.contains("Unable to resolve host", ignoreCase = true) ||
                            msg.contains("network", ignoreCase = true) ||
                            msg.contains("connection", ignoreCase = true) ||
                            msg.contains("timeout", ignoreCase = true)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = if (isNetworkError) "Server belum aktif" else msg
                        )
                    }
                }
        }
    }

    fun onBiometricSuccess() {
        viewModelScope.launch {
            if (_appAccessState.value != AppAccessState.LOCKED) return@launch
            if (!checkOfflineAccess()) return@launch
            _biometricPassed.value = true
            _appAccessState.value = AppAccessState.UNLOCKED
        }
    }

    private fun watchSessionExpiry() {
        offlineExpiryJob?.cancel()
        offlineExpiryJob = viewModelScope.launch {
            while (true) {
                val expiresAt = authRepository.offlineAccessExpiresAt()
                if (expiresAt == null) {
                    expireOfflineAccess()
                    attemptAutoLogin()
                    break
                }
                delay((expiresAt - System.currentTimeMillis()).coerceIn(1L, 30_000L))
            }
        }
    }

    fun onResume() {
        viewModelScope.launch {
            if (_appAccessState.value == AppAccessState.UNLOCKED ||
                _appAccessState.value == AppAccessState.LOCKED
            ) {
                if (!checkOfflineAccess()) attemptAutoLogin()
            }
        }
    }

    private suspend fun checkOfflineAccess(): Boolean {
        if (authRepository.offlineAccessExpiresAt() == null) {
            expireOfflineAccess()
            return false
        }
        return true
    }

    private fun expireOfflineAccess() {
        _biometricPassed.value = false
        _appAccessState.value = AppAccessState.NEEDS_LOGIN
        _uiState.update { it.copy(isLoggedIn = false, error =
            "Akses offline 24 jam berakhir. Hubungkan ke server lalu masuk kembali. Data belum terkirim tetap tersimpan.") }
    }

    fun onBiometricSetupAccept() {
        viewModelScope.launch {
            authRepository.setBiometricEnabled(true)
        }
        _uiState.update { it.copy(showBiometricSetupDialog = false) }
    }

    fun onBiometricSetupDismiss() {
        // User skipped biometric setup — unlock immediately instead of staying at LOCKED.
        _uiState.update { it.copy(showBiometricSetupDialog = false) }
        viewModelScope.launch {
            if (!checkOfflineAccess()) return@launch
            _biometricPassed.value = true
            _appAccessState.value = AppAccessState.UNLOCKED
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            authRepository.setBiometricEnabled(enabled)
        }
    }

    fun dismissBiometricGate() {
        // User wants to use a different account — go to login but keep credentials
        // so they don't have to type NIM+password again. Actual logout clears credentials.
        val savedNim = authRepository.getSavedCredentials()?.first ?: ""
        _biometricPassed.value = false
        _appAccessState.value = AppAccessState.NEEDS_LOGIN
        _uiState.update { it.copy(nim = savedNim) }
    }

    private suspend fun setupBackgroundTasks(useCache: Boolean = false) {
        // Morning notification (07:00) + sync reminder (21:00) + auto-sync (22:00).
        // Previously only BackgroundSotCollection was enqueued, which left class
        // reminders, sync nudges, and the daily 22:00 auto-sync dead. Now every
        // scheduleXxx() defined on NotificationScheduler / DataSyncManager is
        // actually fired here.
        notificationScheduler.scheduleMorningNotification()
        dataSyncManager.scheduleSyncReminder(0)
        dataSyncManager.scheduleAutoSync()

        // Bug 3 fix: keep-alive worker fires every 30 min and restarts
        // SmartMonitoringService if the OS killed it (e.g. after 1 day of
        // no app opens). The worker checks session prefs and skips if the
        // class window has already ended. Combined with onTaskRemoved() restart,
        // the service is now resilient to both app-swipe and OS-kill scenarios.
        notificationScheduler.scheduleMonitoringKeepAlive()

        // Class reminder (2h before) + class start (at class time). Nothing called
        // scheduleAllClassNotifications before, so both workers were dead code.
        // Each worker re-arms itself for the following week.
        // On failure fall back to the Room cache so an offline login still arms
        // them — fetchSchedules() only writes the cache on success.
        val schedules = (if (useCache) null else scheduleRepository.fetchSchedules().getOrNull())
            ?: (1..7).flatMap { scheduleRepository.getSchedulesByDay(it).first() }
        notificationScheduler.scheduleClassNotifications(schedules)

        // Background SOT collection every 4 hours (outside class hours) — kept
        // last so the per-class reminder scheduling above runs first.
        notificationScheduler.scheduleBackgroundSotCollection()
    }

    /**
     * Best-effort fetch of campus geofence + MQTT broker URL after login.
     * Failure is logged but does not block the user — the hardcoded fallback in
     * CampusConfigRepositoryImpl (`tcp://10.0.2.2:1883`) is preserved until success.
     */
    private fun refreshCampusConfig() {
        viewModelScope.launch {
            campusConfigRepository.fetchConfig()
                .onFailure { e ->
                    android.util.Log.w("LoginViewModel", "fetchConfig failed: ${e.message}")
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            autoLoginJob?.cancel()
            offlineExpiryJob?.cancel()
            // Stop all background work before clearing auth state
            dataSyncManager.cancelAutoSync()
            notificationScheduler.cancelBackgroundSotCollection()
            // Wave 3 fix #14: cancelAll() removes WORK_MORNING, WORK_SYNC_REMINDER,
            // and every WORK_CLASS_REMINDER_*/WORK_CLASS_START_* — these kept
            // firing on a logged-out device (07:00 Selamat Pagi + 21:45 sync nudge).
            // cancelAllWork() is idempotent with the specific cancels above.
            notificationScheduler.cancelAll()
            // Bug 3 fix: also cancel the keep-alive worker — there's no active
            // session to keep alive after logout, so it would keep waking the
            // service unnecessarily until the next login or worker's next run.
            notificationScheduler.cancelMonitoringKeepAlive()
            smartMonitoringManager.logout()
            batteryBasedSotManager.logout()
            // Reset the shared UsageStats snapshot so a fresh login starts
            // from today's cumulative total, not whatever the previous user
            // left behind. Done LAST (after managers cancel their coroutines)
            // so no in-flight collectSotData() reads stale state.
            usageSnapshotStore.reset()
            authRepository.logout()
            authRepository.close()
            _biometricPassed.value = false
            _appAccessState.value = AppAccessState.NEEDS_LOGIN
            _uiState.update { LoginUiState() }
        }
    }
}
