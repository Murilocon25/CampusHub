package com.example.loginfirebase.ui.events

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.loginfirebase.data.repository.EventRepository

class UserEventsViewModel(private val repository: EventRepository = EventRepository()) : ViewModel() {
    private val mutableState = MutableLiveData<EventListState>()
    val state: LiveData<EventListState> = mutableState

    fun load(uid: String, collection: String) {
        mutableState.value = EventListState.Loading
        repository.getUserEvents(uid, collection) { result ->
            mutableState.postValue(result.fold(
                onSuccess = { if (it.isEmpty()) EventListState.Empty else EventListState.Success(it) },
                onFailure = { EventListState.Error(it) }
            ))
        }
    }
}
