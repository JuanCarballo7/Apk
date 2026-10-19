package com.example.activiti_main.data

import android.graphics.PointF
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.hypot

/** Sector del autódromo dibujado en el mapa como polígono (coordenadas del plano 547x365), dentro de uno de los 3 sectores de pista. */
data class ZonaCircuito(val id: String, val nombre: String, val sector: Int, val puntos: List<PointF>)

/** Tribuna rectangular de [grosor] a lo largo del segmento (x1,y1)-(x2,y2). */
private fun banda(x1: Float, y1: Float, x2: Float, y2: Float, grosor: Float = 6f): List<PointF> {
    val largo = hypot(x2 - x1, y2 - y1)
    val nx = -(y2 - y1) / largo * grosor / 2
    val ny = (x2 - x1) / largo * grosor / 2
    return listOf(
        PointF(x1 + nx, y1 + ny), PointF(x2 + nx, y2 + ny),
        PointF(x2 - nx, y2 - ny), PointF(x1 - nx, y1 - ny)
    )
}

private fun poligono(vararg xy: Float): List<PointF> =
    xy.toList().chunked(2) { (x, y) -> PointF(x, y) }

enum class CategoriaEntrada(val titulo: String) {
    TRIBUNA("Tribunas"),
    GENERAL("General"),
    HOSPITALITY("Hospitality")
}

data class Entrada(
    val id: String,
    val nombre: String,
    val zonaId: String,
    val categoria: CategoriaEntrada,
    val dias: String,
    val precio: Double,
    val techada: Boolean = false,
    val pantalla: Boolean = false,
    val catering: Boolean = false,
    val agotada: Boolean = false
)

object EntradasCatalogo {
    const val ZONA_PRINCIPAL = "principal"
    const val ZONA_PADDOCK = "paddock"
    const val ZONA_CURVA1 = "curva1"
    const val ZONA_CURVA4 = "curva4"
    const val ZONA_CURVA6 = "curva6"
    const val ZONA_HORQUILLA = "horquilla"
    const val ZONA_CURVA13 = "curva13"
    const val ZONA_CURVA14 = "curva14"
    const val ZONA_CAMPO = "campo"

    private const val TRES_DIAS = "VIERNES - DOMINGO"
    private const val DOMINGO = "DOMINGO"

    val zonas = listOf(
        ZonaCircuito(ZONA_PRINCIPAL, "Tribuna Principal", 1, banda(200f, 123f, 330f, 104f)),
        ZonaCircuito(ZONA_PADDOCK, "Paddock Club", 1, poligono(170f, 152f, 352f, 130f, 352f, 160f, 225f, 176f)),
        ZonaCircuito(ZONA_CURVA1, "Tribuna Curva 1", 1, banda(360f, 160f, 390f, 160f)),
        ZonaCircuito(ZONA_CURVA4, "Tribuna Curva 4", 1, banda(455f, 44f, 512f, 46f)),
        ZonaCircuito(ZONA_CURVA6, "Tribuna Curva 6", 2, banda(447f, 152f, 432f, 172f)),
        ZonaCircuito(ZONA_HORQUILLA, "Tribuna Horquilla", 3, banda(357f, 232f, 357f, 268f)),
        ZonaCircuito(ZONA_CURVA13, "Tribuna Curva 13", 3, banda(222f, 272f, 255f, 300f)),
        ZonaCircuito(ZONA_CURVA14, "Tribuna Curva 14", 3, banda(14f, 78f, 22f, 112f)),
        ZonaCircuito(ZONA_CAMPO, "Campo General", 2, poligono(225f, 205f, 318f, 205f, 318f, 232f, 232f, 230f))
    )

