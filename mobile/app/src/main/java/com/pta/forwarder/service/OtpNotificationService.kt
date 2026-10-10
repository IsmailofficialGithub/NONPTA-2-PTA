package com.pta.forwarder.service

import android.app.Notification
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pta.forwarder.MainActivity
import com.pta.forwarder.PtaApplication
import com.pta.forwarder.R
import com.pta.forwarder.data.AppConfig
import com.pta.forwarder.data.DeviceRole
import com.pta.forwarder.util.OtpParser

class OtpNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "OtpNotificationService"
        private val MONITORED_PACKAGES = setOf(
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.thunderdog.challegram",
            "org.telegram.plus",
            "com.discord"
        )
    }

    private var lastExtractedCode: String? = null
    private var lastExtractedTime: Long = 0

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val config = AppConfig(this)
        if (config.role != DeviceRole.RECEIVER) {
            return
        }

        val packageName = sbn.packageName
        if (!MONITORED_PACKAGES.contains(packageName)) {
            return
        }

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val combinedContent = "$title\n$text\n$bigText"
        Log.d(TAG, "Inspecting notification from $packageName")

        val parsedOtp = OtpParser.extractOtp(combinedContent)
        if (parsedOtp != null) {
            // Deduplicate within 3 seconds
            val now = System.currentTimeMillis()
            if (parsedOtp.code == lastExtractedCode && (now - lastExtractedTime) < 3000) {
                return
            }
            lastExtractedCode = parsedOtp.code
            lastExtractedTime = now

            handleExtractedOtp(parsedOtp.code, parsedOtp.serviceName)
        }
    }

    private fun handleExtractedOtp(code: String, serviceName: String?) {
        Log.i(TAG, "OTP auto-captured: $code for service: $serviceName")

        // 1. Copy to Clipboard
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("OTP Code", code)
        clipboard.setPrimaryClip(clip)

        // 2. Trigger Haptic Vibration
        triggerHaptic()

        // 3. Post Visual Heads-up Notification
        postCopiedNotification(code, serviceName)
    }

    private fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val effect = VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE)
                vibratorManager.vibrate(CombinedVibration.createParallel(effect))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(150)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }

    private fun postCopiedNotification(code: String, serviceName: String?) {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val serviceLabel = if (serviceName != null) " ($serviceName)" else ""
        val notification = NotificationCompat.Builder(this, PtaApplication.CHANNEL_ALERTS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🔑 OTP Copied to Clipboard")
            .setContentText("Code: $code$serviceLabel ready to paste")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(2002, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted")
        }
    }
}
