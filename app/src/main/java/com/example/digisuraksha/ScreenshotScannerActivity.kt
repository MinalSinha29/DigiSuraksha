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
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream

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
        "(?<!\\d)" +
                "(?:" +
                "\\d{4}[\\s-]\\d{4}[\\s-]\\d{4}[\\s-]\\d{4}" +
                "|\\d{16}" +
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

    private val aadhaarRegex = Regex(
        "(?<!\\d)(\\d{4}[\\s-]\\d{4}[\\s-]\\d{4}|\\d{12})(?!\\d)"
    )
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
        "dob",
        "male",
        "female"
    )

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

    private val phoneRegex = Regex(
        "(?<!\\d)(" +
                "\\+91[\\s\\-]?[6-9]\\d{9}" +
                "|91[6-9]\\d{9}" +
                "|0[6-9]\\d{9}" +
                "|[6-9]\\d{4}[\\s\\-]\\d{5}" +
                "|[6-9]\\d{2}[\\s\\-]\\d{4}[\\s\\-]\\d{3}" +
                "|[6-9]\\d{2}[\\s\\-]\\d{3}[\\s\\-]\\d{4}" +
                "|[6-9]\\d{9}" +
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
        "single-use code",
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

    // ============================================================
    // 🆕 FRAUD / SCAM MESSAGE DETECTION
    // Detects common patterns used in SMS/message-based fraud:
    //   - Prize / lottery / lucky winner scams
    //   - KYC / account suspension urgency scams
    //   - Fake refund / cashback scams
    //   - Impersonation of banks / govt agencies
    //   - Phishing links
    //   - Job offer / investment fraud
    //   - Collect money / send money urgency
    // ============================================================

    // High-confidence fraud phrase patterns (any ONE of these alone = HIGH risk)
    private val fraudHighPhrases = listOf(
        Regex("you (have |'ve )?(won|win|winning)", RegexOption.IGNORE_CASE),
        Regex("congratulations.*?(won|prize|reward|lucky)", RegexOption.IGNORE_CASE),
        Regex("lucky (winner|draw|customer)", RegexOption.IGNORE_CASE),
        Regex("claim (your |the )?(prize|reward|money|amount|cash)", RegexOption.IGNORE_CASE),
        Regex("prize (money|amount|of rs|of ₹)", RegexOption.IGNORE_CASE),
        Regex("(rs\\.?|₹)\\s*\\d[\\d,]*(\\s*(lakh|crore|thousand|prize|won|reward))", RegexOption.IGNORE_CASE),
        Regex("send (rs\\.?|₹|money|amount|otp|upi)", RegexOption.IGNORE_CASE),
        Regex("transfer (rs\\.?|₹|money|amount) (to|into)", RegexOption.IGNORE_CASE),
        Regex("your (account|kyc|sim|number) (will be|is|has been) (blocked|suspended|deactivated|disabled)", RegexOption.IGNORE_CASE),
        Regex("(kyc|account|sim).{0,30}(expire|block|suspend|deactivat)", RegexOption.IGNORE_CASE),
        Regex("update (your )?(kyc|aadhaar|pan|account|details) (immediately|now|urgently|today)", RegexOption.IGNORE_CASE),
        Regex("your (aadhaar|pan|kyc|bank account).{0,30}(link|verify|update).{0,20}(immediately|now|urgent|today|or)", RegexOption.IGNORE_CASE),
        Regex("(income tax|it department|cbdt).{0,40}(refund|notice|arrest|action)", RegexOption.IGNORE_CASE),
        Regex("(cci|sebi|rbi|trai|uidai|npci).{0,30}(block|suspend|action|notice|deactivate)", RegexOption.IGNORE_CASE),
        Regex("(arrested|arrest warrant|fir|cybercrime).{0,30}(your name|against you)", RegexOption.IGNORE_CASE),
        Regex("click (here|this link|now|below).{0,30}(claim|verify|activate|get)", RegexOption.IGNORE_CASE),
        Regex("(bit\\.ly|tinyurl|t\\.co|short\\.url|cutt\\.ly|ow\\.ly)/\\S+", RegexOption.IGNORE_CASE),
        Regex("work from home.{0,30}(earn|₹|rs|income|daily|weekly)", RegexOption.IGNORE_CASE),
        Regex("earn (₹|rs\\.?)?\\s*\\d+.{0,20}(per day|daily|per week|weekly|from home)", RegexOption.IGNORE_CASE),
        Regex("(investment|invest).{0,30}(double|triple|guaranteed|profit|return)", RegexOption.IGNORE_CASE),
        Regex("guaranteed (return|profit|income|interest)", RegexOption.IGNORE_CASE),
        Regex("pay (small|a small|rs|₹).{0,20}(fee|charge|processing|registration).{0,20}(get|receive|claim|collect)", RegexOption.IGNORE_CASE),
        Regex("refund of (rs\\.?|₹)\\s*\\d+.{0,30}(credited|process|sent|transfer)", RegexOption.IGNORE_CASE),
        Regex("cashback of (rs\\.?|₹)\\s*\\d+.{0,30}(click|claim|tap|link)", RegexOption.IGNORE_CASE),
        Regex("your (electricity|power|gas|water).{0,30}(cut|disconnect|suspend).{0,30}(pay|payment|immediately)", RegexOption.IGNORE_CASE),
        Regex("(sbi|hdfc|icici|axis|kotak|pnb|bob).{0,30}(block|suspend|deactivat|alert|urgent)", RegexOption.IGNORE_CASE),
        Regex("(dear|hi|hello).{0,20}(customer|user|sir|madam).{0,40}(won|prize|reward|lucky|selected)", RegexOption.IGNORE_CASE),
        Regex("(rupees|rs|₹).{0,10}\\d[\\d,]+.{0,20}(won|prize|reward|gift|waiting)", RegexOption.IGNORE_CASE),
        Regex("scan (this |the )?(qr|code).{0,30}(get|receive|claim|collect|pay)", RegexOption.IGNORE_CASE)
    )

    // Medium-confidence fraud signals (need 2+ to trigger MEDIUM fraud flag)
    private val fraudMediumSignals = listOf(
        Regex("(urgent|urgently|immediately|asap)", RegexOption.IGNORE_CASE),
        Regex("do not (ignore|delay|miss)", RegexOption.IGNORE_CASE),
        Regex("limited (time|offer|period)", RegexOption.IGNORE_CASE),
        Regex("act (now|fast|immediately|today)", RegexOption.IGNORE_CASE),
        Regex("free (gift|offer|reward|recharge|data)", RegexOption.IGNORE_CASE),
        Regex("(selected|chosen|eligible).{0,30}(you|your number|your account)", RegexOption.IGNORE_CASE),
        Regex("(lottery|lucky draw|bumper prize|mega prize)", RegexOption.IGNORE_CASE),
        Regex("(customer care|helpline).{0,20}(\\d{10}|\\+91)", RegexOption.IGNORE_CASE),
        Regex("(whatsapp|telegram|call).{0,20}(us|now|immediately|for details)", RegexOption.IGNORE_CASE),
        Regex("(google pay|phonepe|paytm|bhim).{0,30}(send|transfer|pay|receive)", RegexOption.IGNORE_CASE)
    )

    // Detected fraud findings (populated per scan)
    private var fraudFindings = mutableListOf<String>()
    private var isFraudHigh = false
    private var isFraudMedium = false

    // ============================================================
    // 🆕 UPI QR CODE DETECTION
    // Scans the image for QR codes using ML Kit Barcode Scanning.
    // If a QR encodes a UPI payment URI (upi://pay?...) it is
    // flagged as HIGH risk — sharing such a QR can allow others
    // to initiate payments on your behalf.
    // ============================================================
    private var detectedUpiQrPayee: String? = null   // payee name from QR if found
    private var detectedUpiQrAmount: String? = null  // pre-set amount from QR if found
    private var isUpiQrDetected = false

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
                                val uri = getImageUri(it, "DigiSuraksha_Blurred")
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
    // ✅ AADHAAR DETECTION
    // ============================================================
    private fun detectAadhaar(text: String, lowerText: String): Boolean {
        if (aadhaarSpacedRegex.containsMatchIn(text)) return true
        if (aadhaarRegex.containsMatchIn(text)) {
            if (aadhaarContextKeywords.any { lowerText.contains(it) }) return true
        }
        return false
    }

    // ============================================================
    // ✅ OTP DETECTION
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
    // ✅ PHONE DETECTION
    // ============================================================
    private fun detectPhone(text: String, isAadhaar: Boolean): Boolean {
        if (!phoneRegex.containsMatchIn(text)) return false
        if (isAadhaar) {
            val stripped = aadhaarRegex.replace(text, "XXXXXXXXXXXX")
            return phoneRegex.containsMatchIn(stripped)
        }
        return true
    }

    // ============================================================
    // 🆕 FRAUD MESSAGE DETECTION
    // Returns true if the text contains HIGH-confidence fraud signals.
    // Populates fraudFindings, isFraudHigh, isFraudMedium as side effects.
    // ============================================================
    private fun detectFraud(text: String): Boolean {
        fraudFindings.clear()
        isFraudHigh = false
        isFraudMedium = false

        // Check high-confidence fraud phrases (one match = HIGH)
        for (pattern in fraudHighPhrases) {
            if (pattern.containsMatchIn(text)) {
                isFraudHigh = true
                // Extract a short label from the pattern for the findings list
                val matchValue = pattern.find(text)?.value?.take(40) ?: ""
                if (matchValue.isNotEmpty() && !fraudFindings.contains("Scam Message")) {
                    fraudFindings.add("Scam Message")
                }
            }
        }

        // Check medium-confidence signals (need 2+ to flag)
        val mediumHits = fraudMediumSignals.count { it.containsMatchIn(text) }
        if (mediumHits >= 2) {
            isFraudMedium = true
            if (!fraudFindings.contains("Suspicious Message")) {
                fraudFindings.add("Suspicious Message")
            }
        }

        return isFraudHigh
    }

    // ============================================================
    // 🆕 UPI QR CODE DETECTION
    // Uses ML Kit BarcodeScanning to find QR codes in the bitmap.
    // Parses UPI payment URIs of the form:
    //   upi://pay?pa=VPA&pn=Name&am=Amount&...
    // Calls back with result via a lambda to fit async ML Kit flow.
    // ============================================================
    private fun detectUpiQrCode(bitmap: Bitmap, onResult: (Boolean) -> Unit) {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner: BarcodeScanner = BarcodeScanning.getClient(options)
        val image = InputImage.fromBitmap(bitmap, 0)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                isUpiQrDetected = false
                detectedUpiQrPayee = null
                detectedUpiQrAmount = null

                for (barcode in barcodes) {
                    val rawValue = barcode.rawValue ?: continue
                    val lower = rawValue.lowercase()

                    // UPI deep-link: upi://pay?pa=...
                    if (lower.startsWith("upi://")) {
                        isUpiQrDetected = true
                        // Parse payee name (pn) and amount (am) if present
                        val uri = Uri.parse(rawValue)
                        detectedUpiQrPayee = uri.getQueryParameter("pn")
                            ?: uri.getQueryParameter("pa")
                        detectedUpiQrAmount = uri.getQueryParameter("am")
                        logEvent("UPI QR detected → payee=$detectedUpiQrPayee amount=$detectedUpiQrAmount")
                        break
                    }

                    // Some QRs embed UPI handle text directly (e.g. "name@upihandle")
                    if (upiRegex.containsMatchIn(rawValue)) {
                        isUpiQrDetected = true
                        detectedUpiQrPayee = rawValue.trim()
                        logEvent("UPI handle QR detected → $rawValue")
                        break
                    }
                }
                onResult(isUpiQrDetected)
            }
            .addOnFailureListener {
                isUpiQrDetected = false
                onResult(false)
            }
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
                // Build a context-aware warning message
                val warningDetails = buildString {
                    append("⛔ This image contains HIGHLY SENSITIVE data:\n")
                    if (isUpiQrDetected) {
                        append("• UPI QR Code")
                        detectedUpiQrPayee?.let { append(" (Payee: $it)") }
                        detectedUpiQrAmount?.let { append(" ₹$it") }
                        append("\n")
                    }
                    if (isFraudHigh) {
                        append("• Possible Scam / Fraud Message\n")
                    }
                    append(
                        "\nSharing this can lead to:\n" +
                                "• Bank account fraud\n" +
                                "• Unauthorized UPI transactions\n" +
                                "• Identity theft\n" +
                                "• Financial loss\n\n" +
                                "We strongly recommend sharing the blurred version instead.\n\n" +
                                "Do you still want to share the original?"
                    )
                }

                AlertDialog.Builder(this)
                    .setTitle("🚨 Serious Risk!")
                    .setMessage(warningDetails)
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

        try {

            val uri = getImageUri(bitmap, "DigiSuraksha_Original")

            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "image/*"
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            startActivity(
                Intent.createChooser(intent, "Share Original Image")
            )

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "Failed to share image: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun getImageUri(bitmap: Bitmap, fileName: String): Uri {
        val file = File(cacheDir, "$fileName.png")

        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.flush()
        stream.close()

        return androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.provider",
            file
        )
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
                Toast.makeText(this, "SMS Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK && data != null) {
            val uri: Uri = data.data ?: return
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                originalBitmap = bitmap
                imageView.setImageBitmap(bitmap)

                // 🆕 Run UPI QR detection FIRST (async), then run OCR + analysis
                detectUpiQrCode(bitmap) { qrFound ->
                    runOnUiThread {
                        analyzeImage(bitmap)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Could not load image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

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

        // 🆕 Fraud / scam detection
        val isFraudMessage = detectFraud(recognizedText)

        // -------- MEDIUM RISK --------
        val isEmail = emailRegex.containsMatchIn(recognizedText)
        val isPhone = detectPhone(recognizedText, isAadhaar)
        val isVehicle = vehicleRegex.containsMatchIn(recognizedText)

        // -------- LOW RISK --------
        val isIp = ipRegex.containsMatchIn(recognizedText)

        // -------- Risk classification --------
        // isUpiQrDetected is already set by detectUpiQrCode() called before analyzeImage()
        val highRiskFound = listOf(
            isUpi, isPan, isCard, isAadhaar,
            isPassword, isAddress, isOtp,
            isFraudMessage,       // 🆕 fraud = HIGH
            isUpiQrDetected       // 🆕 UPI QR = HIGH
        ).any { it }

        // Medium fraud (suspicious but not definitive) bumps to at least MEDIUM
        val mediumRiskFound = listOf(
            isEmail, isPhone, isVehicle,
            isFraudMedium         // 🆕 suspicious message = MEDIUM
        ).any { it }

        currentRisk = when {
            highRiskFound -> "HIGH"
            mediumRiskFound -> "MEDIUM"
            else -> "LOW"
        }

        // ============================================================
        // Masking order: AADHAAR FIRST, then PAN, then CARD
        // ============================================================
        var maskedText = recognizedText

        if (isAadhaar) {
            maskedText = maskedText.replace(aadhaarRegex, "XXXX XXXX XXXX")
        }
        if (isPan) {
            maskedText = maskedText.replace(panRegex, "XXXXXXXXXX")
        }
        if (isCard) {
            maskedText = maskedText.replace(cardRegex, "XXXX XXXX XXXX XXXX")
        }
        if (isUpi) {
            maskedText = maskedText.replace(upiRegex, "xxx@xxx")
        }
        if (isPassword) {
            maskedText = maskedText.replace(passwordKeywordRegex, "password: ********")
        }
        if (isOtp) {
            maskedText = maskedText.replace(
                Regex("(?<!\\d)\\d{4,8}(?!\\d)"),
                "XXXXXX"
            )
        }
        if (isPhone) {
            maskedText = maskedText.replace(phoneRegex, "+91-XXXXX-XXXXX")
        }
        if (isEmail) {
            maskedText = maskedText.replace(emailRegex, "xxx@xxx.com")
        }
        if (isVehicle) {
            maskedText = maskedText.replace(vehicleRegex, "XX00XX0000")
        }
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
        // 🆕 New findings
        if (isUpiQrDetected) {
            val qrLabel = buildString {
                append("UPI QR Code")
                detectedUpiQrPayee?.let { append(" ($it)") }
                detectedUpiQrAmount?.let { append(" ₹$it") }
            }
            findings.add(qrLabel)
        }
        findings.addAll(fraudFindings)  // adds "Scam Message" / "Suspicious Message"

        riskLevel.text = "Risk Level: $currentRisk"

        when (currentRisk) {
            "HIGH" -> {
                fraudWarning.text = "🚨 HIGH RISK: ${findings.joinToString(", ")}"

                val explanationBuilder = StringBuilder(
                    "This image contains highly sensitive information that could lead to " +
                            "financial fraud or identity theft if shared."
                )
                // 🆕 Contextual explanation for UPI QR
                if (isUpiQrDetected) {
                    explanationBuilder.append(
                        "\n\n⚠ UPI QR Code detected: Sharing this QR allows anyone to " +
                                "initiate a payment request to this UPI address."
                    )
                    detectedUpiQrAmount?.let {
                        explanationBuilder.append(" Pre-set amount: ₹$it.")
                    }
                }
                // 🆕 Contextual explanation for fraud/scam
                if (isFraudHigh) {
                    explanationBuilder.append(
                        "\n\n🚫 Scam Alert: This message shows signs of a fraud/scam " +
                                "(e.g. fake prizes, KYC threats, phishing links). " +
                                "Do NOT act on it. Do NOT click any links or call any numbers in it."
                    )
                }
                explanationText.text = explanationBuilder.toString()

                tipsText.text = buildString {
                    append("💡 Tips:\n")
                    if (isUpiQrDetected) {
                        append("• Never scan or share UPI QR codes from unknown sources.\n")
                        append("• Scammers send QR codes asking you to SCAN to receive money — this is a lie; scanning a QR always sends money, never receives it.\n")
                    }
                    if (isFraudHigh) {
                        append("• Legitimate companies / banks / govt agencies NEVER ask for OTP, card details, or advance fees via SMS.\n")
                        append("• Report fraud messages to cybercrime.gov.in or call 1930.\n")
                    }
                    if (!isUpiQrDetected && !isFraudHigh) {
                        append("• Never share OTP, Aadhaar, or banking credentials. Use the blurred version if you must share.")
                    }
                }
            }
            "MEDIUM" -> {
                fraudWarning.text = "⚠ MEDIUM RISK: ${findings.joinToString(", ")}"

                val explanationBuilder = StringBuilder(
                    "This image contains personal information that can be used for spam, " +
                            "scams, or social engineering."
                )
                if (isFraudMedium && !isFraudHigh) {
                    explanationBuilder.append(
                        "\n\n⚠ Suspicious Message: This message contains some warning signs " +
                                "(urgency language, free offers, etc.). Be cautious."
                    )
                }
                explanationText.text = explanationBuilder.toString()

                tipsText.text = buildString {
                    append("💡 Tip: Consider whether the recipient really needs this information.")
                    if (isFraudMedium) {
                        append("\n• Be wary of messages creating urgency or offering freebies — these are common manipulation tactics.")
                    }
                }
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

        blurredBitmap = generateBlurredBitmap(bitmap, ocrResult)

        logEvent("Screenshot → $currentRisk → Scanned (${findings.joinToString(",")})")
    }

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

    private fun isSensitiveLine(line: String): Boolean {
        val lower = line.lowercase()
        return upiRegex.containsMatchIn(line) ||
                panRegex.containsMatchIn(line) ||
                cardRegex.containsMatchIn(line) ||
                detectAadhaar(line, lower) ||
                passwordKeywordRegex.containsMatchIn(line) ||
                emailRegex.containsMatchIn(line) ||
                phoneRegex.containsMatchIn(line) ||
                vehicleRegex.containsMatchIn(line) ||
                // 🆕 Also redact lines that are part of a fraud message
                fraudHighPhrases.any { it.containsMatchIn(line) }
    }

    private fun logEvent(event: String) {
        android.util.Log.i("DigiSuraksha", event)
    }
}








