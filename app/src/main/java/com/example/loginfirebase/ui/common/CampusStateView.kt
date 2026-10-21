package com.example.loginfirebase.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import com.example.loginfirebase.R
import com.google.android.material.R as MaterialR
import com.google.android.material.card.MaterialCardView

class CampusStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = MaterialR.attr.materialCardViewStyle
) : MaterialCardView(context, attrs, defStyleAttr) {
    private val messageView = TextView(context).apply {
        textSize = 15f
        setTextColor(context.getColor(R.color.color_text_subtitle))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    init {
        setCardBackgroundColor(context.getColor(R.color.color_surface))
        strokeColor = context.getColor(R.color.color_border)
        strokeWidth = (resources.displayMetrics.density).toInt().coerceAtLeast(1)
        radius = 16f * resources.displayMetrics.density
        cardElevation = resources.displayMetrics.density
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        val density = resources.displayMetrics.density
        setContentPadding(
            (18 * density).toInt(), (20 * density).toInt(),
            (18 * density).toInt(), (20 * density).toInt()
        )
        addView(messageView)
        visibility = View.GONE
    }

    fun showMessage(message: String) {
        messageView.text = message
        visibility = View.VISIBLE
    }

    fun hideMessage() {
        visibility = View.GONE
    }
}
