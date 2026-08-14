package com.example.activiti_main.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TeamEmbed(
    val name: String,
    @SerialName("color_hex") val colorHex: String = "#FFFFFF"
)

@Serializable
data class DriverEmbed(
    val id: Long,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    val code: String,
    @SerialName("driver_number") val driverNumber: Int,
    val country: String,
    @SerialName("birth_date") val birthDate: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    val championships: Int = 0,
    val wins: Int = 0,
    val podiums: Int = 0,
    val teams: TeamEmbed? = null
)

@Serializable
data class StandingApi(
    val position: Int,
    val points: Double,
    val trend: String = "same",
    @SerialName("season_year") val seasonYear: Int,
    val drivers: DriverEmbed
)

@Serializable
data class DriverApi(
    val id: Long,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    val code: String,
    @SerialName("driver_number") val driverNumber: Int,
    val country: String,
    @SerialName("birth_date") val birthDate: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    val championships: Int = 0,
    val wins: Int = 0,
    val podiums: Int = 0,
    val active: Boolean = true,
    val teams: TeamEmbed? = null
)

@Serializable
data class NewsApi(
    val id: Long,
    val title: String,
    val summary: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val badge: String? = null,
    val featured: Boolean = false,
    val published: Boolean = true,
    val category: String = "F1"
)

/** Modelo de UI para la tabla de posiciones (incluye datos para el detalle). */
data class ClasificacionItem(
    val posicion: Int,
    val nombre: String,
    val equipo: String,
    val puntos: Int,
    val colorEquipo: String,
    val tendencia: String,
    val driverId: Long = 0,
    val firstName: String = "",
    val lastName: String = "",
    val driverNumber: Int = 0,
    val country: String = "",
    val photoUrl: String? = null,
    val code: String = "",
    val championships: Int = 0,
    val wins: Int = 0,
    val podiums: Int = 0
)
