package com.example.digisuraksha

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var btnNext: Button
    private lateinit var btnPrev: Button
    private lateinit var btnSkip: TextView
    private lateinit var tvPageIndicator: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.viewPagerOnboarding)
        btnNext = findViewById(R.id.btnNext)
        btnPrev = findViewById(R.id.btnPrev)
        btnSkip = findViewById(R.id.btnSkip)
        tvPageIndicator = findViewById(R.id.tvPageIndicator)

        val pages = listOf(
            OnboardingPage(
                imageResId = R.drawable.onboarding_welcome,
                title = "Welcome to DigiSuraksha",
                description = "DigiSuraksha helps protect your private and sensitive information when sharing screenshots or images."
            ),
            OnboardingPage(
                imageResId = R.drawable.onboarding_scan_screenshots,
                title = "What Does It Detect?",
                description = "DigiSuraksha automatically detects sensitive personal details like Aadhaar, PAN, Card Numbers, UPI IDs, Passwords, OTPs, Phone Numbers, and Email Addresses."
            ),
            OnboardingPage(
                imageResId = R.drawable.onboarding_share_securely,
                title = "How Protection Works",
                description = "All analysis happens 100% locally on your device. You can choose to blur sensitive regions or share masked text before sending."
            ),
            OnboardingPage(
                imageResId = R.drawable.onboarding_fraud_protection,
                title = "Auto-Detect Screenshots",
                description = "Auto-Detect is optional and OFF by default. When enabled in Settings, DigiSuraksha notifies you when a screenshot is taken. No silent background scanning occurs—you must tap the notification to scan."
            )
        )

        viewPager.adapter = OnboardingAdapter(pages)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateNavigationUI(position, pages.size)
            }
        })

        btnNext.setOnClickListener {
            val current = viewPager.currentItem
            if (current < pages.size - 1) {
                viewPager.currentItem = current + 1
            } else {
                finishOnboarding()
            }
        }

        btnPrev.setOnClickListener {
            val current = viewPager.currentItem
            if (current > 0) {
                viewPager.currentItem = current - 1
            }
        }

        btnSkip.setOnClickListener {
            finishOnboarding()
        }

        updateNavigationUI(0, pages.size)
    }

    private fun updateNavigationUI(position: Int, totalPages: Int) {
        tvPageIndicator.text = "Step ${position + 1} of $totalPages"

        btnPrev.visibility = if (position > 0) View.VISIBLE else View.INVISIBLE

        if (position == totalPages - 1) {
            btnNext.text = "Get Started"
            btnSkip.visibility = View.GONE
        } else {
            btnNext.text = "Next"
            btnSkip.visibility = View.VISIBLE
        }
    }

    private fun finishOnboarding() {
        val prefs = getSharedPreferences("digisuraksha_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_completed", true).apply()

        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
