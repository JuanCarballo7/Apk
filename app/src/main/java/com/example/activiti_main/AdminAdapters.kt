package com.example.activiti_main

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.activiti_main.data.CategoriaEntrada
import com.example.activiti_main.data.EntradasCatalogo
import com.example.activiti_main.data.NewsApi
import com.example.activiti_main.data.TicketApi
import com.example.activiti_main.data.UsuarioAdmin
import com.example.activiti_main.data.formatearArs

private const val GRIS = "#8A8A95"
private const val ROJO = "#E10600"

/** Pone el badge en rojo lleno (destacado) o con borde gris. */
private fun TextView.badge(texto: String, lleno: Boolean, colorTexto: String = "#FFFFFF") {
    text = texto
    setBackgroundResource(if (lleno) R.drawable.bg_unlocked_badge else R.drawable.bg_badge_outline)
    setTextColor(Color.parseColor(colorTexto))
}

fun rolLegible(rol: String): String = when (rol) {
    "superadmin" -> "Superadmin"
    "admin" -> "Admin"
    else -> "Usuario"
}

class AdminEntradasAdapter(
    private val onEditar: (TicketApi) -> Unit,
    private val onActivar: (TicketApi) -> Unit,
    private val onAgotar: (TicketApi) -> Unit
) : RecyclerView.Adapter<AdminEntradasAdapter.VH>() {

    private var items: List<TicketApi> = emptyList()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val nombre: TextView = view.findViewById(R.id.txtAdminEntradaNombre)
        val precio: TextView = view.findViewById(R.id.txtAdminEntradaPrecio)
        val detalle: TextView = view.findViewById(R.id.txtAdminEntradaDetalle)
        val estado: TextView = view.findViewById(R.id.txtAdminEntradaEstado)
        val stock: TextView = view.findViewById(R.id.txtAdminEntradaStock)
        val editar: TextView = view.findViewById(R.id.btnAdminEntradaEditar)
        val activar: TextView = view.findViewById(R.id.btnAdminEntradaActivar)
        val agotar: TextView = view.findViewById(R.id.btnAdminEntradaAgotar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_admin_entrada, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = items[position]
        val sector = EntradasCatalogo.zona(t.zoneId)?.sector?.toString() ?: "-"
        val categoria = runCatching { CategoriaEntrada.valueOf(t.categoria).titulo }.getOrDefault(t.categoria)

        holder.nombre.text = t.nombre
        holder.precio.text = formatearArs(t.precio)
        holder.detalle.text = "SECTOR $sector · ${categoria.uppercase()} · ${t.dias}"
        holder.stock.text = "Stock: ${t.stock}"
        when {
            !t.activo -> holder.estado.badge("INACTIVA", lleno = false, colorTexto = GRIS)
            t.agotada -> holder.estado.badge("AGOTADA", lleno = false, colorTexto = ROJO)
            else -> holder.estado.badge("A LA VENTA", lleno = true)
        }
        holder.precio.setTextColor(Color.parseColor(if (t.activo) "#FFFFFF" else GRIS))

        holder.activar.text = if (t.activo) "DESACTIVAR" else "ACTIVAR"
        holder.agotar.isEnabled = !t.agotada
        holder.agotar.alpha = if (t.agotada) 0.35f else 1f

        holder.editar.setOnClickListener { onEditar(t) }
        holder.activar.setOnClickListener { onActivar(t) }
        holder.agotar.setOnClickListener { onAgotar(t) }
    }

    override fun getItemCount() = items.size

    fun submit(nuevos: List<TicketApi>) {
        items = nuevos
        notifyDataSetChanged()
    }

    fun reemplazar(ticket: TicketApi) {
        val i = items.indexOfFirst { it.id == ticket.id }
        if (i < 0) return
        items = items.toMutableList().also { it[i] = ticket }
        notifyItemChanged(i)
    }
}

class AdminNoticiasAdapter(
    private val onClick: (NewsApi) -> Unit
) : RecyclerView.Adapter<AdminNoticiasAdapter.VH>() {

    private var items: List<NewsApi> = emptyList()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val fila: View = view.findViewById(R.id.filaAdminNoticia)
        val estado: TextView = view.findViewById(R.id.txtAdminNoticiaEstado)
        val destacada: TextView = view.findViewById(R.id.txtAdminNoticiaDestacada)
        val badge: TextView = view.findViewById(R.id.txtAdminNoticiaBadge)
        val titulo: TextView = view.findViewById(R.id.txtAdminNoticiaTitulo)
        val resumen: TextView = view.findViewById(R.id.txtAdminNoticiaResumen)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_admin_noticia, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val n = items[position]
        if (n.published) holder.estado.badge("PUBLICADA", lleno = true)
        else holder.estado.badge("BORRADOR", lleno = false, colorTexto = GRIS)
        holder.destacada.visibility = if (n.featured) View.VISIBLE else View.GONE
        holder.badge.visibility = if (n.badge.isNullOrBlank()) View.GONE else View.VISIBLE
        holder.badge.text = n.badge
        holder.titulo.text = n.title
        holder.resumen.text = n.summary
        holder.resumen.visibility = if (n.summary.isNullOrBlank()) View.GONE else View.VISIBLE
        holder.fila.setOnClickListener { onClick(n) }
    }

    override fun getItemCount() = items.size

    fun submit(nuevos: List<NewsApi>) {
        items = nuevos
        notifyDataSetChanged()
    }
}

class AdminUsuariosAdapter(
    private val onClick: (UsuarioAdmin) -> Unit
) : RecyclerView.Adapter<AdminUsuariosAdapter.VH>() {

    private var items: List<UsuarioAdmin> = emptyList()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val fila: View = view.findViewById(R.id.filaAdminUsuario)
        val nombre: TextView = view.findViewById(R.id.txtAdminUsuarioNombre)
        val email: TextView = view.findViewById(R.id.txtAdminUsuarioEmail)
        val rol: TextView = view.findViewById(R.id.txtAdminUsuarioRol)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_admin_usuario, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val u = items[position]
        holder.nombre.text = "${u.firstName} ${u.lastName}".trim().ifBlank { "Sin nombre" }
        holder.email.text = u.email.orEmpty()
        if (u.role == "user") holder.rol.badge("USUARIO", lleno = false, colorTexto = "#CCCCCC")
        else holder.rol.badge(rolLegible(u.role).uppercase(), lleno = true)
        holder.fila.setOnClickListener { onClick(u) }
    }

    override fun getItemCount() = items.size

    fun submit(nuevos: List<UsuarioAdmin>) {
        items = nuevos
        notifyDataSetChanged()
    }
}
