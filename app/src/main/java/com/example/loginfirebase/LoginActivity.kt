package com.example.loginfirebase

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        val email = findViewById<EditText>(R.id.editEmail)
        val senha = findViewById<EditText>(R.id.editSenha)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val txtCadastro = findViewById<TextView>(R.id.txtCadastro)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        btnLogin.setOnClickListener {
            val emailTexto = email.text.toString().trim()
            val senhaTexto = senha.text.toString().trim()

            if (emailTexto.isEmpty() || senhaTexto.isEmpty()) {
                Toast.makeText(this, getString(R.string.erro_campos_vazios), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            btnLogin.isEnabled = false

            auth.signInWithEmailAndPassword(emailTexto, senhaTexto)
                .addOnCompleteListener { task ->
                    progressBar.visibility = View.GONE
                    btnLogin.isEnabled = true

                    if (task.isSuccessful) {
                        abrirEventos()
                    } else {
                        Toast.makeText(
                            this,
                            "Erro no login: ${task.exception?.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }

        txtCadastro.setOnClickListener {
        val endereco = email.text.toString().trim()
            if (endereco.isBlank()){
                email.error ="informe seu e-mail primeiro"
            }else{
                auth.sendPasswordResetEmail(endereco).addOnCompleteListener { task ->
                    Toast.makeText(this, if(isSuccessful) "enviamos um link de reuperação para seu e-mail" else "não foi possivel enviar link de recuperação: ${task.exeption?.localizedMessage}", Toast.LENGHT_LONG).show()
                }
            }
        }


    }

    override fun onStart() {
        super.onStart()
        // Se o usuário já estiver logado (sessão anterior), pula direto para a tela de sucesso
        val usuarioAtual = auth.currentUser
        if (usuarioAtual != null) {
            abrirEventos()        }
    }

    private fun abrirEventos(email: String) {
        val intent = Intent(this, EventsActivity::class.java)
       startActivity(intent)
        finish()
    }
}
