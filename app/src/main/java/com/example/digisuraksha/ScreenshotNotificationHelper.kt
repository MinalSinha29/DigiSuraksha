package com.example.digisuraksha

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * ScreenshotNotificationHelper manages notification channel creation and posting
 * "New screenshot detected" alerts for DigiSuraksha Day 2 MVP.
 */
object ScreenshotNotificationHelper {

    private const val CHANNEL_ID = "digisuraksha_screenshot_channel"
    private const val CHANNEL_NAME = "Screenshot Security Alerts"
    private const val NOTIFICATION_ID_BASE = 3000

    /**
     * Creates NotificationChannel required for Android 8.0+ (API 26+)
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when a new screenshot is detected so you can scan it for sensitive data."
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Displays notification when a new screenshot is detected.
     * Title: "New screenshot detected"
     * Text: "Tap to scan with DigiSuraksha"
     */
    fun showScreenshotNotification(context: Context, mediaId: Long, contentUri: android.net.Uri? = null) {
        val appContext = context.applicationContext
        createNotificationChannel(appContext)

        // Android 13+ (API 33+) POST_NOTIFICATIONS permission safety check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    appContext,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        // PendingIntent for Day 3 activity launching hookup with screenshot Uri
        val intent = Intent(appContext, ScreenshotScannerActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            if (contentUri != null) {
                data = contentUri
                putExtra("screenshot_uri", contentUri.toString())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            putExtra("media_id", mediaId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        val pendingIntent = PendingIntent.getActivity(
            appContext,
            mediaId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("New screenshot detected")
            .setContentText("Tap to scan with DigiSuraksha")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationId = NOTIFICATION_ID_BASE + (mediaId % 1000).toInt()
        notificationManager.notify(notificationId, builder.build())
    }
}
