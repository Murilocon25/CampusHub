package com.example.loginfirebase

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class EventsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireUser()) { startActivity(Intent(this, LoginActivity::class.java)); finish(); return }
        val root = buildCampusScreen(getString(R.string.events_title))
        val body = root.tag as LinearLayout
        body.addCampusText(getString(R.string.events_intro), 15f)
        body.addCampusButton(getString(R.string.logout)) {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish()
        }

        val searchInput = EditText(this).apply {
            hint = "Buscar evento pelo título"
            singleLine = true
        }
        body.addView(searchInput)

        val categoryFilter = Spinner(this)
        body.addView(categoryFilter)

        val statusFilter = Spinner(this)
        body.addView(statusFilter)

        val eventList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(eventList)
        eventList.addCampusText(getString(R.string.events_loading))

        FirebaseFirestoreProvider.db.collection("events").get()
            .addOnSuccessListener { snapshot ->
                val events = snapshot.documents.map { it.toCampusEvent() }

                val categories = listOf("Todas as categorias") +
                    events.map { it.category }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()

                categoryFilter.adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_item,
                    categories
                ).also {
                    it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }

                val statuses = listOf("Todas as situações", "Próximos", "Encerrados")
                statusFilter.adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_item,
                    statuses
                ).also {
                    it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }

                fun renderEvents() {
                    eventList.removeAllViews()

                    val query = searchInput.text.toString().trim()
                    val selectedCategory = categoryFilter.selectedItem?.toString()
                        ?: "Todas as categorias"
                    val selectedStatus = statusFilter.selectedItem?.toString()
                        ?: "Todas as situações"

                    val filteredEvents = events.filter { event ->
                        val matchesTitle = event.title.contains(query, ignoreCase = true)
                        val matchesCategory = selectedCategory == "Todas as categorias" ||
                            event.category == selectedCategory
                        val matchesStatus = when (selectedStatus) {
                            "Próximos" -> !event.hasEnded()
                            "Encerrados" -> event.hasEnded()
                            else -> true
                        }
                        matchesTitle && matchesCategory && matchesStatus
                    }

                    if (filteredEvents.isEmpty()) {
                        eventList.addCampusText(
                            if (events.isEmpty()) getString(R.string.events_empty)
                            else "Nenhum evento corresponde à busca e aos filtros."
                        )
                        return
                    }

                    filteredEvents.forEach { event ->
                        val categoryText =
                            if (event.category.isNotBlank()) "\n${event.category}" else ""

                        eventList.addCampusText(
                            getString(
                                R.string.event_list_item,
                                event.title,
                                event.date,
                                event.place
                            ) + categoryText,
                            17f,
                            bold = true
                        )
                        eventList.addCampusButton(getString(R.string.event_details_button)) {
                            startActivity(
                                Intent(this, EventDetailActivity::class.java)
                                    .putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.id)
                            )
                        }
                    }
                }

                renderEvents()

                searchInput.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?, start: Int, count: Int, after: Int
                    ) {}

                    override fun onTextChanged(
                        s: CharSequence?, start: Int, before: Int, count: Int
                    ) {
                        renderEvents()
                    }

                    override fun afterTextChanged(s: android.text.Editable?) {}
                })

                val filterListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit

                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        renderEvents()
                    }
                }

                categoryFilter.onItemSelectedListener = filterListener
                statusFilter.onItemSelectedListener = filterListener
            }
            .addOnFailureListener {
                eventList.removeAllViews()
                eventList.addCampusText(getString(R.string.events_load_error))
            }
    }
}

class MyEventsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val root = buildCampusScreen(getString(R.string.my_events_title))
        val body = root.tag as LinearLayout
        body.addCampusText(getString(R.string.my_events_subtitle), 15f)

        val eventList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        body.addView(eventList)
        eventList.addCampusText(getString(R.string.my_events_loading))

        FirebaseFirestoreProvider.db
            .registrations(user.uid)
            .get()
            .addOnSuccessListener { registrations ->
                eventList.removeAllViews()

                val eventIds = registrations.documents.map { it.id }

                if (eventIds.isEmpty()) {
                    eventList.addCampusText(getString(R.string.my_events_empty))
                    return@addOnSuccessListener
                }

                eventIds.forEach { eventId ->
                    FirebaseFirestoreProvider.db
                        .collection("events")
                        .document(eventId)
                        .get()
                        .addOnSuccessListener { eventDocument ->
                            if (eventDocument.exists()) {
                                val event = eventDocument.toCampusEvent()

                                eventList.addCampusText(
                                    getString(R.string.my_event_item, event.title, event.date, event.place),
                                    17f,
                                    bold = true
                                )
                                eventList.addCampusButton(getString(R.string.my_event_open)) {
                                    startActivity(
                                        Intent(this, EventDetailActivity::class.java)
                                            .putExtra(
                                                EventDetailActivity.EXTRA_EVENT_ID,
                                                event.id
                                            )
                                    )
                                }
                            } else {
                                eventList.addCampusText(getString(R.string.my_event_missing))
                            }
                        }
                        .addOnFailureListener {
                            eventList.addCampusText(getString(R.string.my_event_load_error))
                        }
                }
            }
            .addOnFailureListener {
                eventList.removeAllViews()
                eventList.addCampusText(getString(R.string.my_events_load_error))
            }
    }
}

object FirebaseFirestoreProvider { val db by lazy { com.google.firebase.firestore.FirebaseFirestore.getInstance() } }
