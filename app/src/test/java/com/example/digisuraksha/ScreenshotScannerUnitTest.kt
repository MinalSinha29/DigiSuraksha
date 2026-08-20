package com.example.digisuraksha

import org.junit.Assert.*
import org.junit.Test

class ScreenshotScannerUnitTest {

    private fun getExcludedType(finding: String): String {
        val upper = finding.uppercase()
        return when {
            upper.contains("AADHAAR") || upper.contains("AADHAR") -> "AADHAAR"
            upper.contains("PAN") -> "PAN"
            upper.contains("CARD") -> "CARD"
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

    private val aadhaarRegex = Regex("(?<!\\d)(\\d{4}[\\s-]\\d{4}[\\s-]\\d{4}|\\d{12})(?!\\d)")
    private val aadhaarSpacedRegex = Regex("(?<!\\d)\\d{4}[\\s-]\\d{4}[\\s-]\\d{4}(?!\\d)")
    private val aadhaarContextKeywords = listOf(
        "government of india", "aadhaar", "aadhar", "uidai", "uid",
        "unique identification", "enrolment no", "enrollment no", "dob", "male", "female"
    )

    private fun detectAadhaar(text: String, lowerText: String): Boolean {
        if (aadhaarSpacedRegex.containsMatchIn(text)) return true
        if (aadhaarRegex.containsMatchIn(text)) {
            if (aadhaarContextKeywords.any { lowerText.contains(it) }) return true
        }
        return false
    }

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

    private fun detectPhone(text: String, isAadhaar: Boolean): Boolean {
        if (!phoneRegex.containsMatchIn(text)) return false
        if (isAadhaar) {
            val stripped = aadhaarRegex.replace(text, "XXXXXXXXXXXX")
            return phoneRegex.containsMatchIn(stripped)
        }
        return true
    }

    private val emailRegex = Regex("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.(com|in|edu|org|net|co|io|gov|ac)")
    private val panRegex = Regex("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b")
    private val cardRegex = Regex(
        "(?<!\\d)" +
                "(?:" +
                "\\d{4}[\\s-]\\d{4}[\\s-]\\d{4}[\\s-]\\d{4}" +
                "|\\d{16}" +
                ")(?!\\d)"
    )
    private val upiHandles = listOf(
        "okaxis", "okhdfcbank", "okicici", "oksbi",
        "paytm", "ybl", "ibl", "axl", "upi"
    )
    private val upiRegex = Regex(
        "\\b[\\w.\\-]+@(${upiHandles.joinToString("|")})\\b",
        RegexOption.IGNORE_CASE
    )

    private fun isSensitiveLine(line: String, excludedTypes: Set<String> = emptySet()): Boolean {
        val lower = line.lowercase()

        val hasAadhaar = aadhaarRegex.containsMatchIn(line) || detectAadhaar(line, lower)
        val hasPan = panRegex.containsMatchIn(line)
        val hasCard = cardRegex.containsMatchIn(line)
        val hasUpi = upiRegex.containsMatchIn(line)
        val hasEmail = emailRegex.containsMatchIn(line)

        val hasPhone = if (hasAadhaar) {
            val stripped = aadhaarRegex.replace(line, "XXXXXXXXXXXX")
            phoneRegex.containsMatchIn(stripped)
        } else {
            phoneRegex.containsMatchIn(line)
        }

        var shouldBlur = false

        if (hasAadhaar && "AADHAAR" !in excludedTypes) shouldBlur = true
        if (hasPan && "PAN" !in excludedTypes) shouldBlur = true
        if (hasCard && "CARD" !in excludedTypes) shouldBlur = true
        if (hasUpi && "UPI" !in excludedTypes) shouldBlur = true
        if (hasEmail && "EMAIL" !in excludedTypes) shouldBlur = true
        if (hasPhone && "PHONE" !in excludedTypes) shouldBlur = true

        return shouldBlur
    }

    private fun maskText(
        rawText: String,
        excludedTypes: Set<String> = emptySet()
    ): String {
        var masked = rawText

        if ("AADHAAR" !in excludedTypes) {
            masked = masked.replace(aadhaarRegex, "XXXX XXXX XXXX")
        }
        if ("PAN" !in excludedTypes) {
            masked = masked.replace(panRegex, "XXXXXXXXXX")
        }
        if ("PHONE" !in excludedTypes) {
            masked = masked.replace(phoneRegex, "+91-XXXXX-XXXXX")
        }
        if ("EMAIL" !in excludedTypes) {
            masked = masked.replace(emailRegex, "xxx@xxx.com")
        }

        return masked
    }

    @Test
    fun testGetExcludedType_formatsCorrectly() {
        assertEquals("AADHAAR", getExcludedType("Aadhaar Number"))
        assertEquals("AADHAAR", getExcludedType("Aadhaar Number — 12-digit Aadhaar pattern found"))
        assertEquals("PHONE", getExcludedType("Phone Number — 10-digit phone number found"))
        assertEquals("EMAIL", getExcludedType("Email — Email address pattern found"))
        assertEquals("PAN", getExcludedType("PAN Number — 10-character PAN format found"))
        assertEquals("CARD", getExcludedType("Card Number — 16-digit card pattern found"))
        assertEquals("UPI", getExcludedType("UPI ID — UPI VPA handle found"))
        assertEquals("UPI", getExcludedType("UPI QR Code — Payment QR code detected"))
        assertEquals("OTP", getExcludedType("OTP — One-time passcode pattern found"))
        assertEquals("PASSWORD", getExcludedType("Password — Password keyword pattern found"))
        assertEquals("VEHICLE", getExcludedType("Vehicle Number — Vehicle registration plate found"))
        assertEquals("IP", getExcludedType("IP Address — IPv4 network address found"))
        assertEquals("ADDRESS", getExcludedType("Address — Street/area and PIN code found"))
    }

    @Test
    fun testDefaultMasking_allSensitiveTypesMasked() {
        val sample = "Aadhaar: 1234 5678 9012, Phone: 9876543210, Email: user@example.com"
        val masked = maskText(sample, emptySet())

        assertFalse(masked.contains("1234 5678 9012"))
        assertFalse(masked.contains("9876543210"))
        assertFalse(masked.contains("user@example.com"))
        assertTrue(masked.contains("XXXX XXXX XXXX"))
        assertTrue(masked.contains("+91-XXXXX-XXXXX"))
        assertTrue(masked.contains("xxx@xxx.com"))
    }

    @Test
    fun testExcludeAadhaar_aadhaarRemainsVisibleWhileOthersMasked() {
        val sample = "Aadhaar: 1234 5678 9012, Phone: 9876543210, Email: user@example.com"
        val excluded = setOf("AADHAAR")
        val masked = maskText(sample, excluded)

        assertTrue(masked.contains("1234 5678 9012"))
        assertFalse(masked.contains("9876543210"))
        assertFalse(masked.contains("user@example.com"))
        assertTrue(masked.contains("+91-XXXXX-XXXXX"))
        assertTrue(masked.contains("xxx@xxx.com"))
    }

    @Test
    fun testExcludePhone_phoneRemainsVisibleWhileOthersMasked() {
        val sample = "Aadhaar: 1234 5678 9012, Phone: 9876543210, Email: user@example.com"
        val excluded = setOf("PHONE")
        val masked = maskText(sample, excluded)

        assertFalse(masked.contains("1234 5678 9012"))
        assertTrue(masked.contains("9876543210"))
        assertFalse(masked.contains("user@example.com"))
        assertTrue(masked.contains("XXXX XXXX XXXX"))
        assertTrue(masked.contains("xxx@xxx.com"))
    }

    @Test
    fun testExcludeMultiple_aadhaarAndEmailUnmasked() {
        val sample = "Aadhaar: 1234 5678 9012, Phone: 9876543210, Email: user@example.com, PAN: ABCDE1234F"
        val excluded = setOf("AADHAAR", "EMAIL")
        val masked = maskText(sample, excluded)

        assertTrue(masked.contains("1234 5678 9012"))
        assertTrue(masked.contains("user@example.com"))
        assertFalse(masked.contains("9876543210"))
        assertFalse(masked.contains("ABCDE1234F"))
        assertTrue(masked.contains("+91-XXXXX-XXXXX"))
        assertTrue(masked.contains("XXXXXXXXXX"))
    }

    @Test
    fun testIsSensitiveLine_excludeAadhaar_aadhaarLineNotBlurred() {
        val aadhaarLine = "9876 5432 1098"
        val phoneLine = "Call: +91 9876543210"

        // Default (empty exclusions -> all sensitive lines blurred)
        assertTrue(isSensitiveLine(aadhaarLine, emptySet()))
        assertTrue(isSensitiveLine(phoneLine, emptySet()))

        // Exclude Aadhaar (Aadhaar line is NOT blurred, Phone line IS blurred)
        assertFalse(isSensitiveLine(aadhaarLine, setOf("AADHAAR")))
        assertTrue(isSensitiveLine(phoneLine, setOf("AADHAAR")))
    }

    @Test
    fun testIsSensitiveLine_unspacedAadhaarStartingWith91_notBlurredWhenExcluded() {
        val aadhaarLine91 = "918765432109" // 12-digit Aadhaar starting with 91

        // Default: blurred
        assertTrue(isSensitiveLine(aadhaarLine91, emptySet()))

        // Exclude Aadhaar: NOT blurred even though it starts with 91
        assertFalse(isSensitiveLine(aadhaarLine91, setOf("AADHAAR")))
    }

    @Test
    fun testIsSensitiveLine_excludePhone_phoneLineNotBlurred() {
        val aadhaarLine = "9876 5432 1098"
        val phoneLine = "Call: +91 9876543210"

        // Exclude Phone (Phone line is NOT blurred, Aadhaar line IS blurred)
        assertTrue(isSensitiveLine(aadhaarLine, setOf("PHONE")))
        assertFalse(isSensitiveLine(phoneLine, setOf("PHONE")))
    }

    @Test
    fun testIsSensitiveLine_excludeMultiple_bothLinesNotBlurred() {
        val aadhaarLine = "9876 5432 1098"
        val emailLine = "Contact: test@gmail.com"
        val panLine = "PAN: ABCDE1234F"

        val excluded = setOf("AADHAAR", "EMAIL")

        assertFalse(isSensitiveLine(aadhaarLine, excluded))
        assertFalse(isSensitiveLine(emailLine, excluded))
        assertTrue(isSensitiveLine(panLine, excluded))
    }
}
