package com.example.loginfirebase.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.loginfirebase.MainActivity
import com.example.loginfirebase.R
import com.example.loginfirebase.data.repository.AuthRepository
import com.example.loginfirebase.util.firebaseErrorMessage
import com.example.loginfirebase.ui.common.showCampusSnackbar
import com.google.android.material.snackbar.Snackbar

class LoginActivity : AppCompatActivity() {

    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val email = findViewById<EditText>(R.id.editEmail)
        val senha = findViewById<EditText>(R.id.editSenha)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val txtCadastro = findViewById<TextView>(R.id.txtCadastro)
        val txtRecuperarSenha = findViewById<TextView>(R.id.txtRecuperarSenha)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        btnLogin.setOnClickListener {
            val credentials = when (val validation = validateLoginInput(
                emailInput = email.text.toString(),
                passwordInput = senha.text.toString(),
                isValidEmail = { Patterns.EMAIL_ADDRESS.matcher(it).matches() }
            )) {
                LoginInputValidation.MissingFields -> {
                    findViewById<View>(android.R.id.content).showCampusSnackbar(R.string.erro_campos_vazios)
                    return@setOnClickListener
                }
                is LoginInputValidation.InvalidEmail -> {
                    email.error = getString(R.string.email_invalid)
                    return@setOnClickListener
                }
                is LoginInputValidation.Valid -> validation
            }

            progressBar.visibility = View.VISIBLE
            btnLogin.isEnabled = false

            authRepository.signIn(credentials.email, credentials.password)
                .addOnCompleteListener { task ->
                    progressBar.visibility = View.GONE
                    btnLogin.isEnabled = true

                    if (task.isSuccessful) {
                        abrirEventos()
                    } else {
                        findViewById<View>(android.R.id.content).showCampusSnackbar(
                            firebaseErrorMessage(task.exception), Snackbar.LENGTH_LONG
                        )
                    }
                }
        }

        txtCadastro.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        txtRecuperarSenha.setOnClickListener {
            val validation = validatePasswordResetEmail(email.text.toString()) {
                Patterns.EMAIL_ADDRESS.matcher(it).matches()
            }
            val endereco = when (validation) {
                PasswordResetEmailValidation.MissingEmail -> {
                    email.error = getString(R.string.reset_email_prompt)
                    return@setOnClickListener
                }
                is PasswordResetEmailValidation.InvalidEmail -> {
                    email.error = getString(R.string.email_invalid)
                    return@setOnClickListener
                }
                is PasswordResetEmailValidation.Valid -> validation.email
            }

            authRepository.sendPasswordReset(endereco).addOnCompleteListener { task ->
                val mensagem = if (task.isSuccessful) {
                    getString(R.string.reset_email_sent)
                } else {
                    firebaseErrorMessage(task.exception)
                }
                findViewById<View>(android.R.id.content).showCampusSnackbar(mensagem, Snackbar.LENGTH_LONG)
            }
        }


    }

    override fun onStart() {
        super.onStart()
        // Se o usuário já estiver logado, abre a lista de eventos.
        val usuarioAtual = authRepository.currentUser
        if (usuarioAtual != null) {
            abrirEventos()
        }
    }

    private fun abrirEventos() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
