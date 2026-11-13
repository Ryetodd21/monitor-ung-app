package com.monitorung.app.data.push

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.monitorung.app.BuildConfig
import com.monitorung.app.domain.repository.CampusConfigRepository
import com.monitorung.app.domain.repository.AuthRepository
import com.monitorung.app.util.AppConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MQTT push client — subscribes to `dashboard/broadcast` on the broker URL
 * fetched from the server. Plain Java client (no `MqttAndroidClient`) because
 * the lifecycle is managed by the application scope, not an Android Service.
 *
 * Events arrive as JSON envelopes with shape `{event: string, ...payload}`.
 * The `_events` `SharedFlow` emits one parsed envelope per inbound message —
 * UI / ViewModels can filter by event name without parsing twice.
 *
 * Auto-reconnects with exponential backoff up to 30s. Initial connect uses
 * `cleanSession=true` so the broker doesn't queue messages while offline
 * (which would flood the client on reconnect).
 */
@Singleton
class MqttPushClient @Inject constructor(
    private val campusConfigRepository: CampusConfigRepository,
    private val authRepository: AuthRepository,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()

    private val _events = MutableSharedFlow<MqttEvent>(
        replay = 0,
        extraBufferCapacity = 32,
    )
    val events: SharedFlow<MqttEvent> = _events.asSharedFlow()

    @Volatile private var client: MqttAsyncClient? = null
    @Volatile private var running = false
    @Volatile private var attempt = 0
    // Round 27: tracks whether we've escalated to the fallback broker. Reset
    // when a successful connection completes so the next outage starts at
    // primary again (otherwise the client would stay on fallback forever
    // even after primary recovers).
    @Volatile private var usingFallback = false
    // How many consecutive primary failures before trying fallback. 3 ≈ 30-90s
    // of total backoff time (10s + 20s + 30s capped) — fast enough to recover
    // quickly when the laptop broker is down, slow enough not to flap on a
    // single dropped connection.
    private val PRIMARY_FAILURE_THRESHOLD = 3

    /**
     * Publish a payload to an MQTT topic. Fire-and-forget — failures are logged
     * and do not propagate. Callers use this for real-time app-usage events
     * so the website dashboard receives data without waiting for the next REST sync.
     */
    fun publish(topic: String, payload: String) {
        val c = client
        if (c == null || !c.isConnected) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val token = authRepository.getRefreshToken()?.takeIf { it.isNotBlank() }
                    ?: return@runCatching
                val sentAt = System.currentTimeMillis() / 1000
                val envelope = gson.toJson(mapOf(
                    "payload" to payload,
                    "sent_at" to sentAt,
                    "signature" to mqttSignature(topic, payload, sentAt, token),
                ))
                val msg = MqttMessage(envelope.toByteArray(Charsets.UTF_8))
                msg.qos = 1
                c.publish(topic, msg)
                Log.d(TAG, "telemetry published")
            }.onFailure { Log.w(TAG, "publish failed ($topic): ${it.message}") }
        }
    }

    /**
     * Start the background subscriber. Idempotent — second call while running
     * is a no-op. Called from `SmartMonitoringService.onStartCommand()` when
     * the user is authenticated AND the feature is enabled in settings.
     *
     * Round 27: auto-fallback to VPS mosquitto. The retry loop tries primary
     * first; after PRIMARY_FAILURE_THRESHOLD consecutive failures, it switches
     * to the fallback URL (VPS). Once a connection (any) succeeds, the next
     * outage starts at primary again — this prevents the client from staying
     * pinned to a degraded fallback forever.
     */
    fun start(context: Context) {
        if (running) return
        running = true
        attempt = 0
        usingFallback = false
        scope.launch {
            // Retry loop with exponential backoff. The broker URL can change
            // (admin updated `campus.mqtt_broker_url`), so re-read each cycle.
            while (running) {
                val fallbackUrl = try {
                    campusConfigRepository.getMqttBrokerUrlFallback()
                } catch (_: Exception) { "" }
                val brokerUrl = if (usingFallback && fallbackUrl.isNotBlank()) {
                    fallbackUrl
                } else {
                    try {
                        campusConfigRepository.getMqttBrokerUrl()
                    } catch (_: Exception) { null }
                }
                if (brokerUrl.isNullOrBlank()) {
                    delayBackoff()
                    continue
                }
                try {
                    connectAndSubscribe(context, brokerUrl)
                    // Block until client disconnects — `connectAndSubscribe`
                    // returns only on error or shutdown. Reset backoff so the
                    // next cycle reconnects immediately.
                    attempt = 0
                    usingFallback = false  // primary is healthy again
                    while (running && client?.isConnected == true) {
                        kotlinx.coroutines.delay(AppConstants.MQTT_KEEPALIVE_INTERVAL_MS)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "connect cycle failed (broker=$brokerUrl, usingFallback=$usingFallback): ${e.message}")
                    // Round 27: if we just failed on the primary broker,
                    // increment a counter; once it crosses the threshold,
                    // switch to fallback for the next cycle.
                    if (!usingFallback) {
                        attempt++
                        if (attempt >= PRIMARY_FAILURE_THRESHOLD && fallbackUrl.isNotBlank()) {
                            usingFallback = true
                            attempt = 0  // reset backoff for the fallback attempt
                            Log.w(TAG, "primary broker failed $PRIMARY_FAILURE_THRESHOLD times — switching to fallback $fallbackUrl")
                        }
                    } else {
                        attempt++
                    }
                }
                if (!running) return@launch
                delayBackoff()
            }
        }
    }

    fun stop() {
        running = false
        try {
            client?.disconnect()
        } catch (_: Exception) {}
        client = null
    }

    private suspend fun connectAndSubscribe(context: Context, brokerUrl: String) {
        // Round 26 (P1) — WSS scheme + path:
        // The server returns `wss://mqtt.monitorterop.my.id/mqtt`. For WSS, we
        // MUST pass the full URI including the path (`/mqtt`) to MqttAsyncClient.
        // MqttClient silently drops the path — without it the WebSocket upgrade
        // reaches the FastAPI /mqtt proxy but the CONNECT never reaches mosquitto.
        // MqttAsyncClient.accepts the full URI (scheme + host + port + path) and
        // includes it in the WebSocket HTTP upgrade request. MqttClient extends
        // MqttAsyncClient so setCallback() still works.
        val rawScheme = brokerUrl.substringBefore("://")
        val afterScheme = brokerUrl.substringAfter("://")
        // Extract path (e.g. "/mqtt" from "mqtt.monitorterop.my.id/mqtt")
        val pathAndMaybePort = afterScheme
        val path = if (pathAndMaybePort.contains("/")) "/${pathAndMaybePort.substringAfter("/")}" else ""
        val hostAndPort = pathAndMaybePort.substringBefore("/")
        // Round 29 audit P1 #10: refuse to silently downgrade to plaintext
        // when the server response carries an unknown scheme. Previously
        // `else -> "tcp://"` accepted any string (typo, hostile response,
        // misconfigured proxy) and sent the connection in cleartext. Now
        // we only allow wss:// and ssl:// for production, and tcp://
        // ONLY for the dev flavor — controlled by BuildConfig, not by
        // a server-controlled string. The brokerUrl is a runtime param
        // because the Android side fetches it from /api/config/campus,
        // so we can't trust it as much as a hardcoded value.
        val clientScheme = when (rawScheme) {
            "wss" -> "wss://"
            "ssl" -> "ssl://"
            "tcp" -> if (BuildConfig.DEBUG) "tcp://" else {
                // Production build + tcp:// in server response = something
                // is wrong (downgrade attack, misconfigured broker). Refuse.
                throw IllegalStateException(
                    "Plaintext tcp:// MQTT rejected in production build. " +
                    "Server-advertised URL: $brokerUrl"
                )
            }
            else -> throw IllegalArgumentException(
                "Unknown MQTT scheme '$rawScheme' in broker URL: $brokerUrl. " +
                "Expected one of: wss, ssl, tcp (dev only)."
            )
        }
        val clientId = "monitorung-${UUID.randomUUID().toString().take(12)}"
        // MemoryPersistence: no disk I/O, no leak across logout (key file
        // would persist past token revocation).
        val mqtt = MqttAsyncClient(
            "$clientScheme$hostAndPort$path",
            clientId,
            MemoryPersistence(),
        )
        val opts = MqttConnectOptions().apply {
            isCleanSession = true
            isAutomaticReconnect = true
            connectionTimeout = 10
            keepAliveInterval = 30
            // Wave 4: password auth. Production broker (mosquitto) is exposed
            // publicly via Cloudflare Tunnel → no-auth would let anyone publish
            // fake data to students/$id/logs or subscribe to dashboard/broadcast.
            // Username/password baked into APK via BuildConfig (same shared
            // secret as the server-side subscriber). Trade-off: APK-extractable
            // secret — proper hardening is TLS + client certs (future wave).
            if (BuildConfig.MQTT_USERNAME.isNotEmpty()) {
                userName = BuildConfig.MQTT_USERNAME
                password = BuildConfig.MQTT_PASSWORD.toCharArray()
            }
            // Backward compat: dev flavor or local broker with allow_anonymous=true
            // leaves userName/password unset → paho sends CONNECT without auth.
        }
        mqtt.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable) {
                Log.w(TAG, "connectionLost: ${cause.message}")
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                if (message == null) return
                val text = String(message.payload, Charsets.UTF_8)
                runCatching {
                    // gson.fromJson returns Map<Any?, Any?> for raw Map.class;
                    // narrow to Map<String, Any?> so downstream consumers get
                    // typed keys. Numeric keys (rare) end up stringified via
                    // toString() — acceptable for our event names which are
                    // always strings in the server schema.
                    val raw = gson.fromJson(text, Map::class.java)
                        ?.mapKeys { it.key.toString() }
                        ?: emptyMap<String, Any?>()
                    val eventName = (raw["event"] as? String) ?: "unknown"
                    scope.launch { _events.emit(MqttEvent(eventName, raw)) }
                }.onFailure {
                    // Round 30 audit (P2): don't log the raw payload —
                    // it can contain student NIMs, locations, and
                    // usage data. Log only the error class + size.
                    Log.w(TAG, "parse failed (len=${text.length}): ${it.javaClass.simpleName}", it)
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
        })
        // MqttAsyncClient.connect() returns IMqttToken — wait for completion in
        // this suspend function. subscribe() also returns IMqttToken.
        mqtt.connect(opts).waitForCompletion()
        client = mqtt
        // Round 29 audit P1 #9: broker URL + clientId are not secrets
        // in the strict sense (the JWT is the auth credential), but
        // leaking them in production logcat gives an attacker the
        // full connection fingerprint (which broker, which client
        // instance) — easier to correlate with a leaked topic or
        // timing side-channel. Move to DEBUG.
        Log.d(TAG, "MQTT connected: broker=$brokerUrl client_id=$clientId")
        mqtt.subscribe("dashboard/broadcast", 1).waitForCompletion()
    }

    private suspend fun delayBackoff() {
        // Exponential backoff capped at 30s — gives the broker room to recover
        // without hammering it during an outage.
        attempt = (attempt + 1).coerceAtMost(AppConstants.MQTT_BACKOFF_SECONDS.size)
        val seconds = AppConstants.MQTT_BACKOFF_SECONDS[attempt - 1]
        kotlinx.coroutines.delay(seconds * 1000L)
    }
}

data class MqttEvent(
    val name: String,
    val payload: Map<String, Any?>,
)

private const val TAG = "MqttPushClient"

internal fun mqttSignature(topic: String, payload: String, sentAt: Long, token: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(token.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    val message = "monitorung-mqtt-v1\n$topic\n$sentAt\n$payload"
    return mac.doFinal(message.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
