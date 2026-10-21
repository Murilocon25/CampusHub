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
        textSize = size
        setTextColor(color ?: context.getColor(R.color.color_text_body))
        if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        val density = resources.displayMetrics.density
        setPadding((4 * density).toInt(), (7 * density).toInt(), (4 * density).toInt(), (7 * density).toInt())
    })
}

fun LinearLayout.addCampusButton(label: String, action: () -> Unit) {
    val density = resources.displayMetrics.density
    addView(MaterialButton(context).apply {
        text = label
        isAllCaps = false
        minHeight = (48 * density).toInt()
        insetTop = 0
        insetBottom = 0
        setOnClickListener { action() }
    }, LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = (6 * density).toInt()
        bottomMargin = (6 * density).toInt()
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
    val density = resources.displayMetrics.density
    val layout = TextInputLayout(this).apply {
        this.hint = hint
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        setBoxBackgroundColorResource(R.color.color_surface)
        setBoxStrokeColor(getColor(R.color.color_primary))
        setBoxCornerRadii(10f * density, 10f * density, 10f * density, 10f * density)
    }
    val editText = TextInputEditText(layout.context).apply {
        this.inputType = inputType
        this.minLines = minLines
        this.maxLines = maxLines
        isSingleLine = maxLines == 1
        minHeight = (52 * density).toInt()
    }
    layout.addView(editText)
    return layout to editText
}