    val entradas = listOf(
        Entrada("principal-3d", "Tribuna Principal 3-Days", ZONA_PRINCIPAL, CategoriaEntrada.TRIBUNA, TRES_DIAS, 1_850_000.0, techada = true, pantalla = true),
        Entrada("principal-dom", "Tribuna Principal Domingo", ZONA_PRINCIPAL, CategoriaEntrada.TRIBUNA, DOMINGO, 1_150_000.0, techada = true, pantalla = true),
        Entrada("curva1-3d", "Tribuna Curva 1 3-Days", ZONA_CURVA1, CategoriaEntrada.TRIBUNA, TRES_DIAS, 1_290_000.0, pantalla = true),
        Entrada("curva4-3d", "Tribuna Curva 4 3-Days", ZONA_CURVA4, CategoriaEntrada.TRIBUNA, TRES_DIAS, 1_090_000.0, pantalla = true),
        Entrada("curva6-3d", "Tribuna Curva 6 3-Days", ZONA_CURVA6, CategoriaEntrada.TRIBUNA, TRES_DIAS, 980_000.0, pantalla = true),
        Entrada("horquilla-3d", "Tribuna Horquilla 3-Days", ZONA_HORQUILLA, CategoriaEntrada.TRIBUNA, TRES_DIAS, 890_000.0, agotada = true),
        Entrada("curva13-3d", "Tribuna Curva 13 3-Days", ZONA_CURVA13, CategoriaEntrada.TRIBUNA, TRES_DIAS, 860_000.0, pantalla = true),
        Entrada("curva14-3d", "Tribuna Curva 14 3-Days", ZONA_CURVA14, CategoriaEntrada.TRIBUNA, TRES_DIAS, 820_000.0),
        Entrada("campo-3d", "Campo General 3-Days", ZONA_CAMPO, CategoriaEntrada.GENERAL, TRES_DIAS, 450_000.0, pantalla = true),
        Entrada("campo-dom", "Campo General Domingo", ZONA_CAMPO, CategoriaEntrada.GENERAL, DOMINGO, 290_000.0, pantalla = true),
        Entrada("paddock-club-3d", "Paddock Club 3-Days", ZONA_PADDOCK, CategoriaEntrada.HOSPITALITY, TRES_DIAS, 20_419_762.97, techada = true, pantalla = true, catering = true),
        Entrada("champions-club-3d", "Champions Club - Recta Principal", ZONA_PRINCIPAL, CategoriaEntrada.HOSPITALITY, TRES_DIAS, 4_555_526.62, techada = true, pantalla = true, catering = true, agotada = true),
        Entrada("terraza-curva4-3d", "Terraza Hospitality Curva 4", ZONA_CURVA4, CategoriaEntrada.HOSPITALITY, TRES_DIAS, 6_980_000.0, techada = true, pantalla = true, catering = true)
    )

    fun zona(id: String): ZonaCircuito? = zonas.firstOrNull { it.id == id }

    fun sectorDe(entrada: Entrada): Int = zona(entrada.zonaId)?.sector ?: 1

    fun curvasDelSector(sector: Int): String = when (sector) {
        1 -> "CURVAS 1-4"
        2 -> "CURVAS 5-10"
        else -> "CURVAS 11-15"
    }

    fun entrada(id: String): Entrada? = entradas.firstOrNull { it.id == id }
}

/** Carrito en memoria: vive mientras la app esté abierta. */
object Carrito {
    const val MAX_POR_ENTRADA = 10

    private val items = linkedMapOf<String, Int>()

    fun agregar(entrada: Entrada, cantidad: Int) {
        val actual = items[entrada.id] ?: 0
        items[entrada.id] = (actual + cantidad).coerceAtMost(MAX_POR_ENTRADA)
    }

    fun lineas(): List<Pair<Entrada, Int>> =
        items.mapNotNull { (id, cant) -> EntradasCatalogo.entrada(id)?.let { it to cant } }

    fun cantidadTotal(): Int = items.values.sum()

    fun total(): Double = lineas().sumOf { (e, cant) -> e.precio * cant }

    fun vaciar() = items.clear()
}

private val formatoArs = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.forLanguageTag("es-AR")))

fun formatearArs(monto: Double): String = "$" + formatoArs.format(monto)
