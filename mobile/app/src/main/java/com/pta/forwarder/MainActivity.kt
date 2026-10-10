package com.pta.forwarder

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.pta.forwarder.data.AppConfig
import com.pta.forwarder.data.DeviceRole
import com.pta.forwarder.data.MessageLogManager
import com.pta.forwarder.databinding.ActivityMainBinding
import com.pta.forwarder.network.CloudDispatcher
import com.pta.forwarder.service.ForwarderService
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var config: AppConfig
    private lateinit var logManager: MessageLogManager
    private val timeFormat = SimpleDateFormat("HH:mm, dd MMM", Locale.getDefault())

    private val messageUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            renderRecentMessages()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "Permissions granted successfully! ✓", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Some permissions were denied", Toast.LENGTH_LONG).show()
        }
        updatePermissionBadges()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        config = AppConfig(this)
        logManager = MessageLogManager(this)

        setupViews()
        loadConfig()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionBadges()
        updateServiceStatusBanner()
        renderRecentMessages()

        val filter = IntentFilter(MessageLogManager.ACTION_NEW_MESSAGE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(messageUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(messageUpdateReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        saveConfig(silent = true)
        try {
            unregisterReceiver(messageUpdateReceiver)
        } catch (_: Exception) {}
    }

    private fun setupViews() {
        // Role Selection
        binding.rgRole.setOnCheckedChangeListener { _, checkedId ->
            val role = if (checkedId == R.id.rbSender) DeviceRole.SENDER else DeviceRole.RECEIVER
            config.role = role
            updateRoleUi(role)
        }

        // Telegram Checkbox
        binding.cbTelegram.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutTelegramInputs.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Discord Checkbox
        binding.cbDiscord.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutDiscordInputs.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Relay Checkbox
        binding.cbRelay.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutRelayInputs.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Save Button
        binding.btnSaveConfig.setOnClickListener {
            if (saveConfig(silent = false)) {
                Toast.makeText(this, "Settings saved successfully! ✓", Toast.LENGTH_SHORT).show()
            }
        }

        // Test Connection Button
        binding.btnTestConnection.setOnClickListener {
            testCloudForwarding()
        }

        // Clear Log Button
        binding.btnClearMessages.setOnClickListener {
            logManager.clear()
            renderRecentMessages()
            Toast.makeText(this, "Message history cleared", Toast.LENGTH_SHORT).show()
        }

        // Permission Buttons
        binding.btnGrantPermissions.setOnClickListener {
            requestAppPermissions()
        }

        binding.btnBatteryOptimization.setOnClickListener {
            requestBatteryOptimizationExemption()
        }

        binding.btnNotificationAccess.setOnClickListener {
            openNotificationListenerSettings()
        }

        // Start / Stop Toggle Button
        binding.btnToggleService.setOnClickListener {
            toggleServiceState()
        }
    }

    private fun loadConfig() {
        // Load Role
        if (config.role == DeviceRole.SENDER) {
            binding.rbSender.isChecked = true
        } else {
            binding.rbReceiver.isChecked = true
        }
        updateRoleUi(config.role)

        // Load Credentials
        binding.cbTelegram.isChecked = config.telegramEnabled
        binding.layoutTelegramInputs.visibility = if (config.telegramEnabled) View.VISIBLE else View.GONE
        binding.etTelegramToken.setText(config.telegramBotToken)
        binding.etTelegramChatId.setText(config.telegramChatId)

        binding.cbDiscord.isChecked = config.discordEnabled
        binding.layoutDiscordInputs.visibility = if (config.discordEnabled) View.VISIBLE else View.GONE
        binding.etDiscordWebhook.setText(config.discordWebhookUrl)

        binding.cbRelay.isChecked = config.relayEnabled
        binding.layoutRelayInputs.visibility = if (config.relayEnabled) View.VISIBLE else View.GONE
        binding.etRelayWsUrl.setText(config.relayWsUrl)

        updateServiceStatusBanner()
    }

    private fun saveConfig(silent: Boolean = false): Boolean {
        config.telegramEnabled = binding.cbTelegram.isChecked
        config.telegramBotToken = binding.etTelegramToken.text.toString().trim()
        config.telegramChatId = binding.etTelegramChatId.text.toString().trim()

        config.discordEnabled = binding.cbDiscord.isChecked
        config.discordWebhookUrl = binding.etDiscordWebhook.text.toString().trim()

        config.relayEnabled = binding.cbRelay.isChecked
        config.relayWsUrl = binding.etRelayWsUrl.text.toString().trim()

        if (!silent && config.role == DeviceRole.SENDER) {
            if (!config.telegramEnabled && !config.discordEnabled && !config.relayEnabled) {
                Toast.makeText(this, "Please enable at least Telegram or Discord", Toast.LENGTH_LONG).show()
                return false
            }
            if (config.telegramEnabled && (config.telegramBotToken.isBlank() || config.telegramChatId.isBlank())) {
                Toast.makeText(this, "Please enter both Telegram Bot Token and Chat ID", Toast.LENGTH_LONG).show()
                return false
            }
            if (config.discordEnabled && config.discordWebhookUrl.isBlank()) {
                Toast.makeText(this, "Please enter Discord Webhook URL", Toast.LENGTH_LONG).show()
                return false
            }
        }
        return true
    }

    private fun testCloudForwarding() {
        saveConfig(silent = true)

        if (!config.telegramEnabled && !config.discordEnabled) {
            Toast.makeText(this, "Enable Telegram or Discord first to test.", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Sending test messages...", Toast.LENGTH_SHORT).show()
        val dispatcher = CloudDispatcher(config)

        var telegramReport: String? = null
        var discordReport: String? = null
        var responsesNeeded = 0
        if (config.telegramEnabled) responsesNeeded++
        if (config.discordEnabled) responsesNeeded++

        fun checkFinished() {
            if (responsesNeeded == 0) {
                runOnUiThread {
                    val message = buildString {
                        if (telegramReport != null) append("$telegramReport\n\n")
                        if (discordReport != null) append(discordReport)
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Connection Test Results")
                        .setMessage(message.trim())
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }

        dispatcher.sendTestMessage(
            onTelegramResult = { success, msg ->
                telegramReport = if (success) "✅ $msg" else "❌ Telegram: $msg"
                responsesNeeded--
                checkFinished()
            },
            onDiscordResult = { success, msg ->
                discordReport = if (success) "✅ $msg" else "❌ Discord: $msg"
                responsesNeeded--
                checkFinished()
            }
        )
    }

    private fun updateRoleUi(role: DeviceRole) {
        if (role == DeviceRole.SENDER) {
            binding.tvRoleDesc.setText(R.string.sender_desc)
            binding.cardSenderConfig.visibility = View.VISIBLE
            binding.cardReceiverConfig.visibility = View.GONE
            binding.cardMessageFeed.visibility = View.VISIBLE
            binding.btnToggleService.visibility = View.VISIBLE
        } else {
            binding.tvRoleDesc.setText(R.string.receiver_desc)
            binding.cardSenderConfig.visibility = View.GONE
            binding.cardReceiverConfig.visibility = View.VISIBLE
            binding.cardMessageFeed.visibility = View.GONE
            binding.btnToggleService.visibility = View.GONE
        }
    }

    private fun toggleServiceState() {
        if (config.isServiceRunning) {
            ForwarderService.stop(this)
            config.isServiceRunning = false
            updateServiceStatusBanner()
            Toast.makeText(this, "Forwarder Service Stopped", Toast.LENGTH_SHORT).show()
        } else {
            if (!saveConfig(silent = false)) return
            ForwarderService.start(this)
            config.isServiceRunning = true
            updateServiceStatusBanner()
            Toast.makeText(this, "Forwarder Service Started ✓", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateServiceStatusBanner() {
        if (config.isServiceRunning) {
            binding.tvStatusChip.text = "● ACTIVE"
            binding.tvStatusChip.setTextColor(ContextCompat.getColor(this, R.color.status_running_text))
            binding.tvStatusChip.setBackgroundColor(ContextCompat.getColor(this, R.color.status_running_bg))

            binding.btnToggleService.text = "STOP FORWARDER"
            binding.btnToggleService.setBackgroundColor(ContextCompat.getColor(this, R.color.danger))
        } else {
            binding.tvStatusChip.text = "● STOPPED"
            binding.tvStatusChip.setTextColor(ContextCompat.getColor(this, R.color.status_stopped_text))
            binding.tvStatusChip.setBackgroundColor(ContextCompat.getColor(this, R.color.status_stopped_bg))

            binding.btnToggleService.text = "START FORWARDER"
            binding.btnToggleService.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
        }
    }

    private fun renderRecentMessages() {
        val messages = logManager.getMessages()
        binding.layoutMessagesContainer.removeAllViews()

        if (messages.isEmpty()) {
            binding.tvNoMessages.visibility = View.VISIBLE
            return
        }

        binding.tvNoMessages.visibility = View.GONE
        val inflater = LayoutInflater.from(this)

        for (item in messages) {
            val itemView = inflater.inflate(R.layout.item_message_log, binding.layoutMessagesContainer, false)
            val tvSender = itemView.findViewById<TextView>(R.id.tvMsgSender)
            val tvSimBadge = itemView.findViewById<TextView>(R.id.tvMsgSimBadge)
            val tvTime = itemView.findViewById<TextView>(R.id.tvMsgTime)
            val tvBody = itemView.findViewById<TextView>(R.id.tvMsgBody)
            val tvOtp = itemView.findViewById<TextView>(R.id.tvMsgOtpBadge)
            val tvStatus = itemView.findViewById<TextView>(R.id.tvMsgStatus)

            tvSender.text = item.sender
            tvSimBadge.text = item.simTag
            tvTime.text = timeFormat.format(Date(item.timestamp))
            tvBody.text = item.body
            tvStatus.text = "✓ ${item.status}"

            if (!item.otp.isNullOrBlank()) {
                tvOtp.visibility = View.VISIBLE
                tvOtp.text = "🔑 OTP: ${item.otp}"
            } else {
                tvOtp.visibility = View.GONE
            }

            binding.layoutMessagesContainer.addView(itemView)
        }
    }

    private fun updatePermissionBadges() {
        val hasSms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        if (hasSms) {
            binding.tvSmsStatus.text = "SMS Capture: ✓ Granted"
            binding.tvSmsStatus.setTextColor(ContextCompat.getColor(this, R.color.accent))
        } else {
            binding.tvSmsStatus.text = "SMS Capture: ⚠️ Missing (Required)"
            binding.tvSmsStatus.setTextColor(ContextCompat.getColor(this, R.color.danger))
        }

        val hasPhone = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        if (hasPhone) {
            binding.tvPhoneStatus.text = "Phone Calls & SIM: ✓ Granted"
            binding.tvPhoneStatus.setTextColor(ContextCompat.getColor(this, R.color.accent))
        } else {
            binding.tvPhoneStatus.text = "Phone Calls & SIM: ⚠️ Missing"
            binding.tvPhoneStatus.setTextColor(ContextCompat.getColor(this, R.color.danger))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
                binding.tvBatteryStatus.text = "Battery Optimization: ✓ Exemption Active"
                binding.tvBatteryStatus.setTextColor(ContextCompat.getColor(this, R.color.accent))
            } else {
                binding.tvBatteryStatus.text = "Battery Optimization: ⚠️ Restricted"
                binding.tvBatteryStatus.setTextColor(ContextCompat.getColor(this, R.color.danger))
            }
        }
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissions.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Battery optimization already disabled!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openNotificationListenerSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }
}
