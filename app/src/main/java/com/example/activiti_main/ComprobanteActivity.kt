package com.example.activiti_main

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.activiti_main.data.AdminRepository
import com.example.activiti_main.data.EntradasCatalogo
import com.example.activiti_main.data.EntradasRepository
import com.example.activiti_main.data.OrdenApi
import com.example.activiti_main.data.SupabaseProvider
import com.example.activiti_main.data.formatearArs
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Comprobante de una orden con su QR de ingreso; se puede compartir como imagen. */
class ComprobanteActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_ORDEN_ID = "orden_id"

        fun intent(context: Context, ordenId: Long) =
            Intent(context, ComprobanteActivity::class.java).putExtra(EXTRA_ORDEN_ID, ordenId)
    }

    private var orden: OrdenApi? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comprobante)

        findViewById<View>(R.id.headerComprobante).applyTopSystemBarPadding()
        findViewById<View>(R.id.scrollComprobante).applyBottomSystemBarPadding()
        findViewById<View>(R.id.btnComprobanteBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnCompartirComprobante).setOnClickListener { compartir() }

        cargar(intent.getLongExtra(EXTRA_ORDEN_ID, -1))
    }

    private fun cargar(ordenId: Long) {
        lifecycleScope.launch {
            try {
                val o = EntradasRepository.getOrden(ordenId)
                val user = SupabaseProvider.client.auth.currentUserOrNull()
                val perfil = runCatching { AdminRepository.perfilActual() }.getOrNull()
                orden = o
                mostrar(
                    o,
                    comprador = perfil?.let { "${it.firstName} ${it.lastName}".trim() }.orEmpty(),
                    email = user?.email.orEmpty()
                )
            } catch (error: Exception) {
                AppDialog.mostrar(
                    this@ComprobanteActivity,
                    AppDialog.Tipo.ERROR,
                    "No pudimos abrir el comprobante",
                    error.paraUsuario("Cargar comprobante de la orden $ordenId"),
                    onPrincipal = ::finish,
                    cancelable = false
                )
            } finally {
                findViewById<View>(R.id.progressComprobante).visibility = View.GONE
            }
        }
    }

    private fun mostrar(o: OrdenApi, comprador: String, email: String) {
        // El QR solo existe para compras pagas: una orden pendiente o cancelada no entra al autódromo.
        val pagada = o.estado == "confirmada"
        val imgQr = findViewById<ImageView>(R.id.imgComprobanteQr)
        if (pagada) {
            val tamanoQr = (210 * resources.displayMetrics.density).toInt()
            imgQr.setImageBitmap(QrHelper.generar(QrHelper.contenidoOrden(o.codigo, o.id), tamanoQr))
            imgQr.visibility = View.VISIBLE
        } else {
            imgQr.visibility = View.GONE
        }
        findViewById<View>(R.id.btnCompartirComprobante).visibility = if (pagada) View.VISIBLE else View.GONE

        findViewById<TextView>(R.id.txtComprobanteCodigo).text =
            if (pagada) o.codigo else "${o.codigo}\n${if (o.estado == "pendiente") "Esperando el pago" else "Sin QR: compra cancelada"}"
        findViewById<TextView>(R.id.txtComprobanteEstado).text = estadoLegible(o.estado)
        findViewById<TextView>(R.id.txtComprobanteComprador).text = comprador.ifBlank { "-" }
        findViewById<TextView>(R.id.txtComprobanteEmail).text = email.ifBlank { "-" }
        findViewById<TextView>(R.id.txtComprobanteFecha).text = formatearFecha(o.createdAt)
        findViewById<TextView>(R.id.txtComprobanteOrden).text = "#${o.id}"
        findViewById<TextView>(R.id.txtComprobanteTotal).text = formatearArs(o.total)

        val contenedor = findViewById<LinearLayout>(R.id.contenedorComprobanteItems)
        contenedor.removeAllViews()
        val inflater = LayoutInflater.from(this)
        o.items.forEach { item ->
            val fila = inflater.inflate(R.layout.item_carrito, contenedor, false)
            val entrada = EntradasCatalogo.entradasLocales.firstOrNull { it.id == item.ticketId }
            fila.findViewById<TextView>(R.id.txtCarritoLineaNombre).text = "${item.cantidad} × ${item.nombre}"
            fila.findViewById<TextView>(R.id.txtCarritoLineaDetalle).text =
                if (entrada != null) "SECTOR ${EntradasCatalogo.sectorDe(entrada)} · ${entrada.dias}"
                else "${formatearArs(item.precioUnitario)} c/u"
            fila.findViewById<TextView>(R.id.txtCarritoLineaPrecio).text =
                formatearArs(item.precioUnitario * item.cantidad)
            contenedor.addView(fila)
        }

        findViewById<View>(R.id.scrollComprobante).visibility = View.VISIBLE
    }

    /** Exporta la tarjeta del comprobante como PNG y abre el menú de compartir. */
    private fun compartir() {
        val o = orden ?: return
        val tarjeta = findViewById<View>(R.id.cardComprobante)
        lifecycleScope.launch {
            try {
                val imagen = Bitmap.createBitmap(tarjeta.width, tarjeta.height, Bitmap.Config.ARGB_8888)
                Canvas(imagen).apply {
                    drawColor(Color.parseColor("#0B0B10"))
                    tarjeta.draw(this)
                }
                val archivo = withContext(Dispatchers.IO) {
                    val carpeta = File(cacheDir, "comprobantes").apply { mkdirs() }
                    File(carpeta, "comprobante_${o.codigo}.png").also { f ->
                        f.outputStream().use { imagen.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                }
                val uri = FileProvider.getUriForFile(
                    this@ComprobanteActivity,
                    "$packageName.fileprovider",
                    archivo
                )
                val enviar = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    // ClipData hace que el permiso de lectura llegue también al selector (vista previa).
                    clipData = ClipData.newRawUri(o.codigo, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Comprobante ${o.codigo} · GP de Argentina 2026")
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Mi comprobante de entradas para el GP de Argentina 2026. Código: ${o.codigo}"
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(enviar, "Compartir comprobante"))
            } catch (error: Exception) {
                AppDialog.error(this@ComprobanteActivity, error.paraUsuario("Compartir comprobante ${o.codigo}"))
            }
        }
    }
}

/** Estado de una orden en palabras para el usuario. */
internal fun estadoLegible(estado: String): String = when (estado) {
    "confirmada" -> "CONFIRMADA"
    "pendiente" -> "PENDIENTE DE PAGO"
    "cancelada" -> "CANCELADA"
    else -> estado.uppercase()
}

/** "2026-10-19T06:13:55.123+00:00" (UTC de Supabase) → "19/10/2026 03:13" en la hora del teléfono. */
internal fun formatearFecha(iso: String): String {
    if (iso.length < 19) return "-"
    return runCatching {
        val entrada = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val fecha = entrada.parse(iso.substring(0, 19)) ?: return "-"
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-AR")).format(fecha)
    }.getOrDefault("-")
}
