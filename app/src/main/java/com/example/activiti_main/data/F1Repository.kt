package com.example.activiti_main.data

import android.util.Log
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException

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

    /**
     * Tabla de posiciones actualizada: primero la API pública de Jolpica (datos en vivo);
     * si falla, la tabla cargada en Supabase. Los datos de carrera de cada piloto
     * (títulos, podios, foto) se completan desde la tabla drivers cuando están.
     */
    suspend fun getTabla(): TablaPosiciones {
        val enVivo = try {
            JolpicaApi.getTablaActual()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w("JA_APP", "[Posiciones en vivo] Jolpica no respondió, se usa Supabase", error)
            null
        }
        if (enVivo == null || enVivo.items.isEmpty()) {
            return TablaPosiciones(getStandings(), SEASON.toString())
        }

        val perfiles = runCatching { getDrivers() }.getOrDefault(emptyList()).associateBy { it.code }
        return enVivo.copy(items = enVivo.items.map { item ->
            val d = perfiles[item.code] ?: return@map item
            item.copy(
                driverId = d.id,
                photoUrl = d.photoUrl,
                championships = d.championships,
                wins = maxOf(d.wins, item.wins),
                podiums = d.podiums
            )
        })
    }

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
            podiums = d.podiums,
            birthDate = d.birthDate
        )
    }

    suspend fun getDriverById(id: Long): DriverApi? {
        if (!SupabaseProvider.isConfigured || id <= 0L) return null
        return runCatching {
            SupabaseProvider.client.from("drivers")
                .select(driverColumns) {
                    filter { eq("id", id) }
                }
                .decodeSingle<DriverApi>()
        }.getOrNull()
    }
}
