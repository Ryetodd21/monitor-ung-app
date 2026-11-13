package com.monitorung.app.data.lock

import androidx.core.content.edit
import android.content.Context
import android.content.SharedPreferences
import com.monitorung.app.util.SecurePrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Counts screen-unlock events for the current local day.
 *
 * Persists count + date in EncryptedSharedPreferences so the counter
 * survives reboot and process death, AND so it can't be read off-device by
 * a forensic dump. Plain SharedPreferences was used here previously — the
 * counter value isn't a secret on its own, but it leaks usage patterns and
 * is inconsistent with the rest of the app's storage strategy.
 *
 * The counter is implicitly reset when the stored date no longer matches
 * today — no midnight alarm needed (we read lazily on every [getTodayCount]).
 *
 * Receives events from [com.monitorung.app.receiver.UnlockReceiver] listening for
 * `Intent.ACTION_USER_PRESENT` and `Intent.ACTION_SCREEN_ON`.
 */
@Singleton
class UnlockTracker @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val PREFS = "unlock_tracker_prefs"
        private const val KEY_COUNT = "count"
        private const val KEY_DATE = "date"
    }

    // SecurePrefs returns EncryptedSharedPreferences (AES256-GCM via Keystore).
    // The returned type is SharedPreferences so call sites stay unchanged.
    private val prefs: SharedPreferences = SecurePrefs.get(context, PREFS)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** Record one unlock. Resets the counter if the stored day is not today. */
    fun recordUnlock() {
        val today = dateFormat.format(Date())
        val storedDate = prefs.getString(KEY_DATE, null)
        val newCount = if (today == storedDate) prefs.getInt(KEY_COUNT, 0) + 1 else 1
        prefs.edit {
            putString(KEY_DATE, today)
            putInt(KEY_COUNT, newCount)
        }
    }

    /** Unlocks since the most recent local midnight. Returns 0 if the stored date is not today. */
    fun getTodayCount(): Int {
        val today = dateFormat.format(Date())
        val stored = prefs.getString(KEY_DATE, null) ?: return 0
        return if (today == stored) prefs.getInt(KEY_COUNT, 0) else 0
    }

    /** Clear stored count. Useful for tests and Settings "reset data". */
    fun resetForDay() {
        prefs.edit {
            clear()
        }
    }
}
