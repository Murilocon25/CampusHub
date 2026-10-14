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
                body.addCampusText("Comentários", 20f, bold = true)

                val commentInput = android.widget.EditText(this).apply {
                    hint = "Escreva seu comentário"
                    minLines = 2
                    maxLines = 5
                }
                body.addView(commentInput)

                val commentsRef = FirebaseFirestoreProvider.db
                    .collection("events")
                    .document(event.id)
                    .collection("comments")

                body.addCampusButton("Publicar comentário") {
                    val text = commentInput.text.toString().trim()
                    if (text.isBlank() || text.length > 1000) {
                        commentInput.error = "Digite de 1 a 1000 caracteres."
                        return@addCampusButton
                    }

                    FirebaseFirestoreProvider.db.collection("users")
                        .document(user.uid)
                        .get()
                        .addOnSuccessListener { profile ->
                            val authorName = profile.getString("name")
                                ?.takeIf { it.isNotBlank() }
                                ?: user.displayName
                                ?: user.email
                                ?: "Aluno"

                            commentsRef.add(
                                mapOf(
                                    "authorUid" to user.uid,
                                    "authorName" to authorName,
                                    "text" to text,
                                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                )
                            ).addOnSuccessListener {
                                recreate()
                            }.addOnFailureListener {
                                Toast.makeText(
                                    this,
                                    "Não foi possível publicar o comentário.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(
                                this,
                                "Não foi possível obter seu nome de perfil.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }

                val commentsList = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                }
                body.addView(commentsList)
                commentsList.addCampusText("Carregando comentários...")

                commentsRef.orderBy("createdAt").get()
                    .addOnSuccessListener { comments ->
                        commentsList.removeAllViews()

                        if (comments.isEmpty) {
                            commentsList.addCampusText("Ainda não há comentários.")
                            return@addOnSuccessListener
                        }

                        comments.documents.forEach { comment ->
                            val authorName = comment.getString("authorName").orEmpty()
                            val text = comment.getString("text").orEmpty()
                            val publishedAt = comment.getTimestamp("createdAt")
                                ?.toDate()
                                ?.let { date ->
                                    java.text.SimpleDateFormat(
                                        "dd/MM/yyyy HH:mm",
                                        java.util.Locale("pt", "BR")
                                    ).format(date)
                                } ?: "Publicando..."

                            commentsList.addCampusText("$authorName · $publishedAt\n$text")

                            if (comment.getString("authorUid") == user.uid) {
                                commentsList.addCampusButton("Editar comentário") {
                                    val editInput = android.widget.EditText(this).apply {
                                        setText(text)
                                    }

                                    androidx.appcompat.app.AlertDialog.Builder(this)
                                        .setTitle("Editar comentário")
                                        .setView(editInput)
                                        .setNegativeButton("Cancelar", null)
                                        .setPositiveButton("Salvar") { _, _ ->
                                            val updatedText = editInput.text.toString().trim()
                                            if (updatedText.isBlank() || updatedText.length > 1000) {
                                                Toast.makeText(
                                                    this,
                                                    "Digite de 1 a 1000 caracteres.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                comment.reference.update(
                                                    mapOf(
                                                        "text" to updatedText,
                                                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                                    )
                                                ).addOnSuccessListener {
                                                    recreate()
                                                }.addOnFailureListener {
                                                    Toast.makeText(
                                                        this,
                                                        "Não foi possível editar o comentário.",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }
                                        .show()
                                }

                                commentsList.addCampusButton("Excluir comentário") {
                                    androidx.appcompat.app.AlertDialog.Builder(this)
                                        .setMessage("Deseja excluir este comentário?")
                                        .setNegativeButton("Cancelar", null)
                                        .setPositiveButton("Excluir") { _, _ ->
                                            comment.reference.delete()
                                                .addOnSuccessListener { recreate() }
                                                .addOnFailureListener {
                                                    Toast.makeText(
                                                        this,
                                                        "Não foi possível excluir o comentário.",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                        }
                                        .show()
                                }
                            }
                        }
                    }
                    .addOnFailureListener {
                        commentsList.removeAllViews()
                        commentsList.addCampusText("Não foi possível carregar os comentários.")
                    }

                val favoriteRef = FirebaseFirestoreProvider.db
                    .favorites(user.uid)
                    .document(event.id)

                favoriteRef.get()
                    .addOnSuccessListener { favoriteSnapshot ->
                        val isFavorite = favoriteSnapshot.exists()

                        body.addCampusButton(
                            if (isFavorite) "Remover dos favoritos" else "Adicionar aos favoritos"
                        ) {
                            if (isFavorite) {
                                favoriteRef.delete()
                                    .addOnSuccessListener {
                                        Toast.makeText(
                                            this,
                                            "Evento removido dos favoritos.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        recreate()
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(
                                            this,
                                            "Não foi possível remover o favorito.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            } else {
                                favoriteRef.set(
                                    mapOf(
                                        "eventId" to event.id,
                                        "savedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                    )
                                )
                                    .addOnSuccessListener {
                                        Toast.makeText(
                                            this,
                                            "Evento adicionado aos favoritos.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        recreate()
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(
                                            this,
                                            "Não foi possível favoritar o evento.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            }
                        }
                    }
                    .addOnFailureListener {
                        body.addCampusText("Não foi possível verificar seus favoritos.")
                    }

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
