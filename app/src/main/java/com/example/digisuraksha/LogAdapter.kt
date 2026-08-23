package com.example.digisuraksha

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class CleanLogItem(
    val time: String,
    val type: String,        // "SCREENSHOT", "SMS", "SYSTEM"
    val risk: String,        // "HIGH", "MEDIUM", "LOW", "INFO"
    val title: String,
    val details: String
)

class LogAdapter(private var logList: List<CleanLogItem>) :
    RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTypeBadge: TextView = itemView.findViewById(R.id.tvTypeBadge)
        val tvRiskBadge: TextView = itemView.findViewById(R.id.tvRiskBadge)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvDetails: TextView = itemView.findViewById(R.id.tvDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val item = logList[position]

        holder.tvTime.text = item.time
        holder.tvTitle.text = item.title
        holder.tvDetails.text = item.details

        // Type Badge styling
        when (item.type) {
            "SMS" -> {
                holder.tvTypeBadge.text = "📩 SMS"
                holder.tvTypeBadge.setBackgroundColor(Color.parseColor("#FEF3C7"))
                holder.tvTypeBadge.setTextColor(Color.parseColor("#92400E"))
            }
            "SCREENSHOT" -> {
                holder.tvTypeBadge.text = "📸 SCREENSHOT"
                holder.tvTypeBadge.setBackgroundColor(Color.parseColor("#EEF2FF"))
                holder.tvTypeBadge.setTextColor(Color.parseColor("#3730A3"))
            }
            else -> {
                holder.tvTypeBadge.text = "🛡️ SYSTEM"
                holder.tvTypeBadge.setBackgroundColor(Color.parseColor("#F1F5F9"))
                holder.tvTypeBadge.setTextColor(Color.parseColor("#475569"))
            }
        }

        // Risk Badge styling
        when (item.risk) {
            "HIGH" -> {
                holder.tvRiskBadge.visibility = View.VISIBLE
                holder.tvRiskBadge.text = "HIGH RISK"
                holder.tvRiskBadge.setBackgroundColor(Color.parseColor("#FEE2E2"))
                holder.tvRiskBadge.setTextColor(Color.parseColor("#991B1B"))
            }
            "MEDIUM" -> {
                holder.tvRiskBadge.visibility = View.VISIBLE
                holder.tvRiskBadge.text = "MEDIUM RISK"
                holder.tvRiskBadge.setBackgroundColor(Color.parseColor("#FFEDD5"))
                holder.tvRiskBadge.setTextColor(Color.parseColor("#C2410C"))
            }
            "LOW" -> {
                holder.tvRiskBadge.visibility = View.VISIBLE
                holder.tvRiskBadge.text = "SAFE / CLEAN"
                holder.tvRiskBadge.setBackgroundColor(Color.parseColor("#DCFCE7"))
                holder.tvRiskBadge.setTextColor(Color.parseColor("#166534"))
            }
            else -> {
                holder.tvRiskBadge.visibility = View.GONE
            }
        }
    }

    override fun getItemCount(): Int = logList.size

    fun updateData(newList: List<CleanLogItem>) {
        this.logList = newList
        notifyDataSetChanged()
    }
}