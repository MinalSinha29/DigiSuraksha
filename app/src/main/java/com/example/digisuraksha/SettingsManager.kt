package com.example.digisuraksha

import android.content.Context
import android.content.SharedPreferences

/**
 * SettingsManager handles persistence for DigiSuraksha user settings via SharedPreferences.
 * Day 3: Auto-Detect Screenshots toggle (Defaults strictly to OFF / false).
 */
object SettingsManager {

    private const val PREFS_NAME = "digisuraksha_settings"
    private const val KEY_AUTO_DETECT_SCREENSHOTS = "key_auto_detect_screenshots"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Checks if Auto-Detect Screenshots is enabled.
     * Default state MUST be false (OFF).
     */
    fun isAutoDetectEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_DETECT_SCREENSHOTS, false)
    }

    /**
     * Updates Auto-Detect Screenshots toggle setting and synchronizes ScreenshotDetector observer.
     */
    fun setAutoDetectEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        getPrefs(appContext).edit().putBoolean(KEY_AUTO_DETECT_SCREENSHOTS, enabled).apply()
        
        // Synchronize ContentObserver lifecycle immediately
        ScreenshotDetector.isAutoDetectEnabled = enabled
        if (enabled) {
            if (MediaPermissionHelper.isPermissionGranted(appContext)) {
                ScreenshotDetector.start(appContext)
            }
        } else {
            ScreenshotDetector.stop(appContext)
        }
    }

    /**
     * Synchronizes ContentObserver state with saved setting on app launch.
     */
    fun syncAutoDetect(context: Context) {
        val appContext = context.applicationContext
        val isEnabled = isAutoDetectEnabled(appContext)
        ScreenshotDetector.isAutoDetectEnabled = isEnabled

        if (isEnabled && MediaPermissionHelper.isPermissionGranted(appContext)) {
            ScreenshotDetector.start(appContext)
        } else {
            ScreenshotDetector.stop(appContext)
        }
    }
}
