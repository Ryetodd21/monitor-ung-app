package com.monitorung.app.data.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.monitorung.app.data.remote.api.MonitorUNGApi
import com.monitorung.app.data.remote.dto.FcmTokenRequest
import com.monitorung.app.util.SecurePrefs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Round 27 — Firebase Cloud Messaging (FCM) service.
 *
 * Fires when the OS hands us a new FCM token (after install, on token
 * rotation) or a new data message. We forward the token to the server
 * so cluster-transition notifications can find this device, and we
 * log incoming data messages for debugging.
 *
 * Graceful dormancy: the Firebase SDK is initialized at app start
 * (via google-services.json) regardless of whether the project is
 * real or a placeholder. With a placeholder, FCM token fetches still
 * succeed but the token is not registered with any real Firebase
 * project — messages sent to that token would silently drop at the
 * Firebase backend. The Android side never errors out.
 *
 * Auth: tokens are PUT to the server's
 * `PUT /api/users/me/fcm-token` with the access token read directly
 * from SecurePrefs (the same encrypted storage the rest of the app
 * uses). No need to inject AuthRepositoryImpl — the FCMService is
 * short-lived and we don't want to pull in a half-dozen unrelated
 * dependencies.
 */
@AndroidEntryPoint
class FCMService : FirebaseMessagingService() {

    @Inject lateinit var api: MonitorUNGApi

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Round 29 audit P1 #7: FCM token is a sensitive identifier
        // (a third party with the token can send messages to this
        // device via Firebase). Move the plaintext token log to
        // DEBUG so it doesn't end up in production logcat (which on
        // Android 4.1+ is readable to any app with READ_LOGS — a
        // permission that user-installed apps can still request on
        // older OS versions and that OEM bloatware always has).
        // Round 29 firebase-fallback: FCM is currently dormant
        // (placeholder google-services.json, no real upstream
        // project) so the callback fires but the registration is a
        // no-op. We log only the HTTP status, not the token.
        Log.d(TAG, "onNewToken: ${token.take(12)}...")
        scope.launch {
            try {
                // FCMService is a Service (a Context), so we can use
                // `this` to open the encrypted prefs. The token key
                // matches AuthRepositoryImpl ("access_token").
                val accessToken = SecurePrefs.get(this@FCMService, "secure_prefs")
                    .getString("access_token", null)
                if (accessToken.isNullOrEmpty()) {
                    // Not logged in — keep the token, it'll be retried
                    // on the next session. FCM tokens don't expire
                    // quickly so this is fine.
                    Log.d(TAG, "onNewToken: not logged in, deferring registration")
                    return@launch
                }
                val response = api.registerFcmToken(accessToken, FcmTokenRequest(token = token))
                Log.i(TAG, "FCM token registered: HTTP ${response.code()}")
            } catch (e: Exception) {
                // Don't crash on failure — the token will be retried
                // on the next fresh-token event. With a placeholder
                // google-services.json, the OS still invokes this
                // callback but the upstream Firebase project doesn't
                // exist, so the call may fail. That's expected and
                // safe to ignore.
                Log.w(TAG, "FCM token registration failed (will retry on next rotation): ${e.message}")
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "Monitor UNG"
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: ""
        // Round 29 audit P1 #8: the FCM data payload can contain
        // server-injected fields (e.g. cluster_label, action_url)
        // that an attacker with logcat access could replay or
        // fingerprint. Move to DEBUG. Round 29 firebase-fallback:
        // FCM is currently dormant so the data payload is never
        // populated in practice, but the log line stays as a
        // debugging hook for when a real google-services.json is
        // dropped in.
        Log.d(TAG, "onMessageReceived: title=$title data=${remoteMessage.data}")
        // We don't post a system notification here — the MQTT
        // channel (and the cluster-transition flows that fire it)
        // already covers in-app delivery. FCM is the OS-level
        // wake-up channel for app-closed scenarios; once the user
        // taps the notification, the OS launches the app, the MQTT
        // client reconnects, and the full live feed resumes. If we
        // ever want to render a system notification too, build it
        // here from `title` + `body` + `remoteMessage.data` and
        // call NotificationManagerCompat.notify(...).
    }

    companion object {
        private const val TAG = "FCMService"
    }
}
