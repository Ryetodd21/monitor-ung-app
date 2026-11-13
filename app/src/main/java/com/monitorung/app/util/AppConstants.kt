package com.monitorung.app.util

/**
 * Centralized client-side constants — values that were previously scattered
 * as inline literals across workers, view models, and MQTT plumbing.
 *
 * Time-based values are kept in milliseconds (matching Android's
 * `TimeUnit.MILLISECONDS` and `kotlinx.coroutines.delay` defaults) so the
 * call site reads naturally (`delay(MQTT_KEEPALIVE_INTERVAL_MS)`).
 *
 * NOT a substitute for BuildConfig — BuildConfig still owns the
 * environment-specific values (API base URL, cert pinning, debug flag).
 * This file holds *behavior* constants that don't vary by flavor.
 */
object AppConstants {

    // ─── Worker retry cap (see service/Workers.kt) ────────────────────────
    /**
     * Max times WorkManager will re-run a worker before giving up. Used by
     * `ClassReminderWorker`, `ClassStartWorker`, `SyncReminderWorker`, and
     * `DataSyncWorker` — previously duplicated as `>= 3` in 3 sites.
     */
    const val MAX_WORKER_RETRY_ATTEMPTS = 3

    // ─── MQTT client behavior (see data/push/MqttPushClient.kt) ───────────
    /**
     * Cadence at which the MQTT client re-checks `isConnected` while
     * waiting for messages. ~2s is a good balance: low enough to detect
     * broker-side disconnects promptly, high enough to be battery-friendly.
     */
    const val MQTT_KEEPALIVE_INTERVAL_MS = 2000L

    /**
     * Exponential backoff (seconds) used between failed MQTT connect
     * attempts. Capped at 30s to avoid hammering a recovering broker. The
     * backoff cycles through these values then stays at 30s indefinitely.
     */
    val MQTT_BACKOFF_SECONDS: List<Int> = listOf(2, 4, 8, 15, 30)
    /** Hard cap on backoff (also the steady-state value once attempts > list size). */
    const val MQTT_BACKOFF_MAX_SECONDS = 30

    // ─── UX feedback timings ──────────────────────────────────────────────
    /**
     * Duration the confetti burst plays before login navigates away. Tied
     * to the 1.6s confetti animation in `ConfettiBurst.kt`.
     */
    const val CONFETTI_ANIMATION_DURATION_MS = 1600L

    /**
     * Minimum visible time for the pull-to-refresh spinner — too short and
     * the user can't tell the gesture registered.
     */
    const val REFRESH_FEEDBACK_DURATION_MS = 600L

    /**
     * How long the "Data lokal berhasil dihapus" toast stays on screen
     * after `clearAllData()`. Mirrors `Toast.LENGTH_LONG` ≈ 3.5s on
     * modern Android.
     */
    const val DELETE_SUCCESS_TOAST_DURATION_MS = 3500L
}
