package com.example.loginfirebase.ui.profile

import com.example.loginfirebase.data.repository.UserProfile

sealed interface ProfileState {
    data object Loading : ProfileState
    data class Success(val profile: UserProfile) : ProfileState
    data object Error : ProfileState
}
