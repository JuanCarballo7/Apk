package com.example.activiti_main.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Posiciones del campeonato en vivo desde Jolpica (sucesora pública de Ergast, sin API key).
 * Docs: https://github.com/jolpica/jolpica-f1
 */
object JolpicaApi {

    private const val BASE = "https://api.jolpi.ca/ergast/f1"
    private const val TIMEOUT_MS = 8_000

    private val json = Json { ignoreUnknownKeys = true }

    /** Tabla de la temporada en curso, con la tendencia calculada contra la fecha anterior. */
    suspend fun getTablaActual(): TablaPosiciones {
        val actual = getStandings("current")
            ?: throw IOException("Jolpica no devolvió posiciones para la temporada actual")

        val ronda = actual.round.toIntOrNull() ?: 0
        // Fecha anterior (para la tendencia) y nombre de la última carrera, en paralelo y opcionales.
        val (previa, carrera) = coroutineScope {
            val previa = async {
                if (ronda > 1) runCatching { getStandings("${actual.season}/${ronda - 1}") }.getOrNull() else null
            }
            val carrera = async { runCatching { getCarrera(actual.season, actual.round) }.getOrNull() }
            previa.await() to carrera.await()
        }
        val anteriores = previa?.driverStandings.orEmpty()
            .associate { it.driver.driverId to (it.position?.toIntOrNull() ?: Int.MAX_VALUE) }

        val items = actual.driverStandings.mapIndexed { i, s ->
            val posicion = s.position?.toIntOrNull() ?: (i + 1)
            val antes = anteriores[s.driver.driverId]
            val equipo = s.constructors.lastOrNull()
            ClasificacionItem(
                posicion = posicion,
                nombre = s.driver.familyName.uppercase(),
                equipo = nombreEquipo(equipo),
                puntos = s.points.toDoubleOrNull()?.toInt() ?: 0,
                colorEquipo = COLORES[equipo?.constructorId] ?: "#FFFFFF",
                tendencia = when {
                    antes == null || antes == posicion -> "same"
                    posicion < antes -> "up"
                    else -> "down"
                },
                firstName = s.driver.givenName,
                lastName = s.driver.familyName,
                driverNumber = s.driver.permanentNumber?.toIntOrNull() ?: 0,
                country = PAISES[s.driver.nationality] ?: s.driver.nationality.orEmpty(),
                code = s.driver.code ?: s.driver.familyName.take(3).uppercase(),
                wins = s.wins.toIntOrNull() ?: 0
            )
        }

        return TablaPosiciones(
            items = items,
            temporada = actual.season,
            ronda = ronda,
            ultimaCarrera = carrera?.let { nombreCarrera(it.raceName) }
        )
    }

    private suspend fun getStandings(ruta: String): StandingsList? =
        get<JolpicaRespuesta>("$BASE/$ruta/driverStandings.json?limit=40")
            .data.standingsTable?.standingsLists?.firstOrNull()

    private suspend fun getCarrera(temporada: String, ronda: String): Carrera? =
        get<JolpicaRespuesta>("$BASE/$temporada/$ronda/races.json")
            .data.raceTable?.races?.firstOrNull()

    private suspend inline fun <reified T> get(url: String): T = withContext(Dispatchers.IO) {
        val conexion = URL(url).openConnection() as HttpURLConnection
        try {
            conexion.connectTimeout = TIMEOUT_MS
            conexion.readTimeout = TIMEOUT_MS
            conexion.setRequestProperty("Accept", "application/json")
            val codigo = conexion.responseCode
            if (codigo !in 200..299) throw IOException("Jolpica respondió HTTP $codigo para $url")
            val cuerpo = conexion.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString<T>(cuerpo)
        } finally {
            conexion.disconnect()
        }
    }

    private fun nombreEquipo(c: ConstructorJ?): String = when (c?.constructorId) {
        null -> "Sin equipo"
        "red_bull" -> "Red Bull Racing"
        "rb" -> "Racing Bulls"
        else -> c.name.removeSuffix(" F1 Team")
    }

