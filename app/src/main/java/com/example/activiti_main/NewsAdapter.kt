package com.example.activiti_main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.activiti_main.data.NewsApi

class NewsAdapter(
    private var items: List<NewsApi> = emptyList(),
    private val onClick: (NewsApi) -> Unit = {}
) : RecyclerView.Adapter<NewsAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.imgNews)
        val badge: TextView = view.findViewById(R.id.txtNewsBadge)
        val title: TextView = view.findViewById(R.id.txtNewsTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_noticia, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        if (item.badge.isNullOrBlank()) {
            holder.badge.visibility = View.GONE
        } else {
            holder.badge.visibility = View.VISIBLE
            holder.badge.text = item.badge
        }
        MediaHelper.loadFlexible(
            holder.img,
            item.imageUrl,
            MediaHelper.newsFallbackRes(position)
        )
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<NewsApi>) {
        items = newItems
        notifyDataSetChanged()
    }
}
