package com.example.loginfirebase.ui.events

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.loginfirebase.data.model.CampusEvent
import com.example.loginfirebase.data.repository.EventListDataSource
import com.example.loginfirebase.data.repository.EventRepository

class EventListViewModel(private val repository: EventListDataSource = EventRepository()) : ViewModel() {
    private val mutableState = MutableLiveData<EventListState>()
    val state: LiveData<EventListState> = mutableState

    fun loadEvents() {
        mutableState.value = EventListState.Loading
        repository.getEvents { result -> mutableState.postValue(result.toEventListState()) }
    }

    fun loadEvents(uid: String, isOrganizer: Boolean) {
        mutableState.value = EventListState.Loading
        repository.getEventsForUser(uid, isOrganizer) { result ->
            mutableState.postValue(result.toEventListState())
        }
    }
}

private fun Result<List<CampusEvent>>.toEventListState(): EventListState = fold(
    onSuccess = { if (it.isEmpty()) EventListState.Empty else EventListState.Success(it) },
    onFailure = { EventListState.Error(it) }
)
