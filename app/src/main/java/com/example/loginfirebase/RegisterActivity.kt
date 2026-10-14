package com.example.loginfirebase

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        val email = findViewById<EditText>(R.id.editEmailCadastro)
        val senha = findViewById<EditText>(R.id.editSenhaCadastro)
        val confirmarSenha = findViewById<EditText>(R.id.editConfirmarSenha)
        val btnCadastrar = findViewById<Button>(R.id.btnCadastrar)
        val txtVoltarLogin = findViewById<TextView>(R.id.txtVoltarLogin)
        val progressBar = findViewById<ProgressBar>(R.id.progressBarCadastro)

        btnCadastrar.setOnClickListener {
            val emailTexto = email.text.toString().trim()
            val senhaTexto = senha.text.toString().trim()
            val confirmarTexto = confirmarSenha.text.toString().trim()

            if (emailTexto.isEmpty() || senhaTexto.isEmpty() || confirmarTexto.isEmpty()) {
                Toast.makeText(this, getString(R.string.erro_campos_vazios), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (senhaTexto != confirmarTexto) {
                Toast.makeText(this, getString(R.string.erro_senhas_diferentes), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            btnCadastrar.isEnabled = false

            auth.createUserWithEmailAndPassword(emailTexto, senhaTexto)
                .addOnCompleteListener { task ->
                    progressBar.visibility = View.GONE
                    btnCadastrar.isEnabled = true

                    if (task.isSuccessful) {
                        val usuario = task.result?.user
                        if (usuario == null) return@addOnCompleteListener

                        val dadosUsuario = hashMapOf(
                            "email" to usuario.email,
                            "createdAt" to System.currentTimeMillis()
                        )

                        FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(usuario.uid)
                            .set(dadosUsuario)
                            .addOnSuccessListener {
                                Toast.makeText(
                                    this,
                                    getString(R.string.register_success),
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish()
                            }
                            .addOnFailureListener {
                                Toast.makeText(
                                    this,
                                    getString(R.string.profile_save_error),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    } else {
                        Toast.makeText(
                            this,
                            getString(R.string.register_error, task.exception?.localizedMessage.orEmpty()),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }

        txtVoltarLogin.setOnClickListener {
            finish()
        }
    }
}
