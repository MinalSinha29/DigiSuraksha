package com.example.digisuraksha

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors
import kotlin.math.abs

class ScreenshotObserver(
    private val context: Context,
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
) : ContentObserver(mainHandler) {

    private var lastNotifiedUri: String? = null
    private val executor = Executors.newSingleThreadExecutor()

    companion object {
        private const val TAG = "ScreenshotObserver"
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Log.d(TAG, "onChange triggered with URI: $uri")

        // Debounce: Wait 350ms so Android OS finishes saving the screenshot file to storage
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            executor.execute {
                checkForScreenshot(uri)
            }
        }, 350)
    }

    private fun checkForScreenshot(triggeredUri: Uri?) {
        try {
            val projection = mutableListOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DISPLAY_NAME
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                projection.add(MediaStore.Images.Media.RELATIVE_PATH)
                projection.add(MediaStore.Images.Media.IS_PENDING)
            } else {
                @Suppress("DEPRECATION")
                projection.add(MediaStore.Images.Media.DATA)
            }

            // Direct query if specific URI is given (blazing fast ~2ms)
            val isSpecificImage = triggeredUri != null && triggeredUri.toString().matches(Regex(".*/\\d+$"))
            val queryUri = if (isSpecificImage) triggeredUri!! else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val cursor = context.contentResolver.query(
                queryUri,
                projection.toTypedArray(),
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    // If OS is still writing image bytes, re-check in 400ms
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val pendingIndex = it.getColumnIndex(MediaStore.Images.Media.IS_PENDING)
                        if (pendingIndex != -1 && it.getInt(pendingIndex) == 1) {
                            Log.d(TAG, "File is still being written by OS. Retrying in 400ms...")
                            mainHandler.postDelayed({ executor.execute { checkForScreenshot(triggeredUri) } }, 400)
                            return
                        }
                    }

                    val idIndex = it.getColumnIndex(MediaStore.Images.Media._ID)
                    val dateAddedIndex = it.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                    val nameIndex = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)

                    val id = if (idIndex != -1) it.getLong(idIndex) else return
                    var dateAdded = if (dateAddedIndex != -1) it.getLong(dateAddedIndex) else 0L
                    val displayName = if (nameIndex != -1) it.getString(nameIndex) ?: "" else ""

                    if (dateAdded > 100000000000L) {
                        dateAdded /= 1000
                    }

                    var pathOrFolder = ""
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val pathIndex = it.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                        if (pathIndex != -1) {
                            pathOrFolder = it.getString(pathIndex) ?: ""
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val dataIndex = it.getColumnIndex(MediaStore.Images.Media.DATA)
                        if (dataIndex != -1) {
                            pathOrFolder = it.getString(dataIndex) ?: ""
                        }
                    }

                    val contentUri = if (isSpecificImage) queryUri else Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    val uriString = contentUri.toString()

                    if (uriString == lastNotifiedUri) {
                        Log.d(TAG, "Already notified for this screenshot.")
                        return
                    }

                    val isScreenshot = displayName.contains("screenshot", ignoreCase = true) ||
                            displayName.contains("screen_shot", ignoreCase = true) ||
                            displayName.contains("screencap", ignoreCase = true) ||
                            displayName.contains("capture", ignoreCase = true) ||
                            pathOrFolder.contains("screenshot", ignoreCase = true) ||
                            pathOrFolder.contains("Screenshots", ignoreCase = true)

                    val currentTimeSec = System.currentTimeMillis() / 1000
                    val timeDiff = abs(currentTimeSec - dateAdded)
                    val isRecent = timeDiff < 90

                    Log.d(TAG, "Evaluated -> Name: $displayName, Path: $pathOrFolder, isScreenshot: $isScreenshot, timeDiff: ${timeDiff}s")

                    if (isScreenshot && isRecent) {
                        lastNotifiedUri = uriString
                        Log.d(TAG, "⚡ Instant Notification Posted: $contentUri")
                        showAutoDetectNotification(contentUri)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking screenshot: ${e.message}", e)
        }
    }

    private fun showAutoDetectNotification(imageUri: Uri) {
        // 🔥 Instant log recording when screenshot is caught in background
        logObserverEvent("📸 Screenshot Auto-Detected in Background → Notification Sent")

        val channelId = "screenshot_autodetect_channel"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Screenshot Auto-Detect",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when a new screenshot is taken"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val scanIntent = Intent(context, ScreenshotScannerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_IMAGE_URI", imageUri.toString())
            putExtra("EXTRA_AUTO_DETECTED", true)
            data = imageUri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            scanIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("🛡️ New Screenshot Detected")
            .setContentText("Tap to scan for sensitive data")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }

    // 🔥 Helper to write directly to SharedPreferences logs
    private fun logObserverEvent(event: String) {
        try {
            val prefs = context.getSharedPreferences("logs", Context.MODE_PRIVATE)
            val oldLog = prefs.getString("data", "") ?: ""
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
            val currentTime = sdf.format(java.util.Date())
            val newEntry = "$currentTime : $event"
            val updated = if (oldLog.isBlank()) newEntry else "$newEntry\n$oldLog"
            prefs.edit().putString("data", updated).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error writing log: ${e.message}")
        }
    }
}