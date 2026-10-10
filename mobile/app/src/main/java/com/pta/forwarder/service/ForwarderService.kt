package com.pta.forwarder.service

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pta.forwarder.MainActivity
import com.pta.forwarder.PtaApplication
import com.pta.forwarder.R
import com.pta.forwarder.data.AppConfig
import com.pta.forwarder.receiver.CallListener

class ForwarderService : Service() {

    companion object {
        private const val TAG = "ForwarderService"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, ForwarderService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ForwarderService::class.java)
            context.stopService(intent)
        }
    }

    private var callListener: CallListener? = null
    private var relayClient: com.pta.forwarder.network.RelayClient? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ForwarderService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val config = AppConfig(this)
        config.isServiceRunning = true

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Initialize Relay WebSocket Client for bidirectional control
        if (relayClient == null && config.relayEnabled && config.relayWsUrl.isNotBlank()) {
            relayClient = com.pta.forwarder.network.RelayClient(
                config = config,
                onAnswer = {
                    Log.i(TAG, "Received remote ANSWER request")
                    callListener?.acceptCall()
                },
                onReject = {
                    Log.i(TAG, "Received remote REJECT request")
                    callListener?.endCall()
                }
            ).apply {
                connect()
            }
        }

        // Initialize Call Listener with Relay event forwarding
        if (callListener == null) {
            callListener = CallListener(this, config) { eventType, number, name, timestamp ->
                val payload = org.json.JSONObject().apply {
                    put("eventType", eventType.name)
                    put("number", number)
                    put("name", name ?: "")
                    put("timestamp", timestamp)
                }
                relayClient?.sendEvent("CALL_STATE", payload)
            }.apply {
                startListening()
            }
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val config = AppConfig(this)
        // If the user hasn't explicitly stopped the service, resurrect it immediately
        if (config.isServiceRunning) {
            Log.d(TAG, "Task removed from recents. Scheduling automatic service restart.")
            val restartIntent = Intent(applicationContext, ForwarderService::class.java).apply {
                setPackage(packageName)
            }
            val pendingIntent = PendingIntent.getService(
                applicationContext,
                101,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                pendingIntent
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Do NOT reset config.isServiceRunning = false here.
        // onDestroy is invoked when Android reclaims resources or swipes recent tasks.
        // User intent is only stopped when they tap STOP in the UI.

        callListener?.stopListening()
        callListener = null

        relayClient?.disconnect()
        relayClient = null

        Log.d(TAG, "ForwarderService destroyed (Resources released)")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, PtaApplication.CHANNEL_SERVICE_ID)
            .setContentTitle("PTA Forwarder Active")
            .setContentText("Forwarding incoming SMS & calls to Telegram & Discord")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
