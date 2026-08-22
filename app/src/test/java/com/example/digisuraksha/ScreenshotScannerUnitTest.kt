package com.example.digisuraksha

import org.junit.Assert.*
import org.junit.Test

class ScreenshotScannerUnitTest {

    // ============================================================
    // 🏷️ EXCLUDED TYPE NORMALIZATION TESTS
    // ============================================================

    @Test
    fun testGetExcludedType_formatsCorrectly() {
        assertEquals("AADHAAR", ScreenshotScannerActivity.getExcludedType("Aadhaar Number"))
        assertEquals("AADHAAR", ScreenshotScannerActivity.getExcludedType("Aadhaar Number — 12-digit Aadhaar pattern found"))
        assertEquals("PHONE", ScreenshotScannerActivity.getExcludedType("Phone Number — 10-digit phone number found"))
        assertEquals("EMAIL", ScreenshotScannerActivity.getExcludedType("Email — Email address pattern found"))
        assertEquals("PAN", ScreenshotScannerActivity.getExcludedType("PAN Number — 10-character PAN format found"))
        assertEquals("CARD", ScreenshotScannerActivity.getExcludedType("Card Number — 16-digit card pattern found"))
        assertEquals("UPI", ScreenshotScannerActivity.getExcludedType("UPI ID — UPI VPA handle found"))
        assertEquals("UPI_QR", ScreenshotScannerActivity.getExcludedType("UPI QR Code — Payment QR code detected"))
        assertEquals("OTP", ScreenshotScannerActivity.getExcludedType("OTP — One-time passcode pattern found"))
        assertEquals("PASSWORD", ScreenshotScannerActivity.getExcludedType("Password — Password keyword pattern found"))
        assertEquals("VEHICLE", ScreenshotScannerActivity.getExcludedType("Vehicle Number — Vehicle registration plate found"))
        assertEquals("IP", ScreenshotScannerActivity.getExcludedType("IP Address — IPv4 network address found"))
        assertEquals("ADDRESS", ScreenshotScannerActivity.getExcludedType("Address — Street/area and PIN code found"))
        assertEquals("SCAM", ScreenshotScannerActivity.getExcludedType("Scam Message"))
        assertEquals("SUSPICIOUS", ScreenshotScannerActivity.getExcludedType("Suspicious Message"))
    }

    // ============================================================
    // 🎭 MASKING & EXCLUSION PREVIEWS
    // ============================================================

    @Test
    fun testDefaultMasking_allSensitiveTypesMasked() {
        val sample = "Aadhaar: 1234 5678 9012, Phone: 9876543210, Email: user@example.com"
        val masked = ScreenshotScannerActivity.buildMaskedText(sample, emptySet())

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
        val masked = ScreenshotScannerActivity.buildMaskedText(sample, excluded)

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
        val masked = ScreenshotScannerActivity.buildMaskedText(sample, excluded)

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
        val masked = ScreenshotScannerActivity.buildMaskedText(sample, excluded)

        assertTrue(masked.contains("1234 5678 9012"))
        assertTrue(masked.contains("user@example.com"))
        assertFalse(masked.contains("9876543210"))
        assertFalse(masked.contains("ABCDE1234F"))
        assertTrue(masked.contains("+91-XXXXX-XXXXX"))
        assertTrue(masked.contains("XXXXXXXXXX"))
    }

    // ============================================================
    // 🔍 LINE REDACTION & CONTEXT DETECTION
    // ============================================================

    @Test
    fun testIsSensitiveLine_excludeAadhaar_aadhaarLineNotBlurred() {
        val aadhaarLine = "9876 5432 1098"
        val phoneLine = "Call: +91 9876543210"

        assertTrue(ScreenshotScannerActivity.isSensitiveLine(aadhaarLine, emptySet()))
        assertTrue(ScreenshotScannerActivity.isSensitiveLine(phoneLine, emptySet()))

        assertFalse(ScreenshotScannerActivity.isSensitiveLine(aadhaarLine, setOf("AADHAAR")))
        assertTrue(ScreenshotScannerActivity.isSensitiveLine(phoneLine, setOf("AADHAAR")))
    }

