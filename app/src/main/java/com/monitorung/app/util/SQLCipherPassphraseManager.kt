package com.monitorung.app.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.monitorung.app.BuildConfig
import java.security.SecureRandom

/**
 * SQLCipher Passphrase Manager
 *
 * The database encryption passphrase is generated once per installation and
 * stored in EncryptedSharedPreferences (backed by Android Keystore). This
 * manager provides utilities to manage the passphrase.
 *
 * PASSPHRASE ROTATION:
 * Full passphrase rotation requires SQLCipher's `PRAGMA rekey` which is
 * complex to implement correctly with Room. For now, the policy is:
 * - Passphrase generated once per installation
 * - Reset (wipe data) available for testing/admin action
 * - Future enhancement: implement rotation via Room migration callback
 *
 * To rotate: uninstall/reinstall app, or call resetPassphrase() which
 * deletes all local data and generates a new passphrase.
 */
object SQLCipherPassphraseManager {

    private const val PREFS_NAME = "monitor_encrypted_prefs"
    private const val KEY_DB_PASSPHRASE = "db_passphrase"
    private const val KEY_PASSPHRASE_VERSION = "db_passphrase_version"
    private const val PASSPHRASE_LENGTH = 32
    private const val CURRENT_VERSION = 1

    /**
     * Get the current passphrase version. Returns 0 if not set.
     */
    fun getPassphraseVersion(context: Context): Int {
        val prefs = getEncryptedPrefs(context)
        return prefs.getInt(KEY_PASSPHRASE_VERSION, 0)
    }

    /**
     * Get the current passphrase. Generates a new one if none exists.
     */
    fun getOrCreatePassphrase(context: Context): ByteArray {
        val prefs = getEncryptedPrefs(context)

        if (prefs.contains(KEY_DB_PASSPHRASE)) {
            val encoded = prefs.getString(KEY_DB_PASSPHRASE, null)
            if (encoded != null) {
                return android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
            }
        }

        // Generate new passphrase
        val random = ByteArray(PASSPHRASE_LENGTH)
        SecureRandom().nextBytes(random)

        val editor = prefs.edit()
        editor.putString(KEY_DB_PASSPHRASE, android.util.Base64.encodeToString(random, android.util.Base64.NO_WRAP))
        editor.putInt(KEY_PASSPHRASE_VERSION, CURRENT_VERSION)
        editor.apply()

        if (BuildConfig.DEBUG) {
            android.util.Log.d("SQLCipherPassphraseManager", "Generated new passphrase v$CURRENT_VERSION")
        }
        return random
    }

    /**
     * Force reset the passphrase (destroys all local data).
     * Use only for testing or when user explicitly requests full data wipe.
     */
    fun resetPassphrase(context: Context): ByteArray {
        val prefs = getEncryptedPrefs(context)
        val editor = prefs.edit()
        editor.remove(KEY_DB_PASSPHRASE)
        editor.remove(KEY_PASSPHRASE_VERSION)
        editor.apply()

        // Delete database file
        context.deleteDatabase("monitor_ung.db")

        return getOrCreatePassphrase(context)
    }

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Migration hook: call during app startup if passphrase version needs
     * to be upgraded (e.g., after app update that changes encryption params).
     */
    fun migrateIfNeeded(context: Context): Boolean {
        val currentVersion = getPassphraseVersion(context)
        if (currentVersion < CURRENT_VERSION) {
            if (BuildConfig.DEBUG) {
                android.util.Log.i("SQLCipherPassphraseManager", "Migrating passphrase from v$currentVersion to v$CURRENT_VERSION")
            }
            return resetPassphrase(context) != null
        }
        return true
    }
}