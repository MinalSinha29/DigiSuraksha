package com.example.digisuraksha

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        // 📸 Screenshot Scanner Card
        val scanCard = findViewById<LinearLayout>(R.id.scanCard)

        scanCard.setOnClickListener {
            val intent = Intent(this, ScreenshotScannerActivity::class.java)
            startActivity(intent)
        }

        // 📩 SMS Analyzer Card
        val smsCard = findViewById<LinearLayout>(R.id.smsCard)

        smsCard.setOnClickListener {
            val intent = Intent(this, SmsAnalyzerActivity::class.java)
            startActivity(intent)
        }

        // UI Insets (keep this same)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}