    @Test
    fun testIsSensitiveLine_unspaced12DigitsWithoutContext_notBlurred() {
        val bareDigits = "123456789012"

        assertFalse(ScreenshotScannerActivity.isSensitiveLine(bareDigits, emptySet(), null))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(bareDigits, emptySet(), "Invoice total 123456789012 paid"))
    }

    @Test
    fun testIsSensitiveLine_unspacedAadhaarWithContextKeyword_blurredAndExcluded() {
        val unspacedAadhaar = "918765432109"
        val context = "Government of India UIDAI: 918765432109"

        assertTrue(ScreenshotScannerActivity.isSensitiveLine(unspacedAadhaar, emptySet(), context))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(unspacedAadhaar, setOf("AADHAAR"), context))
    }

    @Test
    fun testIsSensitiveLine_unspacedPhoneStartingWith91_classifiedAsPhoneWithoutAadhaarContext() {
        val phone91 = "918765432109"

        val decision = ScreenshotScannerActivity.evaluateLineRedaction(phone91, emptySet(), null)
        assertTrue(decision.shouldBlur)
        assertEquals(listOf("PHONE"), decision.detectedTypes)

        assertFalse(ScreenshotScannerActivity.isSensitiveLine(phone91, setOf("PHONE"), null))
    }

    @Test
    fun testIsSensitiveLine_unspacedAadhaarStartingWith91_notBlurredWhenExcluded() {
        val aadhaarLine91 = "918765432109" // 12-digit Aadhaar starting with 91

        // Default: blurred
        assertTrue(ScreenshotScannerActivity.isSensitiveLine(aadhaarLine91, emptySet()))

        // Exclude Aadhaar: NOT blurred even though it starts with 91
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(aadhaarLine91, setOf("AADHAAR")))
    }

    @Test
    fun testIsSensitiveLine_excludePhone_phoneLineNotBlurred() {
        val aadhaarLine = "9876 5432 1098"
        val phoneLine = "Call: +91 9876543210"

        assertTrue(ScreenshotScannerActivity.isSensitiveLine(aadhaarLine, setOf("PHONE")))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(phoneLine, setOf("PHONE")))
    }

    @Test
    fun testIsSensitiveLine_excludeMultiple_bothLinesNotBlurred() {
        val aadhaarLine = "9876 5432 1098"
        val emailLine = "Contact: test@gmail.com"
        val panLine = "PAN: ABCDE1234F"

        val excluded = setOf("AADHAAR", "EMAIL")

        assertFalse(ScreenshotScannerActivity.isSensitiveLine(aadhaarLine, excluded))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(emailLine, excluded))
        assertTrue(ScreenshotScannerActivity.isSensitiveLine(panLine, excluded))
    }

    @Test
    fun testIsSensitiveLine_otpSingleLine_blurredAndExcluded() {
        val otpLine = "Your OTP is 482913 for login"

        assertTrue(ScreenshotScannerActivity.isSensitiveLine(otpLine, emptySet()))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(otpLine, setOf("OTP")))
    }

    @Test
    fun testIsSensitiveLine_otpCrossLine_blurredAndExcluded() {
        val line1 = "Your OTP is"
        val line2 = "482913"
        val context = "$line1\n$line2"

        assertTrue(ScreenshotScannerActivity.isSensitiveLine(line1, emptySet(), context))
        assertTrue(ScreenshotScannerActivity.isSensitiveLine(line2, emptySet(), context))

        assertFalse(ScreenshotScannerActivity.isSensitiveLine(line1, setOf("OTP"), context))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(line2, setOf("OTP"), context))
    }

    @Test
    fun testIsSensitiveLine_addressSingleLine_blurredAndExcluded() {
        val addressLine = "123, MG Road, Sector 4, 400001"

        assertTrue(ScreenshotScannerActivity.isSensitiveLine(addressLine, emptySet()))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(addressLine, setOf("ADDRESS")))
    }

    @Test
    fun testIsSensitiveLine_addressCrossLine_blurredAndExcluded() {
        val line1 = "123, MG Road, Sector 4"
        val line2 = "400001"
        val context = "$line1\n$line2"

        assertTrue(ScreenshotScannerActivity.isSensitiveLine(line1, emptySet(), context))
        assertTrue(ScreenshotScannerActivity.isSensitiveLine(line2, emptySet(), context))

        assertFalse(ScreenshotScannerActivity.isSensitiveLine(line1, setOf("ADDRESS"), context))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(line2, setOf("ADDRESS"), context))
    }

    // ============================================================
    // 🛡️ OVERLAP PROTECTION TESTS
    // ============================================================

    @Test
    fun testOverlap_aadhaarVsPhone_unspacedAadhaarPreservedWhenExcluded() {
        val text = "Aadhaar: 918765432109, Phone: 9876543210"
        val masked = ScreenshotScannerActivity.buildMaskedText(text, setOf("AADHAAR"))

        assertTrue(masked.contains("918765432109"))
        assertFalse(masked.contains("9876543210"))
        assertTrue(masked.contains("+91-XXXXX-XXXXX"))
    }

    @Test
    fun testOverlap_aadhaarVsPhone_detectPhoneFalseForAadhaarOnly() {
        val text = "Aadhaar: 918765432109"
        val isAadhaar = ScreenshotScannerActivity.detectAadhaar(text, text.lowercase())
        assertTrue(isAadhaar)

        val isPhone = ScreenshotScannerActivity.detectPhone(text, isAadhaar)
        assertFalse("Phone should not be detected from Aadhaar number alone", isPhone)
    }

    @Test
    fun testOverlap_cardVsNonCard16Digits_distinguishesCardByKeyword() {
        val cardText = "Debit Card: 4111 2222 3333 4444"
        val orderText = "Order ID: 4111 2222 3333 4444"

        val maskedCard = ScreenshotScannerActivity.buildMaskedText(cardText, emptySet())
        val maskedOrder = ScreenshotScannerActivity.buildMaskedText(orderText, emptySet())

        assertTrue(maskedCard.contains("XXXX XXXX XXXX XXXX"))
        assertFalse(maskedCard.contains("4111 2222 3333 4444"))

        assertFalse(maskedOrder.contains("XXXX XXXX XXXX XXXX"))
        assertTrue(maskedOrder.contains("4111 2222 3333 4444"))

        assertTrue(ScreenshotScannerActivity.isSensitiveLine(cardText, emptySet()))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(orderText, emptySet()))
    }

    @Test
    fun testOverlap_cardCrossLineKeyword_blurredWithContext() {
        val line1 = "Visa Card Payment"
        val line2 = "4111 2222 3333 4444"
        val context = "$line1\n$line2"

        assertTrue(ScreenshotScannerActivity.isSensitiveLine(line2, emptySet(), context))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(line2, setOf("CARD"), context))
    }

    @Test
    fun testOverlap_cardAndAadhaar_placeholderSwapPaths() {
        val combo = "Debit Card: 4111 2222 3333 4444, Aadhaar: 1234 5678 9012"

        // Path A: CARD excluded / AADHAAR included -> Card preserved, Aadhaar masked
        val maskedCardExcluded = ScreenshotScannerActivity.buildMaskedText(combo, setOf("CARD"))
        assertTrue("Card must stay unmasked when CARD is excluded", maskedCardExcluded.contains("4111 2222 3333 4444"))
        assertFalse("Aadhaar must be masked when AADHAAR is included", maskedCardExcluded.contains("1234 5678 9012"))
        assertTrue(maskedCardExcluded.contains("XXXX XXXX XXXX"))

        // Path B: AADHAAR excluded / CARD included -> Aadhaar preserved, Card masked
        val maskedAadhaarExcluded = ScreenshotScannerActivity.buildMaskedText(combo, setOf("AADHAAR"))
        assertFalse("Card must be masked when CARD is included", maskedAadhaarExcluded.contains("4111 2222 3333 4444"))
        assertTrue(maskedAadhaarExcluded.contains("XXXX XXXX XXXX XXXX"))
        assertTrue("Aadhaar must stay unmasked when AADHAAR is excluded", maskedAadhaarExcluded.contains("1234 5678 9012"))

        // Path C: Both included (default) -> Both masked
        val maskedBothIncluded = ScreenshotScannerActivity.buildMaskedText(combo, emptySet())
        assertTrue(maskedBothIncluded.contains("XXXX XXXX XXXX XXXX"))
        assertTrue(maskedBothIncluded.contains("XXXX XXXX XXXX"))
        assertFalse(maskedBothIncluded.contains("4111 2222 3333 4444"))
        assertFalse(maskedBothIncluded.contains("1234 5678 9012"))

        // Path D: Both excluded -> Both unmasked
        val maskedBothExcluded = ScreenshotScannerActivity.buildMaskedText(combo, setOf("CARD", "AADHAAR"))
        assertTrue(maskedBothExcluded.contains("4111 2222 3333 4444"))
        assertTrue(maskedBothExcluded.contains("1234 5678 9012"))
        assertFalse(maskedBothExcluded.contains("XXXX"))
    }

    @Test
    fun testOverlap_upiVsEmail_properClassification() {
        val upiSample = "Send to user@okaxis"
        val emailSample = "Contact support@paytm.com"

        val maskedUpi = ScreenshotScannerActivity.buildMaskedText(upiSample, emptySet())
        val maskedEmail = ScreenshotScannerActivity.buildMaskedText(emailSample, emptySet())

        assertTrue(maskedUpi.contains("xxx@xxx"))
        assertFalse(maskedUpi.contains("user@okaxis"))

        assertTrue(maskedEmail.contains("xxx@xxx.com"))
        assertFalse(maskedEmail.contains("support@paytm.com"))

        val excludeUpiMasked = ScreenshotScannerActivity.buildMaskedText("$upiSample, $emailSample", setOf("UPI"))
        assertTrue(excludeUpiMasked.contains("user@okaxis"))
        assertTrue(excludeUpiMasked.contains("xxx@xxx.com"))

        val excludeEmailMasked = ScreenshotScannerActivity.buildMaskedText("$upiSample, $emailSample", setOf("EMAIL"))
        assertTrue(excludeEmailMasked.contains("xxx@xxx"))
        assertTrue(excludeEmailMasked.contains("support@paytm.com"))
    }

    @Test
    fun testEvaluateLineRedaction_decisionDetailsAndReasons() {
        val line = "PAN: ABCDE1234F"
        val decision = ScreenshotScannerActivity.evaluateLineRedaction(line, emptySet())

        assertTrue(decision.shouldBlur)
        assertEquals(listOf("PAN"), decision.detectedTypes)
        assertTrue(decision.reason.contains("PAN not in excludedTypes"))

        val excludedDecision = ScreenshotScannerActivity.evaluateLineRedaction(line, setOf("PAN"))
        assertFalse(excludedDecision.shouldBlur)
        assertTrue(excludedDecision.reason.contains("Skipped because all detected types (PAN) are in excludedTypes"))
    }

    // ============================================================
    // 🚨 DAY 5: FRAUD EVALUATION SUITE
    // ============================================================

    @Test
    fun testFraud_zeroSignals_notMediumNotHigh() {
        val normalText = "Hello, your package has been delivered to your front door. Thank you for shopping with us."
        val result = ScreenshotScannerActivity.evaluateFraud(normalText)

        assertFalse("Zero signals must not trigger fraud high", result.isFraudHigh)
        assertFalse("Zero signals must not trigger fraud medium", result.isFraudMedium)
        assertTrue("Findings list must be empty for benign text", result.findings.isEmpty())
    }

    @Test
    fun testFraud_oneMediumSignal_notMedium() {
        val textWithOneSignal = "Please submit the regular monthly feedback report asap."
        val result = ScreenshotScannerActivity.evaluateFraud(textWithOneSignal)

        assertFalse("Exactly 1 medium signal must NOT produce fraud medium", result.isFraudMedium)
        assertFalse("1 medium signal must not produce fraud high", result.isFraudHigh)
        assertTrue("Findings must remain empty when threshold (>=2) not met", result.findings.isEmpty())
    }

    @Test
    fun testFraud_twoOrMoreMediumSignals_mediumOnly() {
        val textWithTwoSignals = "Urgent: Limited offer on all electronics! Act now to avail discount."
        val result = ScreenshotScannerActivity.evaluateFraud(textWithTwoSignals)

        assertFalse("Medium signals alone must NOT produce fraud high", result.isFraudHigh)
        assertTrue("2+ medium signals must produce fraud medium", result.isFraudMedium)
        assertTrue("Findings must include Suspicious Message", result.findings.contains("Suspicious Message"))
        assertFalse("Findings must not include Scam Message", result.findings.contains("Scam Message"))
    }

    @Test
    fun testFraud_oneHighPhrase_highOnly() {
        val textWithHighPhrase = "Congratulations! You have won a cash prize of ₹5,00,000 in our lucky draw."
        val result = ScreenshotScannerActivity.evaluateFraud(textWithHighPhrase)

        assertTrue("High-confidence phrase must produce fraud high", result.isFraudHigh)
        assertTrue("Findings must include Scam Message", result.findings.contains("Scam Message"))
    }

    @Test
    fun testFraud_highAndMediumTogether_bothFlagsAndFindingsDeduplicated() {
        val combinedText = "Urgent! Act now! Congratulations, you have won ₹10,00,000 cash prize."
        val result = ScreenshotScannerActivity.evaluateFraud(combinedText)

        assertTrue("Must flag fraud high", result.isFraudHigh)
        assertTrue("Must flag fraud medium", result.isFraudMedium)
        assertTrue("Findings must contain Scam Message", result.findings.contains("Scam Message"))
        assertTrue("Findings must contain Suspicious Message", result.findings.contains("Suspicious Message"))
        assertEquals("Findings should contain exactly 2 distinct entries", 2, result.findings.size)
    }

    @Test
    fun testFraud_multipleHighPhrases_scamMessageFindingDeduplicated() {
        val multiHighText = "You have won ₹10,00,000! Claim your prize immediately. Update your KYC now or account will be suspended."
        val result = ScreenshotScannerActivity.evaluateFraud(multiHighText)

        assertTrue(result.isFraudHigh)
        assertEquals("Scam Message finding must not be duplicated", 1, result.findings.count { it == "Scam Message" })
    }

    // ============================================================
    // 📲 DAY 5: UPI QR PARSER SUITE
    // ============================================================

    @Test
    fun testUpiQr_validDeepLink_paPnAmExtracted() {
        val rawValue = "upi://pay?pa=merchant@okhdfcbank&pn=SuperMart&am=450.00&cu=INR"
        val result = ScreenshotScannerActivity.parseUpiQrPayload(rawValue)

        assertTrue("Must detect valid UPI QR payload", result.isUpiQr)
        assertEquals("SuperMart", result.payee)
        assertEquals("450.00", result.amount)
    }

    @Test
    fun testUpiQr_urlEncodedPayeeName_decodedCorrectly() {
        val rawValue = "upi://pay?pa=store@okaxis&pn=Super%20Mart&am=999.50"
        val result = ScreenshotScannerActivity.parseUpiQrPayload(rawValue)

        assertTrue("Must detect valid UPI QR payload", result.isUpiQr)
        assertEquals("Super Mart", result.payee)
        assertEquals("999.50", result.amount)
    }

    @Test
    fun testUpiQr_validDeepLink_paOnly() {
        val rawValue = "upi://pay?pa=rajesh.kumar@paytm"
        val result = ScreenshotScannerActivity.parseUpiQrPayload(rawValue)

        assertTrue("Must detect valid UPI QR payload", result.isUpiQr)
        assertEquals("rajesh.kumar@paytm", result.payee)
        assertNull("Amount should be null when not present", result.amount)
    }

    @Test
    fun testUpiQr_validPlainHandle() {
        val rawValue = "vendor.store@okaxis"
        val result = ScreenshotScannerActivity.parseUpiQrPayload(rawValue)

        assertTrue("Must detect plain UPI handle QR payload", result.isUpiQr)
        assertEquals("vendor.store@okaxis", result.payee)
        assertNull("Amount should be null for plain handle", result.amount)
    }

    @Test
    fun testUpiQr_nonUpiText_returnsFalse() {
        val urls = listOf(
            "https://www.example.com/products/12345",
            "WIFI:S:HomeNet;T:WPA;P:secretPass;;",
            "Hello World, this is just arbitrary text",
            "mailto:support@google.com"
        )

        for (url in urls) {
            val result = ScreenshotScannerActivity.parseUpiQrPayload(url)
            assertFalse("Non-UPI text '$url' must return isUpiQr = false", result.isUpiQr)
            assertNull(result.payee)
            assertNull(result.amount)
        }
    }

    // ============================================================
    // 🧪 DAY 5: 14 INDIVIDUAL DETECTION & EXCLUSION TESTS
    // ============================================================

    @Test
    fun testCategory_01_UPI_detectionAndExclusion() {
        val line = "Transfer to rahul@okhdfcbank"
        val isUpi = ScreenshotScannerActivity.upiRegex.containsMatchIn(line)
        assertTrue("UPI pattern must match", isUpi)

        // Masking
        val maskedDefault = ScreenshotScannerActivity.buildMaskedText(line, emptySet())
        assertTrue(maskedDefault.contains("xxx@xxx"))
        val maskedExcluded = ScreenshotScannerActivity.buildMaskedText(line, setOf("UPI"))
        assertTrue(maskedExcluded.contains("rahul@okhdfcbank"))

        // Redaction
        assertTrue(ScreenshotScannerActivity.isSensitiveLine(line, emptySet()))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(line, setOf("UPI")))
    }

    @Test
    fun testCategory_02_PAN_detectionAndExclusion() {
        val line = "Permanent Account Number: ABCDE1234F"
        val isPan = ScreenshotScannerActivity.panRegex.containsMatchIn(line)
        assertTrue("PAN pattern must match", isPan)

        // Masking
        val maskedDefault = ScreenshotScannerActivity.buildMaskedText(line, emptySet())
        assertTrue(maskedDefault.contains("XXXXXXXXXX"))
        val maskedExcluded = ScreenshotScannerActivity.buildMaskedText(line, setOf("PAN"))
        assertTrue(maskedExcluded.contains("ABCDE1234F"))

        // Redaction
        assertTrue(ScreenshotScannerActivity.isSensitiveLine(line, emptySet()))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(line, setOf("PAN")))
    }

    @Test
    fun testCategory_03_CARD_detectionAndExclusion() {
        val line = "HDFC Credit Card: 4111 2222 3333 4444"
        val lower = line.lowercase()
        val isCard = ScreenshotScannerActivity.cardRegex.containsMatchIn(line) &&
                ScreenshotScannerActivity.cardKeywords.any { lower.contains(it) }
        assertTrue("Card pattern with keyword must match", isCard)

        // Masking
        val maskedDefault = ScreenshotScannerActivity.buildMaskedText(line, emptySet())
        assertTrue(maskedDefault.contains("XXXX XXXX XXXX XXXX"))
        val maskedExcluded = ScreenshotScannerActivity.buildMaskedText(line, setOf("CARD"))
        assertTrue(maskedExcluded.contains("4111 2222 3333 4444"))

        // Redaction
        assertTrue(ScreenshotScannerActivity.isSensitiveLine(line, emptySet()))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(line, setOf("CARD")))
    }

    @Test
    fun testCategory_04_AADHAAR_detectionAndExclusion() {
        val line = "Aadhaar Number: 9876 5432 1098"
        val lower = line.lowercase()
        val isAadhaar = ScreenshotScannerActivity.detectAadhaar(line, lower)
        assertTrue("Aadhaar must be detected", isAadhaar)

        // Masking
        val maskedDefault = ScreenshotScannerActivity.buildMaskedText(line, emptySet())
        assertTrue(maskedDefault.contains("XXXX XXXX XXXX"))
        val maskedExcluded = ScreenshotScannerActivity.buildMaskedText(line, setOf("AADHAAR"))
        assertTrue(maskedExcluded.contains("9876 5432 1098"))

        // Redaction
        assertTrue(ScreenshotScannerActivity.isSensitiveLine(line, emptySet()))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(line, setOf("AADHAAR")))
    }

    @Test
    fun testCategory_05_PASSWORD_detectionAndExclusion() {
        val line = "Account password: MySecurePass123"
        val isPassword = ScreenshotScannerActivity.passwordKeywordRegex.containsMatchIn(line)
        assertTrue("Password keyword must match", isPassword)

        // Masking
        val maskedDefault = ScreenshotScannerActivity.buildMaskedText(line, emptySet())
        assertTrue(maskedDefault.contains("password: ********"))
        val maskedExcluded = ScreenshotScannerActivity.buildMaskedText(line, setOf("PASSWORD"))
        assertTrue(maskedExcluded.contains("MySecurePass123"))

        // Redaction
        assertTrue(ScreenshotScannerActivity.isSensitiveLine(line, emptySet()))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(line, setOf("PASSWORD")))
    }

    @Test
    fun testCategory_06_ADDRESS_detectionAndExclusion() {
        val line = "Flat 402, Sunshine Apartments, MG Road, Pune, 411001"
        val lower = line.lowercase()
        val hasKeyword = ScreenshotScannerActivity.addressKeywordRegex.containsMatchIn(lower)
        val hasPin = ScreenshotScannerActivity.pincodeRegex.containsMatchIn(line)
        assertTrue("Address must match keyword and PIN", hasKeyword && hasPin)

        // Redaction
        assertTrue(ScreenshotScannerActivity.isSensitiveLine(line, emptySet()))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(line, setOf("ADDRESS")))
    }

    @Test
    fun testCategory_07_OTP_detectionAndExclusion() {
        val line = "Your one-time login OTP is 839201. Valid for 5 mins."
        val lower = line.lowercase()
        val isOtp = ScreenshotScannerActivity.detectOtp(line, lower)
        assertTrue("OTP must be detected", isOtp)

        // Masking
        val maskedDefault = ScreenshotScannerActivity.buildMaskedText(line, emptySet())
        assertTrue(maskedDefault.contains("XXXXXX"))
        val maskedExcluded = ScreenshotScannerActivity.buildMaskedText(line, setOf("OTP"))
        assertTrue(maskedExcluded.contains("839201"))

        // Redaction
        assertTrue(ScreenshotScannerActivity.isSensitiveLine(line, emptySet()))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(line, setOf("OTP")))
    }

    @Test
    fun testCategory_08_FRAUD_HIGH_detectionAndExclusion() {
        val line = "Congratulations! You have won ₹25,00,000 lucky prize."
        val result = ScreenshotScannerActivity.evaluateFraud(line)
        assertTrue("Fraud-high must be detected", result.isFraudHigh)
        assertTrue(result.findings.contains("Scam Message"))
        assertEquals("SCAM", ScreenshotScannerActivity.getExcludedType("Scam Message"))
    }

    @Test
    fun testCategory_09_FRAUD_MEDIUM_detectionAndExclusion() {
        val line = "Urgent action required! Limited time offer, act now."
        val result = ScreenshotScannerActivity.evaluateFraud(line)
        assertFalse("Not high fraud", result.isFraudHigh)
        assertTrue("Fraud-medium must be detected for >=2 signals", result.isFraudMedium)
        assertTrue(result.findings.contains("Suspicious Message"))
        assertEquals("SUSPICIOUS", ScreenshotScannerActivity.getExcludedType("Suspicious Message"))
    }

    @Test
    fun testCategory_10_UPI_QR_detectionAndExclusion() {
        val payload = "upi://pay?pa=store@upi&pn=RetailStore&am=1200"
        val result = ScreenshotScannerActivity.parseUpiQrPayload(payload)
        assertTrue("UPI QR must be parsed", result.isUpiQr)
        assertEquals("RetailStore", result.payee)
        assertEquals("1200", result.amount)
        assertEquals("UPI_QR", ScreenshotScannerActivity.getExcludedType("UPI QR Code — Payment QR code detected"))
    }

    @Test
    fun testCategory_11_EMAIL_detectionAndExclusion() {
        val line = "Billing email: john.doe@company.org"
        val isEmail = ScreenshotScannerActivity.emailRegex.containsMatchIn(line)
        assertTrue("Email pattern must match", isEmail)

        // Masking
        val maskedDefault = ScreenshotScannerActivity.buildMaskedText(line, emptySet())
        assertTrue(maskedDefault.contains("xxx@xxx.com"))
        val maskedExcluded = ScreenshotScannerActivity.buildMaskedText(line, setOf("EMAIL"))
        assertTrue(maskedExcluded.contains("john.doe@company.org"))

        // Redaction
        assertTrue(ScreenshotScannerActivity.isSensitiveLine(line, emptySet()))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(line, setOf("EMAIL")))
    }

    @Test
    fun testCategory_12_PHONE_detectionAndExclusion() {
        val line = "Contact Customer Helpline at +91 9876543210"
        val isPhone = ScreenshotScannerActivity.detectPhone(line, isAadhaar = false)
        assertTrue("Phone must be detected", isPhone)

        // Masking
        val maskedDefault = ScreenshotScannerActivity.buildMaskedText(line, emptySet())
        assertTrue(maskedDefault.contains("+91-XXXXX-XXXXX"))
        val maskedExcluded = ScreenshotScannerActivity.buildMaskedText(line, setOf("PHONE"))
        assertTrue(maskedExcluded.contains("9876543210"))

        // Redaction
        assertTrue(ScreenshotScannerActivity.isSensitiveLine(line, emptySet()))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(line, setOf("PHONE")))
    }

    @Test
    fun testCategory_13_VEHICLE_detectionAndExclusion() {
        val line = "Registration Plate: DL01AB1234"
        val isVehicle = ScreenshotScannerActivity.vehicleRegex.containsMatchIn(line)
        assertTrue("Vehicle regex must match", isVehicle)

        // Redaction
        assertTrue(ScreenshotScannerActivity.isSensitiveLine(line, emptySet()))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(line, setOf("VEHICLE")))
    }

    @Test
    fun testCategory_14_IP_detectionAndExclusion() {
        val line = "Server Gateway IP: 192.168.1.100"
        val isIp = ScreenshotScannerActivity.ipRegex.containsMatchIn(line)
        assertTrue("IP regex must match", isIp)

        // Redaction
        assertTrue(ScreenshotScannerActivity.isSensitiveLine(line, emptySet()))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(line, setOf("IP")))
    }

    // ============================================================
    // 📸 DAY 5: 14 SYNTHETIC FULL-OCR SCREENSHOT REGRESSION PAYLOADS
    // ============================================================

    @Test
    fun testScreenshotPayload_01_gpayUpiTransaction() {
        val ocrPayload = """
            Google Pay
            Payment to Rajesh Kirana
            UPI ID: rajesh.kirana@okaxis
            Amount: ₹350.00
            Completed on 21 Aug 2026, 04:30 PM
            UPI Transaction ID: 423456789012
        """.trimIndent()

        val masked = ScreenshotScannerActivity.buildMaskedText(ocrPayload, emptySet())
        assertTrue("UPI ID must be masked by default", masked.contains("xxx@xxx"))
        assertFalse(masked.contains("rajesh.kirana@okaxis"))

        val maskedExcluded = ScreenshotScannerActivity.buildMaskedText(ocrPayload, setOf("UPI"))
        assertTrue("UPI ID must remain visible when excluded", maskedExcluded.contains("rajesh.kirana@okaxis"))

        assertTrue(ScreenshotScannerActivity.isSensitiveLine("UPI ID: rajesh.kirana@okaxis", emptySet(), ocrPayload))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine("UPI ID: rajesh.kirana@okaxis", setOf("UPI"), ocrPayload))
    }

    @Test
    fun testScreenshotPayload_02_incomeTaxPanPortal() {
        val ocrPayload = """
            Income Tax Department e-Filing
            Taxpayer Name: ANANYA SHARMA
            Permanent Account Number (PAN): BCDPA1234K
            Status: Active & Linked with Aadhaar
            Assessment Year: 2026-27
        """.trimIndent()

        val masked = ScreenshotScannerActivity.buildMaskedText(ocrPayload, emptySet())
        assertTrue("PAN must be masked by default", masked.contains("XXXXXXXXXX"))
        assertFalse(masked.contains("BCDPA1234K"))

        val maskedExcluded = ScreenshotScannerActivity.buildMaskedText(ocrPayload, setOf("PAN"))
        assertTrue("PAN must remain visible when excluded", maskedExcluded.contains("BCDPA1234K"))

        assertTrue(ScreenshotScannerActivity.isSensitiveLine("Permanent Account Number (PAN): BCDPA1234K", emptySet(), ocrPayload))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine("Permanent Account Number (PAN): BCDPA1234K", setOf("PAN"), ocrPayload))
    }

    @Test
    fun testScreenshotPayload_03_creditCardCheckout() {
        val ocrPayload = """
            Amazon India Checkout
            Payment Method: Visa Credit Card
            Card Number: 4532 7512 8901 2345
            Order ID: OD98234710293
            Total Amount: ₹2,499.00
        """.trimIndent()

        val masked = ScreenshotScannerActivity.buildMaskedText(ocrPayload, emptySet())
        assertTrue("Card must be masked by default", masked.contains("XXXX XXXX XXXX XXXX"))
        assertFalse(masked.contains("4532 7512 8901 2345"))

        val maskedExcluded = ScreenshotScannerActivity.buildMaskedText(ocrPayload, setOf("CARD"))
        assertTrue("Card must stay visible when excluded", maskedExcluded.contains("4532 7512 8901 2345"))

        val cardLine = "Card Number: 4532 7512 8901 2345"
        assertTrue(ScreenshotScannerActivity.isSensitiveLine(cardLine, emptySet(), ocrPayload))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(cardLine, setOf("CARD"), ocrPayload))
    }

    @Test
    fun testScreenshotPayload_04_aadhaarEkycVerification() {
        val ocrPayload = """
            Unique Identification Authority of India
            Government of India
            Mera Aadhaar, Meri Pehchan
            Amit Kumar Verma
            DOB: 14/08/1990
            Gender: Male
            5432 8765 1234
        """.trimIndent()

        val masked = ScreenshotScannerActivity.buildMaskedText(ocrPayload, emptySet())
        assertTrue("Aadhaar must be masked by default", masked.contains("XXXX XXXX XXXX"))
        assertFalse(masked.contains("5432 8765 1234"))

        val maskedExcluded = ScreenshotScannerActivity.buildMaskedText(ocrPayload, setOf("AADHAAR"))
        assertTrue("Aadhaar must stay visible when excluded", maskedExcluded.contains("5432 8765 1234"))

        val aadhaarLine = "5432 8765 1234"
        assertTrue(ScreenshotScannerActivity.isSensitiveLine(aadhaarLine, emptySet(), ocrPayload))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(aadhaarLine, setOf("AADHAAR"), ocrPayload))
    }

    @Test
    fun testScreenshotPayload_05_wifiPasswordSettings() {
        val ocrPayload = """
            Wi-Fi Router Configuration
            SSID: Home_Fiber_5G
            Security: WPA3-Personal
            Default password: SuperSecretP@ss99
            IP Address: 192.168.0.1
        """.trimIndent()

        val masked = ScreenshotScannerActivity.buildMaskedText(ocrPayload, emptySet())
        assertTrue("Password must be masked", masked.contains("password: ********"))
        assertFalse(masked.contains("SuperSecretP@ss99"))

        val maskedExcluded = ScreenshotScannerActivity.buildMaskedText(ocrPayload, setOf("PASSWORD"))
        assertTrue("Password must remain visible when excluded", maskedExcluded.contains("SuperSecretP@ss99"))

        val passLine = "Default password: SuperSecretP@ss99"
        assertTrue(ScreenshotScannerActivity.isSensitiveLine(passLine, emptySet(), ocrPayload))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(passLine, setOf("PASSWORD"), ocrPayload))
    }

    @Test
    fun testScreenshotPayload_06_courierAddressDeliveryLabel() {
        val ocrPayload = """
            BlueDart Express Waybill
            Consignee: Priya Nair
            Flat No 102, Palm Grove Apartments, Sector 15
            Navi Mumbai, Maharashtra, 400614
            Phone: +91 9820123456
        """.trimIndent()

        val addressLine = "Flat No 102, Palm Grove Apartments, Sector 15, Navi Mumbai, 400614"
        assertTrue(ScreenshotScannerActivity.isSensitiveLine(addressLine, emptySet(), ocrPayload))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(addressLine, setOf("ADDRESS"), ocrPayload))
    }

    @Test
    fun testScreenshotPayload_07_bankNetbankingOtpSms() {
        val ocrPayload = """
            AXIS BANK Alert:
            Your login OTP is 584920 for NetBanking access.
            Valid for 10 minutes. Do not share your OTP with anyone,
            including bank employees.
        """.trimIndent()

        val masked = ScreenshotScannerActivity.buildMaskedText(ocrPayload, emptySet())
        assertTrue("OTP digits must be masked", masked.contains("XXXXXX"))
        assertFalse(masked.contains("584920"))

        val maskedExcluded = ScreenshotScannerActivity.buildMaskedText(ocrPayload, setOf("OTP"))
        assertTrue("OTP must remain visible when excluded", maskedExcluded.contains("584920"))

        val otpLine = "Your login OTP is 584920 for NetBanking access."
        assertTrue(ScreenshotScannerActivity.isSensitiveLine(otpLine, emptySet(), ocrPayload))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(otpLine, setOf("OTP"), ocrPayload))
    }

    @Test
    fun testScreenshotPayload_08_whatsappLotteryScam() {
        val ocrPayload = """
            WhatsApp Message from +91 9988776655:
            Dear customer, Congratulations! You have won ₹25,00,000 in KBC Lucky Draw.
            Send processing fee ₹2,500 to claim your prize money immediately.
        """.trimIndent()

        val result = ScreenshotScannerActivity.evaluateFraud(ocrPayload)
        assertTrue("Must detect fraud high", result.isFraudHigh)
        assertTrue(result.findings.contains("Scam Message"))
    }

    @Test
    fun testScreenshotPayload_09_electricityCutScam() {
        val ocrPayload = """
            Urgent Alert from Electricity Board:
            Dear Consumer, your electricity will be disconnected tonight. Pay immediately to avoid disconnection.
            Contact helpline at 9876543210.
        """.trimIndent()

        val result = ScreenshotScannerActivity.evaluateFraud(ocrPayload)
        assertTrue("Must detect high-risk power cut scam", result.isFraudHigh)
        assertTrue(result.findings.contains("Scam Message"))
    }

    @Test
    fun testScreenshotPayload_10_workFromHomeScam() {
        val ocrPayload = """
            Telegram Job Offer:
            Work from home and earn ₹5000 daily with simple video liking tasks.
            Guaranteed return on small initial deposit. Act now!
        """.trimIndent()

        val result = ScreenshotScannerActivity.evaluateFraud(ocrPayload)
        assertTrue("Must detect work from home daily income scam", result.isFraudHigh)
        assertTrue(result.findings.contains("Scam Message"))
    }

    @Test
    fun testScreenshotPayload_11_urgentMediumSuspiciousMessage() {
        val ocrPayload = """
            Special Announcement:
            Limited period offer! Free recharge reward eligible for your number.
            Do not delay, claim now.
        """.trimIndent()

        val result = ScreenshotScannerActivity.evaluateFraud(ocrPayload)
        assertFalse("Must not be fraud high", result.isFraudHigh)
        assertTrue("Must be flagged as fraud medium (>=2 signals)", result.isFraudMedium)
        assertTrue(result.findings.contains("Suspicious Message"))
    }

    @Test
    fun testScreenshotPayload_12_supportTicketEmail() {
        val ocrPayload = """
            Zendesk Ticket #89214
            Assigned Agent: sneha.support@servicedesk.org
            Customer: user.query@domain.co.in
            Priority: High
        """.trimIndent()

        val masked = ScreenshotScannerActivity.buildMaskedText(ocrPayload, emptySet())
        assertTrue("Emails must be masked", masked.contains("xxx@xxx.com"))
        assertFalse(masked.contains("sneha.support@servicedesk.org"))

        val maskedExcluded = ScreenshotScannerActivity.buildMaskedText(ocrPayload, setOf("EMAIL"))
        assertTrue("Emails must remain visible when excluded", maskedExcluded.contains("sneha.support@servicedesk.org"))
        assertTrue(maskedExcluded.contains("user.query@domain.co.in"))
    }

    @Test
    fun testScreenshotPayload_13_trafficPoliceVehicleChallan() {
        val ocrPayload = """
            Delhi Traffic Police e-Challan
            Challan No: DL8923481023
            Vehicle Reg No: DL04CA9876
            Offense: Over Speeding (68 km/h in 50 km/h zone)
            Penalty Amount: ₹2,000
        """.trimIndent()

        val vehicleLine = "Vehicle Reg No: DL04CA9876"
        assertTrue(ScreenshotScannerActivity.isSensitiveLine(vehicleLine, emptySet(), ocrPayload))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(vehicleLine, setOf("VEHICLE"), ocrPayload))
    }

    @Test
    fun testScreenshotPayload_14_serverDiagnosticsIpDashboard() {
        val ocrPayload = """
            AWS CloudWatch Metrics
            Cluster: Prod-Kube-01
            Master Node IP: 10.0.12.45
            Public Ingress IP: 54.210.88.19
            Status: Healthy
        """.trimIndent()

        val ipLine = "Public Ingress IP: 54.210.88.19"
        assertTrue(ScreenshotScannerActivity.isSensitiveLine(ipLine, emptySet(), ocrPayload))
        assertFalse(ScreenshotScannerActivity.isSensitiveLine(ipLine, setOf("IP"), ocrPayload))
    }
}