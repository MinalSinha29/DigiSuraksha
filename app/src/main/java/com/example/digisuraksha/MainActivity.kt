package com.example.digisuraksha

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkAndShowPermissionConsent()

        val startButton = findViewById<Button>(R.id.startButton)

        startButton.setOnClickListener {
            // 📖 Phase 3: Route to Onboarding on first launch, or HomeActivity if already seen
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val isCompleted = prefs.getBoolean("onboarding_completed", false)

            if (!isCompleted) {
                startActivity(Intent(this, OnboardingActivity::class.java))
            } else {
                startActivity(Intent(this, HomeActivity::class.java))
            }
        }
    }

    // ── Figures out which permissions are still missing ──
    private fun getMissingPermissions(): List<String> {
        val permissionsToRequest = mutableListOf<String>()

        // SMS permissions
        if (checkSelfPermission(android.Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.RECEIVE_SMS)
        }
        if (checkSelfPermission(android.Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.READ_SMS)
        }

        // Notification permission (Android 13+ only)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Media/image permission — name differs by Android version
        val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (checkSelfPermission(mediaPermission) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(mediaPermission)
        }

        return permissionsToRequest
    }

    // ── Phase 1: Plain-language explanation shown BEFORE the system permission prompt ──
    private fun checkAndShowPermissionConsent() {
        val missing = getMissingPermissions()
        if (missing.isEmpty()) return

        val explanation = buildString {
            append("DigiSuraksha needs a few permissions to protect you:\n\n")
            if (missing.any {
                    it == android.Manifest.permission.RECEIVE_SMS ||
                            it == android.Manifest.permission.READ_SMS
                }) {
                append("• SMS access — to detect fraud and OTP messages in real time\n")
            }
            if (missing.contains(android.Manifest.permission.POST_NOTIFICATIONS)) {
                append("• Notifications — to alert you when a risky message or screenshot is found\n")
            }
            if (missing.any {
                    it == android.Manifest.permission.READ_MEDIA_IMAGES ||
                            it == android.Manifest.permission.READ_EXTERNAL_STORAGE
                }) {
                append("• Photos/Media access — to scan screenshots you choose for sensitive information\n")
            }
            append("\nAll scanning happens on your device — nothing is uploaded anywhere.")
        }

        AlertDialog.Builder(this)
            .setTitle("Why DigiSuraksha needs these permissions")
            .setMessage(explanation)
            .setPositiveButton("Continue") { _, _ ->
                requestPermissions(missing.toTypedArray(), PERMISSION_REQUEST_CODE)
            }
            .setNegativeButton("Not now", null)
            .setCancelable(false)
            .show()
    }
}