package com.pta.forwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Telephony
import android.util.Log
import com.pta.forwarder.data.AppConfig
import com.pta.forwarder.data.DeviceRole
import com.pta.forwarder.data.MessageLogManager
import com.pta.forwarder.network.CloudDispatcher
import com.pta.forwarder.service.ForwarderService
import com.pta.forwarder.util.OtpParser
import com.pta.forwarder.util.SimUtil

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val config = AppConfig(context)
        if (config.role != DeviceRole.SENDER || !config.isServiceRunning) {
            Log.d(TAG, "Ignoring SMS: role=${config.role}, isRunning=${config.isServiceRunning}")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].displayOriginatingAddress ?: "Unknown"
        val timestamp = messages[0].timestampMillis

        // Combine parts if multi-part message
        val fullBody = messages.joinToString(separator = "") { it.displayMessageBody ?: "" }

        // Extract SIM and carrier info
        val simInfo = SimUtil.extractSimInfo(context, intent)

        // Filter based on user preferences for SIM 1 and SIM 2
        if (simInfo.slotIndex == 0 && !config.forwardSim1) {
            Log.d(TAG, "Ignoring SMS: SIM 1 forwarding is disabled in settings")
            return
        }
        if (simInfo.slotIndex == 1 && !config.forwardSim2) {
            Log.d(TAG, "Ignoring SMS: SIM 2 forwarding is disabled in settings")
            return
        }

        val parsedOtp = OtpParser.extractOtp(fullBody)

        Log.d(TAG, "Captured incoming SMS from: $sender on ${simInfo.fullTag}")

        // 1. Save to local log for in-app history feed
        val logManager = MessageLogManager(context)
        logManager.addMessage(
            sender = sender,
            body = fullBody,
            otp = parsedOtp?.code,
            simTag = simInfo.fullTag,
            timestamp = timestamp,
            status = "Sending..."
        )

        // 2. Broadcast to UI if MainActivity is open
        val localIntent = Intent(MessageLogManager.ACTION_NEW_MESSAGE).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(localIntent)

        // 3. Keep process and CPU alive in the background using goAsync() & WakeLock
        val pendingResult = goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PtaForwarder:SmsReceiverWakeLock"
        )
        wakeLock?.acquire(25000) // Hold CPU awake for up to 25 seconds

        Thread {
            try {
                val dispatcher = CloudDispatcher(config)
                val ok = dispatcher.dispatchSmsSync(sender, fullBody, timestamp, simInfo)

                logManager.addMessage(
                    sender = sender,
                    body = fullBody,
                    otp = parsedOtp?.code,
                    simTag = simInfo.fullTag,
                    timestamp = timestamp,
                    status = if (ok) "Forwarded" else "Failed"
                )
                context.sendBroadcast(localIntent)

                // 4. Ensure background service stays alive
                if (config.isServiceRunning) {
                    ForwarderService.start(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in background SMS dispatch", e)
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (_: Exception) {}
                pendingResult.finish()
            }
        }.start()
    }
}
