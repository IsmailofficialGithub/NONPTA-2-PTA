package com.pta.forwarder.ui.dashboard

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.pta.forwarder.MainActivity
import com.pta.forwarder.R
import com.pta.forwarder.data.AppConfig
import com.pta.forwarder.data.MessageLogManager
import com.pta.forwarder.databinding.FragmentDashboardBinding
import com.pta.forwarder.network.CloudDispatcher
import com.pta.forwarder.service.ForwarderService
import com.pta.forwarder.util.SimUtil
import java.text.SimpleDateFormat
import java.util.Locale

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var config: AppConfig
    private lateinit var logManager: MessageLogManager
    private val timeFormat = SimpleDateFormat("HH:mm, dd MMM", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        config = AppConfig(ctx)
        logManager = MessageLogManager(ctx)

        setupListeners()
        loadConfig()
        renderRecentFeed()
        updateHealthBadges()
        updatePowerButton()
    }

    override fun onResume() {
        super.onResume()
        updateHealthBadges()
        updatePowerButton()
        renderRecentFeed()
    }

    private fun setupListeners() {
        // Power button toggle
        binding.btnPowerToggle.setOnClickListener {
            toggleForwarderService()
        }

        // SIM toggles
        binding.cbSim1.setOnCheckedChangeListener { _, isChecked ->
            config.forwardSim1 = isChecked
        }
        binding.cbSim2.setOnCheckedChangeListener { _, isChecked ->
            config.forwardSim2 = isChecked
        }

        // Notifications & WhatsApp
        binding.cbNotifications.setOnCheckedChangeListener { _, isChecked ->
            config.forwardNotifications = isChecked
            binding.layoutWhatsAppOption.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        binding.cbWhatsAppOnly.setOnCheckedChangeListener { _, isChecked ->
            config.forwardWhatsApp = isChecked
        }

        // Discord Webhook
        binding.cbDiscord.setOnCheckedChangeListener { _, isChecked ->
            config.discordEnabled = isChecked
        }
        binding.btnPasteDiscord.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                binding.etDiscordWebhook.setText(text.trim())
            }
        }

        // Telegram
        binding.cbTelegram.setOnCheckedChangeListener { _, isChecked ->
            config.telegramEnabled = isChecked
            binding.layoutTelegramInputs.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Save Button
        binding.btnSaveSettings.setOnClickListener {
            saveConfig()
            Toast.makeText(requireContext(), "Configuration saved", Toast.LENGTH_SHORT).show()
        }

        // Test Forward
        binding.btnTestForward.setOnClickListener {
            testForwardEndpoint()
        }

        // Auto-start settings shortcut
        binding.btnOpenAutoStartSettings.setOnClickListener {
            openSystemAppSettings()
        }

        // View full logs tab shortcut
        binding.btnViewAllLogs.setOnClickListener {
            (activity as? MainActivity)?.navigateToTab(R.id.navigation_logs)
        }
    }

    private fun loadConfig() {
        // Detect carrier names
        val sim1Carrier = SimUtil.getCarrierName(requireContext(), 0) ?: "SIM 1"
        val sim2Carrier = SimUtil.getCarrierName(requireContext(), 1) ?: "SIM 2"
        binding.tvSim1Label.text = "SIM 1 ($sim1Carrier)"
        binding.tvSim2Label.text = "SIM 2 ($sim2Carrier)"

        binding.cbSim1.isChecked = config.forwardSim1
        binding.cbSim2.isChecked = config.forwardSim2
        binding.cbNotifications.isChecked = config.forwardNotifications
        binding.cbWhatsAppOnly.isChecked = config.forwardWhatsApp
        binding.layoutWhatsAppOption.visibility = if (config.forwardNotifications) View.VISIBLE else View.GONE

        binding.cbDiscord.isChecked = config.discordEnabled
        binding.etDiscordWebhook.setText(config.discordWebhookUrl)

        binding.cbTelegram.isChecked = config.telegramEnabled
        binding.layoutTelegramInputs.visibility = if (config.telegramEnabled) View.VISIBLE else View.GONE
        binding.etTelegramToken.setText(config.telegramBotToken)
        binding.etTelegramChatId.setText(config.telegramChatId)
    }

    private fun saveConfig() {
        config.forwardSim1 = binding.cbSim1.isChecked
        config.forwardSim2 = binding.cbSim2.isChecked
        config.forwardNotifications = binding.cbNotifications.isChecked
        config.forwardWhatsApp = binding.cbWhatsAppOnly.isChecked
        config.discordEnabled = binding.cbDiscord.isChecked
        config.discordWebhookUrl = binding.etDiscordWebhook.text.toString().trim()
        config.telegramEnabled = binding.cbTelegram.isChecked
        config.telegramBotToken = binding.etTelegramToken.text.toString().trim()
        config.telegramChatId = binding.etTelegramChatId.text.toString().trim()
    }

    private fun toggleForwarderService() {
        val ctx = requireContext()
        saveConfig()
        if (config.isServiceRunning) {
            ForwarderService.stop(ctx)
            config.isServiceRunning = false
            Toast.makeText(ctx, "Forwarder service stopped", Toast.LENGTH_SHORT).show()
        } else {
            ForwarderService.start(ctx)
            config.isServiceRunning = true
            Toast.makeText(ctx, "Forwarder service started", Toast.LENGTH_SHORT).show()
        }
        updatePowerButton()
        (activity as? MainActivity)?.updateGlobalHeaderStatus()
    }

    fun updatePowerButton() {
        if (_binding == null) return
        val isRunning = config.isServiceRunning
        if (isRunning) {
            binding.btnPowerToggle.setBackgroundResource(R.drawable.bg_power_button_running)
            binding.tvPowerText.text = "STOP FORWARDER"
        } else {
            binding.btnPowerToggle.setBackgroundResource(R.drawable.bg_power_button_idle)
            binding.tvPowerText.text = "START FORWARDER"
        }

        val totalMsgs = logManager.getRecentMessages().size
        binding.tvForwardedCount.text = "Forwarded: $totalMsgs"
    }

    private fun updateHealthBadges() {
        if (_binding == null) return
        val ctx = requireContext()

        val smsGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        if (smsGranted) {
            binding.tvHealthSms.text = "Granted"
            binding.tvHealthSms.setTextColor(ContextCompat.getColor(ctx, R.color.status_active_text))
        } else {
            binding.tvHealthSms.text = "Missing"
            binding.tvHealthSms.setTextColor(ContextCompat.getColor(ctx, R.color.status_stopped_text))
        }

        val batteryIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(ctx.packageName)
        } else true

        if (batteryIgnored) {
            binding.tvHealthBattery.text = "Unrestricted"
            binding.tvHealthBattery.setTextColor(ContextCompat.getColor(ctx, R.color.status_active_text))
        } else {
            binding.tvHealthBattery.text = "Restricted"
            binding.tvHealthBattery.setTextColor(ContextCompat.getColor(ctx, R.color.accent_warning))
        }
    }

    fun renderRecentFeed() {
        if (_binding == null) return
        val messages = logManager.getRecentMessages().take(3)
        binding.layoutRecentFeed.removeAllViews()

        if (messages.isEmpty()) {
            binding.layoutRecentFeed.addView(binding.tvEmptyRecentFeed)
            binding.tvEmptyRecentFeed.visibility = View.VISIBLE
            return
        }

        val inflater = LayoutInflater.from(requireContext())
        for (item in messages) {
            val itemView = inflater.inflate(R.layout.item_message_log, binding.layoutRecentFeed, false)
            val tvSender = itemView.findViewById<TextView>(R.id.tvMsgSender)
            val tvSimBadge = itemView.findViewById<TextView>(R.id.tvMsgSimBadge)
            val tvTime = itemView.findViewById<TextView>(R.id.tvMsgTime)
            val tvBody = itemView.findViewById<TextView>(R.id.tvMsgBody)
            val tvOtpBadge = itemView.findViewById<TextView>(R.id.tvMsgOtpBadge)
            val tvStatus = itemView.findViewById<TextView>(R.id.tvMsgStatus)

            tvSender.text = item.sender
            tvSimBadge.text = item.simSlot
            tvTime.text = timeFormat.format(item.timestamp)
            tvBody.text = item.message
            tvStatus.text = item.status

            if (!item.otpCode.isNullOrEmpty()) {
                tvOtpBadge.visibility = View.VISIBLE
                tvOtpBadge.text = "OTP: ${item.otpCode} · Copy"
                tvOtpBadge.setOnClickListener {
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = android.content.ClipData.newPlainText("OTP Code", item.otpCode)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(requireContext(), "OTP ${item.otpCode} copied", Toast.LENGTH_SHORT).show()
                }
            } else {
                tvOtpBadge.visibility = View.GONE
            }

            binding.layoutRecentFeed.addView(itemView)
        }
    }

    private fun testForwardEndpoint() {
        saveConfig()
        val webhookUrl = config.discordWebhookUrl
        if (webhookUrl.isEmpty()) {
            Toast.makeText(requireContext(), "Enter a Discord Webhook URL first", Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnTestForward.text = "Testing..."
        binding.btnTestForward.isEnabled = false

        CloudDispatcher.testDiscordWebhook(requireContext(), webhookUrl) { success: Boolean, msg: String ->
            binding.btnTestForward.text = "Test Forward"
            binding.btnTestForward.isEnabled = true
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun openSystemAppSettings() {
        com.pta.forwarder.util.OemKeepAliveHelper.showGuidedKeepAliveDialog(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
