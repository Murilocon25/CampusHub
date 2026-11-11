package com.example.loginfirebase.ui.common

import android.app.Application
import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.example.loginfirebase.R
import com.google.android.material.button.MaterialButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import androidx.appcompat.view.ContextThemeWrapper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CampusStateViewTest {
    private val context: Context
        get() = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_CampusHub)

    @Test
    fun loadingMessageShowsProgressAndHidesIllustrationAndAction() {
        val stateView = CampusStateView(context)
        assertEquals(View.GONE, stateView.visibility)
        val content = stateView.getChildAt(0) as LinearLayout
        val icon = content.getChildAt(0) as ImageView
        val progress = content.getChildAt(1) as ProgressBar
        val message = content.getChildAt(2) as TextView
        val action = content.getChildAt(3) as MaterialButton

        stateView.showMessage("Carregando eventos")

        assertEquals(View.VISIBLE, stateView.visibility)
        assertEquals("Carregando eventos", message.text.toString())
        assertEquals(View.GONE, icon.visibility)
        assertEquals(View.VISIBLE, progress.visibility)
        assertEquals(View.GONE, action.visibility)
    }

    @Test
    fun actionMessageShowsButtonAndRunsTheActionWhenClicked() {
        val stateView = CampusStateView(context)
        val content = stateView.getChildAt(0) as LinearLayout
        val icon = content.getChildAt(0) as ImageView
        val progress = content.getChildAt(1) as ProgressBar
        val action = content.getChildAt(3) as MaterialButton
        var actionCalled = false

        stateView.showMessage("Não foi possível carregar", "Tentar novamente") {
            actionCalled = true
        }
        action.performClick()

        assertEquals(View.VISIBLE, icon.visibility)
        assertEquals(View.GONE, progress.visibility)
        assertEquals(View.VISIBLE, action.visibility)
        assertEquals("Tentar novamente", action.text.toString())
        assertTrue(actionCalled)
    }

    @Test
    fun hideMessageHidesTheWholeStateView() {
        val stateView = CampusStateView(context)
        stateView.showMessage("Nenhum evento")

        stateView.hideMessage()

        assertEquals(View.GONE, stateView.visibility)
        assertFalse(stateView.isShown)
    }
}
