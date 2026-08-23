package com.example.digisuraksha

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var switchAutoDetect: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        switchAutoDetect = findViewById(R.id.switchAutoDetect)
        val btnReplayOnboarding = findViewById<LinearLayout>(R.id.btnReplayOnboarding)

        btnBack.setOnClickListener { finish() }

        // Default is OFF — user must opt in (Phase 3 Requirement)
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isAutoDetectEnabled = prefs.getBoolean("auto_detect_enabled", false)
        switchAutoDetect.isChecked = isAutoDetectEnabled

        switchAutoDetect.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_detect_enabled", isChecked).apply()
            if (isChecked) {
                Toast.makeText(this, "🛡️ Screenshot Auto-Detect enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Screenshot Auto-Detect disabled", Toast.LENGTH_SHORT).show()
            }
        }

        btnReplayOnboarding.setOnClickListener {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }
}