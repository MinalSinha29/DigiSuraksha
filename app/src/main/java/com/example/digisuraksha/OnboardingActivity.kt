package com.example.digisuraksha

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

data class OnboardingSlide(
    val imageRes: Int,
    val title: String,
    val description: String
)

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var btnNext: Button
    private lateinit var btnSkip: Button
    private lateinit var tvIndicator: TextView

    private val slides = listOf(
        OnboardingSlide(
            // Uses onboarding_1 if present in drawable, otherwise falls back to launcher icon
            imageRes = R.mipmap.ic_launcher,
            title = "🛡️ Screenshot Auto-Shield",
            description = "Automatically detects sensitive data in screenshots like Aadhaar, PAN cards, passwords & UPI QR codes."
        ),
        OnboardingSlide(
            imageRes = R.mipmap.ic_launcher,
            title = "📩 Phishing & Fraud Scanner",
            description = "Analyzes incoming SMS messages in real-time to alert you of scam links, fake KYC threats & prize frauds."
        ),
        OnboardingSlide(
            imageRes = R.mipmap.ic_launcher,
            title = "🔒 100% On-Device Privacy",
            description = "No cloud uploads. All analysis runs completely offline on your device in compliance with DPDP Act 2023."
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.viewPagerOnboarding)
        btnNext = findViewById(R.id.btnNext)
        btnSkip = findViewById(R.id.btnSkip)
        tvIndicator = findViewById(R.id.tvSlideIndicator)

        viewPager.adapter = OnboardingAdapter(slides)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                tvIndicator.text = "${position + 1} of ${slides.size}"
                if (position == slides.size - 1) {
                    btnNext.text = "Get Started ✓"
                } else {
                    btnNext.text = "Next ›"
                }
            }
        })

        btnNext.setOnClickListener {
            if (viewPager.currentItem < slides.size - 1) {
                viewPager.currentItem += 1
            } else {
                finishOnboarding()
            }
        }

        btnSkip.setOnClickListener {
            finishOnboarding()
        }
    }

    private fun finishOnboarding() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_completed", true).apply()

        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    class OnboardingAdapter(private val slides: List<OnboardingSlide>) :
        RecyclerView.Adapter<OnboardingAdapter.SlideViewHolder>() {

        class SlideViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivImage: ImageView = view.findViewById(R.id.ivSlideImage)
            val tvTitle: TextView = view.findViewById(R.id.tvSlideTitle)
            val tvDesc: TextView = view.findViewById(R.id.tvSlideDesc)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_onboarding_slide, parent, false)
            return SlideViewHolder(view)
        }

        override fun onBindViewHolder(holder: SlideViewHolder, position: Int) {
            val slide = slides[position]
            holder.ivImage.setImageResource(slide.imageRes)
            holder.tvTitle.text = slide.title
            holder.tvDesc.text = slide.description
        }

        override fun getItemCount(): Int = slides.size
    }
}