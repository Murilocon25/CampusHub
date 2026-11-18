package com.example.loginfirebase.ui.events

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.view.View
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.example.loginfirebase.R
import com.example.loginfirebase.data.model.CampusEvent
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal data class EventFormValues(
    val title: String,
    val date: String,
    val place: String,
    val description: String,
    val category: String,
    val startAt: Timestamp,
    val location: GeoPoint,
    val capacity: Int,
    val coverImageUrl: String
)

internal class EventFormDialog(
    private val activity: FragmentActivity,
    private val requestLocation: (currentLocation: GeoPoint?, onPicked: (GeoPoint) -> Unit) -> Unit,
    private val save: (existingEvent: CampusEvent?, values: EventFormValues) -> Task<*>,
    private val onSaved: (isEdit: Boolean) -> Unit,
    private val onSaveFailed: (isEdit: Boolean) -> Unit
) {
    fun show(existingEvent: CampusEvent? = null) {
        val form = activity.layoutInflater.inflate(R.layout.dialog_create_event, null)
        val titleInput = form.findViewById<TextInputEditText>(R.id.create_event_title)
        val categoryInput = form.findViewById<TextInputEditText>(R.id.create_event_category)
        val capacityInput = form.findViewById<TextInputEditText>(R.id.create_event_capacity)
        val coverUrlInput = form.findViewById<TextInputEditText>(R.id.create_event_cover_url)
        val dateInput = form.findViewById<TextInputEditText>(R.id.create_event_date)
        val timeInput = form.findViewById<TextInputEditText>(R.id.create_event_time)
        val addressInput = form.findViewById<TextInputEditText>(R.id.create_event_address)
        val descriptionInput = form.findViewById<TextInputEditText>(R.id.create_event_description)
        val locationButton = form.findViewById<MaterialButton>(R.id.create_event_select_location)
        val locationStatus = form.findViewById<TextView>(R.id.create_event_location_status)
        val editing = existingEvent != null
        form.findViewById<TextView>(R.id.create_event_dialog_title).setText(
            if (editing) R.string.edit_event_dialog_title else R.string.create_event_dialog_title
        )

        val eventDate = existingEvent?.let(::dateForEvent)
        val eventDateTime = Calendar.getInstance().apply { if (eventDate != null) time = eventDate }
        var dateSelected = eventDate != null
        var timeSelected = eventDate != null
        var eventLocation = existingEvent?.location

        existingEvent?.let { event ->
            titleInput.setText(event.title)
            categoryInput.setText(event.category)
            capacityInput.setText(event.capacity.takeIf { it > 0 }?.toString().orEmpty())
            coverUrlInput.setText(event.coverImageUrl)
            addressInput.setText(event.place)
            descriptionInput.setText(event.description)
            if (eventDate != null) {
                dateInput.setText(dateFormat.format(eventDate))
                timeInput.setText(timeFormat.format(eventDate))
            }
        }
        if (eventLocation != null) {
            locationStatus.setText(R.string.create_event_location_selected)
            locationStatus.setTextColor(activity.getColor(R.color.color_success))
        }

        locationButton.setOnClickListener {
            requestLocation(eventLocation) { location ->
                eventLocation = location
                locationStatus.setText(R.string.create_event_location_selected)
                locationStatus.setTextColor(activity.getColor(R.color.color_success))
            }
        }

        dateInput.setOnClickListener {
            DatePickerDialog(
                activity,
                { _, year, month, day ->
                    eventDateTime.set(Calendar.YEAR, year)
                    eventDateTime.set(Calendar.MONTH, month)
                    eventDateTime.set(Calendar.DAY_OF_MONTH, day)
                    dateSelected = true
                    dateInput.setText(dateFormat.format(eventDateTime.time))
                    dateInput.error = null
                },
                eventDateTime.get(Calendar.YEAR),
                eventDateTime.get(Calendar.MONTH),
                eventDateTime.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        timeInput.setOnClickListener {
            TimePickerDialog(
                activity,
                { _, hour, minute ->
                    eventDateTime.set(Calendar.HOUR_OF_DAY, hour)
                    eventDateTime.set(Calendar.MINUTE, minute)
                    eventDateTime.set(Calendar.SECOND, 0)
                    eventDateTime.set(Calendar.MILLISECOND, 0)
                    timeSelected = true
                    timeInput.setText(timeFormat.format(eventDateTime.time))
                    timeInput.error = null
                },
                eventDateTime.get(Calendar.HOUR_OF_DAY),
                eventDateTime.get(Calendar.MINUTE),
                android.text.format.DateFormat.is24HourFormat(activity)
            ).show()
        }

        val dialog = BottomSheetDialog(activity)
        dialog.setContentView(form)
        val saveButton = form.findViewById<MaterialButton>(R.id.create_event_submit)
        saveButton.setText(if (editing) R.string.edit_event_button else R.string.create_event_button)
        form.findViewById<View>(R.id.create_event_close).setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener {
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            dialog.behavior.skipCollapsed = true
            saveButton.setOnClickListener {
                val title = titleInput.text?.toString()?.trim().orEmpty()
                val eventCategory = categoryInput.text?.toString()?.trim().orEmpty()
                val capacity = capacityInput.text?.toString()?.toIntOrNull()
                val coverImageUrl = coverUrlInput.text?.toString()?.trim().orEmpty()
                val address = addressInput.text?.toString()?.trim().orEmpty()
                val description = descriptionInput.text?.toString()?.trim().orEmpty()
                val sameDateTime = eventDate?.time == eventDateTime.timeInMillis
                val futureDate = eventDateTime.timeInMillis > System.currentTimeMillis() || (editing && sameDateTime)

                titleInput.error = if (title.length < 3) activity.getString(R.string.create_event_title_invalid) else null
                categoryInput.error = if (eventCategory.isBlank()) activity.getString(R.string.create_event_required_fields) else null
                val minimumCapacity = (existingEvent?.confirmedCount ?: 0) + (existingEvent?.reservedCount ?: 0)
                capacityInput.error = if (capacity == null || capacity !in 1..50_000 || capacity < minimumCapacity) {
                    activity.getString(R.string.create_event_capacity_invalid)
                } else null
                addressInput.error = if (address.length < 2) activity.getString(R.string.create_event_address_required) else null
                descriptionInput.error = if (description.isBlank()) activity.getString(R.string.create_event_required_fields) else null
                val coverUri = runCatching { android.net.Uri.parse(coverImageUrl) }.getOrNull()
                val validCover = coverUri?.scheme.equals("https", ignoreCase = true) &&
                    !coverUri?.host.isNullOrBlank() && coverUri?.host?.endsWith(".invalid", ignoreCase = true) != true &&
                    coverImageUrl.length <= 2048
                coverUrlInput.error = if (validCover) null else activity.getString(R.string.create_event_cover_invalid)
                dateInput.error = when {
                    !dateSelected -> activity.getString(R.string.create_event_date_required)
                    !futureDate -> activity.getString(R.string.create_event_date_past)
                    else -> null
                }
                timeInput.error = when {
                    !timeSelected -> activity.getString(R.string.create_event_time_required)
                    !futureDate -> activity.getString(R.string.create_event_date_past)
                    else -> null
                }
                if (eventLocation == null) {
                    locationStatus.setText(R.string.create_event_location_required)
                    locationStatus.setTextColor(activity.getColor(R.color.color_error))
                }

                if (title.length !in 3..120 || eventCategory.isBlank() || eventCategory.length > 80 ||
                    capacity == null || capacity !in 1..50_000 || capacity < minimumCapacity || address.length !in 2..200 ||
                    description.isBlank() || description.length > 2000 || !validCover ||
                    !dateSelected || !timeSelected || !futureDate || eventLocation == null
                ) return@setOnClickListener

                val location = eventLocation ?: return@setOnClickListener
                val timestamp = Timestamp(Date(eventDateTime.timeInMillis))
                val legacyDate = legacyDateFormat.format(eventDateTime.time)
                val values = EventFormValues(
                    title = title,
                    date = legacyDate,
                    place = address,
                    description = description,
                    category = eventCategory,
                    startAt = timestamp,
                    location = location,
                    capacity = capacity,
                    coverImageUrl = coverImageUrl
                )
                saveButton.isEnabled = false
                saveButton.setText(if (editing) R.string.edit_event_saving else R.string.create_event_saving)
                save(existingEvent, values)
                    .addOnSuccessListener {
                        if (activity.isFinishing || activity.isDestroyed) return@addOnSuccessListener
                        dialog.dismiss()
                        onSaved(editing)
                    }
                    .addOnFailureListener {
                        if (activity.isFinishing || activity.isDestroyed) return@addOnFailureListener
                        saveButton.isEnabled = true
                        saveButton.setText(if (editing) R.string.edit_event_button else R.string.create_event_button)
                        onSaveFailed(editing)
                    }
            }
        }
        dialog.show()
    }

    private fun dateForEvent(event: CampusEvent): Date? = event.startAt?.toDate()
        ?: runCatching { legacyDateFormat.parse(event.date) }.getOrNull()

    private companion object {
        val ptBrLocale: Locale = Locale.forLanguageTag("pt-BR")
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", ptBrLocale)
        val timeFormat = SimpleDateFormat("HH:mm", ptBrLocale)
        val legacyDateFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", ptBrLocale)
    }
}
