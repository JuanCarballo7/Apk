package com.example.activiti_main

import android.os.Bundle
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
import kotlinx.coroutines.launch

class EntradasActivity : AppCompatActivity() {

    private lateinit var mapa: CircuitMapView
    private lateinit var adapter: EntradasAdapter
    private lateinit var txtFiltroZona: TextView
    private lateinit var txtTituloTabla: TextView
    private lateinit var barraCarrito: View
    private lateinit var txtCarritoCantidad: TextView
    private lateinit var txtCarritoTotal: TextView

    private lateinit var tabs: Map<CategoriaEntrada?, TextView>

    private var categoria: CategoriaEntrada? = null
    private var zonaFiltro: String? = null
    private var sectorFiltro: Int? = null
    private var comprando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_entradas)

        findViewById<View>(R.id.headerEntradas).applyTopSystemBarPadding()
        findViewById<View>(R.id.btnEntradasBack).setOnClickListener { finish() }

        findViewById<View>(R.id.contenedorMapa).clipToOutline = true
        mapa = findViewById(R.id.mapaCircuito)
        txtFiltroZona = findViewById(R.id.txtFiltroZona)
        txtTituloTabla = findViewById(R.id.txtTituloTabla)
        barraCarrito = findViewById(R.id.barraCarrito)
        txtCarritoCantidad = findViewById(R.id.txtCarritoCantidad)
        txtCarritoTotal = findViewById(R.id.txtCarritoTotal)
        barraCarrito.applyBottomSystemBarPadding()

        mapa.onSeleccion = { zonaId, sector ->
            zonaFiltro = zonaId
            sectorFiltro = sector
            if (zonaId != null || sector != null) categoria = null
            actualizarLista()
        }
        findViewById<View>(R.id.btnMapaCentrar).setOnClickListener { mapa.recentrar() }
        findViewById<View>(R.id.btnMapaZoomIn).setOnClickListener { mapa.zoomIn() }
        findViewById<View>(R.id.btnMapaZoomOut).setOnClickListener { mapa.zoomOut() }

        adapter = EntradasAdapter(
            onClick = { mapa.zonaSeleccionada = it.zonaId; adapter.zonaResaltada = it.zonaId },
            onAgregar = ::agregarAlCarrito
        )
        findViewById<RecyclerView>(R.id.recyclerEntradas).apply {
            layoutManager = LinearLayoutManager(this@EntradasActivity)
            adapter = this@EntradasActivity.adapter
        }

        tabs = mapOf(
            null to findViewById(R.id.tabTodas),
            CategoriaEntrada.TRIBUNA to findViewById(R.id.tabTribunas),
            CategoriaEntrada.GENERAL to findViewById(R.id.tabGeneral),
            CategoriaEntrada.HOSPITALITY to findViewById(R.id.tabHospitality)
        )
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
        findViewById<View>(R.id.btnVerCarrito).setOnClickListener { mostrarCarrito() }

        actualizarLista()
        cargarPrecios()
    }

    /** Trae precios y stock de Supabase; si falla, queda el catálogo local de Entradas.kt. */
    private fun cargarPrecios() {
        if (!SupabaseProvider.isConfigured) return
        lifecycleScope.launch {
            try {
                EntradasCatalogo.actualizar(EntradasRepository.getEntradas())
            } catch (error: Exception) {
                error.registrar("Cargar precios de entradas (se usa el catálogo local)")
            }
            actualizarLista()
            actualizarCarrito()
        }
    }

    override fun onResume() {
        super.onResume()
        actualizarCarrito()
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
            this,
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
        txtCarritoCantidad.text = if (cantidad == 1) "1 entrada en el carrito" else "$cantidad entradas en el carrito"
        txtCarritoTotal.text = formatearArs(Carrito.total())
    }

    private fun mostrarCarrito() {
        if (Carrito.cantidadTotal() == 0) {
            AppDialog.mostrar(
                this,
                AppDialog.Tipo.INFO,
                "Tu carrito está vacío",
                "Elegí una ubicación en el mapa o en la lista para empezar."
            )
            return
        }
        AppDialog.mostrar(
            this,
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
        val contenedor = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val inflater = LayoutInflater.from(this)
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
            this,
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

    /** La orden se crea en Supabase: la base toma precios y stock de la tabla tickets, no del carrito. */
    private fun confirmarCompra() {
        val lineas = Carrito.lineas()
        if (lineas.isEmpty() || comprando) return
        val cantidad = lineas.sumOf { it.second }
        comprando = true
        lifecycleScope.launch {
            try {
                val orden = EntradasRepository.crearOrden(lineas)
                Carrito.vaciar()
                actualizarCarrito()
                AppDialog.mostrar(
                    this@EntradasActivity,
                    AppDialog.Tipo.EXITO,
                    "¡Compra confirmada!",
                    "Reservaste $cantidad ${if (cantidad == 1) "entrada" else "entradas"} para el GP de Argentina.\n\n" +
                        "Código de reserva: ${orden.codigo}\nTotal: ${formatearArs(orden.total)}",
                    botonPrincipal = "VER COMPROBANTE",
                    onPrincipal = {
                        startActivity(ComprobanteActivity.intent(this@EntradasActivity, orden.id))
                    },
                    botonSecundario = "LISTO"
                )
            } catch (error: Exception) {
                AppDialog.error(
                    this@EntradasActivity,
                    error.paraUsuario("Confirmar compra de entradas"),
                    "No se pudo confirmar la compra"
                )
            } finally {
                comprando = false
                cargarPrecios()
            }
        }
    }
}
