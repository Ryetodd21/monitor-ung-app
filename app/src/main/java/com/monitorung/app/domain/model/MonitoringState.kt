package com.monitorung.app.domain.model

/**
 * Monitoring state machine states.
 * - IDLE: No active class, no monitoring
 * - IN_CLASS: Currently within class time window
 * - OUTSIDE_CLASS: User is outside geofence during class
 */
enum class MonitoringState {
    IDLE,
    IN_CLASS,
    OUTSIDE_CLASS,
}

data class ClassSession(
    val scheduleId: String,
    val courseName: String,
    val room: String,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
)

data class MonitoringStatus(
    val state: MonitoringState = MonitoringState.IDLE,
    val currentSession: ClassSession? = null,
    val lastLocationCheck: Long? = null,
    val wasOnCampus: Boolean? = null,
    val lastSotCollectedAt: Long? = null,
    val pendingSyncCount: Int = 0,
    val autoSyncEnabled: Boolean = true,
    val lastSyncAt: Long? = null,
    val nextSyncReminderAt: Long? = null,
)
