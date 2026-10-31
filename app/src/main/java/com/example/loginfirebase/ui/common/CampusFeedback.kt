package com.example.loginfirebase.ui.common

import android.view.View
import androidx.annotation.StringRes
import com.example.loginfirebase.R
import com.google.android.material.snackbar.Snackbar

fun View.showCampusSnackbar(message: CharSequence, duration: Int = Snackbar.LENGTH_SHORT) {
    Snackbar.make(this, message, duration)
        .setBackgroundTint(context.getColor(R.color.color_text_title))
        .setTextColor(context.getColor(R.color.color_surface))
        .setActionTextColor(context.getColor(R.color.color_on_primary))
        .show()
}

fun View.showCampusSnackbar(@StringRes message: Int, duration: Int = Snackbar.LENGTH_SHORT) {
    showCampusSnackbar(context.getString(message), duration)
}
