package com.example.activiti_main.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

object F1Repository {

    private const val SEASON = 2026

    private val standingColumns = Columns.raw(
        """
        position, points, trend, season_year,
        drivers (
          id, first_name, last_name, code, driver_number, country,
          birth_date, photo_url, championships, wins, podiums,
          teams ( name, color_hex )
        )
        """.trimIndent()
    )

    private val driverColumns = Columns.raw(
        """
        id, first_name, last_name, code, driver_number, country,
        birth_date, photo_url, championships, wins, podiums, active,
        teams ( name, color_hex )
        """.trimIndent()
    )

    suspend fun getStandings(seasonYear: Int = SEASON): List<ClasificacionItem> {
        if (!SupabaseProvider.isConfigured) return emptyList()

        val rows = SupabaseProvider.client.from("standings")
            .select(standingColumns) {
                filter { eq("season_year", seasonYear) }
                order("position", Order.ASCENDING)
            }
            .decodeList<StandingApi>()

        return rows.map { it.toClasificacionItem() }
    }

    suspend fun getDrivers(): List<DriverApi> {
        if (!SupabaseProvider.isConfigured) return emptyList()

        return SupabaseProvider.client.from("drivers")
            .select(driverColumns) {
                filter { eq("active", true) }
                order("last_name", Order.ASCENDING)
            }
            .decodeList<DriverApi>()
    }

    suspend fun getNews(): List<NewsApi> {
        if (!SupabaseProvider.isConfigured) return emptyList()

        return SupabaseProvider.client.from("news")
            .select {
                filter { eq("published", true) }
                order("featured", Order.DESCENDING)
                order("published_at", Order.DESCENDING)
            }
            .decodeList<NewsApi>()
    }

    private fun StandingApi.toClasificacionItem(): ClasificacionItem {
        val d = drivers
        val team = d.teams
        return ClasificacionItem(
            posicion = position,
            nombre = d.lastName.uppercase(),
            equipo = team?.name ?: "Sin equipo",
            puntos = points.toInt(),
            colorEquipo = team?.colorHex ?: "#FFFFFF",
            tendencia = trend,
            driverId = d.id,
            firstName = d.firstName,
            lastName = d.lastName,
            driverNumber = d.driverNumber,
            country = d.country,
            photoUrl = d.photoUrl,
            code = d.code,
            championships = d.championships,
            wins = d.wins,
            podiums = d.podiums
        )
    }
}
