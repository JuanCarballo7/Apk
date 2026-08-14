package com.example.activiti_main

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.activiti_main.data.ProfileApi
import com.example.activiti_main.data.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class ProfileActivity : AppCompatActivity() {

    private lateinit var firstNameInput: EditText
    private lateinit var lastNameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var passwordConfirmInput: EditText
    private lateinit var saveButton: Button
    private lateinit var passwordButton: Button
    private lateinit var logoutButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var messageText: TextView
    private lateinit var emailHeader: TextView

    private var originalEmail = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        findViewById<View>(R.id.profileHeader).applyTopSystemBarPadding()

        firstNameInput = findViewById(R.id.editProfileFirstName)
        lastNameInput = findViewById(R.id.editProfileLastName)
        emailInput = findViewById(R.id.editProfileEmail)
        passwordInput = findViewById(R.id.editProfilePassword)
        passwordConfirmInput = findViewById(R.id.editProfilePasswordConfirm)
        saveButton = findViewById(R.id.btnSaveProfile)
        passwordButton = findViewById(R.id.btnChangePassword)
        logoutButton = findViewById(R.id.btnProfileLogout)
        progress = findViewById(R.id.progressProfile)
        messageText = findViewById(R.id.txtProfileMessage)
        emailHeader = findViewById(R.id.txtProfileEmailHeader)

        findViewById<View>(R.id.btnProfileBack).setOnClickListener { finish() }
        saveButton.setOnClickListener { guardarDatos() }
        passwordButton.setOnClickListener { cambiarContrasena() }
        logoutButton.setOnClickListener { cerrarSesion() }

        cargarPerfil()
    }

    private fun cargarPerfil() {
        lifecycleScope.launch {
            setLoading(true)
            try {
                SupabaseProvider.client.auth.awaitInitialization()
                val user = SupabaseProvider.client.auth.currentUserOrNull()
                    ?: return@launch volverAlLogin()

                originalEmail = user.email.orEmpty()
                emailInput.setText(originalEmail)
                emailHeader.text = originalEmail

                val metadata = user.userMetadata
                val metadataFirstName =
                    metadata?.get("first_name")?.jsonPrimitive?.contentOrNull.orEmpty()
                val metadataLastName =
                    metadata?.get("last_name")?.jsonPrimitive?.contentOrNull.orEmpty()

                try {
                    val profile = SupabaseProvider.client.from("profiles")
                        .select {
                            filter { eq("id", user.id) }
                        }
                        .decodeSingle<ProfileApi>()
                    firstNameInput.setText(profile.firstName.ifBlank { metadataFirstName })
                    lastNameInput.setText(profile.lastName.ifBlank { metadataLastName })
                } catch (_: Exception) {
                    firstNameInput.setText(metadataFirstName)
                    lastNameInput.setText(metadataLastName)
                }
            } catch (error: Exception) {
                mostrarMensaje(error.mensajeAmigable(), false)
            } finally {
                setLoading(false)
            }
        }
    }

    private fun guardarDatos() {
        val firstName = firstNameInput.text.toString().trim()
        val lastName = lastNameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()

        when {
            firstName.length < 2 -> return mostrarMensaje("Ingresá un nombre válido.", false)
            lastName.length < 2 -> return mostrarMensaje("Ingresá un apellido válido.", false)
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                return mostrarMensaje("Ingresá un email válido.", false)
        }

        lifecycleScope.launch {
            setLoading(true)
            try {
                val user = SupabaseProvider.client.auth.currentUserOrNull()
                    ?: return@launch volverAlLogin()

                SupabaseProvider.client.auth.updateUser {
                    if (email != originalEmail) this.email = email
                    data {
                        put("first_name", firstName)
                        put("last_name", lastName)
                    }
                }

                SupabaseProvider.client.from("profiles").update(
                    {
                        set("first_name", firstName)
                        set("last_name", lastName)
                    }
                ) {
                    filter { eq("id", user.id) }
                }

                if (email != originalEmail) {
                    mostrarMensaje(
                        "Datos guardados. Revisá tu correo para confirmar el nuevo email.",
                        true
                    )
                } else {
                    mostrarMensaje("Datos guardados correctamente.", true)
                }
                emailHeader.text = email
            } catch (error: Exception) {
                mostrarMensaje(error.mensajeAmigable(), false)
            } finally {
                setLoading(false)
            }
        }
    }

    private fun cambiarContrasena() {
        val password = passwordInput.text.toString()
        val confirmation = passwordConfirmInput.text.toString()

        when {
            password.length < 8 ->
                return mostrarMensaje("La contraseña debe tener al menos 8 caracteres.", false)
            password != confirmation ->
                return mostrarMensaje("Las contraseñas no coinciden.", false)
        }

        lifecycleScope.launch {
            setLoading(true)
            try {
                SupabaseProvider.client.auth.updateUser {
                    this.password = password
                }
                passwordInput.text?.clear()
                passwordConfirmInput.text?.clear()
                mostrarMensaje("Contraseña actualizada correctamente.", true)
            } catch (error: Exception) {
                mostrarMensaje(error.mensajeAmigable(), false)
            } finally {
                setLoading(false)
            }
        }
    }

    private fun cerrarSesion() {
        lifecycleScope.launch {
            setLoading(true)
            try {
                SupabaseProvider.client.auth.signOut()
            } finally {
                volverAlLogin()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        saveButton.isEnabled = !loading
        passwordButton.isEnabled = !loading
        logoutButton.isEnabled = !loading
        firstNameInput.isEnabled = !loading
        lastNameInput.isEnabled = !loading
        emailInput.isEnabled = !loading
        passwordInput.isEnabled = !loading
        passwordConfirmInput.isEnabled = !loading
    }

    private fun mostrarMensaje(message: String, success: Boolean) {
        messageText.text = message
        messageText.setTextColor(Color.parseColor(if (success) "#5BE37D" else "#FF6B6B"))
        messageText.visibility = View.VISIBLE
    }

    private fun volverAlLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }
}
