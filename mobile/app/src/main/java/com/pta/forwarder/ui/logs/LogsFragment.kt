package com.pta.forwarder.ui.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.pta.forwarder.R
import com.pta.forwarder.data.MessageLogItem
import com.pta.forwarder.data.MessageLogManager
import com.pta.forwarder.databinding.FragmentLogsBinding

class LogsFragment : Fragment() {

    private var _binding: FragmentLogsBinding? = null
    private val binding get() = _binding!!

    private lateinit var logManager: MessageLogManager
    private lateinit var adapter: MessageLogAdapter
    private var allMessages: List<MessageLogItem> = emptyList()
    private var currentFilter: String = "ALL"
    private var searchQuery: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        logManager = MessageLogManager(requireContext())

        setupRecyclerView()
        setupSearchAndFilters()
        setupActions()
        loadLogs()
    }

    override fun onResume() {
        super.onResume()
        loadLogs()
    }

    private fun setupRecyclerView() {
        adapter = MessageLogAdapter(emptyList()) { item ->
            showMessageDetailDialog(item)
        }
        binding.rvMessageLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMessageLogs.adapter = adapter
    }

    private fun setupSearchAndFilters() {
        // Search text watcher
        binding.etLogSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                binding.btnClearSearch.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etLogSearch.text?.clear()
        }

        // Filter chips
        val chips = listOf(
            binding.chipFilterAll to "ALL",
            binding.chipFilterOtp to "OTP",
            binding.chipFilterSim1 to "SIM 1",
            binding.chipFilterSim2 to "SIM 2",
            binding.chipFilterWhatsApp to "WHATSAPP"
        )

        for ((chip, filterKey) in chips) {
            chip.setOnClickListener {
                currentFilter = filterKey
                updateChipStyles(chips)
                applyFilters()
            }
        }
    }

    private fun updateChipStyles(chips: List<Pair<android.widget.TextView, String>>) {
        val activeBg = R.drawable.bg_pill_active
        val inactiveBg = R.drawable.bg_badge_sim
        val activeColor = ContextCompat.getColor(requireContext(), R.color.status_active_text)
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        for ((chip, filterKey) in chips) {
            if (filterKey == currentFilter) {
                chip.setBackgroundResource(activeBg)
                chip.setTextColor(activeColor)
            } else {
                chip.setBackgroundResource(inactiveBg)
                chip.setTextColor(inactiveColor)
            }
        }
    }

    private fun setupActions() {
        binding.btnClearAllLogs.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear Log History")
                .setMessage("Are you sure you want to delete all locally stored message records?")
                .setPositiveButton("Clear") { _, _ ->
                    logManager.clearLogs()
                    loadLogs()
                    Toast.makeText(requireContext(), "History cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    fun loadLogs() {
        if (_binding == null) return
        allMessages = logManager.getRecentMessages()
        applyFilters()
    }

    private fun applyFilters() {
        if (_binding == null) return
        var filtered = allMessages

        // Apply tab filter
        when (currentFilter) {
            "OTP" -> filtered = filtered.filter { !it.otpCode.isNullOrEmpty() }
            "SIM 1" -> filtered = filtered.filter { it.simSlot.contains("SIM 1", ignoreCase = true) }
            "SIM 2" -> filtered = filtered.filter { it.simSlot.contains("SIM 2", ignoreCase = true) }
            "WHATSAPP" -> filtered = filtered.filter { it.simSlot.contains("WhatsApp", ignoreCase = true) }
        }

        // Apply text search
        if (searchQuery.isNotEmpty()) {
            val q = searchQuery.lowercase()
            filtered = filtered.filter {
                it.sender.lowercase().contains(q) ||
                it.message.lowercase().contains(q) ||
                (it.otpCode?.lowercase()?.contains(q) == true) ||
                it.simSlot.lowercase().contains(q)
            }
        }

        adapter.updateData(filtered)
        binding.tvLogsCount.text = "${filtered.size} messages recorded"

        if (filtered.isEmpty()) {
            binding.rvMessageLogs.visibility = View.GONE
            binding.layoutEmptyLogs.visibility = View.VISIBLE
        } else {
            binding.rvMessageLogs.visibility = View.VISIBLE
            binding.layoutEmptyLogs.visibility = View.GONE
        }
    }

    private fun showMessageDetailDialog(item: MessageLogItem) {
        val options = mutableListOf<String>()
        if (!item.otpCode.isNullOrEmpty()) {
            options.add("Copy OTP (${item.otpCode})")
        }
        options.add("Copy Full Message")
        options.add("Copy Sender Number")

        AlertDialog.Builder(requireContext())
            .setTitle(item.sender)
            .setMessage("SIM: ${item.simSlot}\n\n${item.message}")
            .setItems(options.toTypedArray()) { _, which ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                when (options[which]) {
                    "Copy Full Message" -> {
                        clipboard.setPrimaryClip(ClipData.newPlainText("Message", item.message))
                        Toast.makeText(requireContext(), "Message copied", Toast.LENGTH_SHORT).show()
                    }
                    "Copy Sender Number" -> {
                        clipboard.setPrimaryClip(ClipData.newPlainText("Sender", item.sender))
                        Toast.makeText(requireContext(), "Sender copied", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        if (!item.otpCode.isNullOrEmpty()) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("OTP", item.otpCode))
                            Toast.makeText(requireContext(), "OTP copied", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Dismiss", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
