package com.example.activiti_main

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.activiti_main.data.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var errorText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        findViewById<View>(R.id.authHeader).applyTopSystemBarPadding()

        emailInput = findViewById(R.id.editEmail)
        passwordInput = findViewById(R.id.editPassword)
        loginButton = findViewById(R.id.btnLogin)
        progress = findViewById(R.id.progressLogin)
        errorText = findViewById(R.id.txtAuthError)

        findViewById<TextView>(R.id.btnOpenRegister).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        findViewById<TextView>(R.id.btnForgotPassword).setOnClickListener {
            enviarRecuperacion()
        }
        loginButton.setOnClickListener { iniciarSesion() }

        comprobarSesionGuardada()
    }

    private fun comprobarSesionGuardada() {
        if (!SupabaseProvider.isConfigured) {
            mostrarError(
                "Falta configurar SUPABASE_URL y SUPABASE_PUBLISHABLE_KEY " +
                    "en local.properties."
            )
            return
        }

        lifecycleScope.launch {
            setLoading(true)
            try {
                SupabaseProvider.client.auth.awaitInitialization()
                if (SupabaseProvider.client.auth.currentSessionOrNull() != null) {
                    abrirInicio()
                }
            } catch (error: Exception) {
                mostrarError(error.mensajeAmigable())
            } finally {
                setLoading(false)
            }
        }
    }

    private fun iniciarSesion() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()

        when {
            !SupabaseProvider.isConfigured -> {
                mostrarError("Configurá las credenciales de Supabase en local.properties.")
                return
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                mostrarError("Ingresá un email válido.")
                return
            }
            password.isBlank() -> {
                mostrarError("Ingresá tu contraseña.")
                return
            }
        }

        lifecycleScope.launch {
            setLoading(true)
            try {
                SupabaseProvider.client.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                abrirInicio()
            } catch (error: Exception) {
                mostrarError(error.mensajeAmigable())
            } finally {
                setLoading(false)
            }
        }
    }

    private fun enviarRecuperacion() {
        val email = emailInput.text.toString().trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            mostrarError("Escribí tu email para recuperar la contraseña.")
            return
        }
        if (!SupabaseProvider.isConfigured) {
            mostrarError("Configurá Supabase antes de continuar.")
            return
        }

        lifecycleScope.launch {
            setLoading(true)
            try {
                SupabaseProvider.client.auth.resetPasswordForEmail(email)
                Toast.makeText(
                    this@LoginActivity,
                    "Revisá tu email para recuperar la contraseña.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (error: Exception) {
                mostrarError(error.mensajeAmigable())
            } finally {
                setLoading(false)
            }
        }
    }

    private fun abrirInicio() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        loginButton.isEnabled = !loading
        emailInput.isEnabled = !loading
        passwordInput.isEnabled = !loading
        if (loading) errorText.visibility = View.GONE
    }

    private fun mostrarError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }
}

internal fun Throwable.mensajeAmigable(): String {
    val raw = message.orEmpty()
    return when {
        raw.contains("Invalid login credentials", ignoreCase = true) ->
            "Email o contraseña incorrectos."
        raw.contains("Email not confirmed", ignoreCase = true) ->
            "Primero confirmá tu email desde el mensaje de Supabase."
        raw.contains("User already registered", ignoreCase = true) ->
            "Ya existe una cuenta con ese email."
        raw.contains("Password should be", ignoreCase = true) ->
            "La contraseña debe tener al menos 8 caracteres."
        raw.contains("network", ignoreCase = true) ->
            "No se pudo conectar. Revisá tu conexión a internet."
        raw.isNotBlank() -> raw
        else -> "Ocurrió un error. Intentá nuevamente."
    }
}
