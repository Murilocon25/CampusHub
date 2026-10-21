package com.example.loginfirebase.ui.events

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.loginfirebase.R
import com.example.loginfirebase.data.model.CampusEvent
import com.example.loginfirebase.ui.common.CampusStateView
import com.example.loginfirebase.data.repository.AuthRepository

abstract class UserEventsFragment : Fragment(R.layout.fragment_user_events) {
    protected abstract val titleResource: Int
    protected abstract val subtitleResource: Int
    protected abstract val collection: String

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.user_events_title).setText(titleResource)
        view.findViewById<TextView>(R.id.user_events_subtitle).setText(subtitleResource)
        val state = view.findViewById<CampusStateView>(R.id.user_events_state)
        val list = view.findViewById<RecyclerView>(R.id.user_events_list)
        val adapter = EventAdapter(::openEvent)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        val viewModel = ViewModelProvider(this)[UserEventsViewModel::class.java]
        viewModel.state.observe(viewLifecycleOwner) { screenState ->
            when (screenState) {
                EventListState.Loading -> {
                    state.showMessage(getString(R.string.my_events_loading))
                    list.isVisible = false
                }
                EventListState.Empty -> {
                    state.showMessage(getString(if (collection == "favorites") R.string.favorites_empty else R.string.my_events_empty))
                    list.isVisible = false
                }
                is EventListState.Error -> {
                    state.showMessage(getString(if (collection == "favorites") R.string.favorites_load_error else R.string.my_events_load_error))
                    list.isVisible = false
                }
                is EventListState.Success -> {
                    state.hideMessage()
                    list.isVisible = true
                    adapter.submitList(screenState.events)
                }
            }
        }
        val uid = AuthRepository().currentUser?.uid
        if (uid == null) {
            state.showMessage(getString(R.string.auth_generic_error))
            list.isVisible = false
        } else {
            viewModel.load(uid, collection)
        }
    }

    private fun openEvent(event: CampusEvent) {
        startActivity(Intent(requireContext(), EventDetailActivity::class.java)
            .putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.id))
    }
}
