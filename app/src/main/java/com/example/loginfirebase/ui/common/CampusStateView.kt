package com.example.loginfirebase.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.example.loginfirebase.R
import com.google.android.material.R as MaterialR
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class CampusStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = MaterialR.attr.materialCardViewStyle
) : MaterialCardView(context, attrs, defStyleAttr) {
    private val iconView = ImageView(context).apply {
        setImageResource(R.drawable.ic_event_cover)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        val iconSize = resources.getDimensionPixelSize(R.dimen.space_6)
        layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
            bottomMargin = resources.getDimensionPixelSize(R.dimen.space_2)
        }
    }
    private val messageView = TextView(context).apply {
        setTextAppearance(R.style.TextAppearance_CampusHub_Body)
        setCampusFont()
        gravity = Gravity.CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val progressView = ProgressBar(context).apply {
        isIndeterminate = true
        indeterminateTintList = context.getColorStateList(R.color.color_primary)
        visibility = View.GONE
        val indicatorSize = resources.getDimensionPixelSize(R.dimen.space_6)
        layoutParams = LinearLayout.LayoutParams(indicatorSize, indicatorSize).apply {
            bottomMargin = resources.getDimensionPixelSize(R.dimen.space_2)
        }
    }
    private val actionButton = MaterialButton(context).apply {
        setTextAppearance(R.style.TextAppearance_CampusHub_Label)
        visibility = View.GONE
        minHeight = resources.getDimensionPixelSize(R.dimen.touch_target_min)
        insetTop = 0
        insetBottom = 0
    }
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(iconView)
        addView(progressView)
        addView(messageView, LinearLayout.LayoutParams(-1, -2))
        addView(actionButton, LinearLayout.LayoutParams(-2, -2).apply {
            topMargin = resources.getDimensionPixelSize(R.dimen.space_3)
        })
    }

    init {
        setCardBackgroundColor(context.getColor(R.color.color_surface))
        strokeWidth = 0
        radius = resources.getDimension(R.dimen.shape_medium)
        cardElevation = 0f
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        val inset = resources.getDimensionPixelSize(R.dimen.space_4)
        setContentPadding(
            inset, inset, inset, inset
        )
        addView(content)
        visibility = View.GONE
    }

    fun showMessage(message: String) {
        messageView.text = message
        val isLoading = message.startsWith("Carregando", ignoreCase = true)
        iconView.visibility = if (isLoading) View.GONE else View.VISIBLE
        progressView.visibility = if (isLoading) View.VISIBLE else View.GONE
        actionButton.visibility = View.GONE
        visibility = View.VISIBLE
    }

    fun showMessage(message: String, actionLabel: String, action: () -> Unit) {
        messageView.text = message
        iconView.visibility = View.VISIBLE
        progressView.visibility = View.GONE
        actionButton.text = actionLabel
        actionButton.setOnClickListener { action() }
        actionButton.visibility = View.VISIBLE
        visibility = View.VISIBLE
    }

    fun hideMessage() {
        visibility = View.GONE
    }
}
