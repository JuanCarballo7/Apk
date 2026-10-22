package com.example.activiti_main

import android.util.Log
import io.github.jan.supabase.exceptions.BadRequestRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.NotFoundRestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.exceptions.UnauthorizedRestException
import java.io.IOException

/** Etiqueta para filtrar en Logcat: `tag:JA_APP`. */
private const val TAG = "JA_APP"

/**
 * Registra en Logcat el detalle técnico del error (tipo, mensaje y stack trace)
 * y devuelve un mensaje simple para mostrarle al usuario.
 */
internal fun Throwable.paraUsuario(accion: String): String {
    registrar(accion)
    return mensajeAmigable()
}

/** Solo deja el detalle técnico en Logcat, sin mostrar nada al usuario. */
internal fun Throwable.registrar(accion: String) {
    val detalle = when (this) {
        is RestException -> "HTTP $statusCode · error=$error · descripcion=$description"
        else -> message.orEmpty()
    }
    // El mensaje de RestException incluye los headers del request (token de sesión y apikey):
    // se loguea solo el stack trace, nunca ese mensaje.
    val traza = if (this is RestException) {
        Throwable("${this::class.java.simpleName} (headers ocultos)").also { it.stackTrace = stackTrace }
    } else {
        this
    }
    Log.e(TAG, "[$accion] ${this::class.java.name}: $detalle", traza)
}

/** Traduce el error a un texto corto y sin jerga técnica. */
internal fun Throwable.mensajeAmigable(): String {
    val raw = message.orEmpty()
    return when {
        raw.contains("Invalid login credentials", ignoreCase = true) ->
            "Email o contraseña incorrectos."
        raw.contains("Email not confirmed", ignoreCase = true) ->
            "Primero confirmá tu email desde el mensaje que te enviamos."
        raw.contains("User already registered", ignoreCase = true) ->
            "Ya existe una cuenta con ese email."
        raw.contains("Password should be", ignoreCase = true) ->
            "La contraseña debe tener al menos 8 caracteres."
        raw.contains("rate limit", ignoreCase = true) ->
            "Hiciste demasiados intentos. Esperá unos minutos y probá de nuevo."
        raw.contains("SIN_STOCK") ->
            "Una de las entradas de tu carrito se agotó. Revisá el carrito e intentá de nuevo."
        raw.contains("NO_DISPONIBLE") ->
            "Una de las entradas de tu carrito ya no está a la venta."
        raw.contains("SALDO_INSUFICIENTE") ->
            "No tenés saldo suficiente. Pagá con Mercado Pago o pedile a un administrador que te cargue saldo."
        raw.contains("MP_NO_CONFIGURADO") || raw.contains("MP_NO_DISPONIBLE") ->
            "Mercado Pago no está disponible en este momento. Probá de nuevo en unos minutos o pagá con saldo."
        raw.contains("MONTO_INVALIDO") ->
            "Ingresá un monto válido."
        raw.contains("ROL_PROPIO") ->
            "No podés cambiar tu propio rol."
        raw.contains("NO_AUTORIZADO") || raw.contains("row-level security", ignoreCase = true) ||
            raw.contains("permission denied", ignoreCase = true) ->
            "No tenés permiso para hacer esto."
        this is HttpRequestException || this is IOException ||
            raw.contains("network", ignoreCase = true) ||
            raw.contains("timeout", ignoreCase = true) ->
            "No se pudo conectar. Revisá tu conexión a internet."
        this is UnauthorizedRestException ->
            "Tu sesión venció. Volvé a iniciar sesión."
        this is NotFoundRestException ->
            "No encontramos la información que buscabas."
        this is BadRequestRestException ->
            "Revisá los datos ingresados e intentá de nuevo."
        else -> "Ocurrió un error. Intentá nuevamente."
    }
}
