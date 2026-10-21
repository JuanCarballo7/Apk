package com.example.activiti_main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.activiti_main.data.AdminRepository
import com.example.activiti_main.data.NewsApi
import com.example.activiti_main.data.NewsInput
import com.example.activiti_main.data.ProfileApi
import com.example.activiti_main.data.TicketApi
import com.example.activiti_main.data.UsuarioAdmin
import com.example.activiti_main.data.formatearArs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

/** Panel para admin y superadmin: precios y stock de entradas, noticias y (solo superadmin) roles. */
class AdminActivity : AppCompatActivity() {

    private enum class Seccion(val titulo: String, val vacio: String) {
        ENTRADAS("ENTRADAS · PRECIO Y STOCK", "No hay entradas cargadas."),
        NOTICIAS("NOTICIAS", "Todavía no hay noticias. Creá la primera con + NUEVA."),
        USUARIOS("USUARIOS · ROLES", "No hay usuarios registrados.")
    }

    private lateinit var recycler: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var txtVacio: TextView
    private lateinit var txtTitulo: TextView
    private lateinit var btnNueva: View
    private lateinit var tabs: Map<Seccion, TextView>

    private val entradasAdapter = AdminEntradasAdapter(
        onEditar = ::editarEntrada,
        onActivar = ::confirmarActivacion,
        onAgotar = ::confirmarAgotada
    )
    private val noticiasAdapter = AdminNoticiasAdapter(onClick = ::editarNoticia)
    private val usuariosAdapter = AdminUsuariosAdapter(onClick = ::opcionesUsuario)

    private var perfil: ProfileApi? = null
    private var seccion = Seccion.ENTRADAS
    private var cargaJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        findViewById<View>(R.id.headerAdmin).applyTopSystemBarPadding()
        findViewById<View>(R.id.btnAdminBack).setOnClickListener { finish() }

        recycler = findViewById(R.id.recyclerAdmin)
        progress = findViewById(R.id.progressAdmin)
        txtVacio = findViewById(R.id.txtAdminVacio)
        txtTitulo = findViewById(R.id.txtAdminTitulo)
        btnNueva = findViewById(R.id.btnAdminNueva)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.applyBottomSystemBarPadding()

        tabs = mapOf(
            Seccion.ENTRADAS to findViewById(R.id.tabAdminEntradas),
            Seccion.NOTICIAS to findViewById(R.id.tabAdminNoticias),
            Seccion.USUARIOS to findViewById(R.id.tabAdminUsuarios)
        )
        tabs.forEach { (s, tab) -> tab.setOnClickListener { mostrarSeccion(s) } }
        btnNueva.setOnClickListener { editarNoticia(null) }

