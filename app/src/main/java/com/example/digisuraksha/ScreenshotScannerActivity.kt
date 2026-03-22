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

        // SHARE POPUP
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

            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->

                    val resultText = visionText.text
                    val lowerText = resultText.lowercase()

                    // ================= FRAUD (FIXED) =================
                    val hasReward =
                        lowerText.contains("won") ||
                                lowerText.contains("prize") ||
                                lowerText.contains("lottery")

                    val hasAction =
                        lowerText.contains("click") ||
                                lowerText.contains("claim") ||
                                lowerText.contains("urgent")

                    val isFraud = hasReward && hasAction

                    if (isFraud) {
                        fraudWarning.text = "⚠ Possible Fraud Detected"
                        fraudWarning.setTextColor(getColor(android.R.color.holo_red_dark))
                    } else {
                        fraudWarning.text = ""
                    }

                    // ================= BLUR (RELAXED) =================
                    val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                    val canvas = Canvas(mutableBitmap)
                    val paint = Paint()

                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            for (element in line.elements) {

                                val text = element.text
                                val box = element.boundingBox ?: continue

                                val isBlurSensitive =
                                    Regex("\\b\\d{4,6}\\b").containsMatchIn(text) ||
                                            Regex("\\b[6-9][0-9]{9}\\b").containsMatchIn(text) ||
                                            Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+").containsMatchIn(text) ||
                                            Regex("\\b[\\w.-]+@[\\w.-]+\\b").containsMatchIn(text)

                                if (isBlurSensitive) {

                                    val cropped = Bitmap.createBitmap(
                                        mutableBitmap,
                                        box.left.coerceAtLeast(0),
                                        box.top.coerceAtLeast(0),
                                        box.width().coerceAtMost(mutableBitmap.width - box.left),
                                        box.height().coerceAtMost(mutableBitmap.height - box.top)
                                    )

                                    val blurred = blurBitmap(cropped)

                                    canvas.drawBitmap(
                                        blurred,
                                        box.left.toFloat(),
                                        box.top.toFloat(),
                                        paint
                                    )
                                }
                            }
                        }
                    }

                    imageView.setImageBitmap(mutableBitmap)
                    blurredBitmap = mutableBitmap

                    // ================= MASK =================
                    var maskedText = resultText
                    maskedText = maskedText.replace(Regex("\\b\\d{4,6}\\b"), "******")
                    maskedText = maskedText.replace(Regex("\\b[6-9][0-9]{9}\\b"), "*****#####")
                    maskedText = maskedText.replace(Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+"), "hidden@email")
                    maskedText = maskedText.replace(Regex("\\b[\\w.-]+@[\\w.-]+\\b"), "hidden@upi")

                    extractedText.text = maskedText

                    // ================= RISK =================
                    var risk = "LOW"

                    if (Regex("\\b[6-9][0-9]{9}\\b").containsMatchIn(resultText)) {
                        risk = "MEDIUM"
                    }

                    val isOtp =
                        Regex("\\b\\d{4,6}\\b").containsMatchIn(resultText) &&
                                (lowerText.contains("otp") ||
                                        lowerText.contains("code") ||
                                        lowerText.contains("verification"))

                    if (isOtp) risk = "HIGH"

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

    private fun blurBitmap(bitmap: Bitmap): Bitmap {
        val small = Bitmap.createScaledBitmap(bitmap, 20, 20, true)
        return Bitmap.createScaledBitmap(small, bitmap.width, bitmap.height, true)
    }

    private fun generateExplanation(text: String): String {
        val lower = text.lowercase()
        val reasons = mutableListOf<String>()

        val isOtp =
            Regex("\\b\\d{4,6}\\b").containsMatchIn(text) &&
                    (lower.contains("otp") || lower.contains("code") || lower.contains("verification"))

        if (isOtp) reasons.add("Contains OTP")
        if (Regex("\\b[6-9][0-9]{9}\\b").containsMatchIn(text)) reasons.add("Contains Phone Number")
        if (Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+").containsMatchIn(text)) reasons.add("Contains Email")
        if (Regex("\\b[\\w.-]+@[\\w.-]+\\b").containsMatchIn(text)) reasons.add("Contains UPI ID")

        return if (reasons.isNotEmpty())
            "⚠ This may be unsafe to share because:\n\n• ${reasons.joinToString("\n• ")}"
        else
            "No major threats detected."
    }

    private fun generateSafetyTips(text: String): String {
        val lower = text.lowercase()
        val tips = mutableListOf<String>()

        if (lower.contains("otp")) tips.add("Never share OTP.")
        if (Regex("\\b[6-9][0-9]{9}\\b").containsMatchIn(text)) tips.add("Avoid sharing phone number.")
        if (lower.contains("@")) tips.add("Verify UPI/email before sharing.")

        return if (tips.isNotEmpty())
            "🛡 Safety Tips:\n\n• ${tips.joinToString("\n• ")}"
        else ""
    }
}