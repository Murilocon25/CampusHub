package com.example.loginfirebase.ui.events

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.example.loginfirebase.data.model.CampusEvent
import com.example.loginfirebase.data.repository.EventListDataSource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EventListViewModelsTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Test
    fun eventListViewModel_emitsLoadingAndSuccessForEvents() {
        val events = listOf(event("event-1"), event("event-2"))
        val dataSource = FakeEventListDataSource(eventsResult = Result.success(events))
        val viewModel = EventListViewModel(dataSource)

        val states = recordStates(viewModel.state) { viewModel.loadEvents() }

        assertEquals(listOf(EventListState.Loading, EventListState.Success(events)), states)
        assertEquals(1, dataSource.getEventsCalls)
    }

    @Test
    fun eventListViewModel_mapsEmptyResultsToEmptyState() {
        val viewModel = EventListViewModel(
            FakeEventListDataSource(eventsResult = Result.success(emptyList()))
        )

        val states = recordStates(viewModel.state) { viewModel.loadEvents() }

        assertEquals(listOf(EventListState.Loading, EventListState.Empty), states)
    }

    @Test
    fun eventListViewModel_preservesRepositoryFailureInErrorState() {
        val failure = IllegalStateException("offline")
        val viewModel = EventListViewModel(
            FakeEventListDataSource(eventsResult = Result.failure(failure))
        )

        val states = recordStates(viewModel.state) { viewModel.loadEvents() }

        assertEquals(listOf(EventListState.Loading, EventListState.Error(failure)), states)
    }

    @Test
    fun userEventsViewModel_requestsTheSelectedCollectionAndEmitsEvents() {
        val events = listOf(event("favorite-1"))
        val dataSource = FakeEventListDataSource(userEventsResult = Result.success(events))
        val viewModel = UserEventsViewModel(dataSource)

        val states = recordStates(viewModel.state) {
            viewModel.load(uid = "user-1", collection = "favorites")
        }

        assertEquals("user-1", dataSource.requestedUid)
        assertEquals("favorites", dataSource.requestedCollection)
        assertEquals(listOf(EventListState.Loading, EventListState.Success(events)), states)
    }

    @Test
    fun userEventsViewModel_mapsEmptyResultsToEmptyState() {
        val viewModel = UserEventsViewModel(
            FakeEventListDataSource(userEventsResult = Result.success(emptyList()))
        )

        val states = recordStates(viewModel.state) {
            viewModel.load(uid = "user-1", collection = "registrations")
        }

        assertEquals(listOf(EventListState.Loading, EventListState.Empty), states)
    }

    @Test
    fun userEventsViewModel_preservesRepositoryFailureInErrorState() {
        val failure = IllegalStateException("permission denied")
        val viewModel = UserEventsViewModel(
            FakeEventListDataSource(userEventsResult = Result.failure(failure))
        )

        val states = recordStates(viewModel.state) {
            viewModel.load(uid = "user-1", collection = "registrations")
        }

        assertEquals(listOf(EventListState.Loading, EventListState.Error(failure)), states)
    }

    private fun event(id: String) = CampusEvent(
        id = id,
        title = "Event $id",
        date = "",
        place = "",
        description = ""
    )

    private fun recordStates(liveData: LiveData<EventListState>, action: () -> Unit): List<EventListState> {
        val states = mutableListOf<EventListState>()
        val observer = Observer<EventListState> { states += it }
        liveData.observeForever(observer)
        return try {
            action()
            states
        } finally {
            liveData.removeObserver(observer)
        }
    }

    private class FakeEventListDataSource(
        private val eventsResult: Result<List<CampusEvent>> = Result.success(emptyList()),
        private val userEventsResult: Result<List<CampusEvent>> = Result.success(emptyList())
    ) : EventListDataSource {
        var getEventsCalls = 0
            private set
        var requestedUid: String? = null
            private set
        var requestedCollection: String? = null
            private set

        override fun getEvents(onResult: (Result<List<CampusEvent>>) -> Unit) {
            getEventsCalls += 1
            onResult(eventsResult)
        }

        override fun getUserEvents(
            uid: String,
            collection: String,
            onResult: (Result<List<CampusEvent>>) -> Unit
        ) {
            requestedUid = uid
            requestedCollection = collection
            onResult(userEventsResult)
        }
    }
}
