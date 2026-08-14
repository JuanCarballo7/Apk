package com.example.activiti_main

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DetallePilotoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalle_piloto)

        findViewById<android.view.View>(R.id.detalleRoot).applyTopSystemBarPadding()

        val imgPiloto = findViewById<ImageView>(R.id.imgDetallePiloto)
        val txtEquipo = findViewById<TextView>(R.id.txtEquipoDetalle)
        val txtNombre = findViewById<TextView>(R.id.txtNombreDetalle)
        val txtInfo = findViewById<TextView>(R.id.txtInfoDetalle)
        val txtCampeonatos = findViewById<TextView>(R.id.txtCampeonatos)
        val txtVictorias = findViewById<TextView>(R.id.txtVictorias)
        val txtPodios = findViewById<TextView>(R.id.txtPodios)
        val txtRecord = findViewById<TextView>(R.id.txtRecord)

        val code = intent.getStringExtra(EXTRA_CODE).orEmpty()
        val fotoRes = intent.getIntExtra(EXTRA_FOTO, MediaHelper.driverPhotoRes(code.ifBlank { "VER" }))
        val fotoUrl = intent.getStringExtra(EXTRA_FOTO_URL)
        val equipo = intent.getStringExtra(EXTRA_EQUIPO) ?: "RED BULL RACING"
        val nombre = intent.getStringExtra(EXTRA_NOMBRE) ?: "MAX\nVERSTAPPEN"
        val info = intent.getStringExtra(EXTRA_INFO) ?: "#1 · Países Bajos"
        val campeonatos = intent.getStringExtra(EXTRA_CAMPEONATOS) ?: "4"
        val victorias = intent.getStringExtra(EXTRA_VICTORIAS) ?: "62"
        val podios = intent.getStringExtra(EXTRA_PODIOS) ?: "109"
        val record = intent.getStringExtra(EXTRA_RECORD)
            ?: "Datos desde API Supabase"

        MediaHelper.loadFlexible(imgPiloto, fotoUrl, fotoRes)
        txtEquipo.text = equipo
        txtNombre.text = nombre
        txtInfo.text = info
        txtCampeonatos.text = campeonatos
        txtVictorias.text = victorias
        txtPodios.text = podios
        txtRecord.text = record
    }

    companion object {
        const val EXTRA_FOTO = "foto"
        const val EXTRA_FOTO_URL = "foto_url"
        const val EXTRA_CODE = "code"
        const val EXTRA_EQUIPO = "equipo"
        const val EXTRA_NOMBRE = "nombre"
        const val EXTRA_INFO = "info"
        const val EXTRA_CAMPEONATOS = "campeonatos"
        const val EXTRA_VICTORIAS = "victorias"
        const val EXTRA_PODIOS = "podios"
        const val EXTRA_RECORD = "record"
    }
}
