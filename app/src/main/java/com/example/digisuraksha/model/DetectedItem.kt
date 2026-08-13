package com.example.digisuraksha.model

import android.graphics.Rect

data class DetectedItem(
    val type: String,       // e.g. "AADHAAR", "PHONE", "EMAIL", "UPI_ID", "OTP", "CARD"
    val value: String,      // the actual matched text
    val riskLevel: String,  // "HIGH", "MEDIUM", "LOW"
    val reason: String,     // human-readable explanation
    val boundingBox: Rect? = null // position on image, for blur/mask overlay
)