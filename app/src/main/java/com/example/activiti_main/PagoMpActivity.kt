package com.example.activiti_main

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Recibe el deep link jagp26://pago con el que Mercado Pago devuelve al usuario a la app
 * (trae external_reference = id de la orden) y lo lleva a Entradas, donde se verifica el pago.
 * No tiene pantalla propia: abre el Hub y se cierra.
 */
class PagoMpActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.getQueryParameter("external_reference")?.toLongOrNull()?.let {
            PagoMp.recordar(this, it)
        }
        startActivity(
            Intent(this, HubActivity::class.java)
                .putExtra(HubActivity.EXTRA_TAB, "entradas")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }
}
