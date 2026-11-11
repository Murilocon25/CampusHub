package com.example.loginfirebase.ui.common

import android.app.Application
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.view.ContextThemeWrapper
import com.example.loginfirebase.R
import com.google.android.material.card.MaterialCardView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CampusSkeletonListViewTest {
    private val context: Context
        get() = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_CampusHub)

    @Test
    fun createsTwoAccessibleHiddenSkeletonCardsWithThreePlaceholdersEach() {
        val skeleton = CampusSkeletonListView(context)

        assertEquals(LinearLayout.VERTICAL, skeleton.orientation)
        assertEquals(View.GONE, skeleton.visibility)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, skeleton.importantForAccessibility)
        assertEquals(2, skeleton.childCount)

        repeat(skeleton.childCount) { index ->
            val card = skeleton.getChildAt(index) as MaterialCardView
            val content = card.getChildAt(0) as LinearLayout
            assertEquals(3, content.childCount)
            repeat(content.childCount) { placeholderIndex ->
                val placeholder = content.getChildAt(placeholderIndex)
                assertTrue(placeholder.background is GradientDrawable)
                assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, placeholder.importantForAccessibility)
            }
        }
    }
}
