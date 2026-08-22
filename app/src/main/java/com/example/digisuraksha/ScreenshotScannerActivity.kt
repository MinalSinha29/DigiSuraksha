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
    private lateinit var findingsCheckboxContainer: LinearLayout

    private var blurredBitmap: Bitmap? = null
    private var originalBitmap: Bitmap? = null
    private var latestOcrResult: com.google.mlkit.vision.text.Text? = null
    private var latestRecognizedText: String? = null
    private var currentRisk: String = "LOW"
    // Person 3: stores fields excluded from redaction
    private val excludedTypes = mutableSetOf<String>()

    companion object {
        // ============================================================
        // 🔴 HIGH RISK REGEXES
        // ============================================================
        internal val upiHandles = listOf(
            "okaxis", "okhdfcbank", "okicici", "oksbi",
            "paytm", "ybl", "ibl", "axl", "upi",
            "apl", "rapl", "freecharge", "jiomoney",
            "airtel", "barodampay", "mahb", "idbi",
            "kotak", "indus", "pnb", "federal",
            "centralbank", "unionbank", "pingpay", "relu",
            "timecosmos", "waaxis", "wahdfcbank"
        )

        internal val upiRegex = Regex(
            "\\b[\\w.\\-]+@(${upiHandles.joinToString("|")})(?!\\.[a-zA-Z])\\b",
            RegexOption.IGNORE_CASE
        )

        internal val cardRegex = Regex(
            "(?<!\\d)" +
                    "(?:" +
                    "\\d{4}[\\s-]\\d{4}[\\s-]\\d{4}[\\s-]\\d{4}" +
                    "|\\d{16}" +
                    ")(?!\\d)"
        )
        internal val cardKeywords = listOf(
            "card", "debit", "credit", "visa", "mastercard",
            "rupay", "amex", "atm", "card no", "card number"
        )

        internal val passwordKeywordRegex = Regex(
            "(password|passwd|pwd)[\\s:]+\\S+",
            RegexOption.IGNORE_CASE
        )

        internal val aadhaarRegex = Regex(
            "(?<!\\d)(\\d{4}[\\s-]+\\d{4}[\\s-]+\\d{4}(?![\\s-]*\\d)|\\d{12}(?!\\d))"
        )
        internal val aadhaarSpacedRegex = Regex(
            "(?<!\\d)\\d{4}[\\s-]+\\d{4}[\\s-]+\\d{4}(?![\\s-]*\\d)"
        )
        internal val aadhaarContextKeywords = listOf(
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

        internal val panRegex = Regex(
            "\\b[A-Z]{5}[0-9]{4}[A-Z]\\b"
        )

        internal val addressKeywordRegex = Regex(
            "(flat|floor|sector|ward|nagar|colony|road|street|lane|building|society|plot|block|district|tehsil|taluka)[\\s,]+",
            RegexOption.IGNORE_CASE
        )

        internal val pincodeRegex = Regex("\\b[1-9]\\d{5}\\b")

        // ============================================================
        // 🟠 MEDIUM RISK REGEXES
        // ============================================================
        internal val emailRegex = Regex(
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.(com|in|edu|org|net|co|io|gov|ac)"
        )

        internal val phoneRegex = Regex(
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

        internal val vehicleRegex = Regex(
            "\\b[A-Z]{2}\\d{2}[\\s]?[A-Z]{1,2}[\\s]?\\d{4}\\b"
        )

        // ============================================================
        // 🟢 LOW RISK REGEXES
        // ============================================================
        internal val ipRegex = Regex(
            "\\b((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\b"
        )

        // ============================================================
        // ✅ STRICT OTP KEYWORDS
        // ============================================================
        internal val otpKeywords = listOf(
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
        // ============================================================
        internal val fraudHighPhrases = listOf(
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

        internal val fraudMediumSignals = listOf(
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

        // ============================================================
        // ✅ AADHAAR DETECTION
        // ============================================================
        internal fun detectAadhaar(text: String, lowerText: String): Boolean {
            val textWithoutCard = if (cardRegex.containsMatchIn(text)) {
                cardRegex.replace(text, "XXXXXXXXXXXXXXXX")
            } else {
                text
            }
            if (aadhaarSpacedRegex.containsMatchIn(textWithoutCard)) return true
            if (aadhaarRegex.containsMatchIn(textWithoutCard)) {
                if (aadhaarContextKeywords.any { lowerText.contains(it) }) return true
            }
            return false
        }

        // ============================================================
        // ✅ OTP DETECTION
        // ============================================================
        internal fun detectOtp(text: String, lowerText: String): Boolean {
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
        internal fun detectPhone(text: String, isAadhaar: Boolean): Boolean {
            if (!phoneRegex.containsMatchIn(text)) return false
            if (isAadhaar) {
                val stripped = aadhaarRegex.replace(text, "XXXXXXXXXXXX")
                return phoneRegex.containsMatchIn(stripped)
            }
            return true
        }

        // ============================================================
        // PERSON 3 / PERSON 2 — BUILD MASKED TEXT WITH EXCLUSIONS
        // Masking order: CARD FIRST (with keyword guard), then AADHAAR
        // (with Card placeholder protection), then PAN, then UPI,
        // then PASSWORD, then OTP, then PHONE (with Aadhaar placeholder
        // protection), then EMAIL, then VEHICLE, then IP.
        // ============================================================
        internal fun buildMaskedText(
            recognizedText: String,
            excludedTypes: Set<String> = emptySet()
        ): String {
            val lowerText = recognizedText.lowercase()

            val isUpi = upiRegex.containsMatchIn(recognizedText)
            val isPan = panRegex.containsMatchIn(recognizedText)
            val isCard = cardRegex.containsMatchIn(recognizedText) &&
                    cardKeywords.any { lowerText.contains(it) }
            val isAadhaar = detectAadhaar(recognizedText, lowerText)
            val isPassword = passwordKeywordRegex.containsMatchIn(recognizedText)
            val isOtp = detectOtp(recognizedText, lowerText)
            val isEmail = emailRegex.containsMatchIn(recognizedText)
            val isPhone = detectPhone(recognizedText, isAadhaar)
            val isVehicle = vehicleRegex.containsMatchIn(recognizedText)
            val isIp = ipRegex.containsMatchIn(recognizedText)

            var maskedText = recognizedText

            // 1. CARD MASKING
            if (isCard && "CARD" !in excludedTypes) {
                maskedText = maskedText.replace(cardRegex, "XXXX XXXX XXXX XXXX")
            }

            // 2. AADHAAR MASKING (with card placeholder protection if Card is unmasked)
            if (isAadhaar && "AADHAAR" !in excludedTypes) {
                if (isCard && "CARD" in excludedTypes) {
                    val cardMatches = cardRegex.findAll(maskedText).map { it.value }.toList()
                    var tempText = maskedText
                    val placeholders = mutableListOf<Pair<String, String>>()
                    for ((index, match) in cardMatches.withIndex()) {
                        val token = "__DS_CARD_${index}__"
                        placeholders.add(token to match)
                        tempText = tempText.replaceFirst(match, token)
                    }
                    tempText = tempText.replace(aadhaarRegex, "XXXX XXXX XXXX")
                    for ((token, original) in placeholders) {
                        tempText = tempText.replace(token, original)
                    }
                    maskedText = tempText
                } else {
                    maskedText = maskedText.replace(aadhaarRegex, "XXXX XXXX XXXX")
                }
            }
            if (isPan && "PAN" !in excludedTypes) {
                maskedText = maskedText.replace(panRegex, "XXXXXXXXXX")
            }
            if (isUpi && "UPI" !in excludedTypes) {
                maskedText = maskedText.replace(upiRegex, "xxx@xxx")
            }
            if (isPassword && "PASSWORD" !in excludedTypes) {
                maskedText = maskedText.replace(passwordKeywordRegex, "password: ********")
            }
            if (isOtp && "OTP" !in excludedTypes) {
                maskedText = maskedText.replace(
                    Regex("(?<!\\d)\\d{4,8}(?!\\d)"),
                    "XXXXXX"
                )
            }
            if (isPhone && "PHONE" !in excludedTypes) {
                if (isAadhaar && "AADHAAR" in excludedTypes) {
                    val aadhaarMatches = aadhaarRegex.findAll(maskedText).map { it.value }.toList()
                    var tempText = maskedText
                    val placeholders = mutableListOf<Pair<String, String>>()
                    for ((index, match) in aadhaarMatches.withIndex()) {
                        val token = "__DS_AADHAAR_${index}__"
                        placeholders.add(token to match)
                        tempText = tempText.replaceFirst(match, token)
                    }
                    tempText = tempText.replace(phoneRegex, "+91-XXXXX-XXXXX")
                    for ((token, original) in placeholders) {
                        tempText = tempText.replace(token, original)
                    }
                    maskedText = tempText
                } else {
                    maskedText = maskedText.replace(phoneRegex, "+91-XXXXX-XXXXX")
                }
            }
            if (isEmail && "EMAIL" !in excludedTypes) {
                maskedText = maskedText.replace(emailRegex, "xxx@xxx.com")
            }
            if (isVehicle && "VEHICLE" !in excludedTypes) {
                maskedText = maskedText.replace(vehicleRegex, "XX00XX0000")
            }
            if (isIp && "IP" !in excludedTypes) {
                maskedText = maskedText.replace(ipRegex, "xxx.xxx.xxx.xxx")
            }

            return maskedText
        }

        internal data class RedactionDecision(
            val shouldBlur: Boolean,
            val detectedTypes: List<String>,
            val reason: String
        )

        internal fun evaluateLineRedaction(
            line: String,
            excludedTypes: Set<String>,
            context: String? = null
        ): RedactionDecision {
            val lower = line.lowercase()
            val detected = mutableListOf<String>()
            val blurringReasons = mutableListOf<String>()

            // 1. Identify sensitive types present on this line
            val hasAadhaar = detectAadhaar(line, lower) || (context != null && detectAadhaar(line, context.lowercase()))
            if (hasAadhaar) detected.add("AADHAAR")

            val hasPan = panRegex.containsMatchIn(line)
            if (hasPan) detected.add("PAN")

            val hasCard = cardRegex.containsMatchIn(line) &&
                    (cardKeywords.any { lower.contains(it) } || (context != null && cardKeywords.any { context.lowercase().contains(it) }))
            if (hasCard) detected.add("CARD")

            val hasUpi = upiRegex.containsMatchIn(line)
            if (hasUpi) detected.add("UPI")

            val hasPassword = passwordKeywordRegex.containsMatchIn(line)
            if (hasPassword) detected.add("PASSWORD")

            val hasEmail = emailRegex.containsMatchIn(line)
            if (hasEmail) detected.add("EMAIL")

            // Overlap protection: Aadhaar numbers must not be treated as phone numbers
            val hasPhone = if (hasAadhaar) {
                val stripped = aadhaarRegex.replace(line, "XXXXXXXXXXXX")
                phoneRegex.containsMatchIn(stripped)
            } else {
                phoneRegex.containsMatchIn(line)
            }
            if (hasPhone) detected.add("PHONE")

            val hasVehicle = vehicleRegex.containsMatchIn(line)
            if (hasVehicle) detected.add("VEHICLE")

            val hasIp = ipRegex.containsMatchIn(line)
            if (hasIp) detected.add("IP")

            val hasOtp = detectOtp(line, lower) || (context != null && detectOtp(context, context.lowercase()) &&
                    (otpKeywords.any { lower.contains(it) } || Regex("\\b\\d{4,8}\\b").containsMatchIn(line)))
            if (hasOtp) detected.add("OTP")

            val hasAddress = (addressKeywordRegex.containsMatchIn(line) && pincodeRegex.containsMatchIn(line)) ||
                    (context != null && addressKeywordRegex.containsMatchIn(context) && pincodeRegex.containsMatchIn(context) &&
                            (addressKeywordRegex.containsMatchIn(line) || pincodeRegex.containsMatchIn(line)))
            if (hasAddress) detected.add("ADDRESS")

            val hasFraud = fraudHighPhrases.any { it.containsMatchIn(line) }
            if (hasFraud) detected.add("FRAUD")

            // 2. Check each detected type against excludedTypes
            for (type in detected) {
                if (type == "FRAUD") {
                    blurringReasons.add("FRAUD detected")
                } else if (type !in excludedTypes) {
                    blurringReasons.add("$type not in excludedTypes")
                }
            }

            val shouldBlur = blurringReasons.isNotEmpty()
            val reasonStr = if (shouldBlur) {
                "Blurring because: ${blurringReasons.joinToString(", ")}"
            } else if (detected.isNotEmpty()) {
                "Skipped because all detected types (${detected.joinToString(",")}) are in excludedTypes"
            } else {
                "No sensitive data detected on this line"
            }

            return RedactionDecision(shouldBlur, detected, reasonStr)
        }

        internal fun isSensitiveLine(
            line: String,
            excludedTypes: Set<String> = emptySet(),
            context: String? = null
        ): Boolean {
            return evaluateLineRedaction(line, excludedTypes, context).shouldBlur
        }

        // ============================================================
        // 🆕 FRAUD MESSAGE DETECTION (PURE EVALUATION)
        // ============================================================
        internal data class FraudDetectionResult(
            val isFraudHigh: Boolean,
            val isFraudMedium: Boolean,
            val findings: List<String>
        )

        internal fun evaluateFraud(text: String): FraudDetectionResult {
            val findings = mutableListOf<String>()
            var isFraudHigh = false
            var isFraudMedium = false

            // Check high-confidence fraud phrases (one match = HIGH)
            for (pattern in fraudHighPhrases) {
                if (pattern.containsMatchIn(text)) {
                    isFraudHigh = true
                    val matchValue = pattern.find(text)?.value?.take(40) ?: ""
                    if (matchValue.isNotEmpty() && !findings.contains("Scam Message")) {
                        findings.add("Scam Message")
                    }
                }
            }

            // Check medium-confidence signals (need 2+ to flag)
            val mediumHits = fraudMediumSignals.count { it.containsMatchIn(text) }
            if (mediumHits >= 2) {
                isFraudMedium = true
                if (!findings.contains("Suspicious Message")) {
                    findings.add("Suspicious Message")
                }
            }

            return FraudDetectionResult(isFraudHigh, isFraudMedium, findings)
        }

        // ============================================================
        // 🆕 UPI QR PAYLOAD PARSER (PURE EVALUATION)
        // ============================================================
        internal data class UpiQrParseResult(
            val isUpiQr: Boolean,
            val payee: String? = null,
            val amount: String? = null
        )

        internal fun parseUpiQrPayload(rawValue: String): UpiQrParseResult {
            val lower = rawValue.lowercase()

            // UPI deep-link: upi://pay?pa=...
            if (lower.startsWith("upi://")) {
                val pa = Regex("[?&]pa=([^&]+)", RegexOption.IGNORE_CASE).find(rawValue)?.groupValues?.get(1)
                    ?.let { try { java.net.URLDecoder.decode(it, "UTF-8") } catch (e: Exception) { it } }
                val pn = Regex("[?&]pn=([^&]+)", RegexOption.IGNORE_CASE).find(rawValue)?.groupValues?.get(1)
                    ?.let { try { java.net.URLDecoder.decode(it, "UTF-8") } catch (e: Exception) { it } }
                val am = Regex("[?&]am=([^&]+)", RegexOption.IGNORE_CASE).find(rawValue)?.groupValues?.get(1)
                    ?.let { try { java.net.URLDecoder.decode(it, "UTF-8") } catch (e: Exception) { it } }

                val payee = pn ?: pa
                return UpiQrParseResult(
                    isUpiQr = true,
                    payee = payee,
                    amount = am
                )
            }

            // Some QRs embed UPI handle text directly (e.g. "name@upihandle")
            if (upiRegex.containsMatchIn(rawValue)) {
                return UpiQrParseResult(
                    isUpiQr = true,
                    payee = rawValue.trim(),
                    amount = null
                )
            }

            return UpiQrParseResult(isUpiQr = false)
        }

        // Convert the user-facing finding name into the
        // internal detection type used by Person 2's masking logic.
        internal fun getExcludedType(finding: String): String {
            val upper = finding.uppercase()
            return when {
                upper.contains("AADHAAR") || upper.contains("AADHAR") -> "AADHAAR"
                upper.contains("PAN") -> "PAN"
                upper.contains("CARD") -> "CARD"
                upper.contains("UPI QR") -> "UPI_QR"
                upper.contains("UPI") -> "UPI"
                upper.contains("PASSWORD") || upper.contains("PASSWD") -> "PASSWORD"
                upper.contains("OTP") || upper.contains("PASSCODE") -> "OTP"
                upper.contains("PHONE") || upper.contains("MOBILE") -> "PHONE"
                upper.contains("EMAIL") -> "EMAIL"
                upper.contains("VEHICLE") -> "VEHICLE"
                upper.contains("IP ADDRESS") || upper.startsWith("IP") -> "IP"
                upper.contains("ADDRESS") || upper.contains("PINCODE") -> "ADDRESS"
                upper.contains("SCAM") -> "SCAM"
                upper.contains("SUSPICIOUS") -> "SUSPICIOUS"
                else -> upper.split("—", "-", " ").firstOrNull()?.trim() ?: upper
            }
        }
    }

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
    // 🔧 FIX: store the QR code's on-image position so it can actually be blurred
    private var detectedUpiQrBoundingBox: Rect? = null

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
        findingsCheckboxContainer = findViewById(R.id.findingsCheckboxContainer)

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

                            val textToShare = latestRecognizedText?.let {
                                buildMaskedText(it, excludedTypes)
                            } ?: extractedText.text.toString()

                            val intent = Intent(Intent.ACTION_SEND)
                            intent.type = "text/plain"

                            intent.putExtra(
                                Intent.EXTRA_TEXT,
                                "Scanned using DigiSuraksha\n\n$textToShare\n\n${riskLevel.text}"
                            )

                            startActivity(
                                Intent.createChooser(
                                    intent,
                                    "Secure Share"
                                )
                            )
                        }

                        1 -> {
                            if (originalBitmap != null && latestOcrResult != null) {
                                blurredBitmap = generateBlurredBitmap(
                                    originalBitmap!!,
                                    latestOcrResult!!,
                                    excludedTypes
                                )

                                blurredBitmap?.let {
                                    logEvent("Screenshot → $currentRisk → Shared (Blurred)")

                                    val uri = getImageUri(
                                        it,
                                        "DigiSuraksha_Blurred"
                                    )

                                    val intent = Intent(Intent.ACTION_SEND)
                                    intent.type = "image/*"

                                    intent.putExtra(
                                        Intent.EXTRA_STREAM,
                                        uri
                                    )

                                    intent.addFlags(
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    )

                                    startActivity(
                                        Intent.createChooser(
                                            intent,
                                            "Share Blurred Image"
                                        )
                                    )
                                }
                            }
                        }

                        2 -> {
                            handleOriginalImageShare()
                        }

                        else -> {
                            // No action
                        }
                    }
                }.show()
        }
    }


    // ============================================================
    // 🆕 FRAUD MESSAGE DETECTION
    // Returns true if the text contains HIGH-confidence fraud signals.
    // Populates fraudFindings, isFraudHigh, isFraudMedium as side effects.
    // ============================================================
    private fun detectFraud(text: String): Boolean {
        val result = evaluateFraud(text)
        fraudFindings.clear()
        fraudFindings.addAll(result.findings)
        isFraudHigh = result.isFraudHigh
        isFraudMedium = result.isFraudMedium
        return isFraudHigh
    }

    // ============================================================
    // 🆕 UPI QR CODE DETECTION
    // Uses ML Kit BarcodeScanning to find QR codes in the bitmap.
    // Calls back with result via a lambda to fit async ML Kit flow.
    // 🔧 FIX: also captures barcode.boundingBox so the QR region can be blurred.
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
                detectedUpiQrBoundingBox = null

                for (barcode in barcodes) {
                    val rawValue = barcode.rawValue ?: continue
                    val result = parseUpiQrPayload(rawValue)
                    if (result.isUpiQr) {
                        isUpiQrDetected = true
                        detectedUpiQrPayee = result.payee
                        detectedUpiQrAmount = result.amount
                        detectedUpiQrBoundingBox = barcode.boundingBox
                        logEvent("UPI QR detected → payee=$detectedUpiQrPayee amount=$detectedUpiQrAmount")
                        break
                    }
                }
                onResult(isUpiQrDetected)
            }
            .addOnFailureListener {
                isUpiQrDetected = false
                detectedUpiQrBoundingBox = null
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
                latestOcrResult = null
                latestRecognizedText = null
                excludedTypes.clear()
                findingsCheckboxContainer.removeAllViews()
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

    // ============================================================
    // PERSON 3 / PERSON 2 — BUILD MASKED TEXT WITH EXCLUSIONS
    // ============================================================
    private fun buildMaskedText(
        recognizedText: String,
        excludedTypes: Set<String> = emptySet()
    ): String {
        val lowerText = recognizedText.lowercase()

        val isUpi = upiRegex.containsMatchIn(recognizedText)
        val isPan = panRegex.containsMatchIn(recognizedText)
        val isCard = cardRegex.containsMatchIn(recognizedText) &&
                cardKeywords.any { lowerText.contains(it) }
        val isAadhaar = detectAadhaar(recognizedText, lowerText)
        val isPassword = passwordKeywordRegex.containsMatchIn(recognizedText)
        val isOtp = detectOtp(recognizedText, lowerText)
        val isEmail = emailRegex.containsMatchIn(recognizedText)
        val isPhone = detectPhone(recognizedText, isAadhaar)
        val isVehicle = vehicleRegex.containsMatchIn(recognizedText)
        val isIp = ipRegex.containsMatchIn(recognizedText)

        // Masking order: AADHAAR FIRST, then PAN, then CARD
        var maskedText = recognizedText

        if (isAadhaar && "AADHAAR" !in excludedTypes) {
            maskedText = maskedText.replace(aadhaarRegex, "XXXX XXXX XXXX")
        }
        if (isPan && "PAN" !in excludedTypes) {
            maskedText = maskedText.replace(panRegex, "XXXXXXXXXX")
        }
        if (isCard && "CARD" !in excludedTypes) {
            maskedText = maskedText.replace(cardRegex, "XXXX XXXX XXXX XXXX")
        }
        if (isUpi && "UPI" !in excludedTypes) {
            maskedText = maskedText.replace(upiRegex, "xxx@xxx")
        }
        if (isPassword && "PASSWORD" !in excludedTypes) {
            maskedText = maskedText.replace(passwordKeywordRegex, "password: ********")
        }
        if (isOtp && "OTP" !in excludedTypes) {
            maskedText = maskedText.replace(
                Regex("(?<!\\d)\\d{4,8}(?!\\d)"),
                "XXXXXX"
            )
        }
        if (isPhone && "PHONE" !in excludedTypes) {
            maskedText = maskedText.replace(phoneRegex, "+91-XXXXX-XXXXX")
        }
        if (isEmail && "EMAIL" !in excludedTypes) {
            maskedText = maskedText.replace(emailRegex, "xxx@xxx.com")
        }
        if (isVehicle && "VEHICLE" !in excludedTypes) {
            maskedText = maskedText.replace(vehicleRegex, "XX00XX0000")
        }
        if (isIp && "IP" !in excludedTypes) {
            maskedText = maskedText.replace(ipRegex, "xxx.xxx.xxx.xxx")
        }

        return maskedText
    }

    private fun analyzeText(
        recognizedText: String,
        bitmap: Bitmap,
        ocrResult: com.google.mlkit.vision.text.Text,
        excludedTypes: Set<String> = emptySet()
    ) {
        latestOcrResult = ocrResult
        latestRecognizedText = recognizedText
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
        // Build masked preview text with active exclusions
        // ============================================================
        extractedText.text = buildMaskedText(recognizedText, this.excludedTypes)

        // -------- Build findings list for the UI --------
        val findings = mutableListOf<String>()
        val findingItemsWithReasons = mutableListOf<Pair<String, String>>()

        if (isUpi) {
            findings.add("UPI ID")
            findingItemsWithReasons.add("UPI ID" to "UPI VPA handle found")
        }
        if (isPan) {
            findings.add("PAN Number")
            findingItemsWithReasons.add("PAN Number" to "10-character PAN format found")
        }
        if (isCard) {
            findings.add("Card Number")
            findingItemsWithReasons.add("Card Number" to "16-digit card pattern found")
        }
        if (isAadhaar) {
            findings.add("Aadhaar Number")
            findingItemsWithReasons.add("Aadhaar Number" to "12-digit Aadhaar pattern found")
        }
        if (isPassword) {
            findings.add("Password")
            findingItemsWithReasons.add("Password" to "Password keyword pattern found")
        }
        if (isAddress) {
            findings.add("Address")
            findingItemsWithReasons.add("Address" to "Street/area and PIN code found")
        }
        if (isOtp) {
            findings.add("OTP")
            findingItemsWithReasons.add("OTP" to "One-time passcode pattern found")
        }
        if (isEmail) {
            findings.add("Email")
            findingItemsWithReasons.add("Email" to "Email address pattern found")
        }
        if (isPhone) {
            findings.add("Phone Number")
            findingItemsWithReasons.add("Phone Number" to "10-digit phone number found")
        }
        if (isVehicle) {
            findings.add("Vehicle Number")
            findingItemsWithReasons.add("Vehicle Number" to "Vehicle registration plate found")
        }
        if (isIp) {
            findings.add("IP Address")
            findingItemsWithReasons.add("IP Address" to "IPv4 network address found")
        }
        // 🆕 New findings
        if (isUpiQrDetected) {
            val qrLabel = buildString {
                append("UPI QR Code")
                detectedUpiQrPayee?.let { append(" ($it)") }
                detectedUpiQrAmount?.let { append(" ₹$it") }
            }
            findings.add(qrLabel)
            val qrReason = buildString {
                append("Payment QR code detected")
                if (detectedUpiQrPayee != null || detectedUpiQrAmount != null) {
                    append(" (")
                    detectedUpiQrPayee?.let { append("Payee: $it") }
                    if (detectedUpiQrPayee != null && detectedUpiQrAmount != null) append(", ")
                    detectedUpiQrAmount?.let { append("₹$it") }
                    append(")")
                }
            }
            findingItemsWithReasons.add("UPI QR Code" to qrReason)
        }
        for (f in fraudFindings) {
            findings.add(f)
            val reason = if (f == "Scam Message") {
                "High-confidence fraud phrases detected"
            } else {
                "Suspicious urgency/offer pattern detected"
            }
            findingItemsWithReasons.add(f to reason)
        }

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
        // ============================================================
        // PERSON 3 — BUILD PER-FIELD REDACTION CHECKLIST
        // ============================================================
        setupFindingsCheckboxesWithReasons(findingItemsWithReasons)
        blurredBitmap = generateBlurredBitmap(bitmap, ocrResult, this.excludedTypes)

        logEvent("Screenshot → $currentRisk → Scanned (${findings.joinToString(",")})")
    }

    private fun generateBlurredBitmap(
        original: Bitmap,
        ocrResult: com.google.mlkit.vision.text.Text,
        excludedTypes: Set<String> = emptySet()
    ): Bitmap {
        val blurred = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(blurred)
        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        android.util.Log.i(
            "DigiSuraksha_Redaction",
            "=== Starting generateBlurredBitmap with excludedTypes=$excludedTypes ==="
        )

        for (block in ocrResult.textBlocks) {
            val blockContext = block.text
            for (line in block.lines) {
                val box = line.boundingBox
                val boxStr = box?.let { "(${it.left},${it.top},${it.right},${it.bottom})" } ?: "(null)"
                val decision = evaluateLineRedaction(line.text, excludedTypes, blockContext)

                val decisionStr = if (decision.shouldBlur) "BLUR" else "SKIP"
                val typeStr = if (decision.detectedTypes.isNotEmpty()) decision.detectedTypes.joinToString(",") else "NONE"

                android.util.Log.i(
                    "DigiSuraksha_Redaction",
                    "TEXT='${line.text}' | TYPE=$typeStr | BOX=$boxStr | excluded=$excludedTypes | DECISION=$decisionStr | REASON=${decision.reason}"
                )

                if (decision.shouldBlur) {
                    box?.let { canvas.drawRect(it, paint) }
                }
            }
        }

        // 🔧 FIX: Also blur the UPI QR code region if one was detected and not excluded.
        // QR codes are not picked up by ocrResult.textBlocks (that only contains OCR'd
        // text lines), so without this the QR code itself was never actually redacted
        // even though it was correctly detected and flagged as HIGH risk.
        if (isUpiQrDetected && "UPI_QR" !in excludedTypes) {
            detectedUpiQrBoundingBox?.let { box ->
                android.util.Log.i(
                    "DigiSuraksha_Redaction",
                    "Blurring UPI QR region: (${box.left},${box.top},${box.right},${box.bottom})"
                )
                canvas.drawRect(box, paint)
            }
        }

        android.util.Log.i(
            "DigiSuraksha_Redaction",
            "=== Finished generateBlurredBitmap ==="
        )

        return blurred
    }

    // ============================================================
    // PERSON 3 — PER-FIELD REDACTION CHECKBOX UI
    // ============================================================
    private fun setupFindingsCheckboxesWithReasons(findingItems: List<Pair<String, String>>) {
        findingsCheckboxContainer.removeAllViews()
        excludedTypes.clear()

        // No sensitive information detected
        if (findingItems.isEmpty()) {
            val noFindingsText = TextView(this).apply {
                text = "✓ No sensitive information detected."
                textSize = 15f
                setPadding(8, 8, 8, 8)
            }

            findingsCheckboxContainer.addView(noFindingsText)
            return
        }

        // Create one checkbox for every detected finding with its reason
        for ((name, reason) in findingItems) {
            val type = getExcludedType(name)
            val displayText = "$name — $reason"

            val checkBox = CheckBox(this).apply {
                text = displayText
                textSize = 14f

                // Everything is selected by default.
                // Selected = this information WILL be redacted.
                isChecked = true

                setPadding(8, 4, 8, 4)

                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        // Checked means redact this information.
                        excludedTypes.remove(type)
                    } else {
                        // Unchecked means don't redact this information.
                        excludedTypes.add(type)
                    }

                    // Dynamically update extracted text preview
                    latestRecognizedText?.let { rawText ->
                        extractedText.text = buildMaskedText(rawText, excludedTypes)
                    }
                }
            }

            findingsCheckboxContainer.addView(checkBox)
        }
    }

    // Overload for compatibility if called with List<String>
    private fun setupFindingsCheckboxes(findings: List<String>) {
        val pairs = findings.map { finding ->
            val type = getExcludedType(finding)
            val defaultReason = when (type) {
                "AADHAAR" -> "12-digit Aadhaar pattern found"
                "PAN" -> "10-character PAN format found"
                "CARD" -> "16-digit card pattern found"
                "UPI" -> "UPI VPA handle found"
                "PASSWORD" -> "Password keyword pattern found"
                "OTP" -> "One-time passcode pattern found"
                "PHONE" -> "10-digit phone number found"
                "EMAIL" -> "Email address pattern found"
                "VEHICLE" -> "Vehicle registration plate found"
                "IP" -> "IPv4 network address found"
                "ADDRESS" -> "Street/area and PIN code found"
                "SCAM" -> "High-confidence fraud phrases detected"
                "SUSPICIOUS" -> "Suspicious urgency/offer pattern detected"
                else -> "Sensitive pattern detected"
            }
            finding to defaultReason
        }
        setupFindingsCheckboxesWithReasons(pairs)
    }

    // Convert the user-facing finding name into the
    // internal detection type used by Person 2's masking logic.
    private fun getExcludedType(finding: String): String {
        val upper = finding.uppercase()
        return when {
            upper.contains("AADHAAR") || upper.contains("AADHAR") -> "AADHAAR"
            upper.contains("PAN") -> "PAN"
            upper.contains("CARD") -> "CARD"
            upper.contains("UPI QR") -> "UPI_QR"
            upper.contains("UPI") -> "UPI"
            upper.contains("PASSWORD") || upper.contains("PASSWD") -> "PASSWORD"
            upper.contains("OTP") || upper.contains("PASSCODE") -> "OTP"
            upper.contains("PHONE") || upper.contains("MOBILE") -> "PHONE"
            upper.contains("EMAIL") -> "EMAIL"
            upper.contains("VEHICLE") -> "VEHICLE"
            upper.contains("IP ADDRESS") || upper.startsWith("IP") -> "IP"
            upper.contains("ADDRESS") || upper.contains("PINCODE") -> "ADDRESS"
            upper.contains("SCAM") -> "SCAM"
            upper.contains("SUSPICIOUS") -> "SUSPICIOUS"
            else -> upper.split("—", "-", " ").firstOrNull()?.trim() ?: upper
        }
    }

    private fun logEvent(event: String) {
        val prefs  = getSharedPreferences("logs", MODE_PRIVATE)
        val oldLog = prefs.getString("data", "") ?: ""
        prefs.edit().putString("data", "$oldLog\n${getCurrentTime()} : $event").apply()
    }

    private fun getCurrentTime(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss dd-MM-yyyy", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }
}