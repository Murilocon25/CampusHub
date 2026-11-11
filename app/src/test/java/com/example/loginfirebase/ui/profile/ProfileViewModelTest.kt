package com.example.loginfirebase.ui.profile

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.example.loginfirebase.data.repository.UserProfile
import com.example.loginfirebase.data.repository.UserProfileRepository
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProfileViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Test
    fun load_emitsLoadingThenLoadedProfile() {
        val profile = UserProfile("Ana", "Design", "North Campus")
        val repository = FakeUserProfileRepository(loadResult = Result.success(profile))
        val viewModel = ProfileViewModel(repository)
        val states = mutableListOf<ProfileState>()
        val observer = Observer<ProfileState> { states += it }
        viewModel.state.observeForever(observer)

        try {
            viewModel.load("user-1")
        } finally {
            viewModel.state.removeObserver(observer)
        }

        assertEquals("user-1", repository.loadedUid)
        assertEquals(listOf(ProfileState.Loading, ProfileState.Success(profile)), states)
    }

    @Test
    fun load_mapsRepositoryFailureToErrorState() {
        val repository = FakeUserProfileRepository(loadResult = Result.failure(IllegalStateException("offline")))
        val viewModel = ProfileViewModel(repository)
        val states = mutableListOf<ProfileState>()
        val observer = Observer<ProfileState> { states += it }
        viewModel.state.observeForever(observer)

        try {
            viewModel.load("user-2")
        } finally {
            viewModel.state.removeObserver(observer)
        }

        assertEquals(listOf(ProfileState.Loading, ProfileState.Error), states)
    }

    @Test
    fun save_delegatesProfileAndEmitsSavedThenCanResetToIdle() {
        val repository = FakeUserProfileRepository()
        val viewModel = ProfileViewModel(repository)
        val states = mutableListOf<ProfileSaveState>()
        val observer = Observer<ProfileSaveState> { states += it }
        viewModel.saveState.observeForever(observer)

        try {
            val profile = UserProfile("Ana", "Design", "North Campus")
            viewModel.save("user-3", profile)
            viewModel.clearSaveState()

            assertEquals("user-3", repository.savedUid)
            assertEquals(profile, repository.savedProfile)
            assertEquals(
                listOf(ProfileSaveState.Idle, ProfileSaveState.Saved, ProfileSaveState.Idle),
                states
            )
        } finally {
            viewModel.saveState.removeObserver(observer)
        }
    }

    @Test
    fun save_mapsRepositoryFailureToErrorState() {
        val repository = FakeUserProfileRepository(saveResult = Result.failure(IllegalStateException("offline")))
        val viewModel = ProfileViewModel(repository)
        val states = mutableListOf<ProfileSaveState>()
        val observer = Observer<ProfileSaveState> { states += it }
        viewModel.saveState.observeForever(observer)

        try {
            viewModel.save("user-4", UserProfile(name = "Ana"))
        } finally {
            viewModel.saveState.removeObserver(observer)
        }

        assertEquals(listOf(ProfileSaveState.Idle, ProfileSaveState.Error), states)
    }

    private class FakeUserProfileRepository(
        private val loadResult: Result<UserProfile> = Result.success(UserProfile()),
        private val saveResult: Result<Unit> = Result.success(Unit)
    ) : UserProfileRepository {
        var loadedUid: String? = null
            private set
        var savedUid: String? = null
            private set
        var savedProfile: UserProfile? = null
            private set

        override fun loadProfile(uid: String, onResult: (Result<UserProfile>) -> Unit) {
            loadedUid = uid
            onResult(loadResult)
        }

        override fun saveProfile(
            uid: String,
            name: String,
            course: String,
            campus: String,
            onResult: (Result<Unit>) -> Unit
        ) {
            savedUid = uid
            savedProfile = UserProfile(name, course, campus)
            onResult(saveResult)
        }
    }
}
