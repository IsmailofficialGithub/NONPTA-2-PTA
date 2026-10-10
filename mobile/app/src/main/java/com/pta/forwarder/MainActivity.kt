package com.pta.forwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.pta.forwarder.data.AppConfig
import com.pta.forwarder.data.DeviceRole
import com.pta.forwarder.data.MessageLogManager
import com.pta.forwarder.databinding.ActivityMainBinding
import com.pta.forwarder.ui.dashboard.DashboardFragment
import com.pta.forwarder.ui.logs.LogsFragment
import com.pta.forwarder.ui.receiver.ReceiverFragment
import com.pta.forwarder.ui.settings.SettingsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var config: AppConfig

    private val dashboardFragment by lazy { DashboardFragment() }
    private val receiverFragment by lazy { ReceiverFragment() }
    private val logsFragment by lazy { LogsFragment() }
    private val settingsFragment by lazy { SettingsFragment() }
    private var activeFragment: Fragment? = null

    private val messageUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            dashboardFragment.renderRecentFeed()
            dashboardFragment.updatePowerButton()
            logsFragment.loadLogs()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = AppConfig(this)

        // First-Time Launch Gate
        if (!config.isOnboardingCompleted) {
            val onboardingIntent = Intent(this, OnboardingActivity::class.java)
            startActivity(onboardingIntent)
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        updateGlobalHeaderStatus()
    }

    override fun onResume() {
        super.onResume()
        updateGlobalHeaderStatus()

        val filter = IntentFilter(MessageLogManager.ACTION_NEW_MESSAGE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(messageUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(messageUpdateReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(messageUpdateReceiver)
        } catch (_: Exception) {}
    }

    private fun setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_dashboard -> {
                    switchFragment(dashboardFragment)
                    true
                }
                R.id.navigation_receiver -> {
                    switchFragment(receiverFragment)
                    true
                }
                R.id.navigation_logs -> {
                    switchFragment(logsFragment)
                    true
                }
                R.id.navigation_settings -> {
                    switchFragment(settingsFragment)
                    true
                }
                else -> false
            }
        }

        // Set initial fragment based on role
        if (config.role == DeviceRole.RECEIVER) {
            binding.bottomNavigation.selectedItemId = R.id.navigation_receiver
            switchFragment(receiverFragment)
        } else {
            binding.bottomNavigation.selectedItemId = R.id.navigation_dashboard
            switchFragment(dashboardFragment)
        }
    }

    private fun switchFragment(target: Fragment) {
        if (activeFragment == target) return

        val transaction = supportFragmentManager.beginTransaction()
        if (activeFragment != null) {
            transaction.hide(activeFragment!!)
        }

        if (target.isAdded) {
            transaction.show(target)
        } else {
            transaction.add(R.id.fragmentContainer, target)
        }

        transaction.commit()
        activeFragment = target
        updateGlobalHeaderStatus()
    }

    fun navigateToTab(tabId: Int) {
        binding.bottomNavigation.selectedItemId = tabId
    }

    fun updateGlobalHeaderStatus() {
        val roleStr = if (config.role == DeviceRole.RECEIVER) "Receiver Mode" else "Sender Mode · Dual-SIM"
        binding.tvHeaderSubtitle.text = roleStr

        val isRunning = config.isServiceRunning
        if (isRunning) {
            binding.tvGlobalStatusPill.text = "ACTIVE"
            binding.tvGlobalStatusPill.setBackgroundResource(R.drawable.bg_pill_active)
            binding.tvGlobalStatusPill.setTextColor(ContextCompat.getColor(this, R.color.status_active_text))
        } else {
            binding.tvGlobalStatusPill.text = "STOPPED"
            binding.tvGlobalStatusPill.setBackgroundResource(R.drawable.bg_pill_stopped)
            binding.tvGlobalStatusPill.setTextColor(ContextCompat.getColor(this, R.color.status_stopped_text))
        }
    }
}
