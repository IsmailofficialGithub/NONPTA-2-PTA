package com.pta.forwarder

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
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.pta.forwarder.data.AppConfig
import com.pta.forwarder.data.DeviceRole
import com.pta.forwarder.databinding.ActivityOnboardingBinding
import com.pta.forwarder.network.CloudDispatcher

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var config: AppConfig
    private var currentStep = 0
    private val totalSteps = 5

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        refreshPermissionStates()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        config = AppConfig(this)

        setupStepInteractions()
        updateStepUi()
    }

    override fun onResume() {
        super.onResume()
        if (currentStep == 2) {
            refreshPermissionStates()
        }
    }

    private fun setupStepInteractions() {
        // Step 2: Terms check listener
        binding.cbAgreeTerms.setOnCheckedChangeListener { _, isChecked ->
            if (currentStep == 1) {
                binding.btnOnboardingNext.isEnabled = isChecked
                binding.btnOnboardingNext.alpha = if (isChecked) 1.0f else 0.5f
            }
        }
        binding.tvAgreeTermsLabel.setOnClickListener {
            binding.cbAgreeTerms.isChecked = !binding.cbAgreeTerms.isChecked
        }

        // Step 3: Permissions buttons
        binding.btnGrantSms.setOnClickListener {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
                )
            )
        }

        binding.btnGrantPhone.setOnClickListener {
            val perms = mutableListOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                perms.add(Manifest.permission.READ_PHONE_NUMBERS)
            }
            permissionLauncher.launch(perms.toTypedArray())
        }

        binding.btnGrantNotification.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Notification access settings unavailable", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnGrantBattery.setOnClickListener {
            com.pta.forwarder.util.OemKeepAliveHelper.showGuidedKeepAliveDialog(this)
        }

        // Step 4: Role Selection Cards
        binding.cardRoleSender.setOnClickListener {
            binding.rbOnboardingSender.isChecked = true
            binding.rbOnboardingReceiver.isChecked = false
        }
        binding.cardRoleReceiver.setOnClickListener {
            binding.rbOnboardingReceiver.isChecked = true
            binding.rbOnboardingSender.isChecked = false
        }
        binding.rbOnboardingSender.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) binding.rbOnboardingReceiver.isChecked = false
        }
        binding.rbOnboardingReceiver.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) binding.rbOnboardingSender.isChecked = false
        }

        // Step 5: Webhook paste & test
        binding.btnOnboardingPasteWebhook.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                binding.etOnboardingWebhookUrl.setText(text.trim())
            }
        }

        binding.btnOnboardingTestWebhook.setOnClickListener {
            val url = binding.etOnboardingWebhookUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Please enter a Discord Webhook URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.btnOnboardingTestWebhook.text = "Testing connection..."
            binding.btnOnboardingTestWebhook.isEnabled = false

            CloudDispatcher.testDiscordWebhook(this, url) { success: Boolean, message: String ->
                binding.btnOnboardingTestWebhook.text = "Test Endpoint Connection"
                binding.btnOnboardingTestWebhook.isEnabled = true
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }

        // Navigation controls
        binding.btnOnboardingBack.setOnClickListener {
            if (currentStep > 0) {
                currentStep--
                binding.stepFlipper.displayedChild = currentStep
                updateStepUi()
            }
        }

        binding.btnOnboardingNext.setOnClickListener {
            if (currentStep == 1 && !binding.cbAgreeTerms.isChecked) {
                Toast.makeText(this, "Please accept the operational terms to continue", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (currentStep < totalSteps - 1) {
                currentStep++
                binding.stepFlipper.displayedChild = currentStep
                updateStepUi()
            } else {
                finishOnboarding()
            }
        }
    }

    private fun updateStepUi() {
        binding.tvStepIndicator.text = "Step ${currentStep + 1} of $totalSteps"
        binding.onboardingProgressBar.progress = currentStep + 1

        binding.btnOnboardingBack.visibility = if (currentStep > 0) View.VISIBLE else View.GONE

        if (currentStep == totalSteps - 1) {
            binding.btnOnboardingNext.text = "Complete & Launch"
        } else {
            binding.btnOnboardingNext.text = "Continue"
        }

        if (currentStep == 1) {
            val checked = binding.cbAgreeTerms.isChecked
            binding.btnOnboardingNext.isEnabled = checked
            binding.btnOnboardingNext.alpha = if (checked) 1.0f else 0.5f
        } else {
            binding.btnOnboardingNext.isEnabled = true
            binding.btnOnboardingNext.alpha = 1.0f
        }

        if (currentStep == 2) {
            refreshPermissionStates()
        }
    }

    private fun refreshPermissionStates() {
        val smsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        binding.btnGrantSms.text = if (smsGranted) "Granted" else "Grant"
        binding.btnGrantSms.isEnabled = !smsGranted
        binding.btnGrantSms.alpha = if (smsGranted) 0.6f else 1.0f

        val phoneGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        binding.btnGrantPhone.text = if (phoneGranted) "Granted" else "Grant"
        binding.btnGrantPhone.isEnabled = !phoneGranted
        binding.btnGrantPhone.alpha = if (phoneGranted) 0.6f else 1.0f

        val notifEnabled = isNotificationServiceEnabled()
        binding.btnGrantNotification.text = if (notifEnabled) "Granted" else "Grant"
        binding.btnGrantNotification.isEnabled = !notifEnabled
        binding.btnGrantNotification.alpha = if (notifEnabled) 0.6f else 1.0f

        val batteryIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(packageName)
        } else true
        binding.btnGrantBattery.text = if (batteryIgnored) "Granted" else "Grant"
        binding.btnGrantBattery.isEnabled = !batteryIgnored
        binding.btnGrantBattery.alpha = if (batteryIgnored) 0.6f else 1.0f
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkg = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(pkg) == true
    }

    private fun finishOnboarding() {
        // Save selected role
        val role = if (binding.rbOnboardingSender.isChecked) DeviceRole.SENDER else DeviceRole.RECEIVER
        config.role = role

        // Save webhook URL if provided
        val webhookUrl = binding.etOnboardingWebhookUrl.text.toString().trim()
        if (webhookUrl.isNotEmpty()) {
            config.discordWebhookUrl = webhookUrl
            config.discordEnabled = true
        }

        config.isOnboardingCompleted = true

        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
