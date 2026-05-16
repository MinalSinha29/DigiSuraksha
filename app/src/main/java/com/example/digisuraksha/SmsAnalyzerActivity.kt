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

    // ─── Threat levels ────────────────────────────────────────────────────────
    enum class ThreatLevel { SAFE, INFO, WARNING, CRITICAL }

    data class AnalysisResult(
        val level: ThreatLevel,
        val tags: List<String>,
        val reasons: List<String>,
        val tips: List<String>
    )

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

            val result = analyzeSms(text)
            renderResult(result)
            logEvent("SMS analyzed → ${result.level} → [${result.tags.joinToString(", ")}]")
        }
    }

    // ─── Core analysis engine (mirrors SmsReceiver logic) ────────────────────
    private fun analyzeSms(text: String): AnalysisResult {
        val lower   = text.lowercase()
        val tags    = mutableListOf<String>()
        val reasons = mutableListOf<String>()
        val tips    = mutableListOf<String>()
        var maxLevel = ThreatLevel.SAFE

        // ── 1. OTP Detection ──────────────────────────────────────────────────
        val otpTriggerWords = listOf(
            "otp", "one-time", "one time", "passcode", "verification code",
            "verify", "authenticate", "2fa", "two-factor", "login code",
            "security code", "confirmation code"
        )
        val isOtp = Regex("\\b\\d{4,8}\\b").containsMatchIn(text) &&
                otpTriggerWords.any { lower.contains(it) }
        if (isOtp) {
            tags    += "OTP"
            reasons += "Contains a numeric OTP/verification code"
            tips    += "Never share OTPs with anyone — not even bank officials or customer care"
            maxLevel = maxLevel.coerceAtLeast(ThreatLevel.INFO)
        }

        // ── 2. Suspicious Links ───────────────────────────────────────────────
        val linkPatterns = listOf(
            Regex("https?://\\S+"),
            Regex("www\\.\\S+"),
            Regex("bit\\.ly/\\S+"),
            Regex("tinyurl\\.com/\\S+"),
            Regex("t\\.me/\\S+"),
            Regex("[a-z0-9\\-]+\\.(xyz|top|tk|ml|ga|cf|gq|buzz|click|link|live)/\\S*")
        )
        val hasLink = linkPatterns.any { it.containsMatchIn(text) }

        val phishingUrlPattern = Regex(
            "(bank|secure|login|verify|account|update|paypal|paytm|upi|sbi|hdfc|icici|axis|kyc|income.tax)" +
                    ".*\\.(xyz|top|tk|ml|ga|cf|gq|buzz|click|live|link|net|org|co)",
            RegexOption.IGNORE_CASE
        )
        val hasPhishingUrl = phishingUrlPattern.containsMatchIn(text)

        if (hasPhishingUrl) {
            tags    += "PHISHING_URL"
            reasons += "Contains a phishing/fake banking URL with a suspicious domain"
            tips    += "Never open links from SMS — type the official website address directly in your browser"
            maxLevel = maxLevel.coerceAtLeast(ThreatLevel.CRITICAL)
        } else if (hasLink) {
            tags    += "SUSPICIOUS_LINK"
            reasons += "Contains a web link — could redirect to a malicious site"
            tips    += "Avoid clicking unknown links. Verify with the official source first"
            maxLevel = maxLevel.coerceAtLeast(ThreatLevel.WARNING)
        }

        // ── 3. Banking / KYC Fraud ────────────────────────────────────────────
        val bankFraudKeywords = listOf(
            "kyc", "kyc update", "kyc expired", "kyc verification",
            "account blocked", "account suspended", "account on hold",
            "debit card blocked", "credit card blocked",
            "re-kyc", "complete kyc", "link aadhaar", "aadhaar link",
            "pan update", "update pan", "update your account",
            "upi blocked", "upi limit", "upi pin",
            "net banking", "internet banking blocked",
            "transaction failed", "reverse transaction",
            "refund initiated", "refund pending click",
            "unauthorized transaction", "suspicious activity on your account"
        )
        if (bankFraudKeywords.any { lower.contains(it) }) {
            tags    += "BANKING_FRAUD"
            reasons += "Uses banking/KYC urgency language commonly used in fraud SMS"
            tips    += "Banks never ask you to update KYC via SMS link. Visit your branch or official app directly"
            maxLevel = maxLevel.coerceAtLeast(ThreatLevel.CRITICAL)
        }

        // ── 4. Prize / Lottery / Reward Scams ────────────────────────────────
        val rewardKeywords = listOf(
            "won", "winner", "prize", "lottery", "jackpot",
            "selected", "chosen", "congratulations", "lucky draw",
            "free gift", "reward", "cashback offer", "bonus",
            "rs.", "₹", "lakh", "crore", "cash prize"
        )
        val actionKeywords = listOf(
            "click", "claim", "collect", "redeem", "tap",
            "call now", "reply", "sms back", "share", "send otp",
            "limited time", "expires today", "24 hours", "hurry",
            "immediate", "urgent", "act now", "do not ignore"
        )
        val rewardScore = rewardKeywords.count { lower.contains(it) }
        val actionScore = actionKeywords.count { lower.contains(it) }
        if (rewardScore >= 2 && actionScore >= 1) {
            tags    += "REWARD_SCAM"
            reasons += "Classic lottery/prize scam pattern: reward bait + urgency action"
            tips    += "Legitimate prizes are never announced via SMS. Ignore and delete"
            maxLevel = maxLevel.coerceAtLeast(ThreatLevel.CRITICAL)
        } else if (rewardScore >= 1 && actionScore >= 1) {
            tags    += "SUSPICIOUS_OFFER"
            reasons += "Contains reward language paired with an action request"
            tips    += "Be cautious of unsolicited offers asking you to click or call"
            maxLevel = maxLevel.coerceAtLeast(ThreatLevel.WARNING)
        }

        // ── 5. Job / Work-from-Home Scams ─────────────────────────────────────
        val jobScamKeywords = listOf(
            "work from home", "earn daily", "earn weekly",
            "part time job", "data entry job", "earn ₹", "earn rs",
            "per day income", "investment return", "double your money",
            "mlm", "network marketing", "refer and earn unlimited",
            "guaranteed income", "no experience needed", "whatsapp job"
        )
        if (jobScamKeywords.count { lower.contains(it) } >= 2) {
            tags    += "JOB_SCAM"
            reasons += "Contains multiple work-from-home/easy money scam phrases"
            tips    += "No legitimate job offers guaranteed income via SMS. Research thoroughly before engaging"
            maxLevel = maxLevel.coerceAtLeast(ThreatLevel.WARNING)
        }

        // ── 6. Impersonation (Govt / Bank / Telecom) ─────────────────────────
        val impersonationKeywords = listOf(
            "trai", "dot ", "department of telecom",
            "income tax department", "it department",
            "irdai", "sebi", "rbi notice",
            "police", "cyber cell", "cbi",
            "aadhaar authority", "uidai",
            "sbi", "rbl bank", "icici", "hdfc", "axis bank",
            "jio", "airtel", "vi ", "bsnl"
        )
        val urgencyKeywords = listOf(
            "your sim", "sim card", "will be blocked", "will be deactivated",
            "arrested", "legal action", "fir", "case filed",
            "penalty", "fine", "pay now", "immediate action required"
        )
        if (impersonationKeywords.count { lower.contains(it) } >= 1 &&
            urgencyKeywords.count { lower.contains(it) } >= 1) {
            tags    += "IMPERSONATION"
            reasons += "Impersonates a government body, bank, or telecom operator with threat language"
            tips    += "Government agencies never threaten via SMS. Call the official helpline to verify"
            maxLevel = maxLevel.coerceAtLeast(ThreatLevel.CRITICAL)
        }

        // ── 7. Remote Access / Tech Support Scams ────────────────────────────
        val remoteKeywords = listOf(
            "anydesk", "teamviewer", "quicksupport", "remote access",
            "screen share", "install app", "download apk",
            "grant permission", "allow access"
        )
        if (remoteKeywords.any { lower.contains(it) }) {
            tags    += "REMOTE_ACCESS_SCAM"
            reasons += "Asks you to install a remote access tool — a major red flag for fraud"
            tips    += "Never install apps or grant remote access to anyone claiming to be from a bank or company"
            maxLevel = maxLevel.coerceAtLeast(ThreatLevel.CRITICAL)
        }

        // ── 8. SIM Swap / Porting Scams ───────────────────────────────────────
        val simKeywords = listOf(
            "sim swap", "port your number", "mnp request",
            "sim upgrade", "esim activation"
        )
        if (simKeywords.any { lower.contains(it) }) {
            tags    += "SIM_SWAP_SCAM"
            reasons += "References SIM swap or porting — used to hijack your number and bank accounts"
            tips    += "Never share any OTP that arrives after an unknown SIM-related request"
            maxLevel = maxLevel.coerceAtLeast(ThreatLevel.CRITICAL)
        }

        // ── 9. Sensitive Info Harvesting ──────────────────────────────────────
        val infoKeywords = listOf(
            "share your otp", "send otp", "tell otp", "give otp",
            "cvv", "card number", "expiry date",
            "pin number", "atm pin", "net banking password",
            "aadhaar number", "pan number", "account number",
            "mother's maiden name", "date of birth", "dob"
        )
        if (!isOtp && infoKeywords.any { lower.contains(it) }) {
            tags    += "INFO_HARVESTING"
            reasons += "Requests sensitive personal/financial information — a phishing tactic"
            tips    += "No bank or official body ever asks for PINs, CVV, or passwords over SMS"
            maxLevel = maxLevel.coerceAtLeast(ThreatLevel.CRITICAL)
        }

        // ── 10. Email / UPI ID present ────────────────────────────────────────
        if (Regex("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}").containsMatchIn(text)) {
            tags    += "EMAIL_OR_UPI"
            reasons += "Contains an email address or UPI ID — verify authenticity before use"
            tips    += "Confirm email/UPI IDs through official channels before making any payment"
            maxLevel = maxLevel.coerceAtLeast(ThreatLevel.WARNING)
        }

        return AnalysisResult(maxLevel, tags, reasons, tips)
    }

    // ─── Render results to UI ────────────────────────────────────────────────
    private fun renderResult(result: AnalysisResult) {

        // Risk level label + color
        val (riskLabel, riskColor) = when (result.level) {
            ThreatLevel.CRITICAL -> "🚨 CRITICAL RISK"  to android.R.color.holo_red_dark
            ThreatLevel.WARNING  -> "⚠️ MEDIUM RISK"    to android.R.color.holo_orange_dark
            ThreatLevel.INFO     -> "🔐 LOW RISK (OTP)"  to android.R.color.holo_blue_dark
            ThreatLevel.SAFE     -> "✅ SAFE"             to android.R.color.holo_green_dark
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

    // ─── Enum extension ───────────────────────────────────────────────────────
    private fun ThreatLevel.coerceAtLeast(other: ThreatLevel): ThreatLevel =
        if (this.ordinal >= other.ordinal) this else other
}