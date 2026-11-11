package com.example.loginfirebase.ui.common

import android.app.Application
import android.content.Context
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import com.example.loginfirebase.R
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CampusFontTest {
    private val context: Context
        get() = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_CampusHub)

    @Test
    fun setCampusFontLoadsRegularAndBoldFontFaces() {
        val regular = TextView(context).apply { setCampusFont() }
        val bold = TextView(context).apply { setCampusFont(bold = true) }

        assertNotNull(regular.typeface)
        assertNotNull(bold.typeface)
    }
}
