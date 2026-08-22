package com.example.digisuraksha

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.LayoutInflater
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

/**
 * MediaPermissionHelper handles checking and requesting image/media permissions
 * with a mandatory pre-permission custom consent screen for DigiSuraksha.
 */
object MediaPermissionHelper {

    const val MEDIA_PERMISSION_REQUEST_CODE = 201

    /**
     * Determines the appropriate permission based on SDK version:
     * - Android 13+ (API 33+): READ_MEDIA_IMAGES
     * - Android 12 and below: READ_EXTERNAL_STORAGE
     */
    fun getRequiredPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    /**
     * Checks if the required image/media permission is currently granted.
     */
    fun isPermissionGranted(context: Context): Boolean {
        val permission = getRequiredPermission()
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Displays DigiSuraksha's custom consent dialog before triggering system permission prompt.
     *
     * @param activity Host activity
     * @param onContinue Callback executed when user taps "Continue" to proceed to native dialog
     * @param onCancel Callback executed if user declines consent
     */
    fun showConsentDialog(
        activity: Activity,
        onContinue: () -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_media_consent, null)
        
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnContinue = dialogView.findViewById<Button>(R.id.btnConsentContinue)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnConsentCancel)

        btnContinue.setOnClickListener {
            dialog.dismiss()
            onContinue()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
            onCancel?.invoke()
        }

        dialog.show()
    }

    /**
     * Flow launcher:
     * 1. Checks if permission is already granted. If YES -> calls onAlreadyGranted().
     * 2. If NO -> Displays Custom Consent Dialog.
     *    - User taps Continue -> calls onRequestPermission(permission) to launch native dialog.
     *    - User taps Not Now -> calls onConsentDenied().
     */
    fun requestMediaPermissionWithConsent(
        activity: Activity,
        onAlreadyGranted: () -> Unit,
        onRequestPermission: (permission: String) -> Unit,
        onConsentDenied: (() -> Unit)? = null
    ) {
        if (isPermissionGranted(activity)) {
            onAlreadyGranted()
        } else {
            showConsentDialog(
                activity = activity,
                onContinue = {
                    onRequestPermission(getRequiredPermission())
                },
                onCancel = {
                    onConsentDenied?.invoke()
                }
            )
        }
    }
}
