package com.example.digisuraksha

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.regex.Pattern

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

        // 🔥 POPUP SHARE
        shareButton.setOnClickListener {

            val options = arrayOf("Share Masked Text", "Share Blurred Image")

            val builder = AlertDialog.Builder(this)
            builder.setTitle("Choose Sharing Option")

            builder.setItems(options) { _, which ->

                when (which) {

                    0 -> {
                        val shareIntent = Intent(Intent.ACTION_SEND)
                        shareIntent.type = "text/plain"

                        val message =
                            "Scanned using DigiSuraksha\n\n" +
                                    extractedText.text.toString() +
                                    "\n\n" +
                                    riskLevel.text.toString()

                        shareIntent.putExtra(Intent.EXTRA_TEXT, message)
                        startActivity(Intent.createChooser(shareIntent, "Secure Share"))
                    }

                    1 -> {
                        if (blurredBitmap == null) return@setItems

                        val path = MediaStore.Images.Media.insertImage(
                            contentResolver,
                            blurredBitmap,
                            "DigiSuraksha_Blurred",
                            null
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

            builder.show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK) {

            val imageUri: Uri? = data?.data

            if (imageUri != null) {

                val bitmap: Bitmap =
                    MediaStore.Images.Media.getBitmap(this.contentResolver, imageUri)

                val image = InputImage.fromBitmap(bitmap, 0)

                val recognizer =
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                recognizer.process(image)
                    .addOnSuccessListener { visionText ->

                        val resultText = visionText.text

                        // ===== FRAUD =====
                        val lowerText = resultText.lowercase()

                        if (
                            lowerText.contains("win") ||
                            lowerText.contains("prize") ||
                            lowerText.contains("lottery") ||
                            lowerText.contains("click") ||
                            lowerText.contains("urgent") ||
                            lowerText.contains("free") ||
                            lowerText.contains("offer")
                        ) {
                            fraudWarning.text = "⚠ Possible Fraud Detected"
                            fraudWarning.setTextColor(getColor(android.R.color.holo_red_dark))
                        } else {
                            fraudWarning.text = ""
                        }

                        // ===== BLUR =====
                        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                        val canvas = Canvas(mutableBitmap)
                        val paint = Paint()

                        for (block in visionText.textBlocks) {
                            for (line in block.lines) {
                                for (element in line.elements) {

                                    val text = element.text
                                    val box = element.boundingBox

                                    if (box != null) {

                                        val isSensitive =
                                            Regex("(\\d\\s*){4,6}").containsMatchIn(text) ||
                                                    Regex("\\b[6-9][0-9]{9}\\b").containsMatchIn(text) ||
                                                    Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+").containsMatchIn(text) ||
                                                    Regex("\\b[\\w.-]+@[\\w.-]+\\b").containsMatchIn(text)

                                        if (isSensitive) {

                                            val left = box.left.coerceAtLeast(0)
                                            val top = box.top.coerceAtLeast(0)
                                            val width = box.width().coerceAtMost(mutableBitmap.width - left)
                                            val height = box.height().coerceAtMost(mutableBitmap.height - top)

                                            if (width > 0 && height > 0) {

                                                val cropped = Bitmap.createBitmap(
                                                    mutableBitmap,
                                                    left,
                                                    top,
                                                    width,
                                                    height
                                                )

                                                val blurred = blurBitmap(cropped)

                                                canvas.drawBitmap(
                                                    blurred,
                                                    left.toFloat(),
                                                    top.toFloat(),
                                                    paint
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        imageView.setImageBitmap(mutableBitmap)
                        blurredBitmap = mutableBitmap

                        // ===== MASK TEXT =====
                        var maskedText = resultText

                        maskedText = maskedText.replace(Regex("(\\d\\s*){4,6}"), "******")
                        maskedText = maskedText.replace(Regex("\\b[6-9][0-9]{9}\\b"), "*****#####")
                        maskedText = maskedText.replace(Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+"), "hidden@email")
                        maskedText = maskedText.replace(Regex("\\b[\\w.-]+@[\\w.-]+\\b"), "hidden@upi")

                        extractedText.text = maskedText

                        // ===== RISK =====
                        val phonePattern = Pattern.compile("\\b[6-9][0-9]{9}\\b")
                        val otpPattern = Pattern.compile("(\\d\\s*){4,6}")
                        val upiPattern = Pattern.compile("\\b[\\w.-]+@[\\w.-]+\\b")
                        val emailPattern =
                            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+")

                        var risk = "LOW"

                        if (phonePattern.matcher(resultText).find()) risk = "MEDIUM"
                        if (emailPattern.matcher(resultText).find()) risk = "MEDIUM"
                        if (otpPattern.matcher(resultText).find() ||
                            upiPattern.matcher(resultText).find()
                        ) risk = "HIGH"

                        riskLevel.text = "Risk Level: $risk"

                        when (risk) {
                            "HIGH" -> riskLevel.setTextColor(getColor(android.R.color.holo_red_dark))
                            "MEDIUM" -> riskLevel.setTextColor(getColor(android.R.color.holo_orange_dark))
                            "LOW" -> riskLevel.setTextColor(getColor(android.R.color.holo_green_dark))
                        }

                        // ===== EXPLANATION + TIPS =====
                        val explanation = generateExplanation(resultText)
                        val tips = generateSafetyTips(resultText)

                        if (risk == "HIGH") {
                            explanationText.text = explanation
                            tipsText.text = tips
                        } else if (risk == "MEDIUM") {
                            explanationText.text = explanation
                            tipsText.text = ""
                        } else {
                            explanationText.text = "No major threats detected."
                            tipsText.text = ""
                        }
                    }

                    .addOnFailureListener {
                        extractedText.text = "Failed to detect text"
                    }
            }
        }
    }

    private fun blurBitmap(bitmap: Bitmap): Bitmap {
        val scale = 0.1f
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)

        val blurred = Bitmap.createScaledBitmap(bitmap, width, height, true)
        return Bitmap.createScaledBitmap(blurred, bitmap.width, bitmap.height, true)
    }

    private fun generateExplanation(text: String): String {
        val reasons = mutableListOf<String>()

        if (Regex("(\\d\\s*){4,6}").containsMatchIn(text)) reasons.add("Contains OTP")
        if (Regex("\\b[6-9][0-9]{9}\\b").containsMatchIn(text)) reasons.add("Contains Phone Number")
        if (Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+").containsMatchIn(text)) reasons.add("Contains Email")
        if (Regex("\\b[\\w.-]+@[\\w.-]+\\b").containsMatchIn(text)) reasons.add("Contains UPI ID")

        return if (reasons.isNotEmpty()) {
            "⚠ This may be unsafe to share because:\n\n• " + reasons.joinToString("\n• ")
        } else {
            ""
        }
    }

    private fun generateSafetyTips(text: String): String {
        val tips = mutableListOf<String>()

        if (Regex("(\\d\\s*){4,6}").containsMatchIn(text)) tips.add("Never share OTP with anyone.")
        if (Regex("\\b[6-9][0-9]{9}\\b").containsMatchIn(text)) tips.add("Avoid sharing your phone number.")
        if (Regex("\\b[\\w.-]+@[\\w.-]+\\b").containsMatchIn(text)) tips.add("Verify UPI IDs before sending money.")
        if (Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+").containsMatchIn(text)) tips.add("Be careful sharing email.")

        return if (tips.isNotEmpty()) {
            "🛡 Safety Tips:\n\n• " + tips.joinToString("\n• ")
        } else {
            ""
        }
    }
}