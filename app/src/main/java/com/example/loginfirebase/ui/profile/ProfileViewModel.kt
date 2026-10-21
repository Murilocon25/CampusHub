package com.example.loginfirebase.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.loginfirebase.data.repository.UserProfile
import com.example.loginfirebase.data.repository.UserRepository

class ProfileViewModel(private val repository: UserRepository = UserRepository()) : ViewModel() {
    private val mutableState = MutableLiveData<ProfileState>()
    val state: LiveData<ProfileState> = mutableState
    private val mutableSaveState = MutableLiveData<ProfileSaveState>(ProfileSaveState.Idle)
    val saveState: LiveData<ProfileSaveState> = mutableSaveState

    fun load(uid: String) {
        mutableState.value = ProfileState.Loading
        repository.loadProfile(uid) { result ->
            mutableState.postValue(result.fold(
                onSuccess = { ProfileState.Success(it) },
                onFailure = { ProfileState.Error }
            ))
        }
    }

    fun save(uid: String, profile: UserProfile) {
        repository.saveProfile(uid, profile.name, profile.course, profile.campus) { result ->
            mutableSaveState.postValue(if (result.isSuccess) ProfileSaveState.Saved else ProfileSaveState.Error)
        }
    }

    fun clearSaveState() { mutableSaveState.value = ProfileSaveState.Idle }
}
