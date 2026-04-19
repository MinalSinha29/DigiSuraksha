package com.example.digisuraksha

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning

class ScreenshotScannerActivity : AppCompatActivity() {

    private val PICK_IMAGE = 100
    private val SMS_PERMISSION_CODE = 101

    private lateinit var imageView: ImageView
    private lateinit var extractedText: TextView
    private lateinit var riskLevel: TextView
    private lateinit var fraudWarning: TextView
    private lateinit var shareButton: Button
    private lateinit var explanationText: TextView
    private lateinit var tipsText: TextView

    private var blurredBitmap: Bitmap? = null
    private var originalBitmap: Bitmap? = null
    private var currentRisk: String = "LOW"

    // ============================================================
    // 🔴 HIGH RISK REGEXES
    // ============================================================

    private val upiHandles = listOf(
        "okaxis", "okhdfcbank", "okicici", "oksbi",
        "paytm", "ybl", "ibl", "axl", "upi",
        "apl", "rapl", "freecharge", "jiomoney",
        "airtel", "barodampay", "mahb", "idbi",
        "kotak", "indus", "pnb", "federal",
        "centralbank", "unionbank", "pingpay", "relu",
        "timecosmos", "waaxis", "wahdfcbank"
    )

    private val upiRegex = Regex(
        "\\b[\\w.\\-]+@(${upiHandles.joinToString("|")})\\b",
        RegexOption.IGNORE_CASE
    )

    private val cardRegex = Regex(
        "\\b\\d{4}[\\s\\-]?\\d{4}[\\s\\-]?\\d{4}[\\s\\-]?\\d{4}\\b"
    )

    private val cvvKeywordRegex = Regex(
        "(cvv|cvc|security code|card code)[\\s:]*\\d{3}",
        RegexOption.IGNORE_CASE
    )

    private val passwordKeywordRegex = Regex(
        "(password|passwd|pwd)[\\s:]+\\S+",
        RegexOption.IGNORE_CASE
    )

    private val accountKeywordRegex = Regex(
        "(account|a/c|acc|acct)[\\s#:.]*\\d{9,18}",
        RegexOption.IGNORE_CASE
    )

    private val aadhaarRegex = Regex(
        "\\b[2-9]\\d{3}[\\s]?\\d{4}[\\s]?\\d{4}\\b"
    )

    private val panRegex = Regex(
        "\\b[A-Z]{5}[0-9]{4}[A-Z]{1}\\b"
    )

    private val addressKeywordRegex = Regex(
        "(flat|floor|sector|ward|nagar|colony|road|street|lane|building|society|plot|block|district|tehsil|taluka)[\\s,]+",
        RegexOption.IGNORE_CASE
    )

    private val pincodeRegex = Regex("\\b[1-9]\\d{5}\\b")

    private val ifscRegex = Regex("\\b[A-Z]{4}0[A-Z0-9]{6}\\b")

    // ============================================================
    // 🟠 MEDIUM RISK REGEXES
    // ============================================================

    private val emailRegex = Regex(
        "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.(com|in|edu|org|net|co|io|gov|ac)"
    )

    private val phoneRegex = Regex(
        "(\\+91[\\s\\-]?)?[6-9]\\d{4}[\\s\\-]?\\d{5}"
    )

    private val vehicleRegex = Regex(
        "\\b[A-Z]{2}\\d{2}[\\s]?[A-Z]{1,2}[\\s]?\\d{4}\\b"
    )

    // ============================================================
    // 🟢 LOW RISK REGEXES
    // ============================================================

    private val ipRegex = Regex(
        "\\b((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\b"
    )

    // ============================================================
    // ✅ STRICT OTP KEYWORDS — removed generic words like
    // "code", "pin", "payment", "confirm", "secure"
    // that appear on normal UPI/payment screens
    // ============================================================
    private val otpKeywords = listOf(
        "otp",
        "one time",
        "one-time",
        "onetime",
        "passcode",
        "pass code",
        "verification code",
        "verify your",
        "verified via",
        "authenticate",
        "authentication code",
        "login otp",
        "sign in otp",
        "expire",
        "expires in",
        "expiry",
        "valid for",
        "do not share",
        "don't share",
        "never share",
        "your otp",
        "use otp",
        "enter otp",
        "otp for",
        "otp is",
        "otp:"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screenshot_scanner)

