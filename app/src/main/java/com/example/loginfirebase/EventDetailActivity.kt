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

        val body = buildCampusScreen("Detalhes do evento").tag as LinearLayout
        body.addCampusText("Carregando detalhes do evento...")

        FirebaseFirestoreProvider.db.collection("events").document(eventId).get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    Toast.makeText(this, "Evento não encontrado.", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                val event = document.toCampusEvent()
                body.removeAllViews()
                body.addCampusText(event.title, 23f, bold = true)
                body.addCampusText("📅  ${event.date}", 16f)
                body.addCampusText("📍  ${event.place}", 16f)
                body.addCampusText(event.description, 17f)

                val registration = FirebaseFirestoreProvider.db
                    .registrations(user.uid)
                    .document(event.id)
                registration.get()
                    .addOnSuccessListener { snapshot ->
                        val enrolled = snapshot.exists()
                        body.addCampusButton(if (enrolled) "Cancelar inscrição" else "Inscrever-se") {
                            if (enrolled) {
                                registration.delete()
                                    .addOnSuccessListener {
                                        Toast.makeText(this, "Inscrição cancelada.", Toast.LENGTH_SHORT).show()
                                        recreate()
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(this, "Não foi possível cancelar: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
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
                                        Toast.makeText(this, "Inscrição confirmada!", Toast.LENGTH_SHORT).show()
                                        recreate()
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(this, "Não foi possível concluir a inscrição: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                            }
                        }
                    }
                    .addOnFailureListener {
                        body.addCampusText("Não foi possível consultar sua inscrição. Verifique a conexão e as regras do Firestore.")
                    }
            }
            .addOnFailureListener {
                body.removeAllViews()
                body.addCampusText("Não foi possível carregar o evento. Verifique a conexão e as regras do Firestore.")
            }
    }
}
