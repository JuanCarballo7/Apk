package com.example.activiti_main

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import com.example.activiti_main.data.Entrada
import com.example.activiti_main.data.EntradasRepository
import com.example.activiti_main.data.formatearArs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pago de entradas con Mercado Pago (Checkout Pro).
 *
 * Guarda la orden pendiente en el teléfono para retomarla cuando el usuario vuelve a la app,
 * ya sea por el deep link de Mercado Pago (PagoMpActivity) o con el botón atrás.
 * Quien confirma el pago es la base de datos (webhook de Mercado Pago), nunca la app.
 */
object PagoMp {

    private const val PREFS = "pago_mp"
    private const val KEY_ORDEN = "orden_id"
    private const val KEY_CODIGO = "codigo"
    private const val KEY_TOTAL = "total"
    private const val KEY_LINK = "init_point"

    private const val INTENTOS = 4
    private const val ESPERA_MS = 2500L

    /** Sin volver de Mercado Pago, el aviso de pago pendiente se repite como mucho cada 2 minutos. */
    private const val REVISAR_CADA_MS = 2 * 60 * 1000L

    private var verificando = false
    private var ultimaRevision = 0L
    private var revisarAlVolver = false

    /** Reserva el carrito, lo vacía y abre el checkout de Mercado Pago. */
    fun iniciar(
        activity: AppCompatActivity,
        lineas: List<Pair<Entrada, Int>>,
        onReservada: () -> Unit,
        onFin: () -> Unit
    ) {
        activity.lifecycleScope.launch {
            try {
                val pago = EntradasRepository.crearPagoMp(lineas)
                prefs(activity).edit()
                    .putLong(KEY_ORDEN, pago.ordenId)
                    .putString(KEY_CODIGO, pago.codigo)
                    .putString(KEY_TOTAL, pago.total.toString())
                    .putString(KEY_LINK, pago.initPoint)
                    .apply()
                onReservada()
                revisarAlVolver = true
                abrirCheckout(activity, pago.initPoint)
            } catch (error: Exception) {
                AppDialog.error(
                    activity,
                    error.paraUsuario("Crear pago de Mercado Pago"),
                    "No se pudo iniciar el pago"
                )
            } finally {
                onFin()
            }
        }
    }

    /** Recuerda la orden que vino por el deep link si el teléfono no la tenía guardada. */
    fun recordar(context: Context, ordenId: Long) {
        val p = prefs(context)
        if (p.getLong(KEY_ORDEN, -1L) != ordenId) {
            p.edit().clear().putLong(KEY_ORDEN, ordenId).apply()
        }
        revisarAlVolver = true
    }

    /** Si hay un pago de Mercado Pago sin resolver, consulta cómo terminó y se lo muestra al usuario. */
    fun revisarPendiente(activity: AppCompatActivity) {
        val p = prefs(activity)
        val ordenId = p.getLong(KEY_ORDEN, -1L)
        if (ordenId <= 0 || verificando) return
        val ahora = System.currentTimeMillis()
        if (!revisarAlVolver && ahora - ultimaRevision < REVISAR_CADA_MS) return
        revisarAlVolver = false
        ultimaRevision = ahora
        verificando = true

        val espera = AppDialog.mostrar(
            activity,
            AppDialog.Tipo.INFO,
            "Verificando tu pago…",
            "Estamos consultando a Mercado Pago cómo terminó tu compra.",
            botonPrincipal = "ESPERAR",
            cancelable = false
        )

        activity.lifecycleScope.launch {
            var estado = "pendiente"
            try {
                // El webhook puede tardar unos segundos en llegar: se reintenta antes de rendirse.
                for (intento in 1..INTENTOS) {
                    estado = EntradasRepository.verificarPagoMp(ordenId)
                    if (estado != "pendiente") break
                    if (intento < INTENTOS) delay(ESPERA_MS)
                }
            } catch (error: Exception) {
                error.registrar("Verificar pago de Mercado Pago de la orden $ordenId")
            } finally {
                espera.cerrar()
                verificando = false
            }
            mostrarResultado(activity, ordenId, estado)
        }
    }

