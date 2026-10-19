package com.example.activiti_main.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

object EntradasRepository {

    /** Entradas activas con precio y stock de Supabase, en el orden del catálogo local. */
    suspend fun getEntradas(): List<Entrada> {
        val rows = SupabaseProvider.client.from("tickets")
            .select { filter { eq("activo", true) } }
            .decodeList<TicketApi>()

        val orden = EntradasCatalogo.entradasLocales.map { it.id }
        return rows
            .sortedBy { t -> orden.indexOf(t.id).let { if (it < 0) Int.MAX_VALUE else it } }
            .map { it.toEntrada() }
    }

    /** Crea la orden en Supabase; precios y stock los valida la base, no la app. */
    suspend fun crearOrden(lineas: List<Pair<Entrada, Int>>): OrdenCreada {
        val params = buildJsonObject {
            putJsonArray("p_items") {
                lineas.forEach { (entrada, cantidad) ->
                    addJsonObject {
                        put("ticket_id", entrada.id)
                        put("cantidad", cantidad)
                    }
                }
            }
        }
        return SupabaseProvider.client.postgrest.rpc("crear_orden", params).decodeAs<OrdenCreada>()
    }

    fun TicketApi.toEntrada() = Entrada(
        id = id,
        nombre = nombre,
        zonaId = zoneId,
        categoria = runCatching { CategoriaEntrada.valueOf(categoria) }.getOrDefault(CategoriaEntrada.GENERAL),
        dias = dias,
        precio = precio,
        techada = techada,
        pantalla = pantalla,
        catering = catering,
        agotada = agotada
    )
}
