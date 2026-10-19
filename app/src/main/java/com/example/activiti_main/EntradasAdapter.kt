package com.example.activiti_main

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.activiti_main.data.Carrito
import com.example.activiti_main.data.Entrada
import com.example.activiti_main.data.EntradasCatalogo
import com.example.activiti_main.data.formatearArs

class EntradasAdapter(
    private val onClick: (Entrada) -> Unit,
    private val onAgregar: (Entrada, Int) -> Unit
) : RecyclerView.Adapter<EntradasAdapter.VH>() {

    private var items: List<Entrada> = emptyList()
    private val cantidades = mutableMapOf<String, Int>()

    var zonaResaltada: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val fila: View = view.findViewById(R.id.filaEntrada)
        val nombre: TextView = view.findViewById(R.id.txtEntradaNombre)
        val dias: TextView = view.findViewById(R.id.txtEntradaDias)
        val precio: TextView = view.findViewById(R.id.txtEntradaPrecio)
        val techada: ImageView = view.findViewById(R.id.icoTechada)
        val pantalla: ImageView = view.findViewById(R.id.icoPantalla)
        val catering: ImageView = view.findViewById(R.id.icoCatering)
        val grupoCantidad: View = view.findViewById(R.id.grupoCantidad)
        val menos: TextView = view.findViewById(R.id.btnMenos)
        val mas: TextView = view.findViewById(R.id.btnMas)
        val cantidad: TextView = view.findViewById(R.id.txtCantidad)
        val agregar: TextView = view.findViewById(R.id.btnAgregar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_entrada, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val cant = cantidades[item.id] ?: 1

        holder.nombre.text = item.nombre
        holder.dias.text = "SECTOR ${EntradasCatalogo.sectorDe(item)} · ${item.dias}"
        holder.precio.text = formatearArs(item.precio)
        holder.techada.visibility = if (item.techada) View.VISIBLE else View.GONE
        holder.pantalla.visibility = if (item.pantalla) View.VISIBLE else View.GONE
        holder.catering.visibility = if (item.catering) View.VISIBLE else View.GONE
        holder.fila.setBackgroundColor(
            if (item.zonaId == zonaResaltada) Color.parseColor("#22222B") else Color.TRANSPARENT
        )

        holder.cantidad.text = cant.toString()
        holder.menos.isEnabled = cant > 1
        holder.menos.alpha = if (cant > 1) 1f else 0.35f
        holder.mas.isEnabled = cant < Carrito.MAX_POR_ENTRADA
        holder.mas.alpha = if (cant < Carrito.MAX_POR_ENTRADA) 1f else 0.35f

        if (item.agotada) {
            holder.grupoCantidad.visibility = View.INVISIBLE
            holder.agregar.text = "AGOTADO"
            holder.agregar.isEnabled = false
            holder.agregar.setTextColor(Color.parseColor("#8A8A95"))
            holder.precio.setTextColor(Color.parseColor("#8A8A95"))
        } else {
            holder.grupoCantidad.visibility = View.VISIBLE
            holder.agregar.text = "AGREGAR"
            holder.agregar.isEnabled = true
            holder.agregar.setTextColor(Color.WHITE)
            holder.precio.setTextColor(Color.WHITE)
        }

        holder.menos.setOnClickListener { cambiarCantidad(holder, item, -1) }
        holder.mas.setOnClickListener { cambiarCantidad(holder, item, +1) }
        holder.agregar.setOnClickListener {
            onAgregar(item, cantidades[item.id] ?: 1)
            cantidades.remove(item.id)
            notifyItemChanged(holder.bindingAdapterPosition)
        }
        holder.itemView.setOnClickListener { onClick(item) }
    }

    private fun cambiarCantidad(holder: VH, item: Entrada, delta: Int) {
        val nueva = ((cantidades[item.id] ?: 1) + delta).coerceIn(1, Carrito.MAX_POR_ENTRADA)
        cantidades[item.id] = nueva
        notifyItemChanged(holder.bindingAdapterPosition)
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<Entrada>) {
        items = newItems
        notifyDataSetChanged()
    }
}