        val selectButton = findViewById<Button>(R.id.selectScreenshot)
        imageView = findViewById(R.id.screenshotPreview)
        extractedText = findViewById(R.id.extractedText)
        riskLevel = findViewById(R.id.riskLevel)
        fraudWarning = findViewById(R.id.fraudWarning)
        shareButton = findViewById(R.id.shareSecure)
        explanationText = findViewById(R.id.explanationText)
        tipsText = findViewById(R.id.tipsText)

        if (checkSelfPermission(android.Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.RECEIVE_SMS,
                    android.Manifest.permission.READ_SMS
                ),
                SMS_PERMISSION_CODE
            )
        }

        selectButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, PICK_IMAGE)
        }

        shareButton.setOnClickListener {
            val options = arrayOf(
                "Share Masked Text",
                "Share Blurred Image",
                "⚠ Share Original Image"
            )
            AlertDialog.Builder(this)
                .setTitle("Choose Sharing Option")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            logEvent("Screenshot → $currentRisk → Shared (Masked Text)")
                            val intent = Intent(Intent.ACTION_SEND)
                            intent.type = "text/plain"
                            intent.putExtra(
                                Intent.EXTRA_TEXT,
                                "Scanned using DigiSuraksha\n\n${extractedText.text}\n\n${riskLevel.text}"
                            )
                            startActivity(Intent.createChooser(intent, "Secure Share"))
                        }
                        1 -> {
                            blurredBitmap?.let {
                                logEvent("Screenshot → $currentRisk → Shared (Blurred)")
                                val path = MediaStore.Images.Media.insertImage(
                                    contentResolver, it, "DigiSuraksha_Blurred", null
                                )
                                val uri = Uri.parse(path)
                                val intent = Intent(Intent.ACTION_SEND)
                                intent.type = "image/*"
                                intent.putExtra(Intent.EXTRA_STREAM, uri)
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                startActivity(Intent.createChooser(intent, "Share Blurred Image"))
                            }
                        }
                        2 -> handleOriginalImageShare()
                    }
                }.show()
        }
    }

    // ============================================================
    // ✅ FIXED OTP DETECTION — strict, no false positives
    // ============================================================
    private fun detectOtp(text: String, lowerText: String): Boolean {

        val has4to6Digit = Regex("\\b\\d{4,6}\\b").containsMatchIn(text)
        val hasStrictKeyword = otpKeywords.any { lowerText.contains(it) }

        // Case 1: digit + strict keyword
        if (has4to6Digit && hasStrictKeyword) return true

        // Case 2: standalone 6-digit BUT only with a strict keyword nearby too
        // Removed standalone-only check to avoid false positives like "64%" or time
        val isStandalone6Digit = Regex("(^|\\n)\\s*\\d{6}\\s*(\\n|$)").containsMatchIn(text)
        if (isStandalone6Digit && hasStrictKeyword) return true

        // Case 3: "otp is XXXXXX" or "otp: XXXXXX" pattern
        val otpDirectPattern = Regex(
            "\\botp\\b.{0,20}\\d{4,6}",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)
        if (otpDirectPattern) return true

        // Case 4: "enter otp", "use otp", "submit otp" + digits
        val enterPattern = Regex(
            "\\b(enter otp|submit otp|use otp|input otp)\\s+\\d{4,6}\\b",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)
        if (enterPattern) return true

        return false
    }

    private fun handleOriginalImageShare() {
        val bitmap = originalBitmap ?: run {
            Toast.makeText(this, "Please scan an image first.", Toast.LENGTH_SHORT).show()
            return
        }
        when (currentRisk) {
            "LOW" -> {
                logEvent("Screenshot → LOW → Shared (Original)")
                doShareOriginal(bitmap)
            }
            "MEDIUM" -> {
                AlertDialog.Builder(this)
                    .setTitle("⚠ Heads Up!")
                    .setMessage(
                        "This image contains personal information such as a phone number, " +
                                "email address, or vehicle number.\n\n" +
                                "Are you sure you want to share the original unmasked image?"
                    )
                    .setPositiveButton("Yes, Share") { _, _ ->
                        logEvent("Screenshot → MEDIUM → Shared (Original)")
                        doShareOriginal(bitmap)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            "HIGH" -> {
                AlertDialog.Builder(this)
                    .setTitle("🚨 Serious Risk!")
                    .setMessage(
                        "⛔ This image contains HIGHLY SENSITIVE data such as:\n" +
                                "OTP / UPI ID / Aadhaar / PAN / Card Number / Account Number\n\n" +
                                "Sharing this can lead to:\n" +
                                "• Bank account fraud\n" +
                                "• Unauthorized UPI transactions\n" +
                                "• Identity theft\n" +
                                "• Financial loss\n\n" +
                                "We strongly recommend sharing the blurred version instead.\n\n" +
                                "Do you still want to share the original?"
                    )
                    .setPositiveButton("I Understand, Share Anyway") { _, _ ->
                        logEvent("Screenshot → HIGH → Shared (Original)")
                        doShareOriginal(bitmap)
                    }
                    .setNegativeButton("Cancel — Keep Me Safe", null)
                    .show()
            }
        }
    }

    private fun doShareOriginal(bitmap: Bitmap) {
        val path = MediaStore.Images.Media.insertImage(
            contentResolver, bitmap, "DigiSuraksha_Original", null
        )
        val uri = Uri.parse(path)
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Share Original Image"))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "SMS Permission Granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "SMS Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK) {

            val imageUri = data?.data ?: return
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
            originalBitmap = bitmap

            val image = InputImage.fromBitmap(bitmap, 0)
            val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val barcodeScanner = BarcodeScanning.getClient()

            val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBitmap)

            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->

                    val resultText = visionText.text
                    val lowerText = resultText.lowercase()

                    val isFraud =
                        (lowerText.contains("won") || lowerText.contains("prize") ||
                                lowerText.contains("lottery")) &&
                                (lowerText.contains("click") || lowerText.contains("claim") ||
                                        lowerText.contains("urgent"))
                    fraudWarning.text = if (isFraud) "⚠ Possible Fraud Detected" else ""

                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            for (element in line.elements) {
                                val text = element.text
                                val box = element.boundingBox ?: continue

                                val isSensitive =
                                    upiRegex.containsMatchIn(text) ||
                                            cardRegex.containsMatchIn(text) ||
                                            cvvKeywordRegex.containsMatchIn(text) ||
                                            passwordKeywordRegex.containsMatchIn(text) ||
                                            accountKeywordRegex.containsMatchIn(text) ||
                                            aadhaarRegex.containsMatchIn(text) ||
                                            panRegex.containsMatchIn(text) ||
                                            ifscRegex.containsMatchIn(text) ||
                                            addressKeywordRegex.containsMatchIn(text) ||
                                            pincodeRegex.containsMatchIn(text) ||
                                            phoneRegex.containsMatchIn(text) ||
                                            emailRegex.containsMatchIn(text) ||
                                            vehicleRegex.containsMatchIn(text) ||
                                            ipRegex.containsMatchIn(text)

                                if (isSensitive) blurRegion(canvas, mutableBitmap, box)
                            }
                        }
                    }

                    barcodeScanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            for (barcode in barcodes ?: emptyList()) {
                                val box = barcode.boundingBox ?: continue
                                blurRegion(canvas, mutableBitmap, box)
                            }
                            imageView.setImageBitmap(mutableBitmap)
                            blurredBitmap = mutableBitmap
                        }

                    // Mask text
                    var maskedText = resultText
                    maskedText = maskedText.replace(upiRegex, "[UPI ID HIDDEN]")
                    maskedText = maskedText.replace(cardRegex, "[CARD NUMBER HIDDEN]")
                    maskedText = maskedText.replace(cvvKeywordRegex, "[CVV HIDDEN]")
                    maskedText = maskedText.replace(passwordKeywordRegex, "[PASSWORD HIDDEN]")
                    maskedText = maskedText.replace(accountKeywordRegex, "[ACCOUNT NUMBER HIDDEN]")
                    maskedText = maskedText.replace(aadhaarRegex, "[AADHAAR HIDDEN]")
                    maskedText = maskedText.replace(panRegex, "[PAN HIDDEN]")
                    maskedText = maskedText.replace(ifscRegex, "[IFSC HIDDEN]")
                    maskedText = maskedText.replace(emailRegex, "[EMAIL HIDDEN]")
                    maskedText = maskedText.replace(phoneRegex, "[PHONE HIDDEN]")
                    maskedText = maskedText.replace(vehicleRegex, "[VEHICLE NO. HIDDEN]")
                    maskedText = maskedText.replace(pincodeRegex, "[PINCODE HIDDEN]")
                    maskedText = maskedText.replace(ipRegex, "[IP HIDDEN]")

                    extractedText.text = maskedText

                    // ============================================================
                    // RISK DETECTION
                    // ============================================================
                    val isOtp = detectOtp(resultText, lowerText)
                    val isUpi = upiRegex.containsMatchIn(resultText)
                    val isCard = cardRegex.containsMatchIn(resultText)
                    val isCvv = cvvKeywordRegex.containsMatchIn(resultText)
                    val isPassword = passwordKeywordRegex.containsMatchIn(resultText)
                    val isAccount = accountKeywordRegex.containsMatchIn(resultText)
                    val isAadhaar = aadhaarRegex.containsMatchIn(resultText)
                    val isPan = panRegex.containsMatchIn(resultText)
                    val isIfsc = ifscRegex.containsMatchIn(resultText)
                    val isAddress = addressKeywordRegex.containsMatchIn(resultText) ||
                            pincodeRegex.containsMatchIn(resultText)
                    val isPhone = phoneRegex.containsMatchIn(resultText)
                    val isEmail = emailRegex.containsMatchIn(resultText)
                    val isVehicle = vehicleRegex.containsMatchIn(resultText)
                    val isIp = ipRegex.containsMatchIn(resultText)

                    val risk = when {
                        isOtp || isUpi || isCard || isCvv || isPassword ||
                                isAccount || isAadhaar || isPan || isIfsc || isAddress -> "HIGH"
                        isPhone || isEmail || isVehicle -> "MEDIUM"
                        isIp -> "LOW"
                        else -> "LOW"
                    }

                    currentRisk = risk
                    riskLevel.text = "Risk Level: $risk"
                    when (risk) {
                        "HIGH" -> riskLevel.setTextColor(getColor(android.R.color.holo_red_dark))
                        "MEDIUM" -> riskLevel.setTextColor(getColor(android.R.color.holo_orange_dark))
                        "LOW" -> riskLevel.setTextColor(getColor(android.R.color.holo_green_dark))
                    }

                    explanationText.text = generateExplanation(resultText, lowerText)
                    tipsText.text = generateSafetyTips(resultText, lowerText)
                }
        }
    }

    // ============================================================
    // EXPLANATION
    // ============================================================
    private fun generateExplanation(text: String, lower: String): String {
        val reasons = mutableListOf<String>()

        if (detectOtp(text, lower))
            reasons.add("🔴 OTP detected — extremely sensitive")
        if (upiRegex.containsMatchIn(text))
            reasons.add("🔴 UPI ID detected — direct financial risk")
        if (cardRegex.containsMatchIn(text))
            reasons.add("🔴 Card number detected — banking fraud risk")
        if (cvvKeywordRegex.containsMatchIn(text))
            reasons.add("🔴 CVV detected — card can be misused")
        if (passwordKeywordRegex.containsMatchIn(text))
            reasons.add("🔴 Password detected — account takeover risk")
        if (accountKeywordRegex.containsMatchIn(text))
            reasons.add("🔴 Bank account number detected")
        if (aadhaarRegex.containsMatchIn(text))
            reasons.add("🔴 Aadhaar number detected — identity theft risk")
        if (panRegex.containsMatchIn(text))
            reasons.add("🔴 PAN card detected — financial identity risk")
        if (ifscRegex.containsMatchIn(text))
            reasons.add("🔴 IFSC code detected — bank details exposed")
        if (addressKeywordRegex.containsMatchIn(text) || pincodeRegex.containsMatchIn(text))
            reasons.add("🔴 Address / Pincode detected — location privacy risk")
        if (phoneRegex.containsMatchIn(text))
            reasons.add("🟠 Phone number detected")
        if (emailRegex.containsMatchIn(text))
            reasons.add("🟠 Email address detected")
        if (vehicleRegex.containsMatchIn(text))
            reasons.add("🟠 Vehicle number detected")
        if (ipRegex.containsMatchIn(text))
            reasons.add("🟢 IP address detected — minor privacy concern")

        return if (reasons.isNotEmpty())
            "⚠ Sensitive data found:\n\n• ${reasons.joinToString("\n• ")}"
        else
            "✅ No sensitive data detected."
    }

    // ============================================================
    // SAFETY TIPS
    // ============================================================
    private fun generateSafetyTips(text: String, lower: String): String {
        val tips = mutableListOf<String>()

        if (detectOtp(text, lower))
            tips.add("Never share OTPs — they can be misused instantly.")
        if (upiRegex.containsMatchIn(text))
            tips.add("UPI IDs in screenshots can be used for fraud. Use blurred version.")
        if (cardRegex.containsMatchIn(text))
            tips.add("Never share card numbers. Use virtual cards for online transactions.")
        if (cvvKeywordRegex.containsMatchIn(text))
            tips.add("CVV is secret. No bank or app will ever ask for it.")
        if (passwordKeywordRegex.containsMatchIn(text))
            tips.add("Never screenshot passwords. Use a password manager instead.")
        if (accountKeywordRegex.containsMatchIn(text))
            tips.add("Bank account numbers should never be shared in screenshots.")
        if (aadhaarRegex.containsMatchIn(text))
            tips.add("Share only masked Aadhaar (last 4 digits). Use DigiLocker for verification.")
        if (panRegex.containsMatchIn(text))
            tips.add("PAN misuse can lead to financial fraud in your name.")
        if (ifscRegex.containsMatchIn(text))
            tips.add("IFSC + account number together can enable unauthorized transfers.")
        if (addressKeywordRegex.containsMatchIn(text) || pincodeRegex.containsMatchIn(text))
            tips.add("Sharing your address publicly can compromise your physical safety.")
        if (phoneRegex.containsMatchIn(text))
            tips.add("Avoid sharing phone numbers — can lead to spam or SIM swap attacks.")
        if (emailRegex.containsMatchIn(text))
            tips.add("Email addresses can be used for phishing attacks.")
        if (vehicleRegex.containsMatchIn(text))
            tips.add("Vehicle numbers can be used to track your location or identity.")
        if (ipRegex.containsMatchIn(text))
            tips.add("IP addresses can reveal your approximate location.")

        return if (tips.isNotEmpty())
            "🛡 Safety Tips:\n\n• ${tips.joinToString("\n• ")}"
        else ""
    }

    // ============================================================
    // BLUR HELPERS
    // ============================================================
    private fun blurRegion(canvas: Canvas, bitmap: Bitmap, box: Rect) {
        val padding = 20
        val left = (box.left - padding).coerceAtLeast(0)
        val top = (box.top - padding).coerceAtLeast(0)
        val right = (box.right + padding).coerceAtMost(bitmap.width)
        val bottom = (box.bottom + padding).coerceAtMost(bitmap.height)

        val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        val blurred = strongBlur(cropped)
        canvas.drawBitmap(blurred, left.toFloat(), top.toFloat(), null)
    }

    private fun strongBlur(bitmap: Bitmap): Bitmap {
        var temp = bitmap
        repeat(3) {
            val small = Bitmap.createScaledBitmap(temp, 8, 8, true)
            temp = Bitmap.createScaledBitmap(small, bitmap.width, bitmap.height, true)
        }
        return temp
    }

    // ============================================================
    // LOGGING
    // ============================================================
    private fun logEvent(event: String) {
        val prefs = getSharedPreferences("logs", MODE_PRIVATE)
        val oldLogs = prefs.getString("data", "") ?: ""
        val newLog = oldLogs + "\n" + getCurrentTime() + " : " + event
        prefs.edit().putString("data", newLog).apply()
    }

    private fun getCurrentTime(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }
}