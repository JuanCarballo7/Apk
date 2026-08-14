package com.example.activiti_main.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clasificacion")
data class PilotoRanking(
    @PrimaryKey val posicion: Int,
    val nombre: String,
    val equipo: String,
    val puntos: Int,
    val colorEquipo: String,
    val tendencia: String // "same", "up", "down"
)
