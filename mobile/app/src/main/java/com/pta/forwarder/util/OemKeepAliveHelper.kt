package com.pta.forwarder.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AlertDialog

data class OemGuide(
    val brandName: String,
    val summary: String,
    val steps: List<String>
)

object OemKeepAliveHelper {

    fun getDeviceGuide(): OemGuide {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                OemGuide(
                    brandName = "Xiaomi / Redmi / HyperOS",
                    summary = "MIUI & HyperOS terminate background apps when recent apps are swiped away.",
                    steps = listOf(
                        "Tap 'Open Settings' below to jump to Auto-Start settings.",
                        "Toggle PTA Forwarder to ON.",
                        "In Battery Saver, select 'No restrictions'."
                    )
                )
            }
            manufacturer.contains("infinix") || manufacturer.contains("tecno") || manufacturer.contains("itel") || manufacturer.contains("transsion") -> {
                OemGuide(
                    brandName = "Infinix / Tecno (Transsion)",
                    summary = "XOS & HiOS Phone Master force-stops apps swiped from recents unless Auto-Start is granted.",
                    steps = listOf(
                        "Tap 'Open Settings' below to jump to Phone Master / Auto-Start.",
                        "Enable 'Auto-Start' for PTA Forwarder.",
                        "Set Battery Management to 'Allow background activity'."
                    )
                )
            }
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> {
                OemGuide(
                    brandName = "Vivo / iQOO (Funtouch OS)",
                    summary = "Funtouch OS freezes network sockets of closed apps unless High Background Power is granted.",
                    steps = listOf(
                        "Tap 'Open Settings' below to jump to Permission Manager.",
                        "Enable 'Auto-Start' or 'Autolaunch'.",
                        "In Background Power Consumption, select 'High Background Power Consumption'."
                    )
                )
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> {
                OemGuide(
                    brandName = "Oppo / Realme / ColorOS",
                    summary = "ColorOS restricts background process execution when apps are closed.",
                    steps = listOf(
                        "Tap 'Open Settings' below to jump to Startup Manager.",
                        "Toggle PTA Forwarder to Allowed.",
                        "In Battery settings, turn on 'Allow background activity'."
                    )
                )
            }
            manufacturer.contains("samsung") -> {
                OemGuide(
                    brandName = "Samsung (One UI)",
                    summary = "One UI puts background forwarding apps to sleep if unused directly.",
                    steps = listOf(
                        "Tap 'Open Settings' below to access App Battery settings.",
                        "Set Battery usage from 'Optimized' to 'Unrestricted'.",
                        "Ensure PTA Forwarder is NOT in 'Sleeping apps'."
                    )
                )
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                OemGuide(
                    brandName = "Huawei / Honor (EMUI)",
                    summary = "EMUI App Launch manager terminates background tasks.",
                    steps = listOf(
                        "Tap 'Open Settings' below to access App Launch settings.",
                        "Find PTA Forwarder, turn off 'Manage Automatically'.",
                        "Enable 'Auto-launch', 'Secondary launch', and 'Run in background'."
                    )
                )
            }
            else -> {
                OemGuide(
                    brandName = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
                    summary = "Standard Android allows background execution when Battery Optimization is disabled.",
                    steps = listOf(
                        "Tap 'Open Settings' below to open Battery Optimization.",
                        "Choose 'All apps' and find PTA Forwarder.",
                        "Select 'Don't optimize' or 'Unrestricted'."
                    )
                )
            }
        }
    }

    /**
     * Attempts to directly open the native OEM Auto-Start / Battery Manager screen.
     * Falls back to Android system battery optimization request or App Info settings.
     */
    fun openOemAutoStartSettings(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val oemIntents = mutableListOf<Intent>()

        // 1. Xiaomi / MIUI / HyperOS
        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")) {
            oemIntents.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
            oemIntents.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")))
            oemIntents.add(Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT))
        }

        // 2. Transsion (Infinix / Tecno / Itel)
        if (manufacturer.contains("infinix") || manufacturer.contains("tecno") || manufacturer.contains("itel") || manufacturer.contains("transsion")) {
            oemIntents.add(Intent().setComponent(ComponentName("com.transsion.phonemanager", "com.transsion.phonemanager.settings.AutoStartManageActivity")))
            oemIntents.add(Intent().setComponent(ComponentName("com.transsion.phonemanager", "com.transsion.phonemanager.view.AutoStartListActivity")))
            oemIntents.add(Intent().setComponent(ComponentName("com.transsion.phonemaster", "com.transsion.phonemaster.AutoStartActivity")))
            oemIntents.add(Intent().setComponent(ComponentName("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity")))
        }

        // 3. Vivo / iQOO
        if (manufacturer.contains("vivo") || manufacturer.contains("iqoo")) {
            oemIntents.add(Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")))
            oemIntents.add(Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity")))
            oemIntents.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")))
            oemIntents.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")))
        }

        // 4. Oppo / Realme
        if (manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus")) {
            oemIntents.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")))
            oemIntents.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")))
            oemIntents.add(Intent().setComponent(ComponentName("com.oplus.battery", "com.oplus.battery.AppListActivity")))
            oemIntents.add(Intent().setComponent(ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity")))
        }

        // 5. Huawei / Honor
        if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
            oemIntents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")))
            oemIntents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")))
        }

        // 6. Samsung
        if (manufacturer.contains("samsung")) {
            oemIntents.add(Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")))
            oemIntents.add(Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")))
        }

        // Try proprietary OEM intents first
        val pm = context.packageManager
        for (intent in oemIntents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()) {
                try {
                    context.startActivity(intent)
                    return true
                } catch (_: Exception) {}
            }
        }

        // Fallback 1: Standard Android prompt for battery optimization exemption
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager?.isIgnoringBatteryOptimizations(context.packageName) == false) {
                try {
                    val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(requestIntent)
                    return true
                } catch (_: Exception) {}
            }
        }

        // Fallback 2: Direct App Details screen
        try {
            val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(appDetailsIntent)
            return true
        } catch (_: Exception) {}

        return false
    }

    /**
     * Shows a clean, step-by-step guidance dialog tailored to the user's phone brand,
     * with a direct action button to redirect to the settings screen.
     */
    fun showGuidedKeepAliveDialog(context: Context) {
        val guide = getDeviceGuide()
        val message = buildString {
            append(guide.summary)
            append("\n\nFollow these quick steps:\n")
            guide.steps.forEachIndexed { index, step ->
                append("${index + 1}. $step\n")
            }
        }

        AlertDialog.Builder(context)
            .setTitle(guide.brandName + " Setup")
            .setMessage(message.trim())
            .setPositiveButton("Open Settings") { _, _ ->
                openOemAutoStartSettings(context)
            }
            .setNegativeButton("I'll do it later", null)
            .show()
    }
}
