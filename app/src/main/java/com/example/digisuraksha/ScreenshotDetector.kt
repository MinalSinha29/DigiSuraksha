package com.example.digisuraksha

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log

/**
 * ScreenshotDetector monitors MediaStore for new images and identifies screenshots.
 * Day 2 MVP: Triggers a notification ONLY. Does NOT OCR, scan, or modify image.
 */
object ScreenshotDetector {

    private const val TAG = "ScreenshotDetector"

    private var observer: ContentObserver? = null
    private var isRegistered = false

    // Configurable toggle for Day 3 integration (defaults to true for Day 2 MVP)
    var isAutoDetectEnabled: Boolean = true

    // Track processed media IDs to prevent duplicate notifications from repeated ContentObserver events
    private val processedMediaIds = mutableSetOf<Long>()
    private var observerStartTimeSec: Long = 0L

    /**
     * Registers ContentObserver on MediaStore.Images.Media.EXTERNAL_CONTENT_URI
     * using Application Context to prevent lifecycle leaks.
     */
    fun start(context: Context) {
        if (isRegistered) return

        val appContext = context.applicationContext
        observerStartTimeSec = System.currentTimeMillis() / 1000

        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (!isAutoDetectEnabled) return

                // Check Day 1 media permission before querying MediaStore
                if (!MediaPermissionHelper.isPermissionGranted(appContext)) {
                    Log.d(TAG, "Media permission not granted. Skipping screenshot check.")
                    return
                }

                checkLatestImage(appContext)
            }
        }

        try {
            appContext.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer!!
            )
            isRegistered = true
            Log.d(TAG, "Screenshot ContentObserver registered successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register ContentObserver", e)
        }
    }

    /**
     * Unregisters ContentObserver cleanly.
     */
    fun stop(context: Context) {
        if (!isRegistered || observer == null) return
        try {
            context.applicationContext.contentResolver.unregisterContentObserver(observer!!)
            isRegistered = false
            observer = null
            Log.d(TAG, "Screenshot ContentObserver unregistered.")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering ContentObserver", e)
        }
    }

    private fun checkLatestImage(context: Context) {
        val projection = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Images.Media.RELATIVE_PATH)
        } else {
            @Suppress("DEPRECATION")
            projection.add(MediaStore.Images.Media.DATA)
        }

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection.toTypedArray(),
                null,
                null,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(nameColumn) ?: ""
                    val dateAdded = cursor.getLong(dateAddedColumn)

                    var pathInfo = ""
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val pathColumn = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                        if (pathColumn != -1) {
                            pathInfo = cursor.getString(pathColumn) ?: ""
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val dataColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                        if (dataColumn != -1) {
                            pathInfo = cursor.getString(dataColumn) ?: ""
                        }
                    }

                    // 1. Prevent duplicate notifications for the same image ID
                    if (processedMediaIds.contains(id)) {
                        return
                    }

                    // 2. Ignore existing screenshots saved before observer started
                    // 5-second buffer handles minor system timestamp differences
                    if (dateAdded < (observerStartTimeSec - 5)) {
                        return
                    }

                    // 3. Verify if newly added image is a screenshot
                    if (isScreenshot(displayName, pathInfo)) {
                        processedMediaIds.add(id)
                        Log.d(TAG, "New screenshot detected: $displayName in $pathInfo (ID: $id)")

                        val contentUri = android.content.ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )

                        // PRIVACY REQUIREMENT: NO OCR, NO SCANNING, NO AUTO-BLUR BEFORE TAP.
                        // Pass contentUri via notification intent. User must explicitly tap.
                        ScreenshotNotificationHelper.showScreenshotNotification(context, id, contentUri)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore for screenshots", e)
        }
    }

    /**
     * Case-insensitive check for screenshot indicators in display name or folder path.
     */
    fun isScreenshot(displayName: String, pathInfo: String): Boolean {
        val screenshotKeywords = listOf("screenshot", "screenshots", "screen_shot", "screen_shots", "capture")
        val combined = "$displayName $pathInfo".lowercase()
        return screenshotKeywords.any { combined.contains(it) }
    }
}
