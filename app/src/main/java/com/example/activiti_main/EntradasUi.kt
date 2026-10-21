package com.example.activiti_main

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.activiti_main.data.Carrito
import com.example.activiti_main.data.CategoriaEntrada
import com.example.activiti_main.data.Entrada
import com.example.activiti_main.data.EntradasCatalogo
import com.example.activiti_main.data.EntradasRepository
import com.example.activiti_main.data.SupabaseProvider
import com.example.activiti_main.data.formatearArs
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

/** Mapa, listado y carrito de entradas (Hub o pantalla completa). */
class EntradasUi(
    private val activity: AppCompatActivity,
    root: View
) {
    private val mapa: CircuitMapView = root.findViewById(R.id.mapaCircuito)
    private val adapter: EntradasAdapter
    private val txtFiltroZona: TextView = root.findViewById(R.id.txtFiltroZona)
    private val txtTituloTabla: TextView = root.findViewById(R.id.txtTituloTabla)
    private val barraCarrito: View = root.findViewById(R.id.barraCarrito)
    private val txtCarritoCantidad: TextView = root.findViewById(R.id.txtCarritoCantidad)
    private val txtCarritoTotal: TextView = root.findViewById(R.id.txtCarritoTotal)
    private val tabs: Map<CategoriaEntrada?, TextView>

    private var categoria: CategoriaEntrada? = null
    private var zonaFiltro: String? = null
    private var sectorFiltro: Int? = null
    private var comprando = false

    init {
        root.findViewById<View>(R.id.contenedorMapa).clipToOutline = true
        barraCarrito.applyBottomSystemBarPadding()

        adapter = EntradasAdapter(
            onClick = { mapa.zonaSeleccionada = it.zonaId; adapter.zonaResaltada = it.zonaId },
            onAgregar = ::agregarAlCarrito
        )
        root.findViewById<RecyclerView>(R.id.recyclerEntradas).apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = this@EntradasUi.adapter
        }

        tabs = mapOf(
            null to root.findViewById(R.id.tabTodas),
            CategoriaEntrada.TRIBUNA to root.findViewById(R.id.tabTribunas),
            CategoriaEntrada.GENERAL to root.findViewById(R.id.tabGeneral),
            CategoriaEntrada.HOSPITALITY to root.findViewById(R.id.tabHospitality)
        )

        mapa.onSeleccion = { zonaId, sector ->
            zonaFiltro = zonaId
            sectorFiltro = sector
            if (zonaId != null || sector != null) categoria = null
            actualizarLista()
        }
        root.findViewById<View>(R.id.btnMapaCentrar).setOnClickListener { mapa.recentrar() }
        root.findViewById<View>(R.id.btnMapaZoomIn).setOnClickListener { mapa.zoomIn() }
        root.findViewById<View>(R.id.btnMapaZoomOut).setOnClickListener { mapa.zoomOut() }

        tabs.forEach { (cat, tab) ->
            tab.setOnClickListener {
                categoria = cat
                limpiarSeleccionDelMapa()
                actualizarLista()
            }
        }
        txtFiltroZona.setOnClickListener {
            limpiarSeleccionDelMapa()
            actualizarLista()
        }
        root.findViewById<View>(R.id.btnVerCarrito).setOnClickListener { mostrarCarrito() }
    }

    fun bind() {
        actualizarLista()
        cargarPrecios()
    }

    fun onResume() {
        actualizarCarrito()
    }

    private fun cargarPrecios() {
        if (!SupabaseProvider.isConfigured) return
        activity.lifecycleScope.launch {
            try {
                EntradasCatalogo.actualizar(EntradasRepository.getEntradas())
            } catch (error: Exception) {
                error.registrar("Cargar precios de entradas (se usa el catálogo local)")
            }
            actualizarLista()
            actualizarCarrito()
        }
    }

    private fun limpiarSeleccionDelMapa() {
        zonaFiltro = null
        sectorFiltro = null
        mapa.zonaSeleccionada = null
        mapa.sectorSeleccionado = null
    }

    private fun actualizarLista() {
        val filtradas = EntradasCatalogo.entradas.filter { e ->
            (categoria == null || e.categoria == categoria) &&
                (zonaFiltro == null || e.zonaId == zonaFiltro) &&
                (sectorFiltro == null || EntradasCatalogo.sectorDe(e) == sectorFiltro)
        }
        adapter.zonaResaltada = zonaFiltro
        adapter.submitList(filtradas)

        val sinFiltroDeMapa = zonaFiltro == null && sectorFiltro == null
        tabs.forEach { (cat, tab) ->
            tab.setBackgroundResource(
                if (cat == categoria && sinFiltroDeMapa) R.drawable.bg_tab_active
                else R.drawable.bg_tab_inactive
            )
        }

        val zona = zonaFiltro?.let { EntradasCatalogo.zona(it) }
        val sector = sectorFiltro
        val filtro = when {
            zona != null -> zona.nombre
            sector != null -> "Sector $sector (${EntradasCatalogo.curvasDelSector(sector).lowercase()})"
            else -> null
        }
        txtTituloTabla.text = filtro?.uppercase() ?: categoria?.titulo?.uppercase() ?: "UBICACIÓN"
        if (filtro != null) {
            txtFiltroZona.visibility = View.VISIBLE
            txtFiltroZona.text = "Mostrando: $filtro   ✕"
        } else {
            txtFiltroZona.visibility = View.GONE
        }
    }

    private fun agregarAlCarrito(entrada: Entrada, cantidad: Int) {
        Carrito.agregar(entrada, cantidad)
        actualizarCarrito()
        AppDialog.mostrar(
            activity,
            AppDialog.Tipo.EXITO,
            "Agregado al carrito",
            "$cantidad × ${entrada.nombre}\n${formatearArs(entrada.precio * cantidad)}",
            botonPrincipal = "VER CARRITO",
            onPrincipal = ::mostrarCarrito,
            botonSecundario = "SEGUIR"
        )
    }

    private fun actualizarCarrito() {
        val cantidad = Carrito.cantidadTotal()
        if (cantidad == 0) {
            barraCarrito.visibility = View.GONE
            return
        }
        barraCarrito.visibility = View.VISIBLE
        txtCarritoCantidad.text =
            if (cantidad == 1) "1 entrada en el carrito" else "$cantidad entradas en el carrito"
        txtCarritoTotal.text = formatearArs(Carrito.total())
    }

    private fun mostrarCarrito() {
        if (Carrito.cantidadTotal() == 0) {
            AppDialog.mostrar(
                activity,
                AppDialog.Tipo.INFO,
                "Tu carrito está vacío",
                "Elegí una ubicación en el mapa o en la lista para empezar."
            )
            return
        }
        AppDialog.mostrar(
            activity,
            AppDialog.Tipo.INFO,
            "Tu carrito",
            "Total: ${formatearArs(Carrito.total())}",
            botonPrincipal = "CONFIRMAR",
            onPrincipal = ::confirmarCompra,
            botonSecundario = "SEGUIR",
            enlace = "Vaciar carrito",
            onEnlace = ::preguntarVaciar,
            contenido = vistaLineasCarrito()
        )
    }

    private fun vistaLineasCarrito(): View {
        val contenedor = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val inflater = LayoutInflater.from(activity)
        Carrito.lineas().forEach { (e, cant) ->
            val fila = inflater.inflate(R.layout.item_carrito, contenedor, false)
            fila.findViewById<TextView>(R.id.txtCarritoLineaNombre).text = "$cant × ${e.nombre}"
            fila.findViewById<TextView>(R.id.txtCarritoLineaDetalle).text =
                "SECTOR ${EntradasCatalogo.sectorDe(e)} · ${e.dias}"
            fila.findViewById<TextView>(R.id.txtCarritoLineaPrecio).text = formatearArs(e.precio * cant)
            contenedor.addView(fila)
        }
        return contenedor
    }

    private fun preguntarVaciar() {
        AppDialog.mostrar(
            activity,
            AppDialog.Tipo.CONFIRMAR,
            "¿Vaciar el carrito?",
            "Se van a quitar todas las entradas que elegiste.",
            botonPrincipal = "VACIAR",
            onPrincipal = {
                Carrito.vaciar()
                actualizarCarrito()
            },
            botonSecundario = "CANCELAR",
            onSecundario = ::mostrarCarrito
        )
    }

    private fun confirmarCompra() {
        val lineas = Carrito.lineas()
        if (lineas.isEmpty() || comprando) return
        val cantidad = lineas.sumOf { it.second }
        comprando = true
        activity.lifecycleScope.launch {
            try {
                val orden = EntradasRepository.crearOrden(lineas)
                Carrito.vaciar()
                actualizarCarrito()
                val email = SupabaseProvider.client.auth.currentUserOrNull()?.email
                enviarMail(orden.id, email)
                AppDialog.mostrar(
                    activity,
                    AppDialog.Tipo.EXITO,
                    "¡Compra confirmada!",
                    "Reservaste $cantidad ${if (cantidad == 1) "entrada" else "entradas"} para el GP de Argentina.\n\n" +
                        "Código de reserva: ${orden.codigo}\nTotal: ${formatearArs(orden.total)}" +
                        (email?.let { "\n\nTe enviamos el comprobante con el QR a $it" } ?: ""),
                    botonPrincipal = "VER COMPROBANTE",
                    onPrincipal = {
                        activity.startActivity(ComprobanteActivity.intent(activity, orden.id))
                    },
                    botonSecundario = "LISTO"
                )
            } catch (error: Exception) {
                AppDialog.error(
                    activity,
                    error.paraUsuario("Confirmar compra de entradas"),
                    "No se pudo confirmar la compra"
                )
            } finally {
                comprando = false
                cargarPrecios()
            }
        }
    }

    /** El mail va aparte: si falla, la compra igual quedó hecha y el comprobante está en la app. */
    private fun enviarMail(ordenId: Long, email: String?) {
        if (email == null) return
        activity.lifecycleScope.launch {
            try {
                EntradasRepository.enviarComprobantePorMail(ordenId)
            } catch (error: Exception) {
                error.registrar("Enviar comprobante de la orden $ordenId por mail")
                AppDialog.mostrar(
                    activity,
                    AppDialog.Tipo.ERROR,
                    "No pudimos enviarte el mail",
                    "Tu compra está confirmada. Podés ver el comprobante y el QR en \"Mis entradas\"."
                )
            }
        }
    }
}
