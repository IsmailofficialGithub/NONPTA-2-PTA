package com.pta.forwarder.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

data class SimInfo(
    val slotIndex: Int,          // 0 = SIM 1, 1 = SIM 2
    val displayName: String,     // e.g. "SIM 1"
    val carrierName: String      // e.g. "Jazz", "Zong", "Telenor", "Ufone"
) {
    val fullTag: String
        get() = if (carrierName.isNotBlank() && carrierName != "Cellular") {
            "$displayName ($carrierName)"
        } else {
            displayName
        }
}

object SimUtil {

    fun extractSimInfo(context: Context, intent: Intent): SimInfo {
        var subId = intent.getIntExtra("subscription", -1)
        if (subId == -1) subId = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1)

        var slotIndex = intent.getIntExtra("slot", -1)
        if (slotIndex == -1) slotIndex = intent.getIntExtra("simSlot", -1)
        if (slotIndex == -1) slotIndex = intent.getIntExtra("phone", -1)

        var detectedSlot = if (slotIndex >= 0) slotIndex else 0
        var detectedCarrier = ""

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                if (subManager != null) {
                    val activeSubs = subManager.activeSubscriptionInfoList
                    if (!activeSubs.isNullOrEmpty()) {
                        val matchedInfo = when {
                            subId != -1 -> activeSubs.find { it.subscriptionId == subId }
                            slotIndex != -1 -> activeSubs.find { it.simSlotIndex == slotIndex }
                            else -> activeSubs.firstOrNull()
                        }

                        if (matchedInfo != null) {
                            detectedSlot = matchedInfo.simSlotIndex
                            detectedCarrier = matchedInfo.carrierName?.toString()?.trim()
                                ?: matchedInfo.displayName?.toString()?.trim()
                                ?: ""
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (detectedCarrier.isBlank()) {
            try {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                detectedCarrier = telephonyManager?.networkOperatorName?.trim()
                    ?: telephonyManager?.simOperatorName?.trim()
                    ?: ""
            } catch (_: Exception) {}
        }

        if (detectedCarrier.isBlank()) {
            detectedCarrier = "Cellular"
        }

        val slotLabel = "SIM ${detectedSlot + 1}"
        return SimInfo(
            slotIndex = detectedSlot,
            displayName = slotLabel,
            carrierName = detectedCarrier
        )
    }

    fun getCarrierName(context: Context, slotIndex: Int): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val activeSubs = subManager?.activeSubscriptionInfoList
                val match = activeSubs?.find { it.simSlotIndex == slotIndex }
                if (match != null) {
                    val name = match.carrierName?.toString()?.trim() ?: match.displayName?.toString()?.trim()
                    if (!name.isNullOrBlank()) return name
                }
            } catch (_: Exception) {}
        }
        return null
    }
}
