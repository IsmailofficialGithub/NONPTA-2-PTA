package com.pta.forwarder.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.pta.forwarder.OnboardingActivity
import com.pta.forwarder.data.AppConfig
import com.pta.forwarder.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var config: AppConfig

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        config = AppConfig(requireContext())

        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        binding.etSettingsDiscordWebhook.setText(config.discordWebhookUrl)
        binding.etSettingsTelegramToken.setText(config.telegramBotToken)
        binding.etSettingsTelegramChatId.setText(config.telegramChatId)
    }

    private fun setupListeners() {
        binding.btnSettingsSaveChannels.setOnClickListener {
            config.discordWebhookUrl = binding.etSettingsDiscordWebhook.text.toString().trim()
            config.telegramBotToken = binding.etSettingsTelegramToken.text.toString().trim()
            config.telegramChatId = binding.etSettingsTelegramChatId.text.toString().trim()
            Toast.makeText(requireContext(), "Delivery settings saved", Toast.LENGTH_SHORT).show()
        }

        binding.btnSettingsOpenAppInfo.setOnClickListener {
            com.pta.forwarder.util.OemKeepAliveHelper.showGuidedKeepAliveDialog(requireContext())
        }

        binding.btnSettingsRerunOnboarding.setOnClickListener {
            val intent = Intent(requireContext(), OnboardingActivity::class.java)
            startActivity(intent)
        }

        binding.btnSettingsResetDefaults.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Reset All Settings")
                .setMessage("Reset all configurations and restart onboarding?")
                .setPositiveButton("Reset") { _, _ ->
                    config.resetAll()
                    Toast.makeText(requireContext(), "Settings reset", Toast.LENGTH_SHORT).show()
                    val intent = Intent(requireContext(), OnboardingActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
