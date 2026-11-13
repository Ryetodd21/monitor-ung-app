package com.monitorung.app.presentation.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monitorung.app.data.local.MonitorUNGDatabase
import com.monitorung.app.data.push.MqttPushClient
import com.monitorung.app.domain.model.SyncState
import com.monitorung.app.domain.repository.UsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: MonitorUNGDatabase,
    private val usageRepository: UsageRepository,
    private val authRepository: com.monitorung.app.domain.repository.AuthRepository,
    private val mqttPushClient: MqttPushClient,
) : ViewModel() {

    private val _dataDeleted = MutableStateFlow(false)
    val dataDeleted = _dataDeleted.asStateFlow()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount = _pendingCount.asStateFlow()

    private val _syncState = MutableStateFlow(SyncState())
    val syncState = _syncState.asStateFlow()

    /** Last MQTT event seen by the local push client. Drives the Settings tile
     * so the user can confirm the broker is actually delivering messages —
     * `connected` alone doesn't prove it (TCP may be up while the broker
     * silently drops publishes). */
    val lastMqttEvent = mqttPushClient.events
        .map { it.name }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    init {
        viewModelScope.launch {
            usageRepository.getSyncState().collect { state ->
                _pendingCount.value = state.pendingCount
                _syncState.value = state
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _syncState.value = _syncState.value.copy(isSyncing = true)
            usageRepository.syncPendingLogs()
            // Re-query to pick up the persisted last_sync timestamp.
            usageRepository.getSyncState().first().let { state ->
                _syncState.value = state.copy(isSyncing = false)
            }
        }
    }

    fun openPrivacyPolicy() {
        val url = "https://monitorterop.my.id/privacy"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun deleteAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            // FIX H3: pass actual studentId instead of null so only the current
            // user's data is deleted. null was triggering scheduleDao.deleteAllTables()
            // (no WHERE clause) which wiped ALL students on shared devices.
            val studentId = authRepository.getCurrentUser().first()?.studentId ?: return@launch
            database.clearAllData(studentId)
            _dataDeleted.value = true
        }
    }

    fun resetDataDeletedFlag() {
        _dataDeleted.value = false
    }
}
