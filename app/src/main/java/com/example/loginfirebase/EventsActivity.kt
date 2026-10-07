package com.example.loginfirebase

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class EventsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireUser()) { startActivity(Intent(this, LoginActivity::class.java)); finish(); return }
        val root = buildCampusScreen("Eventos do campus")
        val body = root.tag as LinearLayout
        body.addCampusText("Encontre uma atividade para chamar de sua.", 15f)
        body.addCampusButton("Sair da conta") {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish()
        }

        val eventList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(eventList)
        eventList.addCampusText("Carregando eventos...")

        FirebaseFirestoreProvider.db.collection("events").get()
            .addOnSuccessListener { snapshot ->
                eventList.removeAllViews()
                if (snapshot.isEmpty) {
                    eventList.addCampusText("Nenhum evento disponível no momento.")
                    return@addOnSuccessListener
                }

                snapshot.documents.forEach { document ->
                    val event = document.toCampusEvent()
                    eventList.addCampusText("${event.title}\n${event.date}\n${event.place}", 17f, bold = true)
                    eventList.addCampusButton("Ver detalhes") {
                        startActivity(
                            Intent(this, EventDetailActivity::class.java)
                                .putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.id)
                        )
                    }
                }
            }
            .addOnFailureListener {
                eventList.removeAllViews()
                eventList.addCampusText("Não foi possível carregar os eventos. Verifique a conexão e as regras do Firestore.")
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

        val root = buildCampusScreen("Meus Eventos")
        val body = root.tag as LinearLayout
        body.addCampusText("Eventos em que você confirmou presença", 15f)

        val eventList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        body.addView(eventList)
        eventList.addCampusText("Carregando suas inscrições...")

        FirebaseFirestoreProvider.db
            .registrations(user.uid)
            .get()
            .addOnSuccessListener { registrations ->
                eventList.removeAllViews()

                val eventIds = registrations.documents.map { it.id }

                if (eventIds.isEmpty()) {
                    eventList.addCampusText("Você ainda não está inscrito em nenhum evento.")
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
                                    "${event.title}\n${event.date} · ${event.place}",
                                    17f,
                                    bold = true
                                )
                                eventList.addCampusButton("Abrir evento") {
                                    startActivity(
                                        Intent(this, EventDetailActivity::class.java)
                                            .putExtra(
                                                EventDetailActivity.EXTRA_EVENT_ID,
                                                event.id
                                            )
                                    )
                                }
                            } else {
                                eventList.addCampusText(
                                    "Um evento da sua lista não está mais disponível."
                                )
                            }
                        }
                        .addOnFailureListener {
                            eventList.addCampusText(
                                "Não foi possível carregar um dos eventos."
                            )
                        }
                }
            }
            .addOnFailureListener {
                eventList.removeAllViews()
                eventList.addCampusText(
                    "Não foi possível carregar suas inscrições. Verifique a conexão e as regras do Firestore."
                )
            }
    }
}

object FirebaseFirestoreProvider { val db by lazy { com.google.firebase.firestore.FirebaseFirestore.getInstance() } }
