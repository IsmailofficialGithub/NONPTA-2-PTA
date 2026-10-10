package com.pta.forwarder.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class LoggedMessage(
    val id: String,
    val sender: String,
    val body: String,
    val otp: String?,
    val simTag: String,
    val timestamp: Long,
    val status: String
)

class MessageLogManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "pta_message_logs"
        private const val KEY_MESSAGES = "recent_messages_json"
        private const val MAX_MESSAGES = 20
        const val ACTION_NEW_MESSAGE = "com.pta.forwarder.ACTION_NEW_MESSAGE_LOGGED"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun addMessage(sender: String, body: String, otp: String?, simTag: String, timestamp: Long, status: String = "Forwarded") {
        val currentList = getMessages().toMutableList()
        val newItem = LoggedMessage(
            id = System.currentTimeMillis().toString(),
            sender = sender,
            body = body,
            otp = otp,
            simTag = simTag,
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
    fun getMessages(): List<LoggedMessage> {
        val rawJson = prefs.getString(KEY_MESSAGES, null) ?: return emptyList()
        val result = mutableListOf<LoggedMessage>()
        try {
            val jsonArray = JSONArray(rawJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                result.add(
                    LoggedMessage(
                        id = obj.optString("id"),
                        sender = obj.optString("sender"),
                        body = obj.optString("body"),
                        otp = if (obj.has("otp") && !obj.isNull("otp")) obj.getString("otp") else null,
                        simTag = obj.optString("simTag", "SIM 1"),
                        timestamp = obj.optLong("timestamp"),
                        status = obj.optString("status", "Forwarded")
                    )
                )
            }
        } catch (_: Exception) {}
        return result
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_MESSAGES).apply()
    }

    private fun saveMessages(list: List<LoggedMessage>) {
        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("sender", item.sender)
                put("body", item.body)
                if (item.otp != null) put("otp", item.otp)
                put("simTag", item.simTag)
                put("timestamp", item.timestamp)
                put("status", item.status)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_MESSAGES, jsonArray.toString()).apply()
    }
}
