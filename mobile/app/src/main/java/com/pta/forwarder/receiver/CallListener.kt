package com.pta.forwarder.receiver

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.pta.forwarder.data.AppConfig
import com.pta.forwarder.network.CallEventType
import com.pta.forwarder.network.CloudDispatcher

class CallListener(
    private val context: Context,
    private val config: AppConfig,
    private val onCallState: ((CallEventType, String, String?, Long) -> Unit)? = null
) {

    companion object {
        private const val TAG = "CallListener"
    }

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val dispatcher = CloudDispatcher(config)

    private var previousState = TelephonyManager.CALL_STATE_IDLE
    private var lastIncomingNumber: String? = null
    private var isMissed = false

    private var legacyListener: PhoneStateListener? = null
    private var modernCallback: Any? = null // TelephonyCallback

    fun startListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleStateChange(state, null)
                }
            }
            modernCallback = callback
            telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleStateChange(state, phoneNumber)
                }
            }
            legacyListener = listener
            @Suppress("DEPRECATION")
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    fun stopListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modernCallback?.let {
                telephonyManager.unregisterTelephonyCallback(it as TelephonyCallback)
            }
        } else {
            legacyListener?.let {
                @Suppress("DEPRECATION")
                telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
            }
        }
    }

    fun setIncomingNumber(number: String) {
        lastIncomingNumber = number
    }

    fun acceptCall(): Boolean {
        return try {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ANSWER_PHONE_CALLS)
                == PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    telecom?.acceptRingingCall()
                    Log.i(TAG, "Call accepted via TelecomManager")
                    true
                } else {
                    Log.w(TAG, "acceptRingingCall requires Android 8.0+")
                    false
                }
            } else {
                Log.e(TAG, "Missing ANSWER_PHONE_CALLS permission")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error accepting call: ${e.message}", e)
            false
        }
    }

    fun endCall(): Boolean {
        return try {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ANSWER_PHONE_CALLS)
                == PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    val ended = telecom?.endCall() ?: false
                    Log.i(TAG, "Call ended via TelecomManager: $ended")
                    ended
                } else {
                    Log.w(TAG, "endCall via TelecomManager requires Android 9.0+")
                    false
                }
            } else {
                Log.e(TAG, "Missing ANSWER_PHONE_CALLS permission")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ending call: ${e.message}", e)
            false
        }
    }

    private fun handleStateChange(state: Int, incomingNumber: String?) {
        val number = incomingNumber ?: lastIncomingNumber ?: "Unknown"

        val timestamp = System.currentTimeMillis()
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                isMissed = true
                lastIncomingNumber = number
                val contactName = resolveContactName(number)
                Log.d(TAG, "Incoming call ringing from: $number ($contactName)")
                dispatcher.dispatchCall(number, contactName, CallEventType.RINGING, timestamp)
                onCallState?.invoke(CallEventType.RINGING, number, contactName, timestamp)
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Call was answered
                if (previousState == TelephonyManager.CALL_STATE_RINGING) {
                    isMissed = false
                    val contactName = resolveContactName(number)
                    dispatcher.dispatchCall(number, contactName, CallEventType.ANSWERED, timestamp)
                    onCallState?.invoke(CallEventType.ANSWERED, number, contactName, timestamp)
                }
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                // If it was ringing and never answered (offhook), it is a missed call
                if (previousState == TelephonyManager.CALL_STATE_RINGING && isMissed) {
                    val contactName = resolveContactName(number)
                    Log.d(TAG, "Missed call from: $number")
                    dispatcher.dispatchCall(number, contactName, CallEventType.MISSED, timestamp)
                    onCallState?.invoke(CallEventType.MISSED, number, contactName, timestamp)
                }
                isMissed = false
            }
        }
        previousState = state
    }

    private fun resolveContactName(phoneNumber: String): String? {
        if (phoneNumber == "Unknown" || phoneNumber.isBlank()) return null
        return try {
            val uri: Uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (index >= 0) it.getString(index) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
