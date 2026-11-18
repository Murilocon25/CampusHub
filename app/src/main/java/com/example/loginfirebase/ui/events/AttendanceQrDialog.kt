package com.example.loginfirebase.ui.events

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.loginfirebase.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

internal object AttendanceQrDialog {
    fun show(
        activity: Activity,
        payload: String,
        titleRes: Int = R.string.attendance_qr_title,
        hintRes: Int = R.string.attendance_qr_hint,
        errorRes: Int = R.string.attendance_qr_load_error,
    ) {
        val side = (activity.resources.displayMetrics.widthPixels * 0.72f).toInt().coerceAtLeast(480)
        val matrix = runCatching {
            QRCodeWriter().encode(
                payload,
                BarcodeFormat.QR_CODE,
                side,
                side,
                mapOf(
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN to 2,
                ),
            )
        }.getOrElse {
            android.widget.Toast.makeText(activity, errorRes, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        for (x in 0 until side) {
            for (y in 0 until side) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }

        val image = ImageView(activity).apply {
            setImageBitmap(bitmap)
            setBackgroundColor(Color.WHITE)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(-1, side)
        }
        val hint = TextView(activity).apply {
            setText(hintRes)
            setTextAppearance(R.style.TextAppearance_CampusHub_Body)
            gravity = Gravity.CENTER
            setPadding(0, activity.resources.getDimensionPixelSize(R.dimen.space_2), 0, 0)
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val inset = activity.resources.getDimensionPixelSize(R.dimen.space_3)
            setPadding(inset, activity.resources.getDimensionPixelSize(R.dimen.space_2), inset, activity.resources.getDimensionPixelSize(R.dimen.space_2))
            addView(image)
            addView(hint)
        }

        if (activity.isFinishing || activity.isDestroyed) {
            bitmap.recycle()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(titleRes)
            .setView(content)
            .setPositiveButton(android.R.string.ok, null)
            .setOnDismissListener { bitmap.recycle() }
            .show()
    }
}
