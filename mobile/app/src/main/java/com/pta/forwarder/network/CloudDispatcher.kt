package com.pta.forwarder.network

import android.content.Context
import android.util.Log
import com.pta.forwarder.data.AppConfig
import com.pta.forwarder.util.OtpParser
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

enum class CallEventType {
    RINGING,
    MISSED,
    ANSWERED
}

class CloudDispatcher(private val config: AppConfig) {

    companion object {
        private const val TAG = "CloudDispatcher"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun normalizeDiscordWebhookUrl(raw: String): String {
            val trimmed = raw.trim()
            return when {
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                trimmed.startsWith("discord.com/api/webhooks/") -> "https://$trimmed"
                trimmed.contains("/") && !trimmed.contains(" ") -> "https://discord.com/api/webhooks/$trimmed"
                else -> trimmed
            }
        }

        fun testDiscordWebhook(context: Context, webhookUrl: String, onResult: (Boolean, String) -> Unit) {
            val config = AppConfig(context)
            val dispatcher = CloudDispatcher(config)
            val embed = JSONObject().apply {
                put("title", "PTA Forwarder Test")
                put("description", "Discord webhook is verified and active.")
                put("color", 0x16A34A)
            }
            dispatcher.sendDiscord(webhookUrl, embed) { success, msg ->
                val finalMsg = if (success) "Connection successful! Discord received test ping." else (msg ?: "Failed to connect to Discord")
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onResult(success, finalMsg)
                }
            }
        }
    }

    private val httpClient = OkHttpClient.Builder().build()
    private val timeFormat = SimpleDateFormat("HH:mm:ss, dd MMM", Locale.getDefault())

    fun dispatchSms(sender: String, body: String, timestamp: Long, simInfo: com.pta.forwarder.util.SimInfo? = null) {
        val parsedOtp = OtpParser.extractOtp(body)
        val formattedTime = timeFormat.format(Date(timestamp))
        val simTag = if (simInfo != null) " [${simInfo.fullTag}]" else ""

        // 1. Dispatch to Telegram
        if (config.telegramEnabled && config.telegramBotToken.isNotBlank() && config.telegramChatId.isNotBlank()) {
            val telegramText = buildString {
                if (parsedOtp != null) {
                    append("🔑 <b>OTP: <code>${parsedOtp.code}</code></b>")
                    if (parsedOtp.serviceName != null) {
                        append(" (${parsedOtp.serviceName})")
                    }
                    append("\n\n")
                }
                append("📩 <b>New SMS$simTag</b>\n")
                append("👤 <b>From:</b> <code>${escapeHtml(sender)}</code>\n")
                if (simInfo != null) {
                    append("📶 <b>Network:</b> ${escapeHtml(simInfo.fullTag)}\n")
                }
                append("🕒 <b>Time:</b> ${escapeHtml(formattedTime)}\n\n")
                append("📝 <b>Message:</b>\n${escapeHtml(body)}")
            }
            sendTelegram(config.telegramBotToken, config.telegramChatId, telegramText)
        }

        // 2. Dispatch to Discord
        if (config.discordEnabled && config.discordWebhookUrl.isNotBlank()) {
            val embed = createDiscordEmbed(sender, body, formattedTime, simTag, simInfo, parsedOtp)
            sendDiscord(config.discordWebhookUrl, embed)
        }
    }

    fun dispatchSmsSync(sender: String, body: String, timestamp: Long, simInfo: com.pta.forwarder.util.SimInfo? = null): Boolean {
        val parsedOtp = OtpParser.extractOtp(body)
        val formattedTime = timeFormat.format(Date(timestamp))
        val simTag = if (simInfo != null) " [${simInfo.fullTag}]" else ""
        var success = true

        // 1. Telegram
        if (config.telegramEnabled && config.telegramBotToken.isNotBlank() && config.telegramChatId.isNotBlank()) {
            val telegramText = buildString {
                if (parsedOtp != null) {
                    append("🔑 <b>OTP: <code>${parsedOtp.code}</code></b>")
                    if (parsedOtp.serviceName != null) {
                        append(" (${parsedOtp.serviceName})")
                    }
                    append("\n\n")
                }
                append("📩 <b>New SMS$simTag</b>\n")
                append("👤 <b>From:</b> <code>${escapeHtml(sender)}</code>\n")
                if (simInfo != null) {
                    append("📶 <b>Network:</b> ${escapeHtml(simInfo.fullTag)}\n")
                }
                append("🕒 <b>Time:</b> ${escapeHtml(formattedTime)}\n\n")
                append("📝 <b>Message:</b>\n${escapeHtml(body)}")
            }
            val tgOk = sendTelegramSync(config.telegramBotToken, config.telegramChatId, telegramText)
            if (!tgOk) success = false
        }

        // 2. Discord
        if (config.discordEnabled && config.discordWebhookUrl.isNotBlank()) {
            val embed = createDiscordEmbed(sender, body, formattedTime, simTag, simInfo, parsedOtp)
            val dcOk = sendDiscordSync(config.discordWebhookUrl, embed)
            if (!dcOk) success = false
        }

        return success
    }

    private fun createDiscordEmbed(
        sender: String,
        body: String,
        formattedTime: String,
        simTag: String,
        simInfo: com.pta.forwarder.util.SimInfo?,
        parsedOtp: com.pta.forwarder.util.ParsedOtp?
    ): JSONObject {
        return JSONObject().apply {
            put("title", if (parsedOtp != null) "🔑 OTP: ${parsedOtp.code}" else "📩 New SMS$simTag")
            put("description", body)
            put("color", if (parsedOtp != null) 0x10B981 else 0x2563EB)
            val fields = JSONArray().apply {
                put(JSONObject().apply {
                    put("name", "From")
                    put("value", sender)
                    put("inline", true)
                })
                if (simInfo != null) {
                    put(JSONObject().apply {
                        put("name", "SIM / Network")
                        put("value", simInfo.fullTag)
                        put("inline", true)
                    })
                }
                put(JSONObject().apply {
                    put("name", "Time")
                    put("value", formattedTime)
                    put("inline", true)
                })
                if (parsedOtp?.serviceName != null) {
                    put(JSONObject().apply {
                        put("name", "Service")
                        put("value", parsedOtp.serviceName)
                        put("inline", true)
                    })
                }
            }
            put("fields", fields)
        }
    }

    fun dispatchNotification(appName: String, title: String, text: String, timestamp: Long) {
        val formattedTime = timeFormat.format(Date(timestamp))

        // 1. Dispatch to Telegram
        if (config.telegramEnabled && config.telegramBotToken.isNotBlank() && config.telegramChatId.isNotBlank()) {
            val telegramText = buildString {
                append("💬 <b>${escapeHtml(appName)} Notification</b>\n")
                if (title.isNotBlank()) {
                    append("👤 <b>From:</b> <code>${escapeHtml(title)}</code>\n")
                }
                append("🕒 <b>Time:</b> ${escapeHtml(formattedTime)}\n\n")
                append("📝 <b>Message:</b>\n${escapeHtml(text)}")
            }
            sendTelegram(config.telegramBotToken, config.telegramChatId, telegramText)
        }

        // 2. Dispatch to Discord
        if (config.discordEnabled && config.discordWebhookUrl.isNotBlank()) {
            val isWa = appName.contains("WhatsApp", ignoreCase = true)
            val embed = JSONObject().apply {
                put("title", if (title.isNotBlank()) "💬 $appName: $title" else "💬 $appName Notification")
                put("description", text)
                put("color", if (isWa) 0x25D366 else 0x3B82F6) // WhatsApp green or clean blue
                val fields = JSONArray().apply {
                    put(JSONObject().apply {
                        put("name", "App")
                        put("value", appName)
                        put("inline", true)
                    })
                    if (title.isNotBlank()) {
                        put(JSONObject().apply {
                            put("name", "Sender")
                            put("value", title)
                            put("inline", true)
                        })
                    }
                    put(JSONObject().apply {
                        put("name", "Time")
                        put("value", formattedTime)
                        put("inline", true)
                    })
                }
                put("fields", fields)
            }
            sendDiscord(config.discordWebhookUrl, embed)
        }
    }

    fun dispatchCall(callerNumber: String, callerName: String?, eventType: CallEventType, timestamp: Long) {
        val formattedTime = timeFormat.format(Date(timestamp))
        val displayName = callerName ?: callerNumber

        val (icon, title, colorHex) = when (eventType) {
            CallEventType.RINGING -> Triple("📞", "Incoming Call", 0x2563EB)
            CallEventType.MISSED -> Triple("🔴", "Missed Call", 0xEF4444)
            CallEventType.ANSWERED -> Triple("🟢", "Call Answered", 0x10B981)
        }

        // 1. Dispatch to Telegram
        if (config.telegramEnabled && config.telegramBotToken.isNotBlank() && config.telegramChatId.isNotBlank()) {
            val telegramText = buildString {
                append("$icon <b>$title</b>\n")
                append("👤 <b>Caller:</b> <code>${escapeHtml(displayName)}</code>")
                if (callerName != null) {
                    append(" (${escapeHtml(callerNumber)})")
                }
                append("\n🕒 <b>Time:</b> ${escapeHtml(formattedTime)}")
            }
            sendTelegram(config.telegramBotToken, config.telegramChatId, telegramText)
        }

        // 2. Dispatch to Discord
        if (config.discordEnabled && config.discordWebhookUrl.isNotBlank()) {
            val embed = JSONObject().apply {
                put("title", "$icon $title")
                put("color", colorHex)
                val fields = JSONArray().apply {
                    put(JSONObject().apply {
                        put("name", "Caller")
                        put("value", displayName)
                        put("inline", true)
                    })
                    put(JSONObject().apply {
                        put("name", "Number")
                        put("value", callerNumber)
                        put("inline", true)
                    })
                    put(JSONObject().apply {
                        put("name", "Time")
                        put("value", formattedTime)
                        put("inline", true)
                    })
                }
                put("fields", fields)
            }
            sendDiscord(config.discordWebhookUrl, embed)
        }
    }

    fun sendTestMessage(
        onTelegramResult: (Boolean, String?) -> Unit,
        onDiscordResult: (Boolean, String?) -> Unit
    ) {
        if (config.telegramEnabled && config.telegramBotToken.isNotBlank() && config.telegramChatId.isNotBlank()) {
            val testText = "🚀 <b>PTA Forwarder Test Message</b>\n\nYour Telegram forwarding is set up and working properly! ✅"
            sendTelegram(config.telegramBotToken, config.telegramChatId, testText, onTelegramResult)
        } else {
            onTelegramResult(false, "Telegram is disabled or credentials are missing.")
        }

        if (config.discordEnabled && config.discordWebhookUrl.isNotBlank()) {
            val embed = JSONObject().apply {
                put("title", "🚀 PTA Forwarder Test Message")
                put("description", "Your Discord webhook forwarding is set up and working properly! ✅")
                put("color", 0x10B981)
                val fields = JSONArray().apply {
                    put(JSONObject().apply {
                        put("name", "Status")
                        put("value", "Connected")
                        put("inline", true)
                    })
                    put(JSONObject().apply {
                        put("name", "Time")
                        put("value", timeFormat.format(Date()))
                        put("inline", true)
                    })
                }
                put("fields", fields)
            }
            sendDiscord(config.discordWebhookUrl, embed, onDiscordResult)
        } else {
            onDiscordResult(false, "Discord is disabled or Webhook URL is missing.")
        }
    }

    fun sendTelegram(
        token: String,
        chatId: String,
        htmlText: String,
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        val url = "https://api.telegram.org/bot$token/sendMessage"
        val jsonPayload = JSONObject().apply {
            put("chat_id", chatId)
            put("text", htmlText)
            put("parse_mode", "HTML")
        }.toString()

        val request = try {
            Request.Builder()
                .url(url)
                .post(jsonPayload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Invalid Telegram URL configuration", e)
            onComplete?.invoke(false, "Invalid URL: ${e.localizedMessage}")
            return
        }

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to send to Telegram", e)
                onComplete?.invoke(false, e.localizedMessage ?: "Network connection failed. (If in Pakistan, Telegram may require a VPN).")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val bodyStr = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Telegram error: ${response.code} - $bodyStr")
                        val errorDesc = try {
                            JSONObject(bodyStr).optString("description", "HTTP ${response.code}")
                        } catch (_: Exception) {
                            "HTTP ${response.code}"
                        }
                        onComplete?.invoke(false, "Telegram: $errorDesc (Make sure you sent /start to your bot)")
                    } else {
                        onComplete?.invoke(true, "Telegram: Message sent successfully!")
                    }
                }
            }
        })
    }

    fun sendDiscord(
        webhookUrl: String,
        embed: JSONObject,
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        val cleanUrl = normalizeDiscordWebhookUrl(webhookUrl)
        val jsonPayload = JSONObject().apply {
            put("embeds", JSONArray().put(embed))
        }.toString()

        val request = try {
            Request.Builder()
                .url(cleanUrl)
                .post(jsonPayload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Invalid Discord Webhook URL", e)
            onComplete?.invoke(false, "Invalid Discord URL: ${e.localizedMessage}")
            return
        }

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to send to Discord", e)
                onComplete?.invoke(false, e.localizedMessage ?: "Discord connection failed.")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val bodyStr = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Discord error: ${response.code} - $bodyStr")
                        val errorDesc = when (response.code) {
                            401, 404 -> "Invalid Webhook URL. Please paste the full Discord Webhook URL (from Channel Settings -> Integrations -> Webhooks)."
                            else -> "HTTP ${response.code}: $bodyStr"
                        }
                        onComplete?.invoke(false, "Discord: $errorDesc")
                    } else {
                        onComplete?.invoke(true, "Discord: Embed sent successfully!")
                    }
                }
            }
        })
    }

    fun sendTelegramSync(token: String, chatId: String, htmlText: String): Boolean {
        val url = "https://api.telegram.org/bot$token/sendMessage"
        val jsonPayload = JSONObject().apply {
            put("chat_id", chatId)
            put("text", htmlText)
            put("parse_mode", "HTML")
        }.toString()

        val request = try {
            Request.Builder()
                .url(url)
                .post(jsonPayload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Invalid Telegram URL (sync)", e)
            return false
        }

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Telegram error: ${response.code} - ${response.body?.string()}")
                    false
                } else {
                    Log.i(TAG, "Telegram sent successfully (sync)")
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send to Telegram (sync)", e)
            false
        }
    }

    fun sendDiscordSync(webhookUrl: String, embed: JSONObject): Boolean {
        val cleanUrl = normalizeDiscordWebhookUrl(webhookUrl)
        val jsonPayload = JSONObject().apply {
            put("embeds", JSONArray().put(embed))
        }.toString()

        val request = try {
            Request.Builder()
                .url(cleanUrl)
                .post(jsonPayload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Invalid Discord URL (sync)", e)
            return false
        }

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Discord error: ${response.code} - ${response.body?.string()}")
                    false
                } else {
                    Log.i(TAG, "Discord sent successfully (sync)")
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send to Discord (sync)", e)
            false
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
