package com.monitorung.app.data.repository

import androidx.core.content.edit
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.monitorung.app.data.local.MonitorUNGDatabase
import com.monitorung.app.data.remote.api.MonitorUNGApi
import com.monitorung.app.data.remote.dto.LoginRequest
import com.monitorung.app.data.remote.dto.PasswordChangeRequest
import com.monitorung.app.data.remote.dto.ProfileUpdateRequest
import com.monitorung.app.data.remote.dto.RefreshTokenRequest
import com.monitorung.app.domain.model.User
import com.monitorung.app.domain.model.OfflineAccess
import com.monitorung.app.domain.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "monitor_ung_prefs")

@Singleton
class AuthRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: MonitorUNGApi,
    private val database: MonitorUNGDatabase,
) : AuthRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private object PrefsKeys {
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val STUDENT_ID = stringPreferencesKey("student_id")
        val NAME = stringPreferencesKey("name")
        val EMAIL = stringPreferencesKey("email")
        val PHONE = stringPreferencesKey("phone")
        val AVATAR_URL = stringPreferencesKey("avatar_url")
        val BIO = stringPreferencesKey("bio")
        val ROLE = stringPreferencesKey("role")
        val TOKEN_EXPIRES_AT = longPreferencesKey("token_expires_at")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
    }

    private val securePrefs = com.monitorung.app.util.SecurePrefs.get(context, "secure_prefs")

    override suspend fun login(nim: String, password: String): Result<User> {
        return try {
            // Compute a stable device fingerprint for /api/auth/login. Server
            // dedupes logins per (user_id, device_id) so the same physical
            // device always lands on the same row in login_devices — that
            // powers the "Daftar Perangkat" list on the web profile.
            val deviceId = computeDeviceId(context)
            val deviceName = parseDeviceName()
            val userAgent = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} / Android ${android.os.Build.VERSION.RELEASE}"
            val response = api.login(
                LoginRequest(
                    nim = nim,
                    password = password,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    userAgent = userAgent,
                )
            )
            if (response.isSuccessful) {
                val body = response.body() ?: return Result.failure(Exception("Login response body is empty"))
                // Store NIM for form pre-fill + tokens for auto-login via refresh token
                // Password is NOT stored — auto-login uses refresh token
                securePrefs.edit {
                    putString("saved_nim", nim)
                    putString("access_token", body.accessToken)
                    putString("refresh_token", body.refreshToken)
                }

                val user = User(
                    studentId = body.studentId,
                    name = body.name,
                    email = body.email,
                    accessToken = body.accessToken,
                    refreshToken = body.refreshToken,
                    tokenExpiresAt = System.currentTimeMillis() + (body.expiresIn * 1000)
                )
                // Set isLoggedIn IMMEDIATELY so MainActivity routing works
                context.dataStore.edit { prefs ->
                    prefs[PrefsKeys.IS_LOGGED_IN] = true
                }
                saveUserToPrefs(user)
                Result.success(user)
            } else {
                Result.failure(Exception("Login failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refreshToken(): Result<String> {
        return try {
            val refreshToken = securePrefs.getString("refresh_token", null)
                ?: return Result.failure(Exception("No refresh token"))
            val response = api.refreshToken(RefreshTokenRequest(refreshToken))
            if (response.isSuccessful) {
                val body = response.body() ?: return Result.failure(Exception("Refresh token response body is empty"))
                val newToken = body.accessToken
                // Server rotates refresh_token on every /api/auth/refresh call
                // (server/app/routers/auth.py:263, 280-289 — old Token row
                // deleted, new one issued). Persist the rotated token alongside
                // the new access token. Without this fix the next refresh
                // attempts with the now-deleted old token → 401 → user kicked
                // back to login. Round 17 fix.
                securePrefs.edit {
                    putString("access_token", newToken)
                    putString("refresh_token", body.refreshToken)
                    putLong("last_server_validation", System.currentTimeMillis())
                }
                // Server /api/auth/refresh does NOT return expires_in (raw
                // JSONResponse dict). Fall back to server-configured lifetime
                // (30 min as of round 10) so tokenExpiresAt stays meaningful
                // for HomeScreen "kedaluwarsa" UX.
                val expiresAt = System.currentTimeMillis() + (30L * 60L * 1000L)
                context.dataStore.edit { it[PrefsKeys.TOKEN_EXPIRES_AT] = expiresAt }
                Result.success(newToken)
            } else {
                if (response.code() == 401) {
                    securePrefs.edit {
                        remove("access_token")
                        remove("refresh_token")
                    }
                }
                if (response.code() == 401 || response.code() == 403) {
                    securePrefs.edit {
                        remove("last_server_validation")
                    }
                }
                Result.failure(retrofit2.HttpException(response))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout() {
        // Keep encrypted, account-scoped logs so pending data survives logout.
        _currentUserFlow.value = null
        _isLoggedInFlow.value = false
        securePrefs.edit {
            clear()
        }
        context.dataStore.edit { it.clear() }
    }

    /**
     * Returns saved NIM from secure storage.
     * Password is no longer stored — auto-login uses refresh token instead.
     */
    override fun getSavedCredentials(): Pair<String, String>? {
        val nim = securePrefs.getString("saved_nim", null)
        // Password field kept for interface compatibility but always returns null
        return if (nim != null) Pair(nim, "") else null
    }

    /**
     * Attempts silent auto-login using the stored refresh token.
     * Falls back to failure if refresh token is invalid/expired — user must re-login.
     */
    override suspend fun autoLoginWithSavedCredentials(): Result<User> {
        val refreshToken = securePrefs.getString("refresh_token", null)
            ?: return Result.failure(Exception("No saved refresh token"))
        // Try refresh first. On a real 401, TokenAuthenticator already cleared
        // stored tokens — the securePrefs will be empty and we surface the
        // failure to the caller. On a transient error (network blip, 5xx),
        // keep the credentials intact so the next launch retries — the
        // previous handler wiped everything on any failure, which meant one
        // offline launch erased the user's session permanently. Round 17 fix.
        val refreshResult = refreshToken()
        if (refreshResult.isSuccess) {
            // Refresh succeeded — fetch full profile
            return fetchProfile()
        }
        return Result.failure(refreshResult.exceptionOrNull() ?: Exception("Sesi habis, silakan login ulang"))
    }

    /**
     * Returns Flow<Boolean> that emits true if user is logged in.
     * Uses MutableStateFlow — NEVER blocks main thread.
     * Emits false immediately, then updates with DataStore state in background.
     */
    private val _isLoggedInFlow = MutableStateFlow(false)
    private var _isLoggedInInitialized = false

    override fun isLoggedIn(): Flow<Boolean> {
        if (!_isLoggedInInitialized) {
            _isLoggedInInitialized = true
            // Kick off background read, don't block
            scope.launch {
                try {
                    val prefs = context.dataStore.data.first()
                    _isLoggedInFlow.value = try {
                        prefs[PrefsKeys.IS_LOGGED_IN] == true
                    } catch (e: Exception) { false }
                } catch (e: Exception) {
                    // DataStore error — keep false (login screen)
                }
                // Then keep listening for changes
                try {
                    context.dataStore.data.collect { prefs ->
                        _isLoggedInFlow.value = try {
                            prefs[PrefsKeys.IS_LOGGED_IN] == true
                        } catch (e: Exception) { false }
                    }
                } catch (e: Exception) {
                    // Collection ended
                }
            }
        }
        return _isLoggedInFlow.asStateFlow()
    }

    private val _currentUserFlow = MutableStateFlow<User?>(null)
    private var _currentUserInitialized = false

    override fun getCurrentUser(): Flow<User?> {
        if (!_currentUserInitialized) {
            _currentUserInitialized = true
            scope.launch {
                try {
                    val prefs = context.dataStore.data.first()
                    _currentUserFlow.value = try {
                        val studentId = prefs[PrefsKeys.STUDENT_ID] ?: return@launch
                        User(
                            studentId = studentId,
                            name = prefs[PrefsKeys.NAME] ?: "",
                            email = prefs[PrefsKeys.EMAIL] ?: "",
                            accessToken = securePrefs.getString("access_token", "") ?: "",
                            refreshToken = securePrefs.getString("refresh_token", "") ?: "",
                            tokenExpiresAt = prefs[PrefsKeys.TOKEN_EXPIRES_AT] ?: 0,
                            phone = prefs[PrefsKeys.PHONE] ?: "",
                            avatarUrl = prefs[PrefsKeys.AVATAR_URL] ?: "",
                            bio = prefs[PrefsKeys.BIO] ?: "",
                            role = prefs[PrefsKeys.ROLE] ?: "mahasiswa",
                        )
                    } catch (e: Exception) { null }
                } catch (e: Exception) {
                    // DataStore error
                }
                try {
                    context.dataStore.data.collect { prefs ->
                        _currentUserFlow.value = try {
                            val studentId = prefs[PrefsKeys.STUDENT_ID]
                            // Logout clears STUDENT_ID → emit null so collectors
                            // (UsageRepository.flatMapLatest, SmartMonitoringManager)
                            // immediately stop filtering by the previous user.
                            // The previous `?: return@collect` swallowed this
                            // transition and left subscribers reading a ghost row.
                            if (studentId == null) {
                                null
                            } else {
                                User(
                                    studentId = studentId,
                                    name = prefs[PrefsKeys.NAME] ?: "",
                                    email = prefs[PrefsKeys.EMAIL] ?: "",
                                    accessToken = securePrefs.getString("access_token", "") ?: "",
                                    refreshToken = securePrefs.getString("refresh_token", "") ?: "",
                                    tokenExpiresAt = prefs[PrefsKeys.TOKEN_EXPIRES_AT] ?: 0,
                                    phone = prefs[PrefsKeys.PHONE] ?: "",
                                    avatarUrl = prefs[PrefsKeys.AVATAR_URL] ?: "",
                                    bio = prefs[PrefsKeys.BIO] ?: "",
                                    role = prefs[PrefsKeys.ROLE] ?: "mahasiswa",
                                )
                            }
                        } catch (e: Exception) { null }
                    }
                } catch (e: Exception) {
                    // Collection ended
                }
            }
        }
        return _currentUserFlow.asStateFlow()
    }

    override fun getAccessToken(): String? = securePrefs.getString("access_token", null)
    override fun getRefreshToken(): String? = securePrefs.getString("refresh_token", null)

    override suspend fun offlineAccessExpiresAt(): Long? {
        val prefs = context.dataStore.data.first()
        val validatedAt = securePrefs.getLong("last_server_validation", 0L)
        return if (prefs[PrefsKeys.IS_LOGGED_IN] == true &&
            !prefs[PrefsKeys.STUDENT_ID].isNullOrBlank() &&
            !getAccessToken().isNullOrBlank() && !getRefreshToken().isNullOrBlank() &&
            OfflineAccess.isValid(validatedAt, System.currentTimeMillis())
        ) validatedAt + OfflineAccess.DURATION_MS else null
    }

    override suspend fun fetchProfile(): Result<User> {
        return try {
            val token = getAccessToken() ?: return Result.failure(Exception("No token"))
            val response = api.getMyProfile("Bearer $token")
            if (response.isSuccessful) {
                val dto = response.body() ?: return Result.failure(Exception("Profile response body is empty"))
                // studentId is users.id — the same value /auth/login returns and the
                // key every Room query uses. Using dto.username re-keyed all rows on
                // each auto-login and left them unreadable.
                val user = User(
                    studentId = dto.id,
                    name = dto.fullName,
                    email = dto.email,
                    accessToken = token,
                    refreshToken = securePrefs.getString("refresh_token", "") ?: "",
                    tokenExpiresAt = getCurrentUser().first()?.tokenExpiresAt ?: 0,
                    phone = dto.phone,
                    avatarUrl = dto.avatarUrl,
                    bio = dto.bio,
                    role = dto.role,
                )
                saveUserToPrefs(user)
                Result.success(user)
            } else {
                if (response.code() == 401 || response.code() == 403) {
                    securePrefs.edit {
                        remove("last_server_validation")
                    }
                }
                Result.failure(retrofit2.HttpException(response))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateProfile(
        fullName: String?,
        email: String?,
        phone: String?,
        avatarUrl: String?,
        bio: String?,
    ): Result<User> {
        return try {
            val token = getAccessToken() ?: return Result.failure(Exception("No token"))
            val request = ProfileUpdateRequest(
                fullName = fullName,
                email = email,
                phone = phone,
                avatarUrl = avatarUrl,
                bio = bio,
            )
            val response = api.updateMyProfile("Bearer $token", request)
            if (response.isSuccessful) {
                val dto = response.body() ?: return Result.failure(Exception("Update profile response body is empty"))
                // studentId is users.id — the same value /auth/login returns and the
                // key every Room query uses. Using dto.username re-keyed all rows on
                // each auto-login and left them unreadable.
                val user = User(
                    studentId = dto.id,
                    name = dto.fullName,
                    email = dto.email,
                    accessToken = token,
                    refreshToken = securePrefs.getString("refresh_token", "") ?: "",
                    tokenExpiresAt = getCurrentUser().first()?.tokenExpiresAt ?: 0,
                    phone = dto.phone,
                    avatarUrl = dto.avatarUrl,
                    bio = dto.bio,
                    role = dto.role,
                )
                saveUserToPrefs(user)
                Result.success(user)
            } else {
                Result.failure(Exception("Failed to update profile: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        return try {
            val token = getAccessToken() ?: return Result.failure(Exception("No token"))
            val request = PasswordChangeRequest(currentPassword, newPassword)
            val response = api.changeMyPassword("Bearer $token", request)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Gagal mengubah password: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun saveUserToPrefs(user: User) {
        context.dataStore.edit { prefs ->
            prefs[PrefsKeys.IS_LOGGED_IN] = true
            prefs[PrefsKeys.STUDENT_ID] = user.studentId
            prefs[PrefsKeys.NAME] = user.name
            prefs[PrefsKeys.EMAIL] = user.email
            prefs[PrefsKeys.PHONE] = user.phone
            prefs[PrefsKeys.AVATAR_URL] = user.avatarUrl
            prefs[PrefsKeys.BIO] = user.bio
            prefs[PrefsKeys.ROLE] = user.role
            prefs[PrefsKeys.TOKEN_EXPIRES_AT] = user.tokenExpiresAt
        }
        securePrefs.edit {
            putLong("last_server_validation", System.currentTimeMillis())
        }
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PrefsKeys.BIOMETRIC_ENABLED] = enabled
        }
    }

    override fun isBiometricEnabled(): Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[PrefsKeys.BIOMETRIC_ENABLED] == true }

    override fun close() {
        // Wave 3 fix #15: don't cancel the scope outright — the DataStore
        // collectors launched in isLoggedIn()/getCurrentUser() live on it too.
        // Cancelling the scope orphans the StateFlows; on re-login the same
        // dead flow is returned and downstream repos (UsageRepository,
        // ScheduleRepository) see stale empty data until force-kill.
        // cancelChildren() kills the active collectors only; the scope stays
        // alive for the next login's fresh ones. Reset init flags + clear
        // flows so isLoggedIn()/getCurrentUser() re-launches on next call.
        scope.coroutineContext.cancelChildren()
        _isLoggedInInitialized = false
        _currentUserInitialized = false
        _isLoggedInFlow.value = false
        _currentUserFlow.value = null
    }
}

// ─── Device fingerprint helpers ────────────────────────────────────────────
// These helpers are intentionally file-private (not in a Util class) so the
// only place that calls /api/auth/login picks them up automatically. Format
// mirrors the web client (sha256 → first 32 hex chars) so the server-side
// dedupe works across platforms.

private fun computeDeviceId(context: android.content.Context): String {
    val parts = listOf(
        android.os.Build.MODEL ?: "",
        android.os.Build.MANUFACTURER ?: "",
        android.os.Build.VERSION.RELEASE ?: "",
        context.packageName ?: "",
    ).joinToString("|")
    return try {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(parts.toByteArray(Charsets.UTF_8))
        bytes.joinToString("") { "%02x".format(it) }.take(32)
    } catch (e: Exception) {
        // Fallback: a stable substring of the parts (still dedupable per
        // device install, even if the SHA-256 path is unavailable).
        parts.take(32)
    }
}

private fun parseDeviceName(): String {
    val model = android.os.Build.MODEL ?: ""
    val release = android.os.Build.VERSION.RELEASE ?: ""
    return "Android $model / $release"
}
