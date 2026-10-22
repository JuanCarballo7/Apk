package com.example.activiti_main.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
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

    // ---------- Mercado Pago (Edge Function mp-crear-pago) ----------

    private val json = Json { ignoreUnknownKeys = true }

    /** Reserva el stock en una orden pendiente y devuelve el link de Checkout Pro. */
    suspend fun crearPagoMp(lineas: List<Pair<Entrada, Int>>): PagoMpCreado {
        val respuesta = SupabaseProvider.client.functions.invoke(
            function = "mp-crear-pago",
            body = buildJsonObject {
                putJsonArray("items") {
                    lineas.forEach { (entrada, cantidad) ->
                        addJsonObject {
                            put("ticket_id", entrada.id)
                            put("cantidad", cantidad)
                        }
                    }
                }
            }
        )
        return json.decodeFromString(respuesta.bodyAsText())
    }

    /** Pregunta a Mercado Pago si la orden ya se pagó; devuelve "pendiente", "confirmada" o "cancelada". */
    suspend fun verificarPagoMp(ordenId: Long): String {
        val respuesta = SupabaseProvider.client.functions.invoke(
            function = "mp-crear-pago",
            body = buildJsonObject {
                put("accion", "verificar")
                put("orden_id", ordenId)
            }
        )
        return json.decodeFromString<EstadoPagoMp>(respuesta.bodyAsText()).estado
    }

    /** Abandona un pago de Mercado Pago que no se hizo: la orden se cancela y se libera el stock. */
    suspend fun cancelarPagoMp(ordenId: Long) {
        SupabaseProvider.client.postgrest.rpc(
            "cancelar_mi_orden_mp",
            buildJsonObject { put("p_order_id", ordenId) }
        )
    }

    private val columnasOrden = Columns.raw(
        "id, codigo, total, estado, created_at, order_items ( ticket_id, nombre, cantidad, precio_unitario )"
    )

    /** Una orden del usuario logueado (la RLS solo deja leer las propias). */
    suspend fun getOrden(id: Long): OrdenApi =
        SupabaseProvider.client.from("orders")
            .select(columnasOrden) { filter { eq("id", id) } }
            .decodeSingle<OrdenApi>()

    /** Órdenes del usuario logueado, la más reciente primero. */
    suspend fun getMisOrdenes(): List<OrdenApi> {
        val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: return emptyList()
        return SupabaseProvider.client.from("orders")
            .select(columnasOrden) {
                filter { eq("user_id", userId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<OrdenApi>()
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
