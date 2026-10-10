package com.pta.forwarder.ui.receiver

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.pta.forwarder.R
import com.pta.forwarder.databinding.FragmentReceiverBinding

class ReceiverFragment : Fragment() {

    private var _binding: FragmentReceiverBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReceiverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        updateListenerStatus()
    }

    override fun onResume() {
        super.onResume()
        updateListenerStatus()
    }

    private fun setupListeners() {
        binding.btnGrantReceiverAccess.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Notification settings unavailable", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnPasteTestClipboard.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                binding.etClipboardTest.setText(text)
                if (text.isNotEmpty()) {
                    binding.tvClipboardTestFeedback.text = "Clipboard verified: \"$text\""
                    binding.tvClipboardTestFeedback.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.status_active_text)
                    )
                }
            } else {
                Toast.makeText(requireContext(), "Clipboard is currently empty", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateListenerStatus() {
        if (_binding == null) return
        val enabled = isNotificationListenerEnabled()
        if (enabled) {
            binding.tvReceiverListenerStatus.text = "LISTENER: ACTIVE"
            binding.tvReceiverListenerStatus.setBackgroundResource(R.drawable.bg_pill_active)
            binding.tvReceiverListenerStatus.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.status_active_text)
            )
            binding.btnGrantReceiverAccess.text = "Notification Access Granted"
            binding.btnGrantReceiverAccess.alpha = 0.6f
        } else {
            binding.tvReceiverListenerStatus.text = "LISTENER: INACTIVE"
            binding.tvReceiverListenerStatus.setBackgroundResource(R.drawable.bg_pill_stopped)
            binding.tvReceiverListenerStatus.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.status_stopped_text)
            )
            binding.btnGrantReceiverAccess.text = "Grant Notification Listener Access"
            binding.btnGrantReceiverAccess.alpha = 1.0f
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val pkg = requireContext().packageName
        val flat = Settings.Secure.getString(requireContext().contentResolver, "enabled_notification_listeners")
        return flat?.contains(pkg) == true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
