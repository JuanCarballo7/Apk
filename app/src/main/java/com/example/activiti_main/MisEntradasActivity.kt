package com.example.activiti_main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.activiti_main.data.EntradasRepository
import com.example.activiti_main.data.OrdenApi
import com.example.activiti_main.data.formatearArs
import kotlinx.coroutines.launch

/** Compras del usuario logueado; cada una abre su comprobante con QR. */
class MisEntradasActivity : AppCompatActivity() {

    private val adapter = OrdenesAdapter { startActivity(ComprobanteActivity.intent(this, it.id)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mis_entradas)

        findViewById<View>(R.id.headerMisEntradas).applyTopSystemBarPadding()
        findViewById<View>(R.id.btnMisEntradasBack).setOnClickListener { finish() }
        findViewById<RecyclerView>(R.id.recyclerMisEntradas).apply {
            layoutManager = LinearLayoutManager(this@MisEntradasActivity)
            adapter = this@MisEntradasActivity.adapter
            applyBottomSystemBarPadding()
        }
    }

    override fun onResume() {
        super.onResume()
        cargar()
    }

    private fun cargar() {
        val progress = findViewById<View>(R.id.progressMisEntradas)
        val vacio = findViewById<TextView>(R.id.txtMisEntradasVacio)
        lifecycleScope.launch {
            progress.visibility = View.VISIBLE
            try {
                val ordenes = EntradasRepository.getMisOrdenes()
                adapter.submit(ordenes)
                vacio.visibility = if (ordenes.isEmpty()) View.VISIBLE else View.GONE
            } catch (error: Exception) {
                vacio.text = "No se pudieron cargar tus compras."
                vacio.visibility = View.VISIBLE
                AppDialog.error(this@MisEntradasActivity, error.paraUsuario("Cargar mis entradas"))
            } finally {
                progress.visibility = View.GONE
            }
        }
    }
}

private class OrdenesAdapter(
    private val onClick: (OrdenApi) -> Unit
) : RecyclerView.Adapter<OrdenesAdapter.VH>() {

    private var items: List<OrdenApi> = emptyList()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val fila: View = view.findViewById(R.id.filaOrden)
        val codigo: TextView = view.findViewById(R.id.txtOrdenCodigo)
        val detalle: TextView = view.findViewById(R.id.txtOrdenDetalle)
        val total: TextView = view.findViewById(R.id.txtOrdenTotal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_orden, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val o = items[position]
        val cantidad = o.cantidadEntradas
        holder.codigo.text = o.codigo
        holder.detalle.text =
            "${if (cantidad == 1) "1 entrada" else "$cantidad entradas"} · ${formatearFecha(o.createdAt)}" +
                (if (o.estado == "confirmada") "" else " · ${estadoLegible(o.estado)}")
        holder.total.text = formatearArs(o.total)
        holder.fila.setOnClickListener { onClick(o) }
    }

    override fun getItemCount() = items.size

    fun submit(nuevos: List<OrdenApi>) {
        items = nuevos
        notifyDataSetChanged()
    }
}
