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
                        Toast.makeText(this, "Cadastro realizado com sucesso!", Toast.LENGTH_SHORT).show()
                        finish() // volta para a tela de login
                    } else {
                        Toast.makeText(
                            this,
                            "Erro no cadastro: ${task.exception?.localizedMessage}",
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
