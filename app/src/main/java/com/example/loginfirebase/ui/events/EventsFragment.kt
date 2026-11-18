package com.example.loginfirebase.ui.events

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.loginfirebase.data.repository.AuthRepository
import com.example.loginfirebase.data.repository.EventRepository
import com.example.loginfirebase.data.repository.UserRepository
import com.example.loginfirebase.data.repository.UserRole
import com.example.loginfirebase.R
import com.example.loginfirebase.data.model.CampusEvent
import com.example.loginfirebase.ui.common.CampusStateView
import com.example.loginfirebase.ui.common.CampusSkeletonListView
import com.example.loginfirebase.ui.common.showCampusSnackbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.GeoPoint

class EventsFragment : Fragment(R.layout.fragment_events) {
    private lateinit var viewModel: EventListViewModel
    private lateinit var adapter: EventAdapter
    private lateinit var stateView: CampusStateView
    private lateinit var loadingSkeleton: CampusSkeletonListView
    private lateinit var search: TextInputEditText
    private lateinit var category: ChipGroup
    private lateinit var status: ChipGroup
    private lateinit var createEventButton: ExtendedFloatingActionButton
    private var allEvents: List<CampusEvent> = emptyList()
    private val userRepository = UserRepository()
    private val eventRepository = EventRepository()
    private var isOrganizer = false
    private var currentUserUid: String? = null
    private var isViewReady = false
    private var latestEventState: EventListState? = null
    private var onLocationPicked: ((GeoPoint) -> Unit)? = null
    private val locationPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val latitude = data?.getDoubleExtra(EventLocationPickerActivity.EXTRA_LATITUDE, Double.NaN) ?: Double.NaN
            val longitude = data?.getDoubleExtra(EventLocationPickerActivity.EXTRA_LONGITUDE, Double.NaN) ?: Double.NaN
            if (latitude.isFinite() && longitude.isFinite()) {
                onLocationPicked?.invoke(GeoPoint(latitude, longitude))
            }
        }
        onLocationPicked = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isViewReady = true
        viewModel = ViewModelProvider(this)[EventListViewModel::class.java]
        stateView = view.findViewById(R.id.event_state)
        loadingSkeleton = view.findViewById(R.id.event_loading_skeleton)
        search = view.findViewById(R.id.event_search)
        category = view.findViewById(R.id.category_filter)
        status = view.findViewById(R.id.status_filter)
        val user = AuthRepository().currentUser
        currentUserUid = user?.uid
        createEventButton = view.findViewById(R.id.create_event_button)
        createEventButton.isVisible = false
        val name = user?.displayName?.trim()?.split(Regex("\\s+"))?.firstOrNull()
            ?: user?.email?.substringBefore('@')?.takeIf(String::isNotBlank)
        view.findViewById<TextView>(R.id.event_greeting).text = name?.let {
            getString(R.string.event_greeting_named, it)
        } ?: getString(R.string.event_greeting_fallback)
        createEventButton.setOnClickListener {
            showCreateEventDialog()
        }
        user?.let { currentUser ->
            userRepository.loadProfile(currentUser.uid) { result ->
                if (isAdded && isViewReady) {
                    isOrganizer = result.getOrNull()?.role == UserRole.ORGANIZER
                    if (isOrganizer) createEventButton.extend()
                    createEventButton.isVisible = isOrganizer
                    viewModel.loadEvents(currentUser.uid, isOrganizer)
                    latestEventState?.let(::renderState)
                }
            }
        }
        if (user == null) viewModel.loadEvents()
        adapter = EventAdapter(::openEvent)
        view.findViewById<RecyclerView>(R.id.event_list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@EventsFragment.adapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy > 0) createEventButton.shrink()
                    else if (dy < 0) createEventButton.extend()
                }
            })
        }

        val all = getString(R.string.filter_all)
        populateFilterChips(category, listOf(all), all)
        populateFilterChips(status, listOf(
            all, getString(R.string.event_status_upcoming_filter), getString(R.string.event_status_ended_filter)
        ), all)
        category.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) renderEvents()
        }
        status.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) renderEvents()
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderEvents()
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        viewModel.state.observe(viewLifecycleOwner, ::renderState)
    }

    private fun reloadEvents() {
        currentUserUid?.let { viewModel.loadEvents(it, isOrganizer) } ?: viewModel.loadEvents()
    }

    private fun renderState(state: EventListState) {
        latestEventState = state
        val recycler = requireView().findViewById<RecyclerView>(R.id.event_list)
        when (state) {
            EventListState.Loading -> {
                recycler.isVisible = false
                stateView.hideMessage()
                loadingSkeleton.isVisible = true
            }
            EventListState.Empty -> {
                loadingSkeleton.isVisible = false
                recycler.isVisible = false
                if (isOrganizer) {
                    stateView.showMessage(getString(R.string.events_empty), getString(R.string.state_create_first_event)) {
                        createEventButton.performClick()
                    }
                } else stateView.showMessage(getString(R.string.events_empty))
            }
            is EventListState.Error -> {
                loadingSkeleton.isVisible = false
                recycler.isVisible = false
                stateView.showMessage(getString(R.string.events_load_error), getString(R.string.state_retry)) {
                    reloadEvents()
                }
            }
            is EventListState.Success -> {
                loadingSkeleton.isVisible = false
                allEvents = state.events
                recycler.isVisible = true
                stateView.hideMessage()
                val all = getString(R.string.filter_all)
                val categories = listOf(all) + state.events
                    .map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
                val previous = category.findViewById<Chip>(category.checkedChipId)?.text?.toString() ?: all
                populateFilterChips(category, categories, previous.takeIf(categories::contains) ?: all)
                renderEvents()
            }
        }
    }

    private fun renderEvents() {
        if (!isAdded || !::adapter.isInitialized || !::stateView.isInitialized) return
        val all = getString(R.string.filter_all)
        val selectedCategory = category.findViewById<Chip>(category.checkedChipId)
            ?.text?.toString()?.takeUnless { it == all }
        val selectedStatus = status.findViewById<Chip>(status.checkedChipId)?.text?.toString() ?: all
        val timeFilter = when (selectedStatus) {
            getString(R.string.event_status_upcoming_filter) -> EventTimeFilter.UPCOMING
            getString(R.string.event_status_ended_filter) -> EventTimeFilter.ENDED
            else -> EventTimeFilter.ALL
        }
        val filtered = filterCampusEvents(
            events = allEvents,
            query = search.text?.toString().orEmpty(),
            selectedCategory = selectedCategory,
            timeFilter = timeFilter
        )
        adapter.submitList(filtered)
        if (filtered.isEmpty()) stateView.showMessage(
            getString(R.string.events_filter_empty), getString(R.string.state_clear_filters)
        ) {
            search.text?.clear()
            category.getChildAt(0)?.id?.let(category::check)
            status.getChildAt(0)?.id?.let(status::check)
        }
        else stateView.hideMessage()
    }

    private fun populateFilterChips(group: ChipGroup, labels: List<String>, selected: String) {
        group.removeAllViews()
        labels.forEach { label ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = label
                isCheckable = true
                setEnsureMinTouchTargetSize(true)
                setChipBackgroundColorResource(R.color.campus_chip_background)
                setChipStrokeColorResource(R.color.color_border)
                chipStrokeWidth = resources.getDimension(R.dimen.space_1)
                chipCornerRadius = resources.getDimension(R.dimen.shape_large)
                setTextColor(resources.getColorStateList(R.color.campus_chip_text, requireContext().theme))
                setTextAppearance(R.style.TextAppearance_CampusHub_Caption)
                isChecked = label == selected
            }
            group.addView(chip)
        }
    }

    private fun openEvent(event: CampusEvent) {
        startActivity(Intent(requireContext(), EventDetailActivity::class.java)
            .putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.id))
    }

    private fun showCreateEventDialog() {
        val user = AuthRepository().currentUser
        if (user == null || !isOrganizer) {
            if (user != null) requireView().showCampusSnackbar(R.string.organizer_only_create)
            else requireView().showCampusSnackbar(R.string.auth_generic_error)
            return
        }

        EventFormDialog(
            activity = requireActivity(),
            requestLocation = { currentLocation, onPicked ->
                onLocationPicked = onPicked
                val pickerIntent = Intent(requireContext(), EventLocationPickerActivity::class.java)
                currentLocation?.let {
                    pickerIntent.putExtra(EventLocationPickerActivity.EXTRA_LATITUDE, it.latitude)
                    pickerIntent.putExtra(EventLocationPickerActivity.EXTRA_LONGITUDE, it.longitude)
                }
                locationPickerLauncher.launch(pickerIntent)
            },
            save = { existingEvent, values ->
                if (existingEvent == null) {
                    eventRepository.createEvent(
                        title = values.title,
                        date = values.date,
                        place = values.place,
                        description = values.description,
                        category = values.category,
                        startAt = values.startAt,
                        creatorUid = user.uid,
                        location = values.location,
                        capacity = values.capacity,
                        coverImageUrl = values.coverImageUrl
                    )
                } else {
                    eventRepository.updateEvent(
                        eventId = existingEvent.id,
                        title = values.title,
                        date = values.date,
                        place = values.place,
                        description = values.description,
                        category = values.category,
                        startAt = values.startAt,
                        location = values.location,
                        capacity = values.capacity,
                        coverImageUrl = values.coverImageUrl
                    )
                }
            },
            onSaved = { isEdit ->
                if (isAdded && view != null) {
                    requireView().showCampusSnackbar(
                        if (isEdit) R.string.edit_event_success else R.string.create_event_success
                    )
                    reloadEvents()
                }
            },
            onSaveFailed = { isEdit ->
                if (isAdded && view != null) {
                    requireView().showCampusSnackbar(
                        if (isEdit) R.string.edit_event_error else R.string.create_event_error,
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    )
                }
            }
        ).show()
    }

    override fun onDestroyView() {
        isViewReady = false
        super.onDestroyView()
    }

}
