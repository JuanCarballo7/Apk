package com.example.activiti_main

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.random.Random

/**
 * Test de reacción tipo largada de F1: se encienden 5 semáforos de a uno por segundo y,
 * tras una espera al azar, se apagan todos. Se mide cuánto tarda el usuario en tocar.
 */
class ReaccionUi(context: Context, root: View) {

    private enum class Estado { LISTO, ENCENDIENDO, APAGADO, RESULTADO }

    private val prefs = context.getSharedPreferences("reaccion", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())

    private val txtEstado: TextView = root.findViewById(R.id.txtReaccionEstado)
    private val txtDetalle: TextView = root.findViewById(R.id.txtReaccionDetalle)
    private val txtMejor: TextView = root.findViewById(R.id.txtReaccionMejor)
    private val txtUltimo: TextView = root.findViewById(R.id.txtReaccionUltimo)
    private val txtIntentos: TextView = root.findViewById(R.id.txtReaccionIntentos)
    private val columnas: List<List<View>>

    private var estado = Estado.LISTO
    private var apagadoEn = 0L

    init {
        val contenedor: LinearLayout = root.findViewById(R.id.contenedorLuces)
        val tam = dp(context, 40)
        val margen = dp(context, 5)
        columnas = List(LUCES) {
            val columna = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(margen, 0, margen, 0)
            }
            val luces = List(2) {
                View(context).apply {
                    setBackgroundResource(R.drawable.bg_luz_off)
                    layoutParams = LinearLayout.LayoutParams(tam, tam).apply {
                        topMargin = margen
                        bottomMargin = margen
                    }
                }
            }
            luces.forEach(columna::addView)
            contenedor.addView(columna)
            luces
        }
        root.findViewById<View>(R.id.zonaToqueReaccion).setOnTouchListener(::onToque)
        mostrarEstadisticas(ultimo = null)
    }

    /** Corta una largada en curso (al cambiar de pestaña o salir de la pantalla). */
    fun cancelar() {
        handler.removeCallbacksAndMessages(null)
        if (estado == Estado.ENCENDIENDO || estado == Estado.APAGADO) {
            apagarTodas()
            estado = Estado.LISTO
            mostrarMensaje("TOCÁ PARA EMPEZAR", "No toques antes de que se apaguen las luces.")
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun onToque(v: View, event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return true
        when (estado) {
            Estado.LISTO, Estado.RESULTADO -> largar()
            Estado.ENCENDIENDO -> salidaEnFalso()
            // eventTime usa el mismo reloj que uptimeMillis: mide el toque real, no cuándo lo procesamos
            Estado.APAGADO -> registrar(event.eventTime - apagadoEn)
        }
        return true
    }

    private fun largar() {
        handler.removeCallbacksAndMessages(null)
        apagarTodas()
        estado = Estado.ENCENDIENDO
        mostrarMensaje("ATENTO…", "Esperá a que se apaguen todas las luces.")
        for (i in 0 until LUCES) {
            handler.postDelayed({ encender(i) }, (i + 1) * 1000L)
        }
        val espera = LUCES * 1000L + Random.nextLong(200, 3000)
        handler.postDelayed({
            apagarTodas()
            apagadoEn = SystemClock.uptimeMillis()
            estado = Estado.APAGADO
            mostrarMensaje("¡YA!", "¡Tocá!")
        }, espera)
    }

    private fun salidaEnFalso() {
        handler.removeCallbacksAndMessages(null)
        apagarTodas()
        estado = Estado.RESULTADO
        mostrarMensaje(
            "SALIDA EN FALSO",
            "Tocaste antes de que se apagaran las luces.\nTocá para intentar de nuevo.",
            colorEstado = COLOR_ROJO
        )
    }

    private fun registrar(ms: Long) {
        estado = Estado.RESULTADO
        val intentos = prefs.getInt(KEY_INTENTOS, 0) + 1
        val mejorAnterior = prefs.getLong(KEY_MEJOR, 0L)
        val esRecord = mejorAnterior == 0L || ms < mejorAnterior
        prefs.edit()
            .putInt(KEY_INTENTOS, intentos)
            .apply { if (esRecord) putLong(KEY_MEJOR, ms) }
            .apply()

        val detalle = buildString {
            append(calificar(ms))
            if (esRecord) append("\n¡Nuevo récord personal!")
            append("\nTocá para volver a largar.")
        }
        mostrarMensaje("$ms ms", detalle)
        mostrarEstadisticas(ultimo = ms)
    }

    private fun calificar(ms: Long) = when {
        ms < 200 -> "Nivel piloto de F1 🏆"
        ms < 250 -> "¡Excelente reacción!"
        ms < 300 -> "Muy bien"
        ms < 400 -> "Bien, seguí practicando"
        else -> "Un poco lento… mirá fijo las luces"
    }

    private fun mostrarEstadisticas(ultimo: Long?) {
        val mejor = prefs.getLong(KEY_MEJOR, 0L)
        txtMejor.text = "${if (mejor > 0) "$mejor ms" else "—"}\nMejor tiempo"
        if (ultimo != null) txtUltimo.text = "$ultimo ms\nÚltimo"
        txtIntentos.text = "${prefs.getInt(KEY_INTENTOS, 0)}\nIntentos"
    }

    private fun mostrarMensaje(estado: String, detalle: String, colorEstado: Int = Color.WHITE) {
        txtEstado.text = estado
        txtEstado.setTextColor(colorEstado)
        txtDetalle.text = detalle
    }

    private fun encender(columna: Int) =
        columnas[columna].forEach { it.setBackgroundResource(R.drawable.bg_luz_on) }

    private fun apagarTodas() =
        columnas.flatten().forEach { it.setBackgroundResource(R.drawable.bg_luz_off) }

    private fun dp(context: Context, valor: Int) =
        (valor * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val LUCES = 5
        const val KEY_MEJOR = "mejor_ms"
        const val KEY_INTENTOS = "intentos"
        val COLOR_ROJO = Color.parseColor("#FF3B30")
    }
}
