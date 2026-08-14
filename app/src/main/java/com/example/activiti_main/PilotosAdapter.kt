package com.example.activiti_main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.activiti_main.data.DriverApi

class PilotosAdapter(
    private var items: List<DriverApi> = emptyList(),
    private val onClick: (DriverApi) -> Unit
) : RecyclerView.Adapter<PilotosAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.imgPiloto)
        val nombre: TextView = view.findViewById(R.id.txtNombrePiloto)
        val equipo: TextView = view.findViewById(R.id.txtEquipoPiloto)
        val numero: TextView = view.findViewById(R.id.txtNumeroPiloto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_piloto, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.nombre.text = item.displayName()
        holder.equipo.text = item.teams?.name ?: "Sin equipo"
        holder.numero.text = "Nº ${item.driverNumber}"
        MediaHelper.loadFlexible(
            holder.img,
            item.photoUrl,
            MediaHelper.driverPhotoRes(item.code)
        )
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<DriverApi>) {
        items = newItems
        notifyDataSetChanged()
    }
}
