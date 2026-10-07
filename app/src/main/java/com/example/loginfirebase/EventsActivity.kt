package com.example.loginfirebase

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class EventsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireUser()) { startActivity(Intent(this, LoginActivity::class.java)); finish(); return }
        val root = buildCampusScreen("Eventos do campus")
        val body = root.tag as android.widget.LinearLayout
        body.addCampusText("Encontre uma atividade para chamar de sua.", 15f)
        CampusData.events.forEach { event ->
            body.addCampusText("${event.title}\n${event.date}\n${event.place}", 17f, bold = true)
            body.addCampusButton("Ver detalhes") { startActivity(Intent(this, EventDetailActivity::class.java).putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.id)) }
        }
        body.addCampusButton("Sair da conta") {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish()
        }
    }
}

class MyEventsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) { startActivity(Intent(this, LoginActivity::class.java)); finish(); return }
        val body = buildCampusScreen("Meus Eventos").tag as android.widget.LinearLayout
        body.addCampusText("Eventos em que você confirmou presença", 15f)
        FirebaseFirestoreProvider.db.registrations(user.uid).get().addOnSuccessListener { snapshot ->
            val ids = snapshot.documents.map { it.id }.toSet()
            val events = CampusData.events.filter { ids.contains(it.id) }
            if (events.isEmpty()) body.addCampusText("Você ainda não está inscrito em nenhum evento.")
            events.forEach { event ->
                body.addCampusText("${event.title}\n${event.date} · ${event.place}", 17f, bold = true)
                body.addCampusButton("Abrir evento") { startActivity(Intent(this, EventDetailActivity::class.java).putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.id)) }
            }
        }.addOnFailureListener { body.addCampusText("Não foi possível carregar suas inscrições. Verifique sua conexão.") }
    }
}

object FirebaseFirestoreProvider { val db by lazy { com.google.firebase.firestore.FirebaseFirestore.getInstance() } }