        verificarAcceso()
    }

    /** La app solo esconde el panel; la RLS de Supabase es la que realmente bloquea las escrituras. */
    private fun verificarAcceso() {
        lifecycleScope.launch {
            setCargando(true)
            try {
                val p = AdminRepository.perfilActual()
                if (p == null || !p.esAdmin) {
                    setCargando(false)
                    AppDialog.mostrar(
                        this@AdminActivity,
                        AppDialog.Tipo.ERROR,
                        "Acceso restringido",
                        "Esta sección es solo para administradores.",
                        onPrincipal = ::finish,
                        cancelable = false
                    )
                    return@launch
                }
                perfil = p
                findViewById<TextView>(R.id.txtAdminRol).text =
                    if (p.esSuperadmin) "PANEL SUPERADMIN" else "PANEL ADMIN"
                tabs.getValue(Seccion.USUARIOS).visibility = if (p.esSuperadmin) View.VISIBLE else View.GONE
                findViewById<View>(R.id.btnAdminValidarQr).apply {
                    visibility = if (p.esSuperadmin) View.VISIBLE else View.GONE
                    setOnClickListener {
                        startActivity(Intent(this@AdminActivity, ValidarQrActivity::class.java))
                    }
                }
                mostrarSeccion(Seccion.ENTRADAS)
            } catch (error: Exception) {
                setCargando(false)
                AppDialog.mostrar(
                    this@AdminActivity,
                    AppDialog.Tipo.ERROR,
                    "No pudimos abrir el panel",
                    error.paraUsuario("Verificar rol de admin"),
                    onPrincipal = ::finish,
                    cancelable = false
                )
            }
        }
    }

    private fun mostrarSeccion(nueva: Seccion) {
        seccion = nueva
        tabs.forEach { (s, tab) ->
            tab.setBackgroundResource(if (s == nueva) R.drawable.bg_tab_active else R.drawable.bg_tab_inactive)
        }
        txtTitulo.text = nueva.titulo
        btnNueva.visibility = if (nueva == Seccion.NOTICIAS) View.VISIBLE else View.GONE
        recycler.adapter = when (nueva) {
            Seccion.ENTRADAS -> entradasAdapter
            Seccion.NOTICIAS -> noticiasAdapter
            Seccion.USUARIOS -> usuariosAdapter
        }
        cargar()
    }

    private fun cargar() {
        val actual = seccion
        cargaJob?.cancel()
        cargaJob = lifecycleScope.launch {
            setCargando(true)
            txtVacio.visibility = View.GONE
            try {
                val cantidad = when (actual) {
                    Seccion.ENTRADAS -> AdminRepository.getTickets().also(entradasAdapter::submit).size
                    Seccion.NOTICIAS -> AdminRepository.getNoticias().also(noticiasAdapter::submit).size
                    Seccion.USUARIOS -> AdminRepository.getUsuarios().also(usuariosAdapter::submit).size
                }
                if (cantidad == 0) {
                    txtVacio.text = actual.vacio
                    txtVacio.visibility = View.VISIBLE
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                txtVacio.text = "No se pudo cargar la lista."
                txtVacio.visibility = View.VISIBLE
                AppDialog.error(this@AdminActivity, error.paraUsuario("Cargar ${actual.name.lowercase()} en panel admin"))
            } finally {
                setCargando(false)
            }
        }
    }

    // ---------- Entradas ----------

    private fun editarEntrada(ticket: TicketApi) {
        val form = LayoutInflater.from(this).inflate(R.layout.form_admin_entrada, null)
        val editPrecio = form.findViewById<EditText>(R.id.editAdminPrecio)
        val editStock = form.findViewById<EditText>(R.id.editAdminStock)
        editPrecio.setText(BigDecimal.valueOf(ticket.precio).setScale(2, RoundingMode.HALF_UP).toPlainString())
        editStock.setText(ticket.stock.toString())

        AppDialog.mostrar(
            this,
            AppDialog.Tipo.INFO,
            "Editar entrada",
            ticket.nombre,
            botonPrincipal = "GUARDAR",
            onPrincipal = {
                val precio = editPrecio.text.toString().trim().replace(',', '.').toBigDecimalOrNull()
                val stock = editStock.text.toString().trim().toIntOrNull()
                when {
                    precio == null || precio < BigDecimal.ZERO ->
                        AppDialog.error(this, "Ingresá un precio válido (por ejemplo 1850000.00).", "Precio inválido")
                    stock == null || stock < 0 ->
                        AppDialog.error(this, "El stock tiene que ser un número entero, 0 o más.", "Stock inválido")
                    else -> guardarEntrada(
                        ticket,
                        "Entrada actualizada",
                        precio = precio.setScale(2, RoundingMode.HALF_UP).toDouble(),
                        stock = stock
                    )
                }
            },
            botonSecundario = "CANCELAR",
            contenido = form
        )
    }

    private fun confirmarActivacion(ticket: TicketApi) {
        val activar = !ticket.activo
        AppDialog.mostrar(
            this,
            AppDialog.Tipo.CONFIRMAR,
            if (activar) "¿Activar entrada?" else "¿Desactivar entrada?",
            if (activar) "${ticket.nombre} vuelve a mostrarse en la venta."
            else "${ticket.nombre} deja de mostrarse en la venta de entradas.",
            botonPrincipal = if (activar) "ACTIVAR" else "DESACTIVAR",
            onPrincipal = {
                guardarEntrada(ticket, if (activar) "Entrada activada" else "Entrada desactivada", activo = activar)
            },
            botonSecundario = "CANCELAR"
        )
    }

    private fun confirmarAgotada(ticket: TicketApi) {
        AppDialog.mostrar(
            this,
            AppDialog.Tipo.CONFIRMAR,
            "¿Marcar como agotada?",
            "El stock de ${ticket.nombre} pasa a 0 y nadie más va a poder comprarla.",
            botonPrincipal = "AGOTAR",
            onPrincipal = { guardarEntrada(ticket, "Entrada agotada", stock = 0) },
            botonSecundario = "CANCELAR"
        )
    }

    private fun guardarEntrada(
        ticket: TicketApi,
        mensajeExito: String,
        precio: Double? = null,
        stock: Int? = null,
        activo: Boolean? = null
    ) {
        lifecycleScope.launch {
            setCargando(true)
            try {
                val actualizada = AdminRepository.actualizarTicket(ticket.id, precio, stock, activo)
                entradasAdapter.reemplazar(actualizada)
                AppDialog.mostrar(
                    this@AdminActivity,
                    AppDialog.Tipo.EXITO,
                    mensajeExito,
                    "${actualizada.nombre}\nPrecio: ${formatearArs(actualizada.precio)} · Stock: ${actualizada.stock}"
                )
            } catch (error: Exception) {
                AppDialog.error(this@AdminActivity, error.paraUsuario("Actualizar entrada ${ticket.id}"))
            } finally {
                setCargando(false)
            }
        }
    }

    // ---------- Noticias ----------

    private fun editarNoticia(noticia: NewsApi?) {
        val form = LayoutInflater.from(this).inflate(R.layout.form_admin_noticia, null)
        val editTitulo = form.findViewById<EditText>(R.id.editAdminNoticiaTitulo)
        val editResumen = form.findViewById<EditText>(R.id.editAdminNoticiaResumen)
        val editImagen = form.findViewById<EditText>(R.id.editAdminNoticiaImagen)
        val editBadge = form.findViewById<EditText>(R.id.editAdminNoticiaBadge)
        val switchDestacada = form.findViewById<SwitchCompat>(R.id.switchAdminNoticiaDestacada)
        val switchPublicada = form.findViewById<SwitchCompat>(R.id.switchAdminNoticiaPublicada)

        if (noticia != null) {
            editTitulo.setText(noticia.title)
            editResumen.setText(noticia.summary)
            editImagen.setText(noticia.imageUrl)
            editBadge.setText(noticia.badge)
            switchDestacada.isChecked = noticia.featured
            switchPublicada.isChecked = noticia.published
        }

        AppDialog.mostrar(
            this,
            AppDialog.Tipo.INFO,
            if (noticia == null) "Nueva noticia" else "Editar noticia",
            botonPrincipal = "GUARDAR",
            onPrincipal = {
                val titulo = editTitulo.text.toString().trim()
                if (titulo.length < 5) {
                    AppDialog.error(this, "El título tiene que tener al menos 5 caracteres.", "Falta el título")
                    return@mostrar
                }
                val datos = NewsInput(
                    title = titulo,
                    summary = editResumen.text.toString().trim().ifBlank { null },
                    imageUrl = editImagen.text.toString().trim().ifBlank { null },
                    badge = editBadge.text.toString().trim().uppercase().ifBlank { null },
                    featured = switchDestacada.isChecked,
                    published = switchPublicada.isChecked
                )
                guardarNoticia(noticia?.id, datos)
            },
            botonSecundario = "CANCELAR",
            contenido = form,
            cancelable = false
        )
    }

    private fun guardarNoticia(id: Long?, datos: NewsInput) {
        lifecycleScope.launch {
            setCargando(true)
            try {
                if (id == null) AdminRepository.crearNoticia(datos)
                else AdminRepository.actualizarNoticia(id, datos)
                AppDialog.mostrar(
                    this@AdminActivity,
                    AppDialog.Tipo.EXITO,
                    if (datos.published) "Noticia publicada" else "Borrador guardado",
                    datos.title
                )
                cargar()
            } catch (error: Exception) {
                setCargando(false)
                AppDialog.error(
                    this@AdminActivity,
                    error.paraUsuario(if (id == null) "Crear noticia" else "Editar noticia $id")
                )
            }
        }
    }

    // ---------- Usuarios ----------

    private fun nombreDe(usuario: UsuarioAdmin) =
        "${usuario.firstName} ${usuario.lastName}".trim().ifBlank { usuario.email.orEmpty() }

    private fun opcionesUsuario(usuario: UsuarioAdmin) {
        AppDialog.mostrar(
            this,
            AppDialog.Tipo.INFO,
            nombreDe(usuario),
            "${usuario.email.orEmpty()}\nSaldo: ${formatearArs(usuario.saldo)}",
            botonPrincipal = "CARGAR SALDO",
            onPrincipal = { pedirMonto(usuario, esCarga = true) },
            botonSecundario = "DESCONTAR",
            onSecundario = { pedirMonto(usuario, esCarga = false) },
            enlace = "Cambiar rol",
            onEnlace = { elegirRol(usuario) }
        )
    }

    private fun pedirMonto(usuario: UsuarioAdmin, esCarga: Boolean) {
        val nombre = nombreDe(usuario)
        val form = LayoutInflater.from(this).inflate(R.layout.form_admin_saldo, null)
        val editMonto = form.findViewById<EditText>(R.id.editAdminSaldoMonto)
        val editMotivo = form.findViewById<EditText>(R.id.editAdminSaldoMotivo)

        AppDialog.mostrar(
            this,
            AppDialog.Tipo.INFO,
            if (esCarga) "Cargar saldo" else "Descontar saldo",
            "$nombre · Saldo actual: ${formatearArs(usuario.saldo)}",
            botonPrincipal = if (esCarga) "CARGAR" else "DESCONTAR",
            onPrincipal = {
                val monto = editMonto.text.toString().trim().replace(',', '.').toBigDecimalOrNull()
                    ?.setScale(2, RoundingMode.HALF_UP)
                when {
                    monto == null || monto <= BigDecimal.ZERO ->
                        AppDialog.error(this, "Ingresá un monto mayor a cero (por ejemplo 500000).", "Monto inválido")
                    !esCarga && monto.toDouble() > usuario.saldo ->
                        AppDialog.error(
                            this,
                            "$nombre tiene ${formatearArs(usuario.saldo)}: no se puede descontar más que eso.",
                            "Saldo insuficiente"
                        )
                    else -> confirmarSaldo(
                        usuario,
                        nombre,
                        if (esCarga) monto.toDouble() else -monto.toDouble(),
                        editMotivo.text.toString().trim().ifBlank { null }
                    )
                }
            },
            botonSecundario = "CANCELAR",
            contenido = form,
            cancelable = false
        )
    }

    private fun confirmarSaldo(usuario: UsuarioAdmin, nombre: String, monto: Double, motivo: String?) {
        val esCarga = monto > 0
        AppDialog.mostrar(
            this,
            AppDialog.Tipo.CONFIRMAR,
            if (esCarga) "¿Cargar saldo?" else "¿Descontar saldo?",
            "${if (esCarga) "Se le suman" else "Se le descuentan"} ${formatearArs(kotlin.math.abs(monto))} a $nombre.\n" +
                "Saldo final: ${formatearArs(usuario.saldo + monto)}",
            botonPrincipal = "CONFIRMAR",
            onPrincipal = {
                lifecycleScope.launch {
                    setCargando(true)
                    try {
                        val saldo = AdminRepository.asignarSaldo(usuario.id, monto, motivo)
                        AppDialog.mostrar(
                            this@AdminActivity,
                            AppDialog.Tipo.EXITO,
                            "Saldo actualizado",
                            "$nombre ahora tiene ${formatearArs(saldo)}."
                        )
                        cargar()
                    } catch (error: Exception) {
                        setCargando(false)
                        AppDialog.error(this@AdminActivity, error.paraUsuario("Asignar saldo $monto a ${usuario.id}"))
                    }
                }
            },
            botonSecundario = "CANCELAR"
        )
    }

    private fun elegirRol(usuario: UsuarioAdmin) {
        val nombre = nombreDe(usuario)
        if (usuario.id == perfil?.id) {
            AppDialog.mostrar(this, AppDialog.Tipo.INFO, "Es tu cuenta", "No podés cambiar tu propio rol.")
            return
        }

        val form = LayoutInflater.from(this).inflate(R.layout.form_admin_rol, null) as RadioGroup
        val opciones = mapOf(
            R.id.radioRolUser to "user",
            R.id.radioRolAdmin to "admin",
            R.id.radioRolSuperadmin to "superadmin"
        )
        form.check(opciones.entries.firstOrNull { it.value == usuario.role }?.key ?: R.id.radioRolUser)

        AppDialog.mostrar(
            this,
            AppDialog.Tipo.INFO,
            "Rol de usuario",
            "$nombre\n${usuario.email.orEmpty()}",
            botonPrincipal = "CAMBIAR",
            onPrincipal = {
                val nuevo = opciones[form.checkedRadioButtonId] ?: return@mostrar
                if (nuevo != usuario.role) confirmarRol(usuario, nombre, nuevo)
            },
            botonSecundario = "CANCELAR",
            contenido = form
        )
    }

    private fun confirmarRol(usuario: UsuarioAdmin, nombre: String, nuevo: String) {
        AppDialog.mostrar(
            this,
            AppDialog.Tipo.CONFIRMAR,
            "¿Cambiar rol?",
            "$nombre pasa de ${rolLegible(usuario.role)} a ${rolLegible(nuevo)}.",
            botonPrincipal = "CONFIRMAR",
            onPrincipal = {
                lifecycleScope.launch {
                    setCargando(true)
                    try {
                        AdminRepository.cambiarRol(usuario.id, nuevo)
                        AppDialog.mostrar(
                            this@AdminActivity,
                            AppDialog.Tipo.EXITO,
                            "Rol actualizado",
                            "$nombre ahora es ${rolLegible(nuevo)}."
                        )
                        cargar()
                    } catch (error: Exception) {
                        setCargando(false)
                        AppDialog.error(this@AdminActivity, error.paraUsuario("Cambiar rol de ${usuario.id} a $nuevo"))
                    }
                }
            },
            botonSecundario = "CANCELAR"
        )
    }

    private fun setCargando(cargando: Boolean) {
        progress.visibility = if (cargando) View.VISIBLE else View.GONE
    }
}
