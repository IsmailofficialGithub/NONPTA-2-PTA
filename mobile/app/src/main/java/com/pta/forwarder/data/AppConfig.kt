package com.pta.forwarder.data

import android.content.Context
import android.content.SharedPreferences

enum class DeviceRole {
    SENDER,
    RECEIVER
}

class AppConfig(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pta_forwarder_prefs", Context.MODE_PRIVATE)

    var role: DeviceRole
        get() = try {
            DeviceRole.valueOf(prefs.getString("role", DeviceRole.SENDER.name) ?: DeviceRole.SENDER.name)
        } catch (e: Exception) {
            DeviceRole.SENDER
        }
        set(value) = prefs.edit().putString("role", value.name).apply()

    var telegramEnabled: Boolean
        get() = prefs.getBoolean("telegram_enabled", false)
        set(value) = prefs.edit().putBoolean("telegram_enabled", value).apply()

    var telegramBotToken: String
        get() = prefs.getString("telegram_bot_token", "") ?: ""
        set(value) = prefs.edit().putString("telegram_bot_token", value).apply()

    var telegramChatId: String
        get() = prefs.getString("telegram_chat_id", "") ?: ""
        set(value) = prefs.edit().putString("telegram_chat_id", value).apply()

    var discordEnabled: Boolean
        get() = prefs.getBoolean("discord_enabled", false)
        set(value) = prefs.edit().putBoolean("discord_enabled", value).apply()

    var discordWebhookUrl: String
        get() = prefs.getString("discord_webhook_url", "") ?: ""
        set(value) = prefs.edit().putString("discord_webhook_url", value).apply()

    var relayEnabled: Boolean
        get() = prefs.getBoolean("relay_enabled", true)
        set(value) = prefs.edit().putBoolean("relay_enabled", value).apply()

    var relayWsUrl: String
        get() = prefs.getString("relay_ws_url", "") ?: ""
        set(value) = prefs.edit().putString("relay_ws_url", value).apply()

    var isServiceRunning: Boolean
        get() = prefs.getBoolean("service_running", false)
        set(value) = prefs.edit().putBoolean("service_running", value).apply()

    var forwardSim1: Boolean
        get() = prefs.getBoolean("forward_sim_1", true)
        set(value) = prefs.edit().putBoolean("forward_sim_1", value).apply()

    var forwardSim2: Boolean
        get() = prefs.getBoolean("forward_sim_2", true)
        set(value) = prefs.edit().putBoolean("forward_sim_2", value).apply()

    var forwardNotifications: Boolean
        get() = prefs.getBoolean("forward_notifications", false)
        set(value) = prefs.edit().putBoolean("forward_notifications", value).apply()

    var forwardWhatsApp: Boolean
        get() = prefs.getBoolean("forward_whatsapp", true)
        set(value) = prefs.edit().putBoolean("forward_whatsapp", value).apply()

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean("onboarding_completed", false)
        set(value) = prefs.edit().putBoolean("onboarding_completed", value).apply()

    fun resetAll() {
        prefs.edit().clear().apply()
    }
}
