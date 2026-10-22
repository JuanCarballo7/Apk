package com.example.activiti_main

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.activiti_main.data.AdminRepository
import com.example.activiti_main.data.SupabaseProvider
import com.example.activiti_main.data.formatearArs
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

/**
 * Menú que se desliza desde la izquierda al tocar ☰: secciones de la app, la cuenta
 * y, según el rol, el panel admin y la validación de QR.
 *
 * @param actual sección abierta ahora ("news", "entradas", "reaccion", "tablas" o "nosotros"), se resalta.
 * @param onSeccion abre una sección; cada pantalla decide cómo (cambiar de pestaña o abrir otra pantalla).
 */
object MenuLateral {

    private val secciones = listOf(
        "news" to "📰   News",
        "entradas" to "🎟   Entradas",
        "reaccion" to "🚦   Test de reacción",
        "tablas" to "📊   Tablas",
        "nosotros" to "ℹ️   Nosotros"
    )

    fun mostrar(activity: AppCompatActivity, actual: String, onSeccion: (String) -> Unit) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val vista = LayoutInflater.from(activity).inflate(R.layout.menu_lateral, null)
        dialog.setContentView(vista)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((290 * activity.resources.displayMetrics.density).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.START)
            setWindowAnimations(R.style.MenuLateralAnimacion)
            setDimAmount(0.6f)
        }
        vista.findViewById<View>(R.id.menuCabecera).applyTopSystemBarPadding()
        vista.findViewById<View>(R.id.btnMenuCerrarSesion).applyBottomSystemBarPadding()

        val opciones = vista.findViewById<LinearLayout>(R.id.menuOpciones)
        val ir = { accion: () -> Unit -> { dialog.dismiss(); accion() } }

        opciones.addView(titulo(activity, "SECCIONES"))
        secciones.forEach { (id, texto) ->
            opciones.addView(opcion(activity, texto, activa = id == actual, onClick = ir { onSeccion(id) }))
        }

        opciones.addView(titulo(activity, "MI CUENTA"))
        opciones.addView(opcion(activity, "👤   Mi perfil", onClick = ir {
            activity.startActivity(Intent(activity, ProfileActivity::class.java))
        }))
        opciones.addView(opcion(activity, "🎫   Mis entradas", onClick = ir {
            activity.startActivity(Intent(activity, MisEntradasActivity::class.java))
        }))

        // Se completan cuando llega el perfil: solo se ven según el rol.
        val tituloAdmin = titulo(activity, "ADMINISTRACIÓN").apply { visibility = View.GONE }
        val panelAdmin = opcion(activity, "🛠   Panel admin", onClick = ir {
            activity.startActivity(Intent(activity, AdminActivity::class.java))
        }).apply { visibility = View.GONE }
        val validarQr = opcion(activity, "📷   Validar QR en el ingreso", onClick = ir {
            activity.startActivity(Intent(activity, ValidarQrActivity::class.java))
        }).apply { visibility = View.GONE }
        opciones.addView(tituloAdmin)
        opciones.addView(panelAdmin)
        opciones.addView(validarQr)

        vista.findViewById<View>(R.id.btnMenuCerrarSesion).setOnClickListener(View.OnClickListener {
            dialog.dismiss()
            confirmarCierreDeSesion(activity)
        })

        // Primero se muestra: sin sesión, la carga termina en el acto y necesita el menú ya abierto.
        dialog.show()
        cargarCuenta(activity, vista, dialog) { esAdmin, esSuperadmin ->
            tituloAdmin.visibility = if (esAdmin) View.VISIBLE else View.GONE
            panelAdmin.visibility = if (esAdmin) View.VISIBLE else View.GONE
            validarQr.visibility = if (esSuperadmin) View.VISIBLE else View.GONE
        }
    }

    private fun cargarCuenta(
        activity: AppCompatActivity,
        vista: View,
        dialog: Dialog,
        onRol: (esAdmin: Boolean, esSuperadmin: Boolean) -> Unit
    ) {
        val txtNombre = vista.findViewById<TextView>(R.id.txtMenuNombre)
        val txtEmail = vista.findViewById<TextView>(R.id.txtMenuEmail)
        val txtSaldo = vista.findViewById<TextView>(R.id.txtMenuSaldo)
        if (!SupabaseProvider.isConfigured) {
            txtSaldo.visibility = View.GONE
            return
        }
        activity.lifecycleScope.launch {
            try {
                val user = SupabaseProvider.client.auth.currentUserOrNull()
                val perfil = if (user == null) null else AdminRepository.perfilActual()
                if (!dialog.isShowing) return@launch
                if (perfil == null) {
                    txtNombre.text = "Sin sesión"
                    txtSaldo.visibility = View.GONE
                    return@launch
                }
                txtEmail.text = user?.email.orEmpty()
                txtNombre.text = "${perfil.firstName} ${perfil.lastName}".trim().ifBlank { "Mi cuenta" }
                txtSaldo.text = "Saldo: ${formatearArs(perfil.saldo)}"
                onRol(perfil.esAdmin, perfil.esSuperadmin)
            } catch (error: Exception) {
                error.registrar("Cargar cuenta en el menú lateral")
                txtSaldo.visibility = View.GONE
            }
        }
    }

    private fun confirmarCierreDeSesion(activity: AppCompatActivity) {
        AppDialog.mostrar(
            activity,
            AppDialog.Tipo.CONFIRMAR,
            "¿Cerrar sesión?",
            "Vas a tener que volver a ingresar con tu email y contraseña.",
            botonPrincipal = "CERRAR SESIÓN",
            onPrincipal = {
                activity.lifecycleScope.launch {
                    try {
                        SupabaseProvider.client.auth.signOut()
                    } catch (error: Exception) {
                        error.registrar("Cerrar sesión desde el menú (se limpia la sesión local igual)")
                    } finally {
                        activity.startActivity(
                            Intent(activity, LoginActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                        )
                    }
                }
            },
            botonSecundario = "CANCELAR"
        )
    }

    private fun titulo(activity: AppCompatActivity, texto: String) = TextView(activity).apply {
        text = texto
        setTextColor(Color.parseColor("#6E6E78"))
        textSize = 11f
        setTypeface(typeface, Typeface.BOLD)
        letterSpacing = 0.08f
        val d = activity.resources.displayMetrics.density
        setPadding((12 * d).toInt(), (16 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
    }

    private fun opcion(
        activity: AppCompatActivity,
        texto: String,
        activa: Boolean = false,
        onClick: () -> Unit
    ) = TextView(activity).apply {
        val d = activity.resources.displayMetrics.density
        text = texto
        setTextColor(if (activa) Color.parseColor("#FF4D45") else Color.WHITE)
        textSize = 15f
        if (activa) setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
        minHeight = (50 * d).toInt()
        setPadding((12 * d).toInt(), 0, (12 * d).toInt(), 0)
        if (activa) {
            setBackgroundResource(R.drawable.bg_menu_item_activo)
        } else {
            val fondo = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, fondo, true)
            setBackgroundResource(fondo.resourceId)
        }
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }
}
