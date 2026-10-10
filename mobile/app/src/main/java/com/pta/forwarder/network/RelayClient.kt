package com.pta.forwarder.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.pta.forwarder.data.AppConfig
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class RelayClient(
    private val config: AppConfig,
    private val onAnswer: () -> Unit,
    private val onReject: () -> Unit
) {

    companion object {
        private const val TAG = "RelayClient"
        private const val RECONNECT_DELAY_MS = 5000L
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isIntentionalClose = false

    fun connect() {
        if (!config.relayEnabled || config.relayWsUrl.isBlank()) {
            Log.d(TAG, "Relay client disabled or URL empty")
            return
        }

        isIntentionalClose = false
        val url = config.relayWsUrl.trim()
        Log.i(TAG, "Connecting to relay at $url")

        val request = try {
            Request.Builder().url(url).build()
        } catch (e: Exception) {
            Log.e(TAG, "Invalid relay URL: $url", e)
            return
        }

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "Connected to Relay WebSocket")
                // Identify as Phone A (Sender)
                val initMsg = JSONObject().apply {
                    put("type", "REGISTER")
                    put("role", "SENDER")
                }
                ws.send(initMsg.toString())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                Log.d(TAG, "Received message from Relay: $text")
                try {
                    val json = JSONObject(text)
                    val action = json.optString("action")
                    handler.post {
                        when (action.uppercase()) {
                            "ANSWER" -> {
                                Log.i(TAG, "Executing remote ANSWER command")
                                onAnswer()
                            }
                            "REJECT" -> {
                                Log.i(TAG, "Executing remote REJECT command")
                                onReject()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse incoming relay message: ${e.message}")
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "Relay closing: $reason ($code)")
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "Relay closed: $reason ($code)")
                scheduleReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Relay connection failed: ${t.message}")
                scheduleReconnect()
            }
        })
    }

    fun sendEvent(eventType: String, data: JSONObject) {
        val payload = JSONObject().apply {
            put("type", eventType)
            put("payload", data)
        }
        val sent = webSocket?.send(payload.toString()) ?: false
        if (!sent) {
            Log.w(TAG, "Failed to send event to relay (socket disconnected): $eventType")
        }
    }

    fun disconnect() {
        isIntentionalClose = true
        handler.removeCallbacksAndMessages(null)
        webSocket?.close(1000, "App closing")
        webSocket = null
        Log.i(TAG, "Relay disconnected")
    }

    private fun scheduleReconnect() {
        if (isIntentionalClose || !config.relayEnabled || config.relayWsUrl.isBlank()) return
        Log.d(TAG, "Scheduling reconnect in ${RECONNECT_DELAY_MS / 1000}s...")
        handler.postDelayed({
            if (!isIntentionalClose) {
                connect()
            }
        }, RECONNECT_DELAY_MS)
    }
}
