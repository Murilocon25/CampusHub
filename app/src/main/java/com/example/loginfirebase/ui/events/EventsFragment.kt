package com.example.loginfirebase.ui.events

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import com.example.loginfirebase.R
import com.example.loginfirebase.data.model.CampusEvent
import com.example.loginfirebase.ui.common.CampusStateView
import com.example.loginfirebase.ui.common.CampusSkeletonListView
import com.example.loginfirebase.ui.common.showCampusSnackbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
    private val eventRepository = EventRepository()
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
        viewModel = ViewModelProvider(this)[EventListViewModel::class.java]
        stateView = view.findViewById(R.id.event_state)
        loadingSkeleton = view.findViewById(R.id.event_loading_skeleton)
        search = view.findViewById(R.id.event_search)
        category = view.findViewById(R.id.category_filter)
        status = view.findViewById(R.id.status_filter)
        val user = AuthRepository().currentUser
        val name = user?.displayName?.trim()?.split(Regex("\\s+"))?.firstOrNull()
            ?: user?.email?.substringBefore('@')?.takeIf(String::isNotBlank)
        view.findViewById<TextView>(R.id.event_greeting).text = name?.let {
            getString(R.string.event_greeting_named, it)
        } ?: getString(R.string.event_greeting_fallback)
        createEventButton = view.findViewById(R.id.create_event_button)
        createEventButton.setOnClickListener {
            showCreateEventDialog()
        }
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
        viewModel.loadEvents()
    }

    private fun renderState(state: EventListState) {
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
                stateView.showMessage(getString(R.string.events_empty), getString(R.string.state_create_first_event)) {
                    createEventButton.performClick()
                }
            }
            is EventListState.Error -> {
                loadingSkeleton.isVisible = false
                recycler.isVisible = false
                stateView.showMessage(getString(R.string.events_load_error), getString(R.string.state_retry)) {
                    viewModel.loadEvents()
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
        val selectedCategory = category.findViewById<Chip>(category.checkedChipId)?.text?.toString() ?: all
        val selectedStatus = status.findViewById<Chip>(status.checkedChipId)?.text?.toString() ?: all
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
        if (user == null) {
            requireView().showCampusSnackbar(R.string.auth_generic_error)
            return
        }

        val form = layoutInflater.inflate(R.layout.dialog_create_event, null)
        val titleInput = form.findViewById<TextInputEditText>(R.id.create_event_title)
        val categoryInput = form.findViewById<TextInputEditText>(R.id.create_event_category)
        val dateInput = form.findViewById<TextInputEditText>(R.id.create_event_date)
        val timeInput = form.findViewById<TextInputEditText>(R.id.create_event_time)
        val addressInput = form.findViewById<TextInputEditText>(R.id.create_event_address)
        val descriptionInput = form.findViewById<TextInputEditText>(R.id.create_event_description)
        val locationButton = form.findViewById<MaterialButton>(R.id.create_event_select_location)
        val locationStatus = form.findViewById<android.widget.TextView>(R.id.create_event_location_status)
        var eventLocation: GeoPoint? = null

        locationButton.setOnClickListener {
            onLocationPicked = { location ->
                eventLocation = location
                locationStatus.setText(R.string.create_event_location_selected)
                    locationStatus.setTextColor(requireContext().getColor(R.color.color_success))
            }
            val pickerIntent = Intent(requireContext(), EventLocationPickerActivity::class.java)
            eventLocation?.let { currentLocation ->
                pickerIntent.putExtra(EventLocationPickerActivity.EXTRA_LATITUDE, currentLocation.latitude)
                pickerIntent.putExtra(EventLocationPickerActivity.EXTRA_LONGITUDE, currentLocation.longitude)
            }
            locationPickerLauncher.launch(pickerIntent)
        }

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

        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(form)
        val saveButton = form.findViewById<MaterialButton>(R.id.create_event_submit)
        form.findViewById<View>(R.id.create_event_close).setOnClickListener { dialog.dismiss() }

        dialog.setOnShowListener {
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            dialog.behavior.skipCollapsed = true
            saveButton.setOnClickListener {
                val title = titleInput.text?.toString()?.trim().orEmpty()
                val eventCategory = categoryInput.text?.toString()?.trim().orEmpty()
                val address = addressInput.text?.toString()?.trim().orEmpty()
                val description = descriptionInput.text?.toString()?.trim().orEmpty()

                titleInput.error = if (title.length < 3) getString(R.string.create_event_title_invalid) else null
                categoryInput.error = if (eventCategory.isBlank()) getString(R.string.create_event_required_fields) else null
                addressInput.error = if (address.length < 5) getString(R.string.create_event_address_required) else null
                descriptionInput.error = if (description.isBlank()) getString(R.string.create_event_required_fields) else null
                dateInput.error = if (!dateSelected) getString(R.string.create_event_date_required) else null
                timeInput.error = if (!timeSelected) getString(R.string.create_event_time_required) else null
                if (eventLocation == null) {
                    locationStatus.setText(R.string.create_event_location_required)
                    locationStatus.setTextColor(requireContext().getColor(R.color.color_error))
                }

                if (title.length !in 3..120 || eventCategory.isBlank() || address.length !in 5..200 || description.isBlank() ||
                    !dateSelected || !timeSelected || eventLocation == null
                ) return@setOnClickListener

                val location = eventLocation ?: return@setOnClickListener

                val startTime = Timestamp(Date(eventDateTime.timeInMillis))
                val legacyDate = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", ptBrLocale).format(eventDateTime.time)
                saveButton.isEnabled = false
                saveButton.setText(R.string.create_event_saving)

                eventRepository.createEvent(
                    title = title,
                    date = legacyDate,
                    place = address,
                    description = description,
                    category = eventCategory,
                    startAt = startTime,
                    creatorUid = user.uid,
                    location = location
                ).addOnSuccessListener {
                    if (!isAdded) return@addOnSuccessListener
                    dialog.dismiss()
                    requireView().showCampusSnackbar(R.string.create_event_success)
                    viewModel.loadEvents()
                }.addOnFailureListener {
                    if (!isAdded) return@addOnFailureListener
                    saveButton.isEnabled = true
                    saveButton.setText(R.string.create_event_button)
                    requireView().showCampusSnackbar(R.string.create_event_error, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                }
            }
        }
        dialog.show()
    }

    private companion object {
        val ptBrLocale: Locale = Locale.forLanguageTag("pt-BR")
    }
}
