package com.pta.forwarder

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class PtaApplication : Application() {

    companion object {
        const val CHANNEL_SERVICE_ID = "pta_service_channel"
        const val CHANNEL_ALERTS_ID = "pta_alerts_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                "Forwarder Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps PTA forwarder active in the background"
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                "PTA Forwarder Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows OTP and Call notifications"
                enableVibration(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alertsChannel)
        }
    }
}
