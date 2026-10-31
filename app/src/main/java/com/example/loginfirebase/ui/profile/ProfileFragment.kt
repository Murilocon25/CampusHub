package com.example.loginfirebase.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.loginfirebase.R
import com.example.loginfirebase.data.repository.AuthRepository
import com.example.loginfirebase.data.repository.UserProfile
import com.example.loginfirebase.ui.common.CampusStateView
import com.example.loginfirebase.ui.common.showCampusSnackbar
import com.example.loginfirebase.ui.auth.LoginActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText

class ProfileFragment : Fragment(R.layout.fragment_profile) {
    private val authRepository = AuthRepository()
    private lateinit var viewModel: ProfileViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val user = authRepository.currentUser ?: return
        viewModel = ViewModelProvider(this)[ProfileViewModel::class.java]
        view.findViewById<TextView>(R.id.profile_email).text = user.email.orEmpty()
        val displayName = view.findViewById<TextView>(R.id.profile_display_name)
        val avatar = view.findViewById<TextView>(R.id.profile_avatar)
        val courseBadge = view.findViewById<Chip>(R.id.profile_course_badge)
        val campusBadge = view.findViewById<Chip>(R.id.profile_campus_badge)
        val fallbackName = user.displayName?.takeIf(String::isNotBlank)
            ?: user.email?.substringBefore('@')?.takeIf(String::isNotBlank)
            ?: getString(R.string.profile_student_fallback)
        displayName.text = fallbackName
        avatar.text = initials(fallbackName)
        val state = view.findViewById<CampusStateView>(R.id.profile_state)
        val name = view.findViewById<TextInputEditText>(R.id.profile_name)
        val course = view.findViewById<TextInputEditText>(R.id.profile_course)
        val campus = view.findViewById<TextInputEditText>(R.id.profile_campus)

        viewModel.state.observe(viewLifecycleOwner) { screenState ->
            when (screenState) {
                ProfileState.Loading -> {
                    state.showMessage(getString(R.string.profile_loading))
                }
                ProfileState.Error -> {
                    state.showMessage(getString(R.string.profile_load_error))
                }
                is ProfileState.Success -> {
                    state.hideMessage()
                    val profileName = screenState.profile.name.ifBlank { fallbackName }
                    name.setText(profileName)
                    displayName.text = profileName
                    avatar.text = initials(profileName)
                    course.setText(screenState.profile.course)
                    campus.setText(screenState.profile.campus)
                    courseBadge.isVisible = screenState.profile.course.isNotBlank()
                    campusBadge.isVisible = screenState.profile.campus.isNotBlank()
                    courseBadge.text = getString(R.string.profile_course_badge, screenState.profile.course)
                    campusBadge.text = getString(R.string.profile_campus_badge, screenState.profile.campus)
                }
            }
        }
        viewModel.saveState.observe(viewLifecycleOwner) { saveState ->
            when (saveState) {
                ProfileSaveState.Idle -> Unit
                ProfileSaveState.Saved -> {
                    view.showCampusSnackbar(R.string.profile_updated)
                    viewModel.clearSaveState()
                }
                ProfileSaveState.Error -> {
                    view.showCampusSnackbar(R.string.profile_save_error)
                    viewModel.clearSaveState()
                }
            }
        }

        view.findViewById<MaterialButton>(R.id.profile_save).setOnClickListener {
            val nameText = name.text?.toString()?.trim().orEmpty()
            if (nameText.isBlank()) {
                name.error = getString(R.string.profile_name_required)
                return@setOnClickListener
            }
            viewModel.save(user.uid, UserProfile(
                nameText,
                course.text?.toString()?.trim().orEmpty(),
                campus.text?.toString()?.trim().orEmpty()
            ))
        }
        view.findViewById<MaterialButton>(R.id.profile_sign_out).setOnClickListener {
            authRepository.signOut()
            startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            requireActivity().finish()
        }
        viewModel.load(user.uid)
    }

    private fun initials(name: String): String = name.trim()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .joinToString("")
        .ifBlank { "CH" }
}
