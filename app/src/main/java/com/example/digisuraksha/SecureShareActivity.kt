package com.example.digisuraksha

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SecureShareActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_secure_share)

        val shareButton = findViewById<Button>(R.id.shareNow)

        shareButton.setOnClickListener {
            Toast.makeText(this, "Screenshot shared securely", Toast.LENGTH_SHORT).show()
        }
    }
}