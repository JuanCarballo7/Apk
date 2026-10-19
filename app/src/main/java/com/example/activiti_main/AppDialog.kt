package com.example.activiti_main

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.TextView

/** Modal con la estética de la app (tarjeta oscura, acento rojo) para reemplazar Toasts y AlertDialogs. */
object AppDialog {

    enum class Tipo(val icono: String) {
        INFO("i"),
        EXITO("✓"),
        ERROR("!"),
        CONFIRMAR("?"),
        PROXIMAMENTE("⏱")
    }

    fun mostrar(
        context: Context,
        tipo: Tipo,
        titulo: String,
        mensaje: String? = null,
        botonPrincipal: String = "ENTENDIDO",
        onPrincipal: (() -> Unit)? = null,
        botonSecundario: String? = null,
        onSecundario: (() -> Unit)? = null,
        enlace: String? = null,
        onEnlace: (() -> Unit)? = null,
        contenido: View? = null,
        cancelable: Boolean = true
    ): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val vista = LayoutInflater.from(context).inflate(R.layout.dialog_app, null)
        dialog.setContentView(vista)
        dialog.setCancelable(cancelable)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        vista.findViewById<TextView>(R.id.dialogIcono).text = tipo.icono
        vista.findViewById<TextView>(R.id.dialogTitulo).text = titulo
        vista.findViewById<TextView>(R.id.dialogMensaje).apply {
            text = mensaje
            visibility = if (mensaje.isNullOrBlank()) View.GONE else View.VISIBLE
        }

        if (contenido != null) {
            vista.findViewById<FrameLayout>(R.id.dialogContenido).apply {
                visibility = View.VISIBLE
                addView(contenido)
            }
        }

        vista.findViewById<TextView>(R.id.dialogPrincipal).apply {
            text = botonPrincipal
            setOnClickListener {
                dialog.dismiss()
                onPrincipal?.invoke()
            }
        }

        if (botonSecundario != null) {
            vista.findViewById<TextView>(R.id.dialogSecundario).apply {
                visibility = View.VISIBLE
                text = botonSecundario
                setOnClickListener {
                    dialog.dismiss()
                    onSecundario?.invoke()
                }
            }
        }

        if (enlace != null) {
            vista.findViewById<TextView>(R.id.dialogEnlace).apply {
                visibility = View.VISIBLE
                text = enlace
                setOnClickListener {
                    dialog.dismiss()
                    onEnlace?.invoke()
                }
            }
        }

        dialog.show()
        return dialog
    }

    /** Atajo para avisar un error ya traducido a lenguaje simple. */
    fun error(context: Context, mensaje: String, titulo: String = "Algo salió mal") =
        mostrar(context, Tipo.ERROR, titulo, mensaje)

    /** Atajo para funciones que todavía no están disponibles. */
    fun proximamente(context: Context, que: String) =
        mostrar(
            context,
            Tipo.PROXIMAMENTE,
            "Próximamente",
            "$que va a estar disponible muy pronto. ¡Seguinos para enterarte!"
        )
}
