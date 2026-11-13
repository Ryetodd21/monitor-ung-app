package com.monitorung.app.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Factory for [EncryptedSharedPreferences] using AES256-GCM values +
 * AES256-SIV keys, backed by the Android Keystore.
 *
 * Replaces ad-hoc `context.getSharedPreferences(name, MODE_PRIVATE)` calls
 * for state that should NOT live in plaintext on disk — even though
 * `unlock_tracker_prefs` and friends don't store secrets, leaking them
 * gives an attacker a foothold (e.g. confirming app usage patterns, class
 * schedules, unlock counts). Keeping everything in `EncryptedSharedPreferences`
 * also removes the audit smell of having two prefs systems side-by-side.
 *
 * Usage:
 *   val prefs = SecurePrefs.get(context, "monitor_session_prefs")
 *
 * Note: this mirrors the scheme already used in `AppModule.kt` (DB passphrase)
 * and `AuthRepositoryImpl.kt` (auth tokens). Single source of truth for the
 * key scheme so future audits can verify one config instead of three.
 */
object SecurePrefs {

    private val KEY_SCHEME = MasterKey.KeyScheme.AES256_GCM

    fun get(context: Context, name: String): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(KEY_SCHEME)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            name,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
