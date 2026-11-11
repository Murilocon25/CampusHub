package com.example.loginfirebase.ui.common

import android.app.Application
import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import com.example.loginfirebase.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CampusViewsTest {
    private val context: Context
        get() = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_CampusHub)

    @Test
    fun addCampusTextChoosesTextStylesAcrossSizeThresholds() {
        val layout = LinearLayout(context)
        val sizes = listOf(24f, 20f, 16f, 14f, 12f)

        sizes.forEachIndexed { index, size ->
            layout.addCampusText(
                text = "Label $index",
                size = size,
                color = if (index == 2) R.color.color_primary else null,
                bold = index == 2
            )
        }

        assertEquals(sizes.size, layout.childCount)
        sizes.indices.forEach { index ->
            assertEquals("Label $index", (layout.getChildAt(index) as TextView).text.toString())
        }
    }

    @Test
    fun addCampusButtonRunsTheProvidedAction() {
        val layout = LinearLayout(context)
        var called = false

        layout.addCampusButton("Save") { called = true }
        val button = layout.getChildAt(0) as MaterialButton
        button.performClick()

        assertEquals("Save", button.text.toString())
        assertFalse(button.isAllCaps)
        assertTrue(called)
    }

    @Test
    fun showCampusStateReplacesExistingChildrenWithAVisibleStateView() {
        val layout = LinearLayout(context).apply { addView(TextView(context)) }

        layout.showCampusState("No events found")

        assertEquals(1, layout.childCount)
        val state = layout.getChildAt(0) as CampusStateView
        val content = state.getChildAt(0) as LinearLayout
        assertEquals("No events found", (content.getChildAt(2) as TextView).text.toString())
    }

    @Test
    fun createCampusInputAppliesHintInputTypeAndMultilineMode() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(R.style.Theme_CampusHub)
        val inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE

        val (layout, editText) = activity.createCampusInput(
            "Comment",
            inputType = inputType,
            minLines = 2,
            maxLines = 4
        )

        assertEquals("Comment", layout.hint.toString())
        assertEquals(inputType, editText.inputType)
        assertFalse(editText.isSingleLine)
        assertEquals(1, layout.childCount)
        assertTrue(layout.editText === editText)
    }

    @Test
    fun createCampusInputMakesSingleLineInputByDefault() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()

        val (_, editText) = activity.createCampusInput("Email")

        assertEquals(android.text.InputType.TYPE_CLASS_TEXT, editText.inputType)
        assertTrue(editText.isSingleLine)
    }
}
