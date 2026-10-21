package com.example.loginfirebase.ui.profile

sealed interface ProfileSaveState {
    data object Idle : ProfileSaveState
    data object Saved : ProfileSaveState
    data object Error : ProfileSaveState
}
