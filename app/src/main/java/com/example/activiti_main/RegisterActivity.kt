package com.example.activiti_main

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.activiti_main.data.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class RegisterActivity : AppCompatActivity() {

    private lateinit var firstNameInput: EditText
    private lateinit var lastNameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var termsCheck: CheckBox
    private lateinit var registerButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var errorText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        findViewById<View>(R.id.authHeader).applyTopSystemBarPadding()

        firstNameInput = findViewById(R.id.editFirstName)
        lastNameInput = findViewById(R.id.editLastName)
        emailInput = findViewById(R.id.editRegisterEmail)
        passwordInput = findViewById(R.id.editRegisterPassword)
        confirmPasswordInput = findViewById(R.id.editConfirmPassword)
        termsCheck = findViewById(R.id.checkTerms)
        registerButton = findViewById(R.id.btnRegister)
        progress = findViewById(R.id.progressRegister)
        errorText = findViewById(R.id.txtRegisterError)

        registerButton.setOnClickListener { crearCuenta() }
        findViewById<TextView>(R.id.btnOpenLogin).setOnClickListener { finish() }
    }

    private fun crearCuenta() {
        val firstName = firstNameInput.text.toString().trim()
        val lastName = lastNameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()
        val confirmPassword = confirmPasswordInput.text.toString()

        when {
            !SupabaseProvider.isConfigured -> {
                mostrarError("Configurá las credenciales de Supabase en local.properties.")
                return
            }
            firstName.length < 2 -> {
                mostrarError("Ingresá tu nombre.")
                return
            }
            lastName.length < 2 -> {
                mostrarError("Ingresá tu apellido.")
                return
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                mostrarError("Ingresá un email válido.")
                return
            }
            password.length < 8 -> {
                mostrarError("La contraseña debe tener al menos 8 caracteres.")
                return
            }
            password != confirmPassword -> {
                mostrarError("Las contraseñas no coinciden.")
                return
            }
            !termsCheck.isChecked -> {
                mostrarError("Tenés que aceptar los Términos y la Política de Privacidad.")
                return
            }
        }

        lifecycleScope.launch {
            setLoading(true)
            try {
                SupabaseProvider.client.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                    data = buildJsonObject {
                        put("first_name", firstName)
                        put("last_name", lastName)
                    }
                }

                if (SupabaseProvider.client.auth.currentSessionOrNull() != null) {
                    abrirInicio()
                } else {
                    mostrarConfirmacionEmail(email)
                }
            } catch (error: Exception) {
                mostrarError(error.mensajeAmigable())
            } finally {
                setLoading(false)
            }
        }
    }

    private fun mostrarConfirmacionEmail(email: String) {
        AlertDialog.Builder(this)
            .setTitle("Confirmá tu email")
            .setMessage(
                "Creamos tu cuenta. Abrí el mensaje enviado a $email y confirmala " +
                    "antes de iniciar sesión."
            )
            .setPositiveButton("IR AL LOGIN") { _, _ -> finish() }
            .setCancelable(false)
            .show()
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
        registerButton.isEnabled = !loading
        firstNameInput.isEnabled = !loading
        lastNameInput.isEnabled = !loading
        emailInput.isEnabled = !loading
        passwordInput.isEnabled = !loading
        confirmPasswordInput.isEnabled = !loading
        termsCheck.isEnabled = !loading
        if (loading) errorText.visibility = View.GONE
    }

    private fun mostrarError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }
}
