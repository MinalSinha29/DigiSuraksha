package com.example.digisuraksha

import android.app.Activity
import android.content.Intent
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

    private lateinit var imageView: ImageView
    private lateinit var extractedText: TextView
    private lateinit var riskLevel: TextView
    private lateinit var fraudWarning: TextView
    private lateinit var shareButton: Button
    private lateinit var explanationText: TextView
    private lateinit var tipsText: TextView

    private var blurredBitmap: Bitmap? = null

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

        selectButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, PICK_IMAGE)
        }

        shareButton.setOnClickListener {

            val options = arrayOf("Share Masked Text", "Share Blurred Image")

            AlertDialog.Builder(this)
                .setTitle("Choose Sharing Option")
                .setItems(options) { _, which ->
                    when (which) {

                        0 -> {
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
                    }
                }.show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK) {

            val imageUri = data?.data ?: return
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
            val image = InputImage.fromBitmap(bitmap, 0)

            val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val barcodeScanner = BarcodeScanning.getClient()

            val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBitmap)

            val phoneRegex = Regex("(\\+91[\\s-]?)?[6-9]\\d{4}[\\s-]?\\d{5}")
            val upiRegex = Regex("\\b[\\w.-]+@[a-zA-Z]+\\b")
            val emailRegex = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.(com|in|edu|org|net)")

            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->

                    val resultText = visionText.text
                    val lowerText = resultText.lowercase()

                    // ===== FRAUD =====
                    val isFraud =
                        (lowerText.contains("won") || lowerText.contains("prize") || lowerText.contains("lottery")) &&
                                (lowerText.contains("click") || lowerText.contains("claim") || lowerText.contains("urgent"))

                    fraudWarning.text = if (isFraud) "⚠ Possible Fraud Detected" else ""

                    // ===== TEXT BLUR =====
                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            for (element in line.elements) {

                                val text = element.text
                                val box = element.boundingBox ?: continue

                                val isSensitive =
                                    phoneRegex.containsMatchIn(text) ||
                                            emailRegex.containsMatchIn(text) ||
                                            upiRegex.containsMatchIn(text) ||
                                            Regex("\\b\\d{4,6}\\b").containsMatchIn(text)

                                if (isSensitive) {
                                    blurRegion(canvas, mutableBitmap, box)
                                }
                            }
                        }
                    }

                    // ===== QR BLUR =====
                    barcodeScanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            for (barcode in barcodes ?: emptyList()) {
                                val box = barcode.boundingBox ?: continue
                                blurRegion(canvas, mutableBitmap, box)
                            }

                            imageView.setImageBitmap(mutableBitmap)
                            blurredBitmap = mutableBitmap
                        }

                    // ===== MASK =====
                    var maskedText = resultText
                    maskedText = maskedText.replace(phoneRegex, "*****#####")
                    maskedText = maskedText.replace(emailRegex, "hidden@email")
                    maskedText = maskedText.replace(upiRegex, "hidden@upi")
                    maskedText = maskedText.replace(Regex("\\b\\d{4,6}\\b"), "******")

                    extractedText.text = maskedText

                    // ===== RISK =====
                    val isOtp =
                        Regex("\\b\\d{4,6}\\b").containsMatchIn(resultText) &&
                                (lowerText.contains("otp") || lowerText.contains("code"))

                    val isUpi = upiRegex.containsMatchIn(resultText)
                    val isPhone = phoneRegex.containsMatchIn(resultText)
                    val isEmail = emailRegex.containsMatchIn(resultText)

                    val risk = when {
                        isOtp || isUpi -> "HIGH"
                        isPhone || isEmail -> "MEDIUM"
                        else -> "LOW"
                    }

                    riskLevel.text = "Risk Level: $risk"

                    when (risk) {
                        "HIGH" -> riskLevel.setTextColor(getColor(android.R.color.holo_red_dark))
                        "MEDIUM" -> riskLevel.setTextColor(getColor(android.R.color.holo_orange_dark))
                        "LOW" -> riskLevel.setTextColor(getColor(android.R.color.holo_green_dark))
                    }

                    explanationText.text = generateExplanation(resultText)
                    tipsText.text = generateSafetyTips(resultText)
                }
        }
    }

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

    private fun generateExplanation(text: String): String {
        val reasons = mutableListOf<String>()
        if (text.contains("otp", true)) reasons.add("Contains OTP")
        if (Regex("(\\+91[\\s-]?)?[6-9]\\d{4}[\\s-]?\\d{5}").containsMatchIn(text)) reasons.add("Contains Phone Number")
        if (Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.(com|in|edu|org|net)").containsMatchIn(text)) reasons.add("Contains Email")
        if (Regex("\\b[\\w.-]+@[a-zA-Z]+\\b").containsMatchIn(text)) reasons.add("Contains UPI ID")

        return if (reasons.isNotEmpty())
            "⚠ This may be unsafe to share because:\n\n• ${reasons.joinToString("\n• ")}"
        else "No major threats detected."
    }

    private fun generateSafetyTips(text: String): String {
        val tips = mutableListOf<String>()
        if (text.contains("otp", true)) tips.add("Never share OTP.")
        if (Regex("(\\+91[\\s-]?)?[6-9]\\d{4}[\\s-]?\\d{5}").containsMatchIn(text)) tips.add("Avoid sharing phone number.")
        if (text.contains("@")) tips.add("Verify UPI/email before sharing.")

        return if (tips.isNotEmpty())
            "🛡 Safety Tips:\n\n• ${tips.joinToString("\n• ")}"
        else ""
    }
}