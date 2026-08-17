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
        android.util.Log.d("DigiSuraksha", "SMS Receiver triggered!")
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val result = SmsRiskAnalyzer.analyzeSms(sms.messageBody)
                if (result.level != SmsRiskAnalyzer.ThreatLevel.SAFE) {
                    showNotification(context, result)
                }
            }
        }
    }

    // ─── Notification ─────────────────────────────────────────────────────────
    private fun showNotification(context: Context, result: SmsRiskAnalyzer.AnalysisResult) {
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

        val notification = NotificationCompat.Builder(context, channelId)
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
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}