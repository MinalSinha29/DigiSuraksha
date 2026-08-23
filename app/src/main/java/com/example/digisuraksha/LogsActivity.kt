package com.example.digisuraksha

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class LogsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LogAdapter
    private lateinit var emptyStateLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs)

        recyclerView = findViewById(R.id.recyclerViewLogs)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnClearLogs = findViewById<Button>(R.id.btnClearLogs)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = LogAdapter(emptyList())
        recyclerView.adapter = adapter

        btnBack.setOnClickListener { finish() }

        btnClearLogs.setOnClickListener {
            showClearConfirmationDialog()
        }

        loadAndParseLogs()
    }

    override fun onResume() {
        super.onResume()
        loadAndParseLogs()
    }

    private fun loadAndParseLogs() {
        val prefs = getSharedPreferences("logs", MODE_PRIVATE)
        val rawData = prefs.getString("data", "") ?: ""

        val parsedItems = mutableListOf<CleanLogItem>()

        val lines = rawData.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isNotBlank()) {
                val parsed = parseLogLine(trimmed)
                parsedItems.add(parsed)
            }
        }

        if (parsedItems.isEmpty()) {
            emptyStateLayout.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyStateLayout.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            adapter.updateData(parsedItems)
        }
    }

    // 🎯 Robust parser that correctly handles all timestamp formats
    private fun parseLogLine(line: String): CleanLogItem {
        var rawTime = ""
        var rawMessage = line.trim()

        // Match "dd/MM/yyyy HH:mm:ss : Event" or "dd-MM-yyyy HH:mm:ss : Event"
        val fullDateTimeRegex = Regex("^(\\d{2}[-/]\\d{2}[-/]\\d{4}\\s+\\d{1,2}:\\d{2}(?::\\d{2})?)\\s*:\\s*(.*)$")
        // Match "HH:mm:ss dd-MM-yyyy : Event"
        val timeAndDateRegex = Regex("^(\\d{1,2}:\\d{2}(?::\\d{2})?\\s+\\d{2}[-/]\\d{2}[-/]\\d{4})\\s*:\\s*(.*)$")
        // Match "HH:mm:ss : Event"
        val timeOnlyRegex = Regex("^(\\d{1,2}:\\d{2}(?::\\d{2})?)\\s*:\\s*(.*)$")

        val m1 = fullDateTimeRegex.find(rawMessage)
        val m2 = if (m1 == null) timeAndDateRegex.find(rawMessage) else null
        val m3 = if (m1 == null && m2 == null) timeOnlyRegex.find(rawMessage) else null

        when {
            m1 != null -> {
                rawTime = m1.groupValues[1]
                rawMessage = m1.groupValues[2]
            }
            m2 != null -> {
                rawTime = m2.groupValues[1]
                rawMessage = m2.groupValues[2]
            }
            m3 != null -> {
                rawTime = m3.groupValues[1]
                rawMessage = m3.groupValues[2]
            }
            else -> {
                rawTime = "Recent"
            }
        }

        // Determine Type
        val type = when {
            rawMessage.contains("SMS", ignoreCase = true) -> "SMS"
            rawMessage.contains("Screenshot", ignoreCase = true) -> "SCREENSHOT"
            else -> "SYSTEM"
        }

        // Determine Risk
        val risk = when {
            rawMessage.contains("HIGH", ignoreCase = true) ||
                    rawMessage.contains("CRITICAL", ignoreCase = true) ||
                    rawMessage.contains("High risk", ignoreCase = true) -> "HIGH"

            rawMessage.contains("MEDIUM", ignoreCase = true) ||
                    rawMessage.contains("WARNING", ignoreCase = true) -> "MEDIUM"

            rawMessage.contains("LOW", ignoreCase = true) ||
                    rawMessage.contains("SAFE", ignoreCase = true) ||
                    rawMessage.contains("CLEAN", ignoreCase = true) -> "LOW"

            else -> "INFO"
        }

        // Clean details text (strip duplicate arrows & technical artifacts)
        var cleanDetails = rawMessage
            .replace("→", "•")
            .replace("->", "•")
            .replace(Regex("Screenshot\\s*•?\\s*"), "")
            .replace(Regex("SMS\\s*•?\\s*"), "")
            .replace(Regex("LOW\\s*•?\\s*"), "")
            .replace(Regex("HIGH\\s*•?\\s*"), "")
            .replace(Regex("MEDIUM\\s*•?\\s*"), "")
            .replace(Regex("CRITICAL\\s*•?\\s*"), "")
            .replace("Scanned ()", "Scanned (Safe — No sensitive PII detected)")
            .trim()
            .trimStart('•', ':', ' ')
            .trim()

        // Clean user-friendly Title
        val title = when {
            rawMessage.contains("Auto-Detected in Background", true) -> "⚡ Auto-Detected in Background"
            rawMessage.contains("Opened via Notification", true) || rawMessage.contains("Auto-Detected from Background Notification", true) -> "📲 Opened via Notification"
            rawMessage.contains("Shared (Blurred)", true) || rawMessage.contains("Shared blurred image", true) -> "🛡️ Shared Blurred Image"
            rawMessage.contains("Shared (Masked", true) -> "📝 Shared Masked Text"
            rawMessage.contains("Shared (Original", true) -> "⚠️ Shared Original Image"
            rawMessage.contains("User attempted to share", true) -> "📤 Share Attempt"
            rawMessage.contains("Scanned", true) -> "🔍 Screenshot Scanned"
            rawMessage.contains("SMS analyzed", true) -> "📩 SMS Scanned"
            rawMessage.contains("High risk", true) -> "🚨 High Risk Threat Detected"
            else -> cleanDetails.split("•").firstOrNull()?.trim() ?: "Security Event"
        }

        return CleanLogItem(
            time = rawTime,
            type = type,
            risk = risk,
            title = title,
            details = cleanDetails.ifBlank { "Analysis complete" }
        )
    }

    private fun showClearConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear All Logs?")
            .setMessage("This will remove all scan history from your screen.")
            .setPositiveButton("Clear All") { _, _ ->
                val prefs = getSharedPreferences("logs", MODE_PRIVATE)
                prefs.edit().clear().apply()
                loadAndParseLogs()
                Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}