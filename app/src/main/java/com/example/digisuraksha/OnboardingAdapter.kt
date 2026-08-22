package com.example.digisuraksha

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class OnboardingPage(
    val imageResId: Int,
    val title: String,
    val description: String
)

class OnboardingAdapter(private val pages: List<OnboardingPage>) :
    RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder>() {

    class OnboardingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgOnboarding: ImageView = itemView.findViewById(R.id.imgOnboarding)
        val tvTitle: TextView = itemView.findViewById(R.id.tvOnboardingTitle)
        val tvDescription: TextView = itemView.findViewById(R.id.tvOnboardingDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_onboarding_page, parent, false)
        return OnboardingViewHolder(view)
    }

    override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
        val page = pages[position]
        holder.imgOnboarding.setImageResource(page.imageResId)
        holder.tvTitle.text = page.title
        holder.tvDescription.text = page.description
    }

    override fun getItemCount(): Int = pages.size
}
