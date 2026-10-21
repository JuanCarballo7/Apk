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
    val podiums: Int = 0,
    val birthDate: String? = null
)

@Serializable
data class TicketApi(
    val id: String,
    @SerialName("zone_id") val zoneId: String,
    val nombre: String,
    val categoria: String,
    val dias: String,
    val precio: Double,
    val stock: Int = 0,
    val techada: Boolean = false,
    val pantalla: Boolean = false,
    val catering: Boolean = false,
    val activo: Boolean = true
) {
    val agotada: Boolean get() = stock <= 0
}

/** Campos editables de una noticia desde el panel admin (sin valores por defecto: siempre se envían todos). */
@Serializable
data class NewsInput(
    val title: String,
    val summary: String?,
    @SerialName("image_url") val imageUrl: String?,
    val badge: String?,
    val featured: Boolean,
    val published: Boolean
)

@Serializable
data class UsuarioAdmin(
    val id: String,
    val email: String? = null,
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    val role: String = "user"
)

@Serializable
data class OrdenCreada(
    val id: Long,
    val codigo: String,
    val total: Double
)

@Serializable
data class OrdenItemApi(
    @SerialName("ticket_id") val ticketId: String,
    val nombre: String,
    val cantidad: Int,
    @SerialName("precio_unitario") val precioUnitario: Double
)

@Serializable
data class OrdenApi(
    val id: Long,
    val codigo: String,
    val total: Double,
    val estado: String = "confirmada",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("order_items") val items: List<OrdenItemApi> = emptyList()
) {
    val cantidadEntradas: Int get() = items.sumOf { it.cantidad }
}

@Serializable
data class ValidacionItem(val nombre: String, val cantidad: Int)

/** Respuesta de la RPC validar_entrada (control de ingreso con QR). */
@Serializable
data class ValidacionApi(
    val resultado: String,
    val registrada: Boolean = false,
    @SerialName("orden_id") val ordenId: Long? = null,
    val codigo: String = "",
    val total: Double = 0.0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("usada_at") val usadaAt: String? = null,
    val comprador: String? = null,
    val email: String? = null,
    val items: List<ValidacionItem> = emptyList()
)
