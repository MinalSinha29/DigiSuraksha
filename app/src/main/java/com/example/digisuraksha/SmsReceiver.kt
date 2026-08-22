package com.example.digisuraksha

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("DigiSuraksha", "SMS Receiver triggered!")
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val sender = sms.originatingAddress ?: "Unknown"
                val result = SmsRiskAnalyzer.analyzeSms(sms.messageBody)
                logEvent(context, "SMS received (real-time) → ${result.level} → [${result.tags.joinToString(", ")}]")
                if (result.level != SmsRiskAnalyzer.ThreatLevel.SAFE) {
                    showNotification(context, result, sms.messageBody, sender)
                }
            }
        }
    }

    // ─── Notification ─────────────────────────────────────────────────────────
    private fun showNotification(
        context: Context,
        result: SmsRiskAnalyzer.AnalysisResult,
        messageBody: String,
        sender: String
    ) {
        val channelId = "sms_alert_channel"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = when (result.level) {
                SmsRiskAnalyzer.ThreatLevel.CRITICAL -> NotificationManager.IMPORTANCE_HIGH
                SmsRiskAnalyzer.ThreatLevel.WARNING  -> NotificationManager.IMPORTANCE_DEFAULT
                else                                  -> NotificationManager.IMPORTANCE_LOW
            }
            val channel = NotificationChannel(channelId, "SMS Alerts", importance)
            notificationManager.createNotificationChannel(channel)
        }

        val title = when (result.level) {
            SmsRiskAnalyzer.ThreatLevel.CRITICAL -> "🚨 DigiSuraksha — Fraud Detected!"
            SmsRiskAnalyzer.ThreatLevel.WARNING  -> "⚠️ DigiSuraksha — Suspicious SMS"
            SmsRiskAnalyzer.ThreatLevel.INFO     -> "🔐 DigiSuraksha — OTP Alert"
            SmsRiskAnalyzer.ThreatLevel.SAFE     -> "DigiSuraksha"
        }

        val message = result.toNotificationMessage()
        val notificationId = System.currentTimeMillis().toInt()

        // ── Tap-to-detail: opens SmsAnalyzerActivity pre-filled with this message ──
        val detailIntent = Intent(context, SmsAnalyzerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_SMS_BODY", messageBody)
            putExtra("EXTRA_SMS_SENDER", sender)
            putExtra("EXTRA_AUTO_ANALYZE", true)
        }
        val detailPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            detailIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── Block-number shortcut: opens Dialer with sender's number ──
        // Note: DigiSuraksha isn't the default SMS/Dialer app, so Android won't allow
        // silently blocking a number. This opens the Dialer so the user can block it
        // via the call log in one extra tap, rather than hunting through Settings.
        val blockIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sender"))
        val blockPendingIntent = PendingIntent.getActivity(
            context,
            notificationId + 1,
            blockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(
                if (result.level == SmsRiskAnalyzer.ThreatLevel.CRITICAL)
                    NotificationCompat.PRIORITY_MAX
                else
                    NotificationCompat.PRIORITY_HIGH
            )
            .setContentIntent(detailPendingIntent)
            .setAutoCancel(true)

        // Only add the block-number action for risky (non-SAFE, non-INFO) messages,
        // since a plain OTP doesn't warrant a "block sender" suggestion.
        if (result.level == SmsRiskAnalyzer.ThreatLevel.CRITICAL ||
            result.level == SmsRiskAnalyzer.ThreatLevel.WARNING) {
            notificationBuilder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Block Number",
                blockPendingIntent
            )
        }

        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    // ─── Logging ─────────────────────────────────────────────────────────────
    private fun logEvent(context: Context, event: String) {
        val prefs  = context.getSharedPreferences("logs", Context.MODE_PRIVATE)
        val oldLog = prefs.getString("data", "") ?: ""
        prefs.edit().putString("data", "$oldLog\n${getCurrentTime()} : $event").apply()
    }

    private fun getCurrentTime(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss dd-MM-yyyy", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }
}