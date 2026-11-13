package com.monitorung.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.monitorung.app.service.SmartMonitoringManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Handles Android Geofencing API ENTER/EXIT transitions for the campus
 * perimeter. The geofence is registered by [SmartMonitoringManager] at
 * class start and torn down on stopAll(); this receiver fires whenever
 * the user crosses the campus boundary mid-class.
 *
 * Replaces the old 45-minute polling loop (`LocationRequest` + manual
 * `distanceBetween`) that woke the device every 45 minutes and burned
 * GPS battery continuously. Geofencing is hardware-assisted: Android
 * uses cell-tower / wifi first, GPS only when the user is actually
 * moving across the boundary, so it can run for hours without
 * measurable battery cost.
 *
 * Round 28: replaces the 45-min FusedLocationProviderClient polling
 * pattern in `SmartMonitoringManager.startClassMonitoring()`. The
 * 45-min interval constant is retained as a fallback when the
 * geofence registration fails (location services off, permission
 * denied, etc.).
 */
@AndroidEntryPoint
class GeofenceReceiver : BroadcastReceiver() {

    @Inject
    lateinit var smartMonitoringManager: SmartMonitoringManager

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent)
        if (event == null) {
            Log.w(TAG, "GeofenceReceiver: null GeofencingEvent, ignoring")
            return
        }
        if (event.hasError()) {
            Log.e(TAG, "GeofenceReceiver: error code=${event.errorCode}")
            return
        }

        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                val location = event.triggeringLocation
                Log.d(
                    TAG,
                    "Geofence ENTER: lat=${location?.latitude} lon=${location?.longitude}",
                )
                smartMonitoringManager.onGeofenceEnter(location)
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                val location = event.triggeringLocation
                Log.d(
                    TAG,
                    "Geofence EXIT: lat=${location?.latitude} lon=${location?.longitude}",
                )
                smartMonitoringManager.onGeofenceExit(location)
            }
            else -> {
                Log.d(TAG, "GeofenceReceiver: unknown transition ${event.geofenceTransition}")
            }
        }
    }

    companion object {
        private const val TAG = "GeofenceReceiver"
    }
}
