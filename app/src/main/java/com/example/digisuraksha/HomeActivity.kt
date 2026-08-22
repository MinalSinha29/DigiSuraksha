package com.example.digisuraksha

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class HomeActivity : AppCompatActivity() {

    private lateinit var switchAutoDetect: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        // 📸 Screenshot Scanner Card
        val scanCard = findViewById<LinearLayout>(R.id.scanCard)
        scanCard.setOnClickListener {
            startActivity(Intent(this, ScreenshotScannerActivity::class.java))
        }

        // 📩 SMS Analyzer Card
        val smsCard = findViewById<LinearLayout>(R.id.smsCard)
        smsCard.setOnClickListener {
            startActivity(Intent(this, SmsAnalyzerActivity::class.java))
        }

        // 📜 Logs Card
        val logsCard = findViewById<LinearLayout>(R.id.logsCard)
        logsCard.setOnClickListener {
            startActivity(Intent(this, LogsActivity::class.java))
        }

        // ⚙️ Auto-Detect Screenshots Setting Toggle (Day 3)
        switchAutoDetect = findViewById(R.id.switchAutoDetect)
        switchAutoDetect.isChecked = SettingsManager.isAutoDetectEnabled(this)

        switchAutoDetect.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!MediaPermissionHelper.isPermissionGranted(this)) {
                    // Trigger Day 1 custom consent dialog flow before enabling
                    MediaPermissionHelper.requestMediaPermissionWithConsent(
                        activity = this,
                        onAlreadyGranted = {
                            SettingsManager.setAutoDetectEnabled(this, true)
                        },
                        onRequestPermission = { permission ->
                            requestPermissions(
                                arrayOf(permission),
                                MediaPermissionHelper.MEDIA_PERMISSION_REQUEST_CODE
                            )
                        },
                        onConsentDenied = {
                            switchAutoDetect.isChecked = false
                            SettingsManager.setAutoDetectEnabled(this, false)
                        }
                    )
                } else {
                    SettingsManager.setAutoDetectEnabled(this, true)
                }
            } else {
                SettingsManager.setAutoDetectEnabled(this, false)
            }
        }

        // UI Insets (keep this same)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        if (::switchAutoDetect.isInitialized) {
            switchAutoDetect.isChecked = SettingsManager.isAutoDetectEnabled(this)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MediaPermissionHelper.MEDIA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                switchAutoDetect.isChecked = true
                SettingsManager.setAutoDetectEnabled(this, true)
                Toast.makeText(this, "Auto-Detect enabled", Toast.LENGTH_SHORT).show()
            } else {
                switchAutoDetect.isChecked = false
                SettingsManager.setAutoDetectEnabled(this, false)
                Toast.makeText(
                    this,
                    "Media permission required for Auto-Detect screenshots",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}