package com.example.digisuraksha

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LogsActivity : AppCompatActivity() {

    private lateinit var logText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs)

        logText = findViewById(R.id.logText)
        loadLogs()
    }

    override fun onResume() {
        super.onResume()
        loadLogs() // Refresh logs every time user returns to this screen
    }

    private fun loadLogs() {
        val prefs = getSharedPreferences("logs", MODE_PRIVATE)
        val logs = prefs.getString("data", null)

        if (logs.isNullOrBlank()) {
            logText.text = "No security logs recorded yet.\n\nScreenshots scanned and detected items will appear here."
        } else {
            logText.text = logs
        }
    }
}