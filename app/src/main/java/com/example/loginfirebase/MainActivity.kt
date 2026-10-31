package com.example.loginfirebase

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.Fragment
import com.example.loginfirebase.data.repository.AuthRepository
import com.example.loginfirebase.ui.auth.LoginActivity
import com.example.loginfirebase.ui.events.EventDetailActivity
import com.example.loginfirebase.ui.events.EventsFragment
import com.example.loginfirebase.ui.events.FavoritesFragment
import com.example.loginfirebase.ui.events.MyEventsFragment
import com.example.loginfirebase.ui.profile.ProfileFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private val authRepository = AuthRepository()
    private var selectedItem = R.id.navigation_events

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (authRepository.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_main)
        val navigation = findViewById<BottomNavigationView>(R.id.main_navigation)
        navigation.setItemActiveIndicatorEnabled(true)
        selectedItem = intent.getIntExtra(EXTRA_OPEN_TAB, 0).takeIf { it != 0 }
            ?: savedInstanceState?.getInt(STATE_SELECTED_ITEM)
            ?: R.id.navigation_events
        navigation.setOnItemSelectedListener { item ->
            selectedItem = item.itemId
            showTab(item.itemId)
            true
        }
        if (savedInstanceState == null) showTab(selectedItem)
        navigation.selectedItemId = selectedItem
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val tab = intent.getIntExtra(EXTRA_OPEN_TAB, 0)
        if (tab != 0) {
            findViewById<BottomNavigationView>(R.id.main_navigation).selectedItemId = tab
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SELECTED_ITEM, selectedItem)
        super.onSaveInstanceState(outState)
    }

    private fun showTab(itemId: Int) {
        val manager = supportFragmentManager
        val tag = "tab_$itemId"
        val current = manager.findFragmentById(R.id.fragment_container)
        if (current?.tag == tag) return
        val fragment = manager.findFragmentByTag(tag) ?: createFragment(itemId)
        manager.beginTransaction().setReorderingAllowed(true).apply {
            manager.fragments.forEach { hide(it) }
            if (fragment.isAdded) show(fragment)
            else add(R.id.fragment_container, fragment, tag)
        }.commit()
    }

    private fun createFragment(itemId: Int): Fragment = when (itemId) {
        R.id.navigation_my_events -> MyEventsFragment()
        R.id.navigation_favorites -> FavoritesFragment()
        R.id.navigation_profile -> ProfileFragment()
        else -> EventsFragment()
    }

    companion object {
        private const val STATE_SELECTED_ITEM = "selected_tab"
        const val EXTRA_OPEN_TAB = "open_tab"
    }
}
