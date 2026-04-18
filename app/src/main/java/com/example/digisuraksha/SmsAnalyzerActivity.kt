package com.example.digisuraksha

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

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

        smsInput = findViewById(R.id.smsInput)
        analyzeBtn = findViewById(R.id.analyzeSms)
        riskLevel = findViewById(R.id.riskLevel)
        fraudWarning = findViewById(R.id.fraudWarning)
        explanationText = findViewById(R.id.explanationText)
        tipsText = findViewById(R.id.tipsText)

        analyzeBtn.setOnClickListener {

            val text = smsInput.text.toString()

            if (text.isEmpty()) {
                Toast.makeText(this, "Enter SMS first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val lowerText = text.lowercase()

            // 🔐 OTP DETECTION
            val isOtp =
                Regex("\\b\\d{4,6}\\b").containsMatchIn(text) &&
                        (lowerText.contains("otp") ||
                                lowerText.contains("code") ||
                                lowerText.contains("verification"))

            // ⚠️ FRAUD DETECTION
            val hasReward =
                lowerText.contains("won") ||
                        lowerText.contains("prize") ||
                        lowerText.contains("lottery")

            val hasAction =
                lowerText.contains("click") ||
                        lowerText.contains("claim") ||
                        lowerText.contains("urgent")

            val isFraud = hasReward && hasAction

            // 🔗 LINK DETECTION
            val hasLink = text.contains("http") || text.contains("www")

            // 📊 RISK LEVEL
            val risk = when {
                isOtp || isFraud || hasLink -> "HIGH"
                text.contains("@") -> "MEDIUM"
                else -> "LOW"
            }

            riskLevel.text = "Risk Level: $risk"

            when (risk) {
                "HIGH" -> riskLevel.setTextColor(getColor(android.R.color.holo_red_dark))
                "MEDIUM" -> riskLevel.setTextColor(getColor(android.R.color.holo_orange_dark))
                "LOW" -> riskLevel.setTextColor(getColor(android.R.color.holo_green_dark))
            }

            // ⚠️ WARNING MESSAGE
            fraudWarning.text = when {
                isFraud -> "⚠ Possible Fraud SMS"
                hasLink -> "⚠ Suspicious Link Detected"
                isOtp -> "🔐 OTP detected – Do not share"
                else -> ""
            }

            // 🧠 EXPLANATION
            val reasons = mutableListOf<String>()

            if (isOtp) reasons.add("Contains OTP")
            if (hasLink) reasons.add("Contains link")
            if (text.contains("@")) reasons.add("Contains email/UPI")

            explanationText.text =
                if (reasons.isNotEmpty())
                    "⚠ This may be unsafe because:\n\n• " + reasons.joinToString("\n• ")
                else "No major threats detected."

            // 🛡 SAFETY TIPS
            val tips = mutableListOf<String>()

            if (isOtp) tips.add("Never share OTP.")
            if (hasLink) tips.add("Avoid clicking unknown links.")
            if (text.contains("@")) tips.add("Verify UPI/email before sharing.")

            tipsText.text =
                if (tips.isNotEmpty())
                    "🛡 Safety Tips:\n\n• " + tips.joinToString("\n• ")
                else ""
        }
    }
}