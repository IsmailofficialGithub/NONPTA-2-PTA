package com.pta.forwarder.ui.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.pta.forwarder.R
import com.pta.forwarder.data.MessageLogItem
import java.text.SimpleDateFormat
import java.util.Locale

class MessageLogAdapter(
    private var items: List<MessageLogItem>,
    private val onItemClick: (MessageLogItem) -> Unit
) : RecyclerView.Adapter<MessageLogAdapter.ViewHolder>() {

    private val timeFormat = SimpleDateFormat("HH:mm, dd MMM", Locale.getDefault())

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvSender: TextView = itemView.findViewById(R.id.tvMsgSender)
        val tvSimBadge: TextView = itemView.findViewById(R.id.tvMsgSimBadge)
        val tvTime: TextView = itemView.findViewById(R.id.tvMsgTime)
        val tvBody: TextView = itemView.findViewById(R.id.tvMsgBody)
        val tvOtpBadge: TextView = itemView.findViewById(R.id.tvMsgOtpBadge)
        val tvStatus: TextView = itemView.findViewById(R.id.tvMsgStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        holder.tvSender.text = item.sender
        holder.tvSimBadge.text = item.simSlot
        holder.tvTime.text = timeFormat.format(item.timestamp)
        holder.tvBody.text = item.message
        holder.tvStatus.text = item.status

        if (!item.otpCode.isNullOrEmpty()) {
            holder.tvOtpBadge.visibility = View.VISIBLE
            holder.tvOtpBadge.text = "OTP: ${item.otpCode} · Copy"
            holder.tvOtpBadge.setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("OTP Code", item.otpCode)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "OTP ${item.otpCode} copied", Toast.LENGTH_SHORT).show()
            }
        } else {
            holder.tvOtpBadge.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<MessageLogItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }
}
