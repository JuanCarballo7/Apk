package com.example.activiti_main

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.activiti_main.data.AdminRepository
import com.example.activiti_main.data.ValidacionApi
import com.example.activiti_main.data.formatearArs
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

/** Control de ingreso (solo superadmin): escanea el QR del comprobante y lo valida contra Supabase. */
class ValidarQrActivity : AppCompatActivity() {

    private enum class Estilo(val color: String, val icono: String) {
        OK("#2ECC71", "✓"),
        AVISO("#F5A623", "!"),
        ERROR("#E10600", "✕")
    }

    private lateinit var progress: ProgressBar
    private lateinit var editCodigo: EditText
    private lateinit var card: View

    private var actual: ValidacionApi? = null

    private val escaner = registerForActivityResult(ScanContract()) { resultado ->
        val texto = resultado.contents ?: return@registerForActivityResult
        val qr = leerQrOrden(texto)
        if (qr == null) {
            mostrarError("QR no reconocido", "Este código no es un comprobante de entradas del GP de Argentina.")
        } else {
            validar(qr.codigo, qr.ordenId, registrar = false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_validar_qr)

        findViewById<View>(R.id.headerValidar).applyTopSystemBarPadding()
        findViewById<View>(R.id.scrollValidar).applyBottomSystemBarPadding()
        findViewById<View>(R.id.btnValidarBack).setOnClickListener { finish() }

        progress = findViewById(R.id.progressValidar)
        editCodigo = findViewById(R.id.editCodigoManual)
        card = findViewById(R.id.cardResultado)

        findViewById<View>(R.id.btnEscanearQr).setOnClickListener { abrirEscaner() }
        findViewById<View>(R.id.btnValidarManual).setOnClickListener { validarManual() }
        editCodigo.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_DONE) validarManual()
            false
        }
        findViewById<View>(R.id.btnRegistrarIngreso).setOnClickListener { confirmarIngreso() }

        verificarAcceso()
    }

    private fun verificarAcceso() {
        lifecycleScope.launch {
            try {
                val perfil = AdminRepository.perfilActual()
                if (perfil?.esSuperadmin != true) {
                    AppDialog.mostrar(
                        this@ValidarQrActivity,
                        AppDialog.Tipo.ERROR,
                        "Acceso restringido",
                        "Solo un superadmin puede validar entradas.",
                        onPrincipal = ::finish,
                        cancelable = false
                    )
                }
            } catch (error: Exception) {
                AppDialog.mostrar(
                    this@ValidarQrActivity,
                    AppDialog.Tipo.ERROR,
                    "No pudimos abrir el control de ingreso",
                    error.paraUsuario("Verificar rol para validar QR"),
                    onPrincipal = ::finish,
                    cancelable = false
                )
            }
        }
    }

    private fun abrirEscaner() {
        escaner.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Apuntá al QR del comprobante")
                .setBeepEnabled(true)
                .setOrientationLocked(false)
        )
    }

    private fun validarManual() {
        val qr = leerQrOrden(editCodigo.text.toString())
        if (qr == null) {
            AppDialog.error(this, "Escribí el código completo, por ejemplo GP26-123456.", "Código inválido")
            return
        }
        validar(qr.codigo, qr.ordenId, registrar = false)
    }

    private fun confirmarIngreso() {
        val v = actual ?: return
        val cantidad = v.items.sumOf { it.cantidad }
        AppDialog.mostrar(
            this,
            AppDialog.Tipo.CONFIRMAR,
            "¿Registrar ingreso?",
            "${v.codigo} · ${if (cantidad == 1) "1 entrada" else "$cantidad entradas"}\n" +
                "Después de esto el QR ya no se puede volver a usar.",
            botonPrincipal = "REGISTRAR",
            onPrincipal = { validar(v.codigo, v.ordenId, registrar = true) },
            botonSecundario = "CANCELAR"
        )
    }

    private fun validar(codigo: String, ordenId: Long?, registrar: Boolean) {
        lifecycleScope.launch {
            progress.visibility = View.VISIBLE
            card.visibility = View.GONE
            try {
                mostrar(AdminRepository.validarEntrada(codigo, ordenId, registrar))
                editCodigo.text?.clear()
            } catch (error: Exception) {
                AppDialog.error(this@ValidarQrActivity, error.paraUsuario("Validar entrada $codigo"))
            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    private fun mostrar(v: ValidacionApi) {
        actual = v
        val (estilo, titulo, mensaje) = when {
            v.resultado == "VALIDA" && v.registrada -> Triple(
                Estilo.OK, "INGRESO REGISTRADO", "Listo, puede pasar. El QR quedó marcado como usado."
            )
            v.resultado == "VALIDA" -> Triple(
                Estilo.OK, "ENTRADA VÁLIDA", "Puede ingresar. Registrá el ingreso para que el QR no se vuelva a usar."
            )
            v.resultado == "YA_USADA" -> Triple(
                Estilo.AVISO, "YA FUE USADA", "Este QR ya ingresó el ${formatearFecha(v.usadaAt.orEmpty())}."
            )
            v.resultado == "PENDIENTE" -> Triple(
                Estilo.AVISO, "COMPRA PENDIENTE", "La compra todavía no está confirmada."
            )
            v.resultado == "CANCELADA" -> Triple(
                Estilo.ERROR, "COMPRA CANCELADA", "Esta entrada fue cancelada y no permite el ingreso."
            )
            else -> Triple(
                Estilo.ERROR, "NO VÁLIDA", "No existe ninguna compra con este código."
            )
        }
        pintar(estilo, titulo, mensaje, v.codigo)

        val hayOrden = v.ordenId != null
        findViewById<View>(R.id.grupoResultadoDetalle).visibility = if (hayOrden) View.VISIBLE else View.GONE
        if (hayOrden) {
            val entradas = v.items.joinToString("\n   ") { "${it.cantidad} × ${it.nombre}" }
            findViewById<TextView>(R.id.txtResultadoDetalle).text = buildString {
                append("Comprador: ${v.comprador.orEmpty().ifBlank { "-" }}\n")
                append("Email: ${v.email.orEmpty().ifBlank { "-" }}\n")
                append("Entradas: $entradas\n")
                append("Total: ${formatearArs(v.total)}\n")
                append("Compra: ${formatearFecha(v.createdAt.orEmpty())}")
                if (v.usadaAt != null) append("\nIngreso: ${formatearFecha(v.usadaAt)}")
            }
        }
        findViewById<View>(R.id.btnRegistrarIngreso).visibility =
            if (v.resultado == "VALIDA" && !v.registrada) View.VISIBLE else View.GONE
    }

    private fun mostrarError(titulo: String, mensaje: String) {
        actual = null
        pintar(Estilo.ERROR, titulo.uppercase(), mensaje, "")
        findViewById<View>(R.id.grupoResultadoDetalle).visibility = View.GONE
        findViewById<View>(R.id.btnRegistrarIngreso).visibility = View.GONE
    }

    private fun pintar(estilo: Estilo, titulo: String, mensaje: String, codigo: String) {
        val color = Color.parseColor(estilo.color)
        findViewById<TextView>(R.id.txtResultadoIcono).apply {
            text = estilo.icono
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
        }
        findViewById<TextView>(R.id.txtResultadoTitulo).apply {
            text = titulo
            setTextColor(color)
        }
        findViewById<TextView>(R.id.txtResultadoMensaje).text = mensaje
        findViewById<TextView>(R.id.txtResultadoCodigo).apply {
            text = codigo
            visibility = if (codigo.isBlank()) View.GONE else View.VISIBLE
        }
        card.visibility = View.VISIBLE
    }
}
