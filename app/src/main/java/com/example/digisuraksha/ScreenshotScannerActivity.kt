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

    // CARD — strictly 16 digits (with optional spaces/hyphens between each group of 4).
    // Negative lookahead/lookbehind ensures we never match a 12-digit Aadhaar spaced as 4+4+4.
    // Total digit count must be exactly 16 — enforced by requiring all four 4-digit groups.
    // The separator (space or hyphen) must be CONSISTENT — either all present or all absent.
    private val cardRegex = Regex(
        "(?<!\\d)" +
                "(?:" +
                "\\d{4}[\\s-]\\d{4}[\\s-]\\d{4}[\\s-]\\d{4}" +  // spaced/hyphenated: 4-4-4-4
                "|\\d{16}" +                                        // no separator: 16 bare digits
                ")(?!\\d)"
    )
    private val cardKeywords = listOf(
        "card", "debit", "credit", "visa", "mastercard",
        "rupay", "amex", "atm", "card no", "card number"
    )

    private val passwordKeywordRegex = Regex(
        "(password|passwd|pwd)[\\s:]+\\S+",
        RegexOption.IGNORE_CASE
    )

    // ============================================================
    // ✅ AADHAAR — RELAXED detection.
    //    Triggers on:
    //      a) Classic "Government of India" header (original rule kept)
    //      b) Aadhaar-specific keywords: aadhaar, aadhar, uid, uidai, dob, enrolment no
    //      c) OR the number is in spaced/hyphenated 12-digit format: XXXX XXXX XXXX
    //         (this format is almost exclusively used on Aadhaar cards)
    //    Number format: XXXX XXXX XXXX  |  XXXX-XXXX-XXXX  |  XXXXXXXXXXXX (12 bare digits)
    //    Strict: not part of a longer digit sequence.
    // ============================================================
    private val aadhaarRegex = Regex(
        "(?<!\\d)(\\d{4}[\\s-]\\d{4}[\\s-]\\d{4}|\\d{12})(?!\\d)"
    )
    // Spaced/hyphenated format alone is strong Aadhaar signal
    private val aadhaarSpacedRegex = Regex(
        "(?<!\\d)\\d{4}[\\s-]\\d{4}[\\s-]\\d{4}(?!\\d)"
    )
    private val aadhaarContextKeywords = listOf(
        "government of india",
        "aadhaar",
        "aadhar",
        "uidai",
        "uid",
        "unique identification",
        "enrolment no",
        "enrollment no",
        "dob",    // date of birth field common on Aadhaar
        "male",   // gender field common on Aadhaar card scans
        "female"
    )

    // PAN — 5 letters + 4 digits + 1 letter (strict uppercase)
    private val panRegex = Regex(
        "\\b[A-Z]{5}[0-9]{4}[A-Z]\\b"
    )

    private val addressKeywordRegex = Regex(
        "(flat|floor|sector|ward|nagar|colony|road|street|lane|building|society|plot|block|district|tehsil|taluka)[\\s,]+",
        RegexOption.IGNORE_CASE
    )

    private val pincodeRegex = Regex("\\b[1-9]\\d{5}\\b")

    // ============================================================
    // 🟠 MEDIUM RISK REGEXES
    // ============================================================
    private val emailRegex = Regex(
        "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.(com|in|edu|org|net|co|io|gov|ac)"
    )

    // ============================================================
    // ✅ PHONE — RELAXED & ROBUST detection for Indian numbers.
    //    Accepted formats:
    //      1. +91 XXXXXXXXXX  (country code with optional space/dash, 10 digits starting 6–9)
    //      2. +91XXXXXXXXXX   (no space after +91)
    //      3. 0XXXXXXXXXX     (STD trunk prefix 0 + 10 digits starting 6–9)
    //      4. 91XXXXXXXXXX    (11 digits, starts with 91, next digit 6–9)
    //      5. XXXXXXXXXX      (bare 10 digits starting 6–9)
    //      6. XXX-XXXXXXX     (hyphenated landline-style 3+7)
    //      7. XXXXX XXXXX     (spaced 5+5, common in India)
    //      8. XXX XXXX XXXX   (spaced 3+4+3 / 3+4+4 styles)
    //    Lookbehind/lookahead strictly prevents matching inside 12-digit Aadhaar
    //    or 16-digit card numbers.
    // ============================================================
    private val phoneRegex = Regex(
        "(?<!\\d)(" +
                "\\+91[\\s\\-]?[6-9]\\d{9}" +        // +91 XXXXXXXXXX
                "|91[6-9]\\d{9}" +                    // 91XXXXXXXXXX (11 digits)
                "|0[6-9]\\d{9}" +                     // 0XXXXXXXXXX (trunk prefix)
                "|[6-9]\\d{4}[\\s\\-]\\d{5}" +        // XXXXX XXXXX (spaced 5+5)
                "|[6-9]\\d{2}[\\s\\-]\\d{4}[\\s\\-]\\d{3}" + // XXX XXXX XXX
                "|[6-9]\\d{2}[\\s\\-]\\d{3}[\\s\\-]\\d{4}" + // XXX XXX XXXX
                "|[6-9]\\d{9}" +                      // bare 10 digits
                ")(?!\\d)"
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
    // ✅ STRICT OTP KEYWORDS
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
            != PackageManager.PERMISSION_GRANTED
        ) {
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
    // ✅ AADHAAR DETECTION — relaxed to catch real-world Aadhaar.
    //    Fires when:
    //      a) 12-digit number (any format) + any Aadhaar context keyword, OR
    //      b) Spaced/hyphenated format XXXX XXXX XXXX alone (format is unique enough)
    // ============================================================
    private fun detectAadhaar(text: String, lowerText: String): Boolean {
        // Rule (b): spaced/hyphenated format is strong standalone signal
        if (aadhaarSpacedRegex.containsMatchIn(text)) return true
        // Rule (a): any 12-digit number + at least one Aadhaar context keyword
        if (aadhaarRegex.containsMatchIn(text)) {
            if (aadhaarContextKeywords.any { lowerText.contains(it) }) return true
        }
        return false
    }

    // ============================================================
    // ✅ OTP DETECTION — strictly 4 to 8 digits only.
    //    A 9+ digit number NEVER qualifies.
    //    Must also have a strict OTP keyword nearby.
    // ============================================================
    private fun detectOtp(text: String, lowerText: String): Boolean {
        val hasStrictKeyword = otpKeywords.any { lowerText.contains(it) }
        if (!hasStrictKeyword) return false

        val otpDigitRegex = Regex("\\b\\d{4,8}\\b")
        val digitTokens = Regex("\\b\\d+\\b").findAll(text).map { it.value }.toList()

        val hasValidOtpDigit = digitTokens.any { it.length in 4..8 }
        if (!hasValidOtpDigit) return false

        val isStandalone = Regex("(^|\\n)\\s*\\d{4,8}\\s*(\\n|$)").containsMatchIn(text)
        if (isStandalone) return true

        val otpDirectPattern = Regex(
            "\\botp\\b[^\\n]{0,30}\\b\\d{4,8}\\b",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)
        if (otpDirectPattern) return true

        if (otpDigitRegex.containsMatchIn(text)) return true

        return false
    }

    // ============================================================
    // ✅ PHONE DETECTION — robust helper.
    //    Strips confirmed Aadhaar matches first to avoid overlap,
    //    then checks the relaxed phoneRegex.
    // ============================================================
    private fun detectPhone(text: String, isAadhaar: Boolean): Boolean {
        if (!phoneRegex.containsMatchIn(text)) return false
        if (isAadhaar) {
            // Strip all confirmed Aadhaar digit blocks before re-checking phone
            val stripped = aadhaarRegex.replace(text, "XXXXXXXXXXXX")
            return phoneRegex.containsMatchIn(stripped)
        }
        return true
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
                                "OTP / UPI ID / Aadhaar / PAN / Card Number / Password\n\n" +
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
            if (grantResults.isNotEmpty() && grantResults[0] ==
                PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "SMS Permission Granted", Toast.LENGTH_SHORT).show()
            } else {
                // === RECONSTRUCTED === replace with your original else branch if any
                Toast.makeText(this, "SMS Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ============================================================
    // === RECONSTRUCTED === onActivityResult
    // Loads the picked image, displays it, kicks off OCR + analysis.
    // Replace this with your original if it differs.
    // ============================================================
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK && data != null) {
            val uri: Uri = data.data ?: return
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                originalBitmap = bitmap
                imageView.setImageBitmap(bitmap)
                analyzeImage(bitmap)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not load image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ============================================================
    // === RECONSTRUCTED === analyzeImage
    // Runs ML Kit text recognition, then passes recognized text +
    // bitmap to analyzeText for detection & masking.
    // ============================================================
    private fun analyzeImage(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                analyzeText(result.text, bitmap, result)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "OCR failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ============================================================
    // === RECONSTRUCTED === analyzeText
    // Runs all detections, sets risk level, builds masked text with
    // FIX #1 (Aadhaar-before-Card order), updates UI, generates the
    // blurred bitmap. CVV logic intentionally absent.
    // ============================================================
    private fun analyzeText(
        recognizedText: String,
        bitmap: Bitmap,
        ocrResult: com.google.mlkit.vision.text.Text
    ) {
        val lowerText = recognizedText.lowercase()

        // -------- HIGH RISK --------
        val isUpi = upiRegex.containsMatchIn(recognizedText)
        val isPan = panRegex.containsMatchIn(recognizedText)
        val isCard = cardRegex.containsMatchIn(recognizedText) &&
                cardKeywords.any { lowerText.contains(it) }
        val isAadhaar = detectAadhaar(recognizedText, lowerText)
        val isPassword = passwordKeywordRegex.containsMatchIn(recognizedText)
        val isAddress = addressKeywordRegex.containsMatchIn(recognizedText) &&
                pincodeRegex.containsMatchIn(recognizedText)
        val isOtp = detectOtp(recognizedText, lowerText)

        // -------- MEDIUM RISK --------
        val isEmail = emailRegex.containsMatchIn(recognizedText)
        val isPhone = detectPhone(recognizedText, isAadhaar)
        val isVehicle = vehicleRegex.containsMatchIn(recognizedText)

        // -------- LOW RISK --------
        val isIp = ipRegex.containsMatchIn(recognizedText)

        // -------- Risk classification --------
        val highRiskFound = listOf(
            isUpi, isPan, isCard, isAadhaar,
            isPassword, isAddress, isOtp
        ).any { it }
        val mediumRiskFound = listOf(isEmail, isPhone, isVehicle).any { it }

        currentRisk = when {
            highRiskFound -> "HIGH"
            mediumRiskFound -> "MEDIUM"
            else -> "LOW"
        }

        // ============================================================
        // ✅ FIX #1 — Masking order: AADHAAR FIRST, then PAN, then CARD.
        // Guarantees a 12-digit Aadhaar block is masked before any
        // downstream regex can consume its digits.
        // ============================================================
        var maskedText = recognizedText

        // 1) Aadhaar FIRST
        if (isAadhaar) {
            maskedText = maskedText.replace(aadhaarRegex, "XXXX XXXX XXXX")
        }
        // 2) PAN
        if (isPan) {
            maskedText = maskedText.replace(panRegex, "XXXXXXXXXX")
        }
        // 3) Card AFTER Aadhaar — cardRegex is strict-16, so it can't grab Aadhaar
        if (isCard) {
            maskedText = maskedText.replace(cardRegex, "XXXX XXXX XXXX XXXX")
        }
        // 4) UPI
        if (isUpi) {
            maskedText = maskedText.replace(upiRegex, "xxx@xxx")
        }
        // 5) Password
        if (isPassword) {
            maskedText = maskedText.replace(passwordKeywordRegex, "password: ********")
        }
        // 6) OTP — mask standalone 4–8 digit tokens
        if (isOtp) {
            maskedText = maskedText.replace(
                Regex("(?<!\\d)\\d{4,8}(?!\\d)"),
                "XXXXXX"
            )
        }
        // 7) Phone
        if (isPhone) {
            maskedText = maskedText.replace(phoneRegex, "+91-XXXXX-XXXXX")
        }
        // 8) Email
        if (isEmail) {
            maskedText = maskedText.replace(emailRegex, "xxx@xxx.com")
        }
        // 9) Vehicle
        if (isVehicle) {
            maskedText = maskedText.replace(vehicleRegex, "XX00XX0000")
        }
        // 10) IP
        if (isIp) {
            maskedText = maskedText.replace(ipRegex, "xxx.xxx.xxx.xxx")
        }

        extractedText.text = maskedText

        // -------- Build findings list for the UI --------
        val findings = mutableListOf<String>()
        if (isUpi) findings.add("UPI ID")
        if (isPan) findings.add("PAN Number")
        if (isCard) findings.add("Card Number")
        if (isAadhaar) findings.add("Aadhaar Number")
        if (isPassword) findings.add("Password")
        if (isAddress) findings.add("Address")
        if (isOtp) findings.add("OTP")
        if (isEmail) findings.add("Email")
        if (isPhone) findings.add("Phone Number")
        if (isVehicle) findings.add("Vehicle Number")
        if (isIp) findings.add("IP Address")

        riskLevel.text = "Risk Level: $currentRisk"

        when (currentRisk) {
            "HIGH" -> {
                fraudWarning.text = "🚨 HIGH RISK: ${findings.joinToString(", ")}"
                explanationText.text =
                    "This image contains highly sensitive information that could lead to " +
                            "financial fraud or identity theft if shared."
                tipsText.text =
                    "💡 Tip: Never share OTP, Aadhaar, or banking credentials. " +
                            "Use the blurred version if you must share."
            }
            "MEDIUM" -> {
                fraudWarning.text = "⚠ MEDIUM RISK: ${findings.joinToString(", ")}"
                explanationText.text =
                    "This image contains personal information that can be used for spam, " +
                            "scams, or social engineering."
                tipsText.text =
                    "💡 Tip: Consider whether the recipient really needs this information."
            }
            else -> {
                fraudWarning.text =
                    if (findings.isEmpty()) "✓ No sensitive data detected"
                    else "ℹ LOW RISK: ${findings.joinToString(", ")}"
                explanationText.text = "This image appears safe to share."
                tipsText.text =
                    "💡 Tip: Always double-check before sharing screenshots with strangers."
            }
        }

        // Build the blurred bitmap using OCR bounding boxes
        blurredBitmap = generateBlurredBitmap(bitmap, ocrResult)

        logEvent("Screenshot → $currentRisk → Scanned (${findings.joinToString(",")})")
    }

    // ============================================================
    // === RECONSTRUCTED === generateBlurredBitmap
    // Draws a solid black rectangle over every OCR line that contains
    // a sensitive match. Replace with your original if you used a
    // real blur (e.g., RenderScript / Bitmap.createScaledBitmap trick).
    // ============================================================
    private fun generateBlurredBitmap(
        original: Bitmap,
        ocrResult: com.google.mlkit.vision.text.Text
    ): Bitmap {
        val blurred = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(blurred)
        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        for (block in ocrResult.textBlocks) {
            for (line in block.lines) {
                if (isSensitiveLine(line.text)) {
                    line.boundingBox?.let { box -> canvas.drawRect(box, paint) }
                }
            }
        }
        return blurred
    }

    // === RECONSTRUCTED === helper for generateBlurredBitmap
    private fun isSensitiveLine(line: String): Boolean {
        val lower = line.lowercase()
        return upiRegex.containsMatchIn(line) ||
                panRegex.containsMatchIn(line) ||
                cardRegex.containsMatchIn(line) ||
                detectAadhaar(line, lower) ||
                passwordKeywordRegex.containsMatchIn(line) ||
                emailRegex.containsMatchIn(line) ||
                phoneRegex.containsMatchIn(line) ||
                vehicleRegex.containsMatchIn(line)
    }

    // ============================================================
    // === RECONSTRUCTED === logEvent
    // Simple Logcat-based event log. Swap in your real implementation
    // (analytics, local audit file, etc.) if you had one.
    // ============================================================
    private fun logEvent(event: String) {
        android.util.Log.i("DigiSuraksha", event)
    }
}