    /** "Singapore Grand Prix" → "GP de Singapur". */
    private fun nombreCarrera(raceName: String): String {
        val lugar = raceName.removeSuffix(" Grand Prix").trim()
        return "GP de " + (CARRERAS[lugar] ?: lugar)
    }

    private val COLORES = mapOf(
        "mercedes" to "#00D2BE",
        "ferrari" to "#E8002D",
        "mclaren" to "#FF8000",
        "red_bull" to "#3671C6",
        "williams" to "#64C4FF",
        "aston_martin" to "#229971",
        "alpine" to "#FF87BC",
        "rb" to "#6692FF",
        "haas" to "#B6BABD",
        "audi" to "#FF2D00",
        "sauber" to "#52E252",
        "cadillac" to "#C9A94D"
    )

    private val PAISES = mapOf(
        "Argentine" to "Argentina",
        "American" to "Estados Unidos",
        "Australian" to "Australia",
        "Austrian" to "Austria",
        "Belgian" to "Bélgica",
        "Brazilian" to "Brasil",
        "British" to "Reino Unido",
        "Canadian" to "Canadá",
        "Chinese" to "China",
        "Danish" to "Dinamarca",
        "Dutch" to "Países Bajos",
        "Finnish" to "Finlandia",
        "French" to "Francia",
        "German" to "Alemania",
        "Italian" to "Italia",
        "Japanese" to "Japón",
        "Mexican" to "México",
        "Monegasque" to "Mónaco",
        "New Zealander" to "Nueva Zelanda",
        "Polish" to "Polonia",
        "Spanish" to "España",
        "Swiss" to "Suiza",
        "Thai" to "Tailandia"
    )

    private val CARRERAS = mapOf(
        "Australian" to "Australia",
        "Chinese" to "China",
        "Japanese" to "Japón",
        "Bahrain" to "Baréin",
        "Saudi Arabian" to "Arabia Saudita",
        "Emilia Romagna" to "Emilia-Romaña",
        "Monaco" to "Mónaco",
        "Spanish" to "España",
        "Barcelona-Catalunya" to "Barcelona",
        "Canadian" to "Canadá",
        "Austrian" to "Austria",
        "British" to "Gran Bretaña",
        "Belgian" to "Bélgica",
        "Hungarian" to "Hungría",
        "Dutch" to "Países Bajos",
        "Italian" to "Italia",
        "Azerbaijan" to "Azerbaiyán",
        "Singapore" to "Singapur",
        "United States" to "Estados Unidos",
        "Mexico City" to "Ciudad de México",
        "São Paulo" to "San Pablo",
        "Qatar" to "Catar",
        "Argentine" to "Argentina"
    )
}

/** Tabla lista para mostrar, con datos de cuándo se actualizó. */
data class TablaPosiciones(
    val items: List<ClasificacionItem>,
    val temporada: String,
    val ronda: Int = 0,
    val ultimaCarrera: String? = null
)

@Serializable
private data class JolpicaRespuesta(@SerialName("MRData") val data: MRData)

@Serializable
private data class MRData(
    @SerialName("StandingsTable") val standingsTable: StandingsTable? = null,
    @SerialName("RaceTable") val raceTable: RaceTable? = null
)

@Serializable
private data class StandingsTable(
    @SerialName("StandingsLists") val standingsLists: List<StandingsList> = emptyList()
)

@Serializable
private data class StandingsList(
    val season: String,
    val round: String,
    @SerialName("DriverStandings") val driverStandings: List<DriverStanding> = emptyList()
)

@Serializable
private data class DriverStanding(
    val position: String? = null,
    val points: String = "0",
    val wins: String = "0",
    @SerialName("Driver") val driver: DriverJ,
    @SerialName("Constructors") val constructors: List<ConstructorJ> = emptyList()
)

@Serializable
private data class DriverJ(
    val driverId: String,
    val permanentNumber: String? = null,
    val code: String? = null,
    val givenName: String = "",
    val familyName: String = "",
    val nationality: String? = null
)

@Serializable
private data class ConstructorJ(
    val constructorId: String,
    val name: String = ""
)

@Serializable
private data class RaceTable(
    @SerialName("Races") val races: List<Carrera> = emptyList()
)

@Serializable
private data class Carrera(val raceName: String = "")
