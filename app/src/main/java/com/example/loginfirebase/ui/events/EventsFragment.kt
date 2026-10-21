package com.example.loginfirebase.ui.events

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.loginfirebase.data.repository.AuthRepository
import com.example.loginfirebase.data.repository.EventRepository
import com.example.loginfirebase.R
import com.example.loginfirebase.data.model.CampusEvent
import com.example.loginfirebase.ui.common.CampusStateView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class EventsFragment : Fragment(R.layout.fragment_events) {
    private lateinit var viewModel: EventListViewModel
    private lateinit var adapter: EventAdapter
    private lateinit var stateView: CampusStateView
    private lateinit var search: TextInputEditText
    private lateinit var category: MaterialAutoCompleteTextView
    private lateinit var status: MaterialAutoCompleteTextView
    private var allEvents: List<CampusEvent> = emptyList()
    private val eventRepository = EventRepository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[EventListViewModel::class.java]
        stateView = view.findViewById(R.id.event_state)
        search = view.findViewById(R.id.event_search)
        category = view.findViewById(R.id.category_filter)
        status = view.findViewById(R.id.status_filter)
        view.findViewById<MaterialButton>(R.id.create_event_button).setOnClickListener {
            showCreateEventDialog()
        }
        adapter = EventAdapter(::openEvent)
        view.findViewById<RecyclerView>(R.id.event_list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@EventsFragment.adapter
        }

        val all = getString(R.string.filter_all)
        category.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, listOf(all)))
        category.setText(all, false)
        status.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, listOf(
            all, getString(R.string.event_status_upcoming_filter), getString(R.string.event_status_ended_filter)
        )))
        status.setText(all, false)
        category.setOnItemClickListener { _, _, _, _ -> renderEvents() }
        status.setOnItemClickListener { _, _, _, _ -> renderEvents() }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderEvents()
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        viewModel.state.observe(viewLifecycleOwner, ::renderState)
        viewModel.loadEvents()
    }

    private fun renderState(state: EventListState) {
        val recycler = requireView().findViewById<RecyclerView>(R.id.event_list)
        when (state) {
            EventListState.Loading -> {
                recycler.isVisible = false
                stateView.showMessage(getString(R.string.events_loading))
            }
            EventListState.Empty -> {
                recycler.isVisible = false
                stateView.showMessage(getString(R.string.events_empty))
            }
            is EventListState.Error -> {
                recycler.isVisible = false
                stateView.showMessage(getString(R.string.events_load_error))
            }
            is EventListState.Success -> {
                allEvents = state.events
                recycler.isVisible = true
                stateView.hideMessage()
                val categories = listOf(getString(R.string.filter_all)) + state.events
                    .map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
                category.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, categories))
                renderEvents()
            }
        }
    }

    private fun renderEvents() {
        if (!isAdded || !::adapter.isInitialized || !::stateView.isInitialized) return
        val all = getString(R.string.filter_all)
        val selectedCategory = category.text?.toString().orEmpty().ifBlank { all }
        val selectedStatus = status.text?.toString().orEmpty().ifBlank { all }
        val filtered = allEvents.filter { event ->
            val matchesText = event.title.contains(search.text?.toString().orEmpty().trim(), ignoreCase = true)
            val matchesCategory = selectedCategory == all || selectedCategory == event.category
            val matchesStatus = when (selectedStatus) {
                getString(R.string.event_status_upcoming_filter) -> !event.hasEnded()
                getString(R.string.event_status_ended_filter) -> event.hasEnded()
                else -> true
            }
            matchesText && matchesCategory && matchesStatus
        }
        adapter.submitList(filtered)
        if (filtered.isEmpty()) stateView.showMessage(getString(R.string.events_filter_empty))
        else stateView.hideMessage()
    }

    private fun openEvent(event: CampusEvent) {
        startActivity(Intent(requireContext(), EventDetailActivity::class.java)
            .putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.id))
    }

    private fun showCreateEventDialog() {
        val user = AuthRepository().currentUser
        if (user == null) {
            Toast.makeText(requireContext(), getString(R.string.auth_generic_error), Toast.LENGTH_SHORT).show()
            return
        }

        val form = layoutInflater.inflate(R.layout.dialog_create_event, null)
        val titleInput = form.findViewById<TextInputEditText>(R.id.create_event_title)
        val categoryInput = form.findViewById<TextInputEditText>(R.id.create_event_category)
        val dateInput = form.findViewById<TextInputEditText>(R.id.create_event_date)
        val timeInput = form.findViewById<TextInputEditText>(R.id.create_event_time)
        val placeInput = form.findViewById<MaterialAutoCompleteTextView>(R.id.create_event_place)
        val descriptionInput = form.findViewById<TextInputEditText>(R.id.create_event_description)
        val locations = resources.getStringArray(R.array.uri_santiago_places)
        placeInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, locations))

        val eventDateTime = Calendar.getInstance()
        var dateSelected = false
        var timeSelected = false

        dateInput.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    eventDateTime.set(Calendar.YEAR, year)
                    eventDateTime.set(Calendar.MONTH, month)
                    eventDateTime.set(Calendar.DAY_OF_MONTH, day)
                    dateSelected = true
                    dateInput.setText(SimpleDateFormat("dd/MM/yyyy", ptBrLocale).format(eventDateTime.time))
                    dateInput.error = null
                },
                eventDateTime.get(Calendar.YEAR),
                eventDateTime.get(Calendar.MONTH),
                eventDateTime.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        timeInput.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                { _, hour, minute ->
                    eventDateTime.set(Calendar.HOUR_OF_DAY, hour)
                    eventDateTime.set(Calendar.MINUTE, minute)
                    eventDateTime.set(Calendar.SECOND, 0)
                    eventDateTime.set(Calendar.MILLISECOND, 0)
                    timeSelected = true
                    timeInput.setText(String.format(ptBrLocale, "%02d:%02d", hour, minute))
                    timeInput.error = null
                },
                eventDateTime.get(Calendar.HOUR_OF_DAY),
                eventDateTime.get(Calendar.MINUTE),
                android.text.format.DateFormat.is24HourFormat(requireContext())
            ).show()
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.create_event_dialog_title)
            .setView(form)
            .setNegativeButton(R.string.back_to_events, null)
            .setPositiveButton(R.string.create_event_button, null)
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val title = titleInput.text?.toString()?.trim().orEmpty()
                val eventCategory = categoryInput.text?.toString()?.trim().orEmpty()
                val place = placeInput.text?.toString()?.trim().orEmpty()
                val description = descriptionInput.text?.toString()?.trim().orEmpty()

                titleInput.error = if (title.length < 3) getString(R.string.create_event_title_invalid) else null
                categoryInput.error = if (eventCategory.isBlank()) getString(R.string.create_event_required_fields) else null
                placeInput.error = if (place.isBlank()) getString(R.string.create_event_required_fields) else null
                descriptionInput.error = if (description.isBlank()) getString(R.string.create_event_required_fields) else null
                dateInput.error = if (!dateSelected) getString(R.string.create_event_date_required) else null
                timeInput.error = if (!timeSelected) getString(R.string.create_event_time_required) else null

                if (title.length !in 3..120 || eventCategory.isBlank() || place.length !in 2..200 || description.isBlank() ||
                    !dateSelected || !timeSelected
                ) return@setOnClickListener

                val startTime = Timestamp(Date(eventDateTime.timeInMillis))
                val legacyDate = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", ptBrLocale).format(eventDateTime.time)
                saveButton.isEnabled = false
                saveButton.setText(R.string.create_event_saving)

                eventRepository.createEvent(
                    title = title,
                    date = legacyDate,
                    place = place,
                    description = description,
                    category = eventCategory,
                    startAt = startTime,
                    creatorUid = user.uid
                ).addOnSuccessListener {
                    if (!isAdded) return@addOnSuccessListener
                    dialog.dismiss()
                    Toast.makeText(requireContext(), getString(R.string.create_event_success), Toast.LENGTH_SHORT).show()
                    viewModel.loadEvents()
                }.addOnFailureListener {
                    if (!isAdded) return@addOnFailureListener
                    saveButton.isEnabled = true
                    saveButton.setText(R.string.create_event_button)
                    Toast.makeText(requireContext(), getString(R.string.create_event_error), Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
    }

    private companion object {
        val ptBrLocale: Locale = Locale.forLanguageTag("pt-BR")
    }
}
