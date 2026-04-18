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

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {

            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            for (sms in messages) {

                val text = sms.messageBody
                val lowerText = text.lowercase()

                // ===== OTP DETECTION =====
                val isOtp =
                    Regex("\\b\\d{4,6}\\b").containsMatchIn(text) &&
                            (lowerText.contains("otp") ||
                                    lowerText.contains("code") ||
                                    lowerText.contains("verification"))

                // ===== FRAUD DETECTION =====
                val hasReward =
                    lowerText.contains("won") ||
                            lowerText.contains("prize") ||
                            lowerText.contains("lottery")

                val hasAction =
                    lowerText.contains("click") ||
                            lowerText.contains("claim") ||
                            lowerText.contains("urgent")

                val isFraud = hasReward && hasAction

                // ===== LINK DETECTION =====
                val hasLink = text.contains("http") || text.contains("www")

                // ===== DECIDE MESSAGE =====
                val alertMessage = when {
                    isFraud || hasLink -> "⚠ Fraud SMS Detected! Avoid clicking links."
                    isOtp -> "🔐 OTP detected – Do not share with anyone."
                    else -> "📩 SMS received"
                }

                showNotification(context, alertMessage)
            }
        }
    }

    private fun showNotification(context: Context, message: String) {

        val channelId = "sms_alert_channel"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 🔥 Create channel (required for Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SMS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("DigiSuraksha Alert")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}