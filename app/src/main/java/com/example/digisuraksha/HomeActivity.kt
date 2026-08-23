package com.example.digisuraksha

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class HomeActivity : AppCompatActivity() {

    private var screenshotObserver: ScreenshotObserver? = null
    private var isObserverRegistered = false

    // System Permission Launcher
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val notifGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: true
        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_IMAGES] ?: false
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        }

        if (storageGranted) {
            enableAutoDetect(true)
            Toast.makeText(this, "🛡️ Screenshot auto-protection active", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Storage permission required for auto-detect. You can still scan manually.", Toast.LENGTH_LONG).show()
        }
    }

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

        // 🛡️ Phase 1: Permission Transparency Check on Launch
        checkPermissionsWithTransparency()

        // UI Insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    // ============================================================
    // 🛡️ PHASE 1: PERMISSION TRANSPARENCY
    // ============================================================
    private fun checkPermissionsWithTransparency() {
        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (hasStorage && hasNotif) {
            enableAutoDetect(true)
        } else {
            showPermissionTransparencyDialog()
        }
    }

    private fun showPermissionTransparencyDialog() {
        val message = """
            DigiSuraksha protects your personal data from accidental leaks.
            
            Why we request Photos & Notification access:
            • 🔍 Auto-Detect: Instantly scans new screenshots for sensitive data.
            • 🛡️ Sensitive Data Masking: Redacts Aadhaar, PAN, Cards, Passwords & UPI QR codes.
            • 🔒 100% On-Device: All scanning happens locally on your phone.
            • 🚫 Zero Cloud Uploads: Your media and text NEVER leave your device.
            
            Compliant with DPDP Act 2023 Purpose Limitation and Data Privacy standards.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("🛡️ Privacy & Permission Transparency")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("I Understand, Continue") { _, _ ->
                requestSystemPermissions()
            }
            .setNegativeButton("Not Now") { dialog, _ ->
                dialog.dismiss()
                enableAutoDetect(false)
                Toast.makeText(this, "Auto-detect disabled. You can still scan manually.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun requestSystemPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun enableAutoDetect(enable: Boolean) {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("auto_detect_enabled", enable).apply()

        if (enable && !isObserverRegistered) {
            if (screenshotObserver == null) {
                screenshotObserver = ScreenshotObserver(this)
            }
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                screenshotObserver!!
            )
            isObserverRegistered = true
        } else if (!enable && isObserverRegistered && screenshotObserver != null) {
            contentResolver.unregisterContentObserver(screenshotObserver!!)
            isObserverRegistered = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isObserverRegistered && screenshotObserver != null) {
            contentResolver.unregisterContentObserver(screenshotObserver!!)
            isObserverRegistered = false
        }
    }
}