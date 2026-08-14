package com.example.activiti_main

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.activiti_main.data.ClasificacionItem

class ClasificacionAdapter(
    private var items: List<ClasificacionItem> = emptyList(),
    private val onClick: (ClasificacionItem) -> Unit = {}
) : RecyclerView.Adapter<ClasificacionAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val viewColor: View = view.findViewById(R.id.viewColorEquipo)
        val txtPosicion: TextView = view.findViewById(R.id.txtPosicion)
        val viewAvatar: View = view.findViewById(R.id.viewAvatar)
        val txtInicial: TextView = view.findViewById(R.id.txtInicial)
        val txtNombre: TextView = view.findViewById(R.id.txtNombreRanking)
        val txtEquipo: TextView = view.findViewById(R.id.txtEquipoRanking)
        val txtPuntos: TextView = view.findViewById(R.id.txtPuntos)
        val txtTendencia: TextView = view.findViewById(R.id.txtTendencia)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_clasificacion, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val color = try {
            Color.parseColor(item.colorEquipo)
        } catch (_: Exception) {
            Color.WHITE
        }

        holder.txtPosicion.text = item.posicion.toString()
        holder.txtNombre.text = item.nombre
        holder.txtEquipo.text = item.equipo
        holder.txtPuntos.text = item.puntos.toString()
        holder.txtInicial.text = item.nombre.firstOrNull()?.toString() ?: "?"

        holder.viewColor.setBackgroundColor(color)

        val avatar = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        holder.viewAvatar.background = avatar

        when (item.tendencia) {
            "up" -> {
                holder.txtTendencia.text = "▲"
                holder.txtTendencia.setTextColor(Color.parseColor("#00C853"))
            }
            "down" -> {
                holder.txtTendencia.text = "▼"
                holder.txtTendencia.setTextColor(Color.parseColor("#E10600"))
            }
            else -> {
                holder.txtTendencia.text = "—"
                holder.txtTendencia.setTextColor(Color.parseColor("#9A9A9A"))
            }
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<ClasificacionItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
