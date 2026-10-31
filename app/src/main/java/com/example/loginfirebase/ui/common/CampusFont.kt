package com.example.loginfirebase.ui.common

import android.graphics.Typeface
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.example.loginfirebase.R

fun TextView.setCampusFont(bold: Boolean = false) {
    val font = ResourcesCompat.getFont(
        context,
        if (bold) R.font.campus_sans_bold else R.font.campus_sans_regular
    )
    typeface = font ?: Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
}
