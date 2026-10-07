package com.example.loginfirebase

import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


data class CampusEvent(val id: String, val title: String, val date: String, val place: String, val description: String)

object CampusData {
    val events = listOf(

        CampusEvent("welcome", "Recepção dos Calouros", "18 de outubro · 18h", "Auditório Central", "Uma noite para conhecer os cursos, fazer novas amizades e descobrir tudo o que acontece no campus."),
        CampusEvent("tech", "Campus Tech Summit", "22 de outubro · 14h", "Centro de Tecnologia", "Palestras e demonstrações sobre inovação, desenvolvimento de software e oportunidades na área de tecnologia."),
        CampusEvent("music", "Festival de Música Universitária", "25 de outubro · 16h", "Praça do Campus", "Apresentações de bandas e artistas da comunidade universitária. Traga seus amigos!"),
        CampusEvent("career", "Feira de Estágios e Carreiras", "30 de outubro · 10h", "Ginásio Universitário", "Converse com empresas, conheça oportunidades de estágio e prepare os próximos passos da sua carreira.")
    )
}

fun AppCompatActivity.buildCampusScreen(title: String): LinearLayout {
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(22, 18, 22, 18)
        setBackgroundColor(Color.rgb(246, 244, 252))
    }

    val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
    val heading = TextView(this).apply {
        text = title
        textSize = 26f
        setTextColor(Color.rgb(45, 34, 86))
        setTypeface(null, android.graphics.Typeface.BOLD)
    }
    top.addView(heading, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    root.addView(top)

    val scroll = ScrollView(this)
    val body = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 16, 0, 16)
    }
    scroll.addView(body)
    root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

    val nav = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        orientation = LinearLayout.HORIZONTAL
    }
    listOf("Eventos", "Meus Eventos", "Perfil").forEachIndexed { index, label ->
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 12f
        }
        button.setOnClickListener {
            val target = when (index) {
                1 -> MyEventsActivity::class.java
                2 -> ProfileActivity::class.java
                else -> EventsActivity::class.java
            }
            startActivity(android.content.Intent(this, target))
            finish()
        }
        nav.addView(button, LinearLayout.LayoutParams(0, -2, 1f))
    }
    root.addView(nav)
    setContentView(root)
    root.tag = body
    return root
}

fun LinearLayout.addCampusText(
    text: String,
    size: Float = 16f,
    color: Int = Color.DKGRAY,
    bold: Boolean = false
) {
    addView(TextView(context).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(4, 7, 4, 7)
    })
}

fun LinearLayout.addCampusButton(label: String, action: () -> Unit) {
    addView(
        Button(context).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        },
        LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = 6
            bottomMargin = 6
        }
    )
}

fun AppCompatActivity.requireUser(): Boolean = FirebaseAuth.getInstance().currentUser != null

fun FirebaseFirestore.registrations(uid: String) =
    collection("users").document(uid).collection("registrations")
