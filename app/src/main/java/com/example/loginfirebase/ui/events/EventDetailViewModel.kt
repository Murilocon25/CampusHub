package com.example.loginfirebase.ui.events

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.loginfirebase.data.repository.EventDetailDataSource
import com.example.loginfirebase.data.repository.EventRepository

class EventDetailViewModel(private val repository: EventDetailDataSource = EventRepository()) : ViewModel() {
    private val mutableState = MutableLiveData<EventDetailState>()
    val state: LiveData<EventDetailState> = mutableState

    fun load(eventId: String) {
        mutableState.value = EventDetailState.Loading
        repository.loadEvent(eventId) { result ->
            mutableState.postValue(result.fold(
                onSuccess = { event -> if (event == null) EventDetailState.Empty else EventDetailState.Success(event) },
                onFailure = { EventDetailState.Error(it) }
            ))
        }
    }
}
