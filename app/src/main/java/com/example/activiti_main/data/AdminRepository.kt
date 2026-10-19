package com.example.activiti_main.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Excepción para cuando la base no aplicó un cambio (por ejemplo, la RLS lo rechazó). */
class SinPermisoException(accion: String) : Exception("NO_AUTORIZADO: $accion")

object AdminRepository {

    private val client get() = SupabaseProvider.client

    /** Perfil del usuario logueado (incluye el rol), o null si no hay sesión. */
    suspend fun perfilActual(): ProfileApi? {
        client.auth.awaitInitialization()
        val user = client.auth.currentUserOrNull() ?: return null
        return client.from("profiles")
            .select { filter { eq("id", user.id) } }
            .decodeSingle<ProfileApi>()
    }

    // ---------- Entradas ----------

    suspend fun getTickets(): List<TicketApi> {
        val orden = EntradasCatalogo.entradasLocales.map { it.id }
        return client.from("tickets").select().decodeList<TicketApi>()
            .sortedBy { t -> orden.indexOf(t.id).let { if (it < 0) Int.MAX_VALUE else it } }
    }

    suspend fun actualizarTicket(id: String, precio: Double? = null, stock: Int? = null, activo: Boolean? = null): TicketApi {
        val filas = client.from("tickets").update(
            {
                precio?.let { set("precio", it) }
                stock?.let { set("stock", it) }
                activo?.let { set("activo", it) }
            }
        ) {
            select()
            filter { eq("id", id) }
        }.decodeList<TicketApi>()
        return filas.firstOrNull() ?: throw SinPermisoException("actualizar ticket $id")
    }

    // ---------- Noticias ----------

    suspend fun getNoticias(): List<NewsApi> =
        client.from("news").select {
            order("published_at", Order.DESCENDING)
        }.decodeList<NewsApi>()

    suspend fun crearNoticia(datos: NewsInput): NewsApi =
        client.from("news").insert(datos) { select() }.decodeSingle<NewsApi>()

    suspend fun actualizarNoticia(id: Long, datos: NewsInput): NewsApi {
        val filas = client.from("news").update(datos) {
            select()
            filter { eq("id", id) }
        }.decodeList<NewsApi>()
        return filas.firstOrNull() ?: throw SinPermisoException("actualizar noticia $id")
    }

    // ---------- Control de ingreso (solo superadmin) ----------

    /** Valida un QR en la base; con [registrar] también marca el ingreso (una sola vez por orden). */
    suspend fun validarEntrada(codigo: String, ordenId: Long?, registrar: Boolean): ValidacionApi =
        client.postgrest.rpc(
            "validar_entrada",
            buildJsonObject {
                put("p_codigo", codigo)
                put("p_orden_id", ordenId)
                put("p_registrar", registrar)
            }
        ).decodeAs<ValidacionApi>()

    // ---------- Usuarios (solo superadmin) ----------

    suspend fun getUsuarios(): List<UsuarioAdmin> =
        client.postgrest.rpc("admin_listar_usuarios").decodeList<UsuarioAdmin>()

    suspend fun cambiarRol(userId: String, rol: String) {
        client.postgrest.rpc(
            "admin_cambiar_rol",
            buildJsonObject {
                put("p_user_id", userId)
                put("p_role", rol)
            }
        )
    }
}
