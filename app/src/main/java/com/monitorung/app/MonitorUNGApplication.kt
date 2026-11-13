package com.monitorung.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.monitorung.app.data.lock.UnlockTracker
import com.monitorung.app.receiver.UnlockReceiver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MonitorUNGApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var unlockTracker: UnlockTracker

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        registerUnlockReceiver()
    }

    private fun createNotificationChannels() {

        val manager = getSystemService(NotificationManager::class.java)

        listOf(
            NotificationChannel(
                getString(R.string.channel_monitoring_id),
                getString(R.string.channel_monitoring_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_monitoring_desc)
                setShowBadge(false)
            },
            NotificationChannel(
                getString(R.string.channel_reminder_id),
                getString(R.string.channel_reminder_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_reminder_desc)
            },
            NotificationChannel(
                getString(R.string.channel_sync_id),
                getString(R.string.channel_sync_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.channel_sync_desc)
            },
        ).forEach { manager.createNotificationChannel(it) }
    }

    private fun registerUnlockReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(UnlockReceiver(unlockTracker), filter)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
