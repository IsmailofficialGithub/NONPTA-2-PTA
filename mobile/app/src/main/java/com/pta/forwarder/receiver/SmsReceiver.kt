package com.pta.forwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
            status = "Forwarded"
        )

        // 2. Broadcast to UI if MainActivity is open
        val localIntent = Intent(MessageLogManager.ACTION_NEW_MESSAGE).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(localIntent)

        // 3. Dispatch to Telegram and Discord
        val dispatcher = CloudDispatcher(config)
        dispatcher.dispatchSms(sender, fullBody, timestamp, simInfo)

        // 4. Ensure background service stays alive
        if (config.isServiceRunning) {
            ForwarderService.start(context)
        }
    }
}
