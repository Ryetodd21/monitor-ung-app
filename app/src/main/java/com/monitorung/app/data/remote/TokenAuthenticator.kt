package com.monitorung.app.data.remote

import androidx.core.content.edit
import android.content.Context
import com.monitorung.app.BuildConfig
import com.monitorung.app.data.remote.api.MonitorUNGApi
import com.monitorung.app.data.remote.dto.RefreshTokenRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.google.gson.GsonBuilder
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp [Authenticator] that catches HTTP 401 responses, exchanges the saved
 * refresh token for a new access token, and retries the original request once.
 *
 * Why an `Authenticator` and not an `Interceptor`:
 * - OkHttp calls `Authenticator` AFTER all interceptors run and ONLY when the
 *   server returns 401. This guarantees we only refresh when the access token
 *   is actually stale — not on every request.
 * - The pattern is "silent retry": the calling code (Repository / ViewModel)
 *   never sees the 401, just the eventual successful response.
 *
 * Concurrency:
 * - Multiple in-flight requests can hit 401 simultaneously. The `refreshMutex`
 *   ensures only ONE refresh request runs at a time — others wait, then read
 *   the freshly-stored access token and retry with it.
 *
 * Token storage:
 * - Access + refresh tokens are stored in `EncryptedSharedPreferences` by
 *   `AuthRepositoryImpl` (AES256-GCM, backed by Android Keystore). We read
 *   them directly here to avoid a circular dep on `AuthRepository`.
 *
 * Failure modes:
 * - No refresh token saved → return null (caller sees 401, surfaces error).
 * - Refresh 401 clears tokens; transient HTTP/network errors keep the session for retry.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : Authenticator {

    private val refreshMutex = Mutex()

    /**
     * Self-contained Retrofit client for the refresh-token call.
     * Uses its own OkHttpClient with this Authenticator so retry logic
     * works correctly. NOT shared with the main client — avoids DI cycles.
     */
    private val api: MonitorUNGApi by lazy {
        val refreshClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // Only the refresh call uses this client; the Authenticator
            // is set so the retry interceptors in the main client chain
            // are consistent. It will never loop since the refresh
            // endpoint does not require auth.
            .authenticator(this)
            .build()
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(refreshClient)
            // Parity with AppModule.provideRetrofit: use a configured Gson that
            // reflects Kotlin null types + generic types faithfully. Without
            // serializeNulls() + setLenient(), R8-stripped generic type params
            // on suspend `Response<RefreshTokenResponse>` would throw
            // `java.lang.Class cannot be cast to java.lang.reflect.ParameterizedType`
            // on the first token refresh — the exact crash round 13 fixed on
            // the main Retrofit, but missed this secondary one.
            .addConverterFactory(
                GsonConverterFactory.create(
                    GsonBuilder().serializeNulls().setStrictness(com.google.gson.Strictness.LENIENT).create()
                )
            )
            .build()
            .create(MonitorUNGApi::class.java)
    }

    private val securePrefs by lazy {
        com.monitorung.app.util.SecurePrefs.get(context, "secure_prefs")
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        // Don't loop forever — if we already retried this request once, give up.
        if (response.request.header(HEADER_RETRY) != null) {
            return null
        }
        // If the failing request didn't use a Bearer token, there's nothing to refresh.
        val authHeader = response.request.header("Authorization") ?: return null
        if (!authHeader.startsWith("Bearer ")) return null

        // Run the refresh synchronously — OkHttp's Authenticator contract is
        // blocking. Mutex serialises concurrent 401s across all in-flight calls.
        val newToken = runBlocking {
            refreshMutex.withLock {
                // Double-check inside the lock: another caller may have already
                // refreshed while we were waiting. If the current access token
                // differs from the one that was sent, just use it.
                val currentToken = securePrefs.getString(KEY_ACCESS_TOKEN, null)
                val sentToken = authHeader.removePrefix("Bearer ").trim()
                if (currentToken != null && currentToken != sentToken) {
                    return@withLock currentToken
                }
                val refreshToken = securePrefs.getString(KEY_REFRESH_TOKEN, null)
                    ?: return@withLock null
                // `try/catch` (not `runCatching`) so the suspending
                // `api.refreshToken(...)` call is allowed inside this suspending
                // `withLock` block — `runCatching`'s lambda is non-suspending.
                val newAccessToken: String? = try {
                    val resp = api.refreshToken(RefreshTokenRequest(refreshToken))
                    if (!resp.isSuccessful) {
                        if (resp.code() == 401 || resp.code() == 403) {
                            securePrefs.edit {
                                remove("last_server_validation")
                            }
                        }
                        if (resp.code() == 401) clearStoredTokens()
                        return@withLock null
                    }
                    val body = resp.body() ?: return@withLock null
                    // Server rotates the refresh token on every /api/auth/refresh
                    // call (server/app/routers/auth.py:263, 280-289 — old Token row
                    // is deleted, new one is issued). The previous handler stored
                    // only the access token, so the next refresh call hit the
                    // server with the now-deleted old refresh_token → 401 →
                    // TokenAuthenticator cleared stored tokens → user had to
                    // re-login. Round 17 fix: persist the rotated refresh token.
                    securePrefs.edit {
                        putString(KEY_ACCESS_TOKEN, body.accessToken)
                        putString(KEY_REFRESH_TOKEN, body.refreshToken)
                        putLong("last_server_validation", System.currentTimeMillis())
                    }
                    body.accessToken
                } catch (e: Exception) {
                    null
                }
                newAccessToken
            }
        } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .header(HEADER_RETRY, "1")
            .build()
    }

    private fun clearStoredTokens() {
        securePrefs.edit {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
        }
    }

    companion object {
        private const val HEADER_RETRY = "X-Token-Retry"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}
