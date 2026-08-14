package com.example.activiti_main

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.activiti_main.data.DriverApi
import com.example.activiti_main.data.F1Repository
import com.example.activiti_main.data.TeamEmbed
import kotlinx.coroutines.launch

class PilotosActivity : AppCompatActivity() {

    private lateinit var adapter: PilotosAdapter
    private lateinit var txtEstado: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pilotos)

        findViewById<android.view.View>(R.id.pilotosRoot).applyTopSystemBarPadding()

        txtEstado = findViewById(R.id.txtPilotosEstado)
        adapter = PilotosAdapter(onClick = ::abrirDetalle)
        findViewById<RecyclerView>(R.id.recyclerPilotos).apply {
            layoutManager = LinearLayoutManager(this@PilotosActivity)
            adapter = this@PilotosActivity.adapter
        }

        cargarPilotos()
    }

    private fun cargarPilotos() {
        lifecycleScope.launch {
            try {
                val drivers = F1Repository.getDrivers()
                if (drivers.isNotEmpty()) {
                    adapter.submitList(drivers)
                    txtEstado.text = "${drivers.size} pilotos · API Supabase"
                } else {
                    adapter.submitList(driversFallback())
                    txtEstado.text = "Datos locales (API vacía)"
                }
            } catch (e: Exception) {
                adapter.submitList(driversFallback())
                txtEstado.text = "Datos locales (API no disponible)"
                Toast.makeText(this@PilotosActivity, e.message ?: "Error API", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun abrirDetalle(driver: DriverApi) {
        val intent = Intent(this, DetallePilotoActivity::class.java).apply {
            putExtra(DetallePilotoActivity.EXTRA_EQUIPO, driver.teamName())
            putExtra(DetallePilotoActivity.EXTRA_NOMBRE, driver.detailName())
            putExtra(DetallePilotoActivity.EXTRA_INFO, driver.infoLine())
            putExtra(DetallePilotoActivity.EXTRA_CAMPEONATOS, driver.championships.toString())
            putExtra(DetallePilotoActivity.EXTRA_VICTORIAS, driver.wins.toString())
            putExtra(DetallePilotoActivity.EXTRA_PODIOS, driver.podiums.toString())
            putExtra(
                DetallePilotoActivity.EXTRA_RECORD,
                "${driver.teams?.name ?: "Sin equipo"} · #${driver.driverNumber}"
            )
            putExtra(DetallePilotoActivity.EXTRA_FOTO, MediaHelper.driverPhotoRes(driver.code))
            putExtra(DetallePilotoActivity.EXTRA_FOTO_URL, driver.photoUrl)
            putExtra(DetallePilotoActivity.EXTRA_CODE, driver.code)
        }
        startActivity(intent)
    }

    private fun driversFallback(): List<DriverApi> = listOf(
        DriverApi(6, "Max", "Verstappen", "VER", 1, "Países Bajos", championships = 4, wins = 62, podiums = 109, teams = TeamEmbed("Red Bull Racing", "#3671C6")),
        DriverApi(4, "Charles", "Leclerc", "LEC", 16, "Mónaco", wins = 8, podiums = 43, teams = TeamEmbed("Ferrari", "#E8002D")),
        DriverApi(2, "Lewis", "Hamilton", "HAM", 44, "Reino Unido", championships = 7, wins = 105, podiums = 202, teams = TeamEmbed("Ferrari", "#E8002D")),
        DriverApi(1, "Kimi", "Antonelli", "ANT", 12, "Italia", teams = TeamEmbed("Mercedes", "#00D2BE"))
    )
}