    private fun mostrarResultado(activity: AppCompatActivity, ordenId: Long, estado: String) {
        val p = prefs(activity)
        val codigo = p.getString(KEY_CODIGO, null)
        val total = p.getString(KEY_TOTAL, null)?.toDoubleOrNull()

        when (estado) {
            "confirmada" -> {
                p.edit().clear().apply()
                AppDialog.mostrar(
                    activity,
                    AppDialog.Tipo.EXITO,
                    "¡Pago aprobado!",
                    listOfNotNull(
                        "Tus entradas para el GP de Argentina están confirmadas.",
                        codigo?.let { "Código de reserva: $it" },
                        total?.let { "Total pagado: ${formatearArs(it)}" }
                    ).joinToString("\n"),
                    botonPrincipal = "VER COMPROBANTE",
                    onPrincipal = { activity.startActivity(ComprobanteActivity.intent(activity, ordenId)) },
                    botonSecundario = "LISTO"
                )
            }
            "cancelada" -> {
                p.edit().clear().apply()
                AppDialog.mostrar(
                    activity,
                    AppDialog.Tipo.ERROR,
                    "El pago no se completó",
                    "Mercado Pago no aprobó el pago o venció el tiempo para pagar. " +
                        "No se te cobró nada y las entradas volvieron a estar disponibles."
                )
            }
            else -> {
                val link = p.getString(KEY_LINK, null)
                AppDialog.mostrar(
                    activity,
                    AppDialog.Tipo.INFO,
                    "Tu pago todavía no se acreditó",
                    "Si ya pagaste, puede tardar unos minutos: lo vas a ver en Mis entradas. " +
                        "Tus entradas quedan reservadas 30 minutos.",
                    botonPrincipal = if (link != null) "IR A PAGAR" else "ENTENDIDO",
                    onPrincipal = {
                        if (link != null) {
                            revisarAlVolver = true
                            abrirCheckout(activity, link)
                        }
                    },
                    botonSecundario = "CANCELAR COMPRA",
                    onSecundario = { confirmarCancelacion(activity, ordenId) },
                    enlace = "Lo reviso después"
                )
            }
        }
    }

    private fun confirmarCancelacion(activity: AppCompatActivity, ordenId: Long) {
        AppDialog.mostrar(
            activity,
            AppDialog.Tipo.CONFIRMAR,
            "¿Cancelar la compra?",
            "Se libera la reserva y las entradas vuelven a estar a la venta.",
            botonPrincipal = "CANCELAR COMPRA",
            onPrincipal = {
                activity.lifecycleScope.launch {
                    try {
                        EntradasRepository.cancelarPagoMp(ordenId)
                        prefs(activity).edit().clear().apply()
                        AppDialog.mostrar(activity, AppDialog.Tipo.EXITO, "Compra cancelada", "No se te cobró nada.")
                    } catch (error: Exception) {
                        val pagoEnCurso = error.message.orEmpty().contains("NO_AUTORIZADO")
                        AppDialog.error(
                            activity,
                            if (pagoEnCurso) "Mercado Pago ya tiene un pago en proceso para esta compra. " +
                                "Esperá a que se acredite o se rechace."
                            else error.paraUsuario("Cancelar pago de Mercado Pago de la orden $ordenId"),
                            "No se pudo cancelar"
                        )
                    }
                }
            },
            botonSecundario = "VOLVER"
        )
    }

    private fun abrirCheckout(activity: AppCompatActivity, link: String) {
        val uri = Uri.parse(link)
        try {
            CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(
                    CustomTabColorSchemeParams.Builder().setToolbarColor(Color.parseColor("#0B0B10")).build()
                )
                .setShowTitle(true)
                .build()
                .launchUrl(activity, uri)
        } catch (error: ActivityNotFoundException) {
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (sinNavegador: ActivityNotFoundException) {
                AppDialog.error(activity, "Instalá un navegador para pagar con Mercado Pago.", "No se pudo abrir el pago")
            }
        }
    }

    private fun Dialog.cerrar() {
        if (isShowing) dismiss()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
