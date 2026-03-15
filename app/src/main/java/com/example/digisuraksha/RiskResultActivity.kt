package com.example.digisuraksha

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class RiskResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_risk_result)

        val shareButton = findViewById<Button>(R.id.secureShare)

        shareButton.setOnClickListener {
            val intent = Intent(this, SecureShareActivity::class.java)
            startActivity(intent)
        }
    }
}