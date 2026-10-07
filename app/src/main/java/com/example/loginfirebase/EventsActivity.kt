package com.example.loginfirebase

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
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

        val eventList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(eventList)
        eventList.addCampusText(getString(R.string.events_loading))

        FirebaseFirestoreProvider.db.collection("events").get()
            .addOnSuccessListener { snapshot ->
                eventList.removeAllViews()
                if (snapshot.isEmpty) {
                    eventList.addCampusText(getString(R.string.events_empty))
                    return@addOnSuccessListener
                }

                snapshot.documents.forEach { document ->
                    val event = document.toCampusEvent()
                    eventList.addCampusText(
                        getString(R.string.event_list_item, event.title, event.date, event.place),
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
