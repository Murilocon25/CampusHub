package com.example.loginfirebase.ui.common

import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.loginfirebase.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

fun LinearLayout.addCampusText(
    text: String,
    size: Float = 16f,
    color: Int? = null,
    bold: Boolean = false
) {
    addView(TextView(context).apply {
        this.text = text
        setTextAppearance(when {
            size >= 24f -> R.style.TextAppearance_CampusHub_Headline
            size >= 19f -> R.style.TextAppearance_CampusHub_Title
            size >= 16f -> R.style.TextAppearance_CampusHub_Body
            size >= 14f -> R.style.TextAppearance_CampusHub_Label
            else -> R.style.TextAppearance_CampusHub_Caption
        })
        setCampusFont(bold)
        if (color != null) setTextColor(context.getColor(color))
        val inset = resources.getDimensionPixelSize(R.dimen.space_1)
        val vertical = resources.getDimensionPixelSize(R.dimen.space_2)
        setPadding(inset, vertical, inset, vertical)
    })
}

fun LinearLayout.addCampusButton(label: String, action: () -> Unit) {
    addView(MaterialButton(context).apply {
        text = label
        isAllCaps = false
        minHeight = resources.getDimensionPixelSize(R.dimen.button_height)
        insetTop = 0
        insetBottom = 0
        setOnClickListener { action() }
    }, LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = resources.getDimensionPixelSize(R.dimen.space_2)
        bottomMargin = resources.getDimensionPixelSize(R.dimen.space_2)
    })
}

fun LinearLayout.showCampusState(message: String) {
    removeAllViews()
    addView(CampusStateView(context).apply { showMessage(message) }, LinearLayout.LayoutParams(-1, -2))
}

fun AppCompatActivity.createCampusInput(
    hint: String,
    inputType: Int = android.text.InputType.TYPE_CLASS_TEXT,
    minLines: Int = 1,
    maxLines: Int = 1
): Pair<TextInputLayout, TextInputEditText> {
    val layout = TextInputLayout(this).apply {
        this.hint = hint
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        setBoxBackgroundColorResource(R.color.color_surface)
        setBoxStrokeColor(getColor(R.color.color_primary))
        val corner = resources.getDimension(R.dimen.shape_small)
        setBoxCornerRadii(corner, corner, corner, corner)
    }
    val editText = TextInputEditText(layout.context).apply {
        this.inputType = inputType
        this.minLines = minLines
        this.maxLines = maxLines
        isSingleLine = maxLines == 1
        minHeight = resources.getDimensionPixelSize(R.dimen.button_height)
    }
    layout.addView(editText)
    return layout to editText
}
