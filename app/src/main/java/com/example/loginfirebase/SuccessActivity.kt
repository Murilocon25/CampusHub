package com.example.loginfirebase

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SuccessActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EMAIL = "EXTRA_EMAIL"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_success)

        val emailRecebido = intent.getStringExtra(EXTRA_EMAIL)
        val email = emailRecebido?.takeIf { it.isNotBlank() }
            ?: FirebaseAuth.getInstance().currentUser?.email
            ?: "usuário"

        findViewById<TextView>(R.id.txtEmailUsuario).text = email

        findViewById<Button>(R.id.btnSair).setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
