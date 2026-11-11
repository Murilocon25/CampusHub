package com.example.loginfirebase.ui.events

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.example.loginfirebase.data.model.CampusEvent
import com.example.loginfirebase.data.repository.EventDetailDataSource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EventDetailViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Test
    fun load_emitsLoadingThenEvent() {
        val event = campusEvent("event-1")
        val dataSource = FakeEventDetailDataSource(Result.success(event))
        val viewModel = EventDetailViewModel(dataSource)

        val states = recordStates(viewModel) { viewModel.load("event-1") }

        assertEquals("event-1", dataSource.requestedEventId)
        assertEquals(listOf(EventDetailState.Loading, EventDetailState.Success(event)), states)
    }

    @Test
    fun load_mapsMissingEventToEmptyState() {
        val viewModel = EventDetailViewModel(FakeEventDetailDataSource(Result.success(null)))

        val states = recordStates(viewModel) { viewModel.load("missing") }

        assertEquals(listOf(EventDetailState.Loading, EventDetailState.Empty), states)
    }

    @Test
    fun load_preservesRepositoryFailureInErrorState() {
        val failure = IllegalStateException("offline")
        val viewModel = EventDetailViewModel(FakeEventDetailDataSource(Result.failure(failure)))

        val states = recordStates(viewModel) { viewModel.load("event-1") }

        assertEquals(listOf(EventDetailState.Loading, EventDetailState.Error(failure)), states)
    }

    private fun campusEvent(id: String) = CampusEvent(
        id = id,
        title = "Campus meetup",
        date = "",
        place = "Library",
        description = ""
    )

    private fun recordStates(viewModel: EventDetailViewModel, action: () -> Unit): List<EventDetailState> {
        val states = mutableListOf<EventDetailState>()
        val observer = Observer<EventDetailState> { states += it }
        viewModel.state.observeForever(observer)
        return try {
            action()
            states
        } finally {
            viewModel.state.removeObserver(observer)
        }
    }

    private class FakeEventDetailDataSource(
        private val result: Result<CampusEvent?>
    ) : EventDetailDataSource {
        var requestedEventId: String? = null
            private set

        override fun loadEvent(eventId: String, onResult: (Result<CampusEvent?>) -> Unit) {
            requestedEventId = eventId
            onResult(result)
        }
    }
}
