package com.example.digisuraksha

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat

class SmsReceiver : BroadcastReceiver() {

    // ─── Severity levels for alert prioritization ───────────────────────────
    enum class ThreatLevel { SAFE, INFO, WARNING, CRITICAL }

    data class AnalysisResult(
        val level: ThreatLevel,
        val tags: List<String>,
        val message: String
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val result = analyzeSms(sms.messageBody)
                if (result.level != ThreatLevel.SAFE) {
                    showNotification(context, result)
                }
            }
        }
    }

    // ─── Core analysis engine ────────────────────────────────────────────────
    private fun analyzeSms(text: String): AnalysisResult {
        val lower = text.lowercase()
        val tags = mutableListOf<String>()
        var maxLevel = ThreatLevel.SAFE

        // ── 1. OTP / Verification ──────────────────────────────────────────
        val isOtp = Regex("\\b\\d{4,8}\\b").containsMatchIn(text) &&
                listOf("otp", "one-time", "one time", "passcode", "verification code",
                    "verify", "authenticate", "2fa", "two-factor", "login code",
                    "security code", "confirmation code").any { lower.contains(it) }
        if (isOtp) {
            tags += "OTP"
            maxLevel = maxLevel.coerceAtLeast(ThreatLevel.INFO)
        }

        // ── 2. Suspicious links ────────────────────────────────────────────
        val linkPatterns = listOf(
            Regex("https?://\\S+"),                          // standard URLs
            Regex("www\\.\\S+"),                             // www links
            Regex("bit\\.ly/\\S+"), Regex("tinyurl\\.com/\\S+"),
            Regex("t\\.me/\\S+"),                            // Telegram links
            Regex("[a-z0-9\\-]+\\.(xyz|top|tk|ml|ga|cf|gq|buzz|click|link|live)/\\S*")  // suspicious TLDs
        )
        val hasLink = linkPatterns.any { it.containsMatchIn(text) }
        val hasSuspiciousUrl = Regex(
            "(bank|secure|login|verify|account|update|paypal|paytm|upi|sbi|hdfc|icici|axis|kyc|income.tax)" +
                    ".*\\.(xyz|top|tk|ml|ga|cf|gq|buzz|click|live|link|net|org|co)",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)

        if (hasSuspiciousUrl) {
            tags += "PHISHING_URL"
            maxLevel = maxLevel.coerceAtLeast(ThreatLevel.CRITICAL)
        } else if (hasLink) {
            tags += "LINK"
            maxLevel = maxLevel.coerceAtLeast(ThreatLevel.WARNING)
        }

        // ── 3. Financial / Banking fraud ──────────────────────────────────
        val bankKeywords = listOf(
            "kyc", "kyc update", "kyc expired", "kyc verification",
            "account blocked", "account suspended", "account on hold",
            "debit card blocked", "credit card blocked",
            "re-kyc", "complete kyc", "link aadhaar", "aadhaar link",
            "pan update", "update pan", "update your account",
            "upi blocked", "upi limit", "upi pin",
            "net banking", "internet banking blocked",
            "transaction failed", "reverse transaction",
            "refund initiated", "refund pending click",
            "unauthorized transaction", "suspicious activity"
        )
        val hasBankFraud = bankKeywords.any { lower.contains(it) }
        if (hasBankFraud) {
            tags += "BANKING_FRAUD"
            maxLevel = maxLevel.coerceAtLeast(ThreatLevel.CRITICAL)
        }

        // ── 4. Prize / Lottery / Reward scams ─────────────────────────────
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
            tags += "REWARD_SCAM"
            maxLevel = maxLevel.coerceAtLeast(ThreatLevel.CRITICAL)
        } else if (rewardScore >= 1 && actionScore >= 1) {
            tags += "SUSPICIOUS_OFFER"
            maxLevel = maxLevel.coerceAtLeast(ThreatLevel.WARNING)
        }

        // ── 5. Job / Work-from-home scams ─────────────────────────────────
        val jobScamKeywords = listOf(
            "work from home", "earn daily", "earn weekly",
            "part time job", "data entry job", "earn ₹", "earn rs",
            "per day income", "investment return", "double your money",
            "mlm", "network marketing", "refer and earn unlimited",
            "guaranteed income", "no experience needed", "whatsapp job"
        )
        val hasJobScam = jobScamKeywords.count { lower.contains(it) } >= 2
        if (hasJobScam) {
            tags += "JOB_SCAM"
            maxLevel = maxLevel.coerceAtLeast(ThreatLevel.WARNING)
        }

        // ── 6. Impersonation (Govt / Bank / Telecom) ──────────────────────
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
        val impersonationScore = impersonationKeywords.count { lower.contains(it) }
        val urgencyScore = urgencyKeywords.count { lower.contains(it) }
        if (impersonationScore >= 1 && urgencyScore >= 1) {
            tags += "IMPERSONATION"
            maxLevel = maxLevel.coerceAtLeast(ThreatLevel.CRITICAL)
        }

        // ── 7. Remote access / Tech support scams ─────────────────────────
        val remoteAccessKeywords = listOf(
            "anydesk", "teamviewer", "quicksupport", "remote access",
            "screen share", "install app", "download apk",
            "grant permission", "allow access"
        )
        val hasRemoteScam = remoteAccessKeywords.any { lower.contains(it) }
        if (hasRemoteScam) {
            tags += "REMOTE_ACCESS_SCAM"
            maxLevel = maxLevel.coerceAtLeast(ThreatLevel.CRITICAL)
        }

        // ── 8. SIM swap / Number porting scams ────────────────────────────
        val simScamKeywords = listOf("sim swap", "port your number", "mnp request",
            "sim upgrade", "esim activation")
        if (simScamKeywords.any { lower.contains(it) }) {
            tags += "SIM_SCAM"
            maxLevel = maxLevel.coerceAtLeast(ThreatLevel.CRITICAL)
        }

        // ── 9. Sensitive info requests ────────────────────────────────────
        val infoRequestKeywords = listOf(
            "share your otp", "send otp", "tell otp", "give otp",
            "cvv", "card number", "expiry date",
            "pin number", "atm pin", "net banking password",
            "aadhaar number", "pan number", "account number",
            "mother's maiden name", "date of birth", "dob"
        )
        val hasInfoRequest = infoRequestKeywords.count { lower.contains(it) } >= 1
        if (hasInfoRequest && !isOtp) { // real OTPs never ask you to share
            tags += "INFO_HARVEST"
            maxLevel = maxLevel.coerceAtLeast(ThreatLevel.CRITICAL)
        }

        // ── Build final result ────────────────────────────────────────────
        return when (maxLevel) {
            ThreatLevel.CRITICAL -> AnalysisResult(
                level = ThreatLevel.CRITICAL,
                tags = tags,
                message = "🚨 HIGH RISK: ${tags.joinToString(", ")} — Do NOT click links or share any details."
            )
            ThreatLevel.WARNING -> AnalysisResult(
                level = ThreatLevel.WARNING,
                tags = tags,
                message = "⚠️ SUSPICIOUS SMS: ${tags.joinToString(", ")} — Stay cautious."
            )
            ThreatLevel.INFO -> AnalysisResult(
                level = ThreatLevel.INFO,
                tags = tags,
                message = "🔐 OTP Received — Never share this with anyone, including bank officials."
            )
            ThreatLevel.SAFE -> AnalysisResult(ThreatLevel.SAFE, emptyList(), "")
        }
    }

    // ─── Notification ─────────────────────────────────────────────────────────
    private fun showNotification(context: Context, result: AnalysisResult) {
        val channelId = "sms_alert_channel"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = when (result.level) {
                ThreatLevel.CRITICAL -> NotificationManager.IMPORTANCE_HIGH
                ThreatLevel.WARNING  -> NotificationManager.IMPORTANCE_DEFAULT
                else                 -> NotificationManager.IMPORTANCE_LOW
            }
            val channel = NotificationChannel(channelId, "SMS Alerts", importance)
            notificationManager.createNotificationChannel(channel)
        }

        val title = when (result.level) {
            ThreatLevel.CRITICAL -> "🚨 DigiSuraksha — Fraud Detected!"
            ThreatLevel.WARNING  -> "⚠️ DigiSuraksha — Suspicious SMS"
            ThreatLevel.INFO     -> "🔐 DigiSuraksha — OTP Alert"
            ThreatLevel.SAFE     -> "DigiSuraksha"
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(result.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(result.message))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(
                if (result.level == ThreatLevel.CRITICAL)
                    NotificationCompat.PRIORITY_MAX
                else
                    NotificationCompat.PRIORITY_HIGH
            )
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    // ─── Kotlin extension to compare enum ordinals ───────────────────────────
    private fun ThreatLevel.coerceAtLeast(other: ThreatLevel): ThreatLevel =
        if (this.ordinal >= other.ordinal) this else other
}