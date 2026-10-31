package com.example.loginfirebase.ui.auth

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.loginfirebase.R
import com.example.loginfirebase.data.repository.AuthRepository
import com.example.loginfirebase.data.repository.UserRepository
import com.example.loginfirebase.util.firebaseErrorMessage
import com.example.loginfirebase.ui.common.showCampusSnackbar
import com.google.android.material.snackbar.Snackbar

class RegisterActivity : AppCompatActivity() {

    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

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
                findViewById<View>(android.R.id.content).showCampusSnackbar(R.string.erro_campos_vazios)
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(emailTexto).matches()) {
                email.error = getString(R.string.email_invalid)
                return@setOnClickListener
            }

            if (senhaTexto.length < 6) {
                senha.error = getString(R.string.password_too_short)
                return@setOnClickListener
            }

            if (senhaTexto != confirmarTexto) {
                confirmarSenha.error = getString(R.string.erro_senhas_diferentes)
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            btnCadastrar.isEnabled = false

            authRepository.register(emailTexto, senhaTexto)
                .addOnCompleteListener { task ->
                    progressBar.visibility = View.GONE
                    btnCadastrar.isEnabled = true

                    if (task.isSuccessful) {
                        val usuario = task.result?.user
                        if (usuario == null) {
                            findViewById<View>(android.R.id.content).showCampusSnackbar(R.string.register_user_missing, Snackbar.LENGTH_LONG)
                            return@addOnCompleteListener
                        }

                        userRepository.createEmptyProfile(usuario.uid) { profileResult ->
                            if (profileResult.isSuccess) {
                                Toast.makeText(this, getString(R.string.register_success), Toast.LENGTH_SHORT).show()
                                finish()
                            } else {
                                findViewById<View>(android.R.id.content).showCampusSnackbar(
                                    R.string.profile_save_error, Snackbar.LENGTH_LONG
                                )
                            }
                        }
                    } else {
                        findViewById<View>(android.R.id.content).showCampusSnackbar(
                            firebaseErrorMessage(task.exception), Snackbar.LENGTH_LONG
                        )
                    }
                }
        }

        txtVoltarLogin.setOnClickListener {
            finish()
        }
    }
}
