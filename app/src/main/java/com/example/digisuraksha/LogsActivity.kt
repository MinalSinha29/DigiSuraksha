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

        // Split by lines and parse each line cleanly
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

    // Intelligent parser for any log string format
    private fun parseLogLine(line: String): CleanLogItem {
        // Split timestamp from message: "dd/MM/yyyy HH:mm:ss : Event" or "HH:mm:ss : Event"
        val parts = line.split(Regex("\\s*:\\s*"), limit = 2)
        val timePart = if (parts.isNotEmpty()) parts[0].trim() else "Recent"
        val messagePart = if (parts.size > 1) parts[1].trim() else line.trim()

        val type = when {
            messagePart.contains("SMS", ignoreCase = true) -> "SMS"
            messagePart.contains("Screenshot", ignoreCase = true) -> "SCREENSHOT"
            else -> "SYSTEM"
        }

        val risk = when {
            messagePart.contains("HIGH", ignoreCase = true) || messagePart.contains("CRITICAL", ignoreCase = true) -> "HIGH"
            messagePart.contains("MEDIUM", ignoreCase = true) || messagePart.contains("WARNING", ignoreCase = true) -> "MEDIUM"
            messagePart.contains("LOW", ignoreCase = true) || messagePart.contains("SAFE", ignoreCase = true) || messagePart.contains("CLEAN", ignoreCase = true) -> "LOW"
            else -> "INFO"
        }

        val title = when {
            messagePart.contains("Auto-Detected in Background", true) -> "⚡ Auto-Detected in Background"
            messagePart.contains("Auto-Detected from Background Notification", true) -> "📲 Opened via Notification"
            messagePart.contains("Shared (Blurred)", true) -> "🛡️ Shared Blurred Image"
            messagePart.contains("Shared (Masked", true) -> "📝 Shared Masked Text"
            messagePart.contains("Shared (Original", true) -> "⚠️ Shared Original Image"
            messagePart.contains("Scanned", true) -> "🔍 Screenshot Scanned"
            messagePart.contains("SMS analyzed", true) -> "📩 SMS Scanned"
            messagePart.contains("High risk", true) -> "🚨 High Risk Threat Detected"
            else -> messagePart.replace("→", "•").split("•").firstOrNull()?.trim() ?: "Security Event"
        }

        val details = messagePart.replace("→", "•").replace("->", "•")

        return CleanLogItem(
            time = timePart,
            type = type,
            risk = risk,
            title = title,
            details = details
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