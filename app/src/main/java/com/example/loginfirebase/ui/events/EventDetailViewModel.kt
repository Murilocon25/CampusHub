package com.example.loginfirebase.ui.events

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.loginfirebase.data.model.toCampusEvent
import com.example.loginfirebase.data.repository.EventRepository

class EventDetailViewModel(private val repository: EventRepository = EventRepository()) : ViewModel() {
    private val mutableState = MutableLiveData<EventDetailState>()
    val state: LiveData<EventDetailState> = mutableState

    fun load(eventId: String) {
        mutableState.value = EventDetailState.Loading
        repository.getEvent(eventId).addOnSuccessListener { snapshot ->
            mutableState.postValue(
                if (snapshot.exists()) EventDetailState.Success(snapshot.toCampusEvent())
                else EventDetailState.Empty
            )
        }.addOnFailureListener { mutableState.postValue(EventDetailState.Error(it)) }
    }
}
