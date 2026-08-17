package com.example.digisuraksha

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SmsAnalyzerActivity : AppCompatActivity() {

    private lateinit var smsInput: EditText
    private lateinit var analyzeBtn: Button
    private lateinit var riskLevel: TextView
    private lateinit var fraudWarning: TextView
    private lateinit var explanationText: TextView
    private lateinit var tipsText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sms_analyzer)

        smsInput       = findViewById(R.id.smsInput)
        analyzeBtn     = findViewById(R.id.analyzeSms)
        riskLevel      = findViewById(R.id.riskLevel)
        fraudWarning   = findViewById(R.id.fraudWarning)
        explanationText = findViewById(R.id.explanationText)
        tipsText       = findViewById(R.id.tipsText)

        analyzeBtn.setOnClickListener {
            val text = smsInput.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "Please enter an SMS to analyze", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val result = SmsRiskAnalyzer.analyzeSms(text)
            renderResult(result)
            logEvent("SMS analyzed → ${result.level} → [${result.tags.joinToString(", ")}]")
        }
    }

    // ─── Render results to UI ────────────────────────────────────────────────
    private fun renderResult(result: SmsRiskAnalyzer.AnalysisResult) {

        // Risk level label + color
        val (riskLabel, riskColor) = when (result.level) {
            SmsRiskAnalyzer.ThreatLevel.CRITICAL -> "🚨 CRITICAL RISK"  to android.R.color.holo_red_dark
            SmsRiskAnalyzer.ThreatLevel.WARNING  -> "⚠️ MEDIUM RISK"    to android.R.color.holo_orange_dark
            SmsRiskAnalyzer.ThreatLevel.INFO     -> "🔐 LOW RISK (OTP)"  to android.R.color.holo_blue_dark
            SmsRiskAnalyzer.ThreatLevel.SAFE     -> "✅ SAFE"             to android.R.color.holo_green_dark
        }
        riskLevel.text = riskLabel
        riskLevel.setTextColor(ContextCompat.getColor(this, riskColor))

        // Detection tags banner
        fraudWarning.text = if (result.tags.isNotEmpty())
            "Detected: ${result.tags.joinToString(" · ")}"
        else
            "No threats detected"

        // Explanation block
        explanationText.text = if (result.reasons.isNotEmpty())
            "⚠️ Why this SMS is suspicious:\n\n• " + result.reasons.joinToString("\n\n• ")
        else
            "✅ This SMS appears safe. No suspicious patterns found."

        // Safety tips block
        tipsText.text = if (result.tips.isNotEmpty())
            "🛡️ Safety Tips:\n\n• " + result.tips.joinToString("\n\n• ")
        else
            ""
    }

    // ─── Logging ─────────────────────────────────────────────────────────────
    private fun logEvent(event: String) {
        val prefs  = getSharedPreferences("logs", MODE_PRIVATE)
        val oldLog = prefs.getString("data", "") ?: ""
        prefs.edit().putString("data", "$oldLog\n${getCurrentTime()} : $event").apply()
    }

    private fun getCurrentTime(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss dd-MM-yyyy", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }
}