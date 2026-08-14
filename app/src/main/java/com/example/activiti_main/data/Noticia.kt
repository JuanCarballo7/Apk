package com.example.activiti_main.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "noticias")
data class Noticia(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val titulo: String,
    val etiqueta: String?,
    val destacada: Boolean,
    val colorFondo: String
)
