package com.example.loginfirebase

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth


class EventDetailActivity : AppCompatActivity(){

    companion object {const val  EXTRA_EVENT_ID = "event_id"}
    override fun  onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null){ startActivity(Intent(this, LoginActivity::class.java)); finish(); return}
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
        if (eventId.isNullOrBlank()) {
            finish()
            return
        }

        val body = buildCampusScreen(getString(R.string.event_details_title)).tag as LinearLayout
        body.addCampusText(getString(R.string.event_details_loading))

        FirebaseFirestoreProvider.db.collection("events").document(eventId).get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    Toast.makeText(this, getString(R.string.event_not_found), Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                val event = document.toCampusEvent()
                body.removeAllViews()
                body.addCampusText(event.title, 23f, bold = true)
                body.addCampusText(getString(R.string.event_date, event.date), 16f)
                body.addCampusText(getString(R.string.event_place, event.place), 16f)
                body.addCampusText(event.description, 17f)

                val registration = FirebaseFirestoreProvider.db
                    .registrations(user.uid)
                    .document(event.id)
                registration.get()
                    .addOnSuccessListener { snapshot ->
                        val enrolled = snapshot.exists()
                        val buttonText = if (enrolled) {
                            getString(R.string.registration_cancel)
                        } else {
                            getString(R.string.registration_join)
                        }
                        body.addCampusButton(buttonText) {
                            if (enrolled) {
                                registration.delete()
                                    .addOnSuccessListener {
                                        Toast.makeText(this, getString(R.string.registration_cancelled), Toast.LENGTH_SHORT).show()
                                        recreate()
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(
                                            this,
                                            getString(R.string.registration_cancel_error, it.localizedMessage.orEmpty()),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                            } else {
                                registration.set(
                                    mapOf(
                                        "eventId" to event.id,
                                        "eventTitle" to event.title,
                                        "registeredAt" to System.currentTimeMillis()
                                    )
                                )
                                    .addOnSuccessListener {
                                        Toast.makeText(this, getString(R.string.registration_confirmed), Toast.LENGTH_SHORT).show()
                                        recreate()
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(
                                            this,
                                            getString(R.string.registration_error, it.localizedMessage.orEmpty()),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                            }
                        }
                    }
                    .addOnFailureListener {
                        body.addCampusText(getString(R.string.registration_check_error))
                    }
            }
            .addOnFailureListener {
                body.removeAllViews()
                body.addCampusText(getString(R.string.event_load_error))
            }
    }
}
