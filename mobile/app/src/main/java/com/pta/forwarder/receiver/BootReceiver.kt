package com.pta.forwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pta.forwarder.data.AppConfig
import com.pta.forwarder.data.DeviceRole
import com.pta.forwarder.service.ForwarderService

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val config = AppConfig(context)
            Log.d(TAG, "Device rebooted. Role: ${config.role}, Active: ${config.isServiceRunning}")

            if (config.role == DeviceRole.SENDER && config.isServiceRunning) {
                ForwarderService.start(context)
                Log.d(TAG, "Auto-restarted ForwarderService after reboot")
            }
        }
    }
}
