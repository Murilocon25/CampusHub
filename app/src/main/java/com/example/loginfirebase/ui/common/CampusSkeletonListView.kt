package com.example.loginfirebase.ui.common

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.example.loginfirebase.R
import com.google.android.material.card.MaterialCardView

class CampusSkeletonListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    init {
        orientation = VERTICAL
        val inset = resources.getDimensionPixelSize(R.dimen.space_4)
        repeat(2) {
            val content = LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(inset, inset, inset, inset)
                addView(placeholder(
                    LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, resources.getDimensionPixelSize(R.dimen.skeleton_cover_height))
                ))
                addView(placeholder(
                    LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, resources.getDimensionPixelSize(R.dimen.space_3)).apply {
                        topMargin = resources.getDimensionPixelSize(R.dimen.space_4)
                    }
                ))
                addView(placeholder(
                    LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, resources.getDimensionPixelSize(R.dimen.space_2)).apply {
                        topMargin = resources.getDimensionPixelSize(R.dimen.space_2)
                    }
                ))
            }
            val card = MaterialCardView(context).apply {
                radius = resources.getDimension(R.dimen.shape_medium)
                cardElevation = 0f
                strokeWidth = resources.getDimensionPixelSize(R.dimen.stroke_width)
                strokeColor = context.getColor(R.color.color_border)
                setCardBackgroundColor(context.getColor(R.color.color_surface))
                addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }
            addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = resources.getDimensionPixelSize(R.dimen.space_3)
            })
        }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
    }

    private fun placeholder(params: LayoutParams): View = View(context).apply {
        layoutParams = params
        background = GradientDrawable().apply {
            setColor(context.getColor(R.color.color_surface_variant))
            cornerRadius = resources.getDimension(R.dimen.shape_small)
        }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
}
