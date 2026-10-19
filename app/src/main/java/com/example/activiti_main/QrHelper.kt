package com.example.activiti_main

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrHelper {

    /** Contenido del QR de una orden: lo que leería el control de acceso en el autódromo. */
    fun contenidoOrden(codigo: String, ordenId: Long) = "JA-GP26|$codigo|$ordenId"

    fun generar(texto: String, tamanoPx: Int): Bitmap {
        val matriz = QRCodeWriter().encode(
            texto,
            BarcodeFormat.QR_CODE,
            tamanoPx,
            tamanoPx,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1
            )
        )
        val pixeles = IntArray(tamanoPx * tamanoPx) { i ->
            if (matriz[i % tamanoPx, i / tamanoPx]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixeles, tamanoPx, tamanoPx, Bitmap.Config.ARGB_8888)
    }
}

/** Código y número de orden leídos de un QR del comprobante (o de un código tipeado a mano). */
data class QrOrden(val codigo: String, val ordenId: Long?)

/** Acepta "JA-GP26|GP26-123456|42" (QR del comprobante) o "GP26-123456" (tipeado). */
fun leerQrOrden(texto: String): QrOrden? {
    val limpio = texto.trim()
    val partes = limpio.split("|")
    if (partes.size == 3 && partes[0] == "JA-GP26") {
        return QrOrden(partes[1].uppercase(), partes[2].toLongOrNull())
    }
    val codigo = limpio.uppercase()
    return if (Regex("""GP26-\d{6}""").matches(codigo)) QrOrden(codigo, null) else null
}
