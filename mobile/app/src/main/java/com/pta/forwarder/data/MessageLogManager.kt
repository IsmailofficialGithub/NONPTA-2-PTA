package com.pta.forwarder.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class MessageLogItem(
    val id: String,
    val sender: String,
    val message: String,
    val otpCode: String?,
    val simSlot: String,
    val timestamp: Long,
    val status: String
) {
    val body: String get() = message
    val otp: String? get() = otpCode
    val simTag: String get() = simSlot
}

typealias LoggedMessage = MessageLogItem

class MessageLogManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "pta_message_logs"
        private const val KEY_MESSAGES = "recent_messages_json"
        private const val MAX_MESSAGES = 50
        const val ACTION_NEW_MESSAGE = "com.pta.forwarder.ACTION_NEW_MESSAGE_LOGGED"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun addMessage(sender: String, body: String, otp: String?, simTag: String, timestamp: Long, status: String = "Forwarded") {
        val currentList = getMessages().toMutableList()
        val newItem = MessageLogItem(
            id = System.currentTimeMillis().toString(),
            sender = sender,
            message = body,
            otpCode = otp,
            simSlot = simTag,
            timestamp = timestamp,
            status = status
        )
        currentList.add(0, newItem)
        if (currentList.size > MAX_MESSAGES) {
            currentList.removeAt(currentList.lastIndex)
        }

        saveMessages(currentList)
    }

    @Synchronized
    fun getMessages(): List<MessageLogItem> {
        val rawJson = prefs.getString(KEY_MESSAGES, null) ?: return emptyList()
        val result = mutableListOf<MessageLogItem>()
        try {
            val jsonArray = JSONArray(rawJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                result.add(
                    MessageLogItem(
                        id = obj.optString("id"),
                        sender = obj.optString("sender"),
                        message = obj.optString("body", obj.optString("message")),
                        otpCode = if (obj.has("otp") && !obj.isNull("otp")) obj.getString("otp") else if (obj.has("otpCode") && !obj.isNull("otpCode")) obj.getString("otpCode") else null,
                        simSlot = obj.optString("simTag", obj.optString("simSlot", "SIM 1")),
                        timestamp = obj.optLong("timestamp"),
                        status = obj.optString("status", "Forwarded")
                    )
                )
            }
        } catch (_: Exception) {}
        return result
    }

    fun getRecentMessages(): List<MessageLogItem> = getMessages()

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_MESSAGES).apply()
    }

    fun clearLogs() = clear()

    private fun saveMessages(list: List<MessageLogItem>) {
        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("sender", item.sender)
                put("body", item.message)
                if (item.otpCode != null) put("otp", item.otpCode)
                put("simTag", item.simSlot)
                put("timestamp", item.timestamp)
                put("status", item.status)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_MESSAGES, jsonArray.toString()).apply()
    }
}
