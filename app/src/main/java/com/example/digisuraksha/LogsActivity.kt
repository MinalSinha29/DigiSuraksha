package com.example.digisuraksha

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LogsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs)

        val logText = findViewById<TextView>(R.id.logText)

        // 🔥 GET LOGS FROM STORAGE
        val prefs = getSharedPreferences("logs", MODE_PRIVATE)
        val logs = prefs.getString("data", "No logs found")

        // 🔥 SHOW LOGS
        logText.text = logs
    }
}