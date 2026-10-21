package com.example.activiti_main

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.activiti_main.data.SupabaseProvider
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<android.view.View>(R.id.headerBar).applyTopSystemBarPadding()

        comprobarAcceso()
        findViewById<android.view.View>(R.id.btnProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<TextView>(R.id.tabNews).setOnClickListener {
            abrirHub("news")
        }
        findViewById<TextView>(R.id.tabTablas).setOnClickListener {
            abrirHub("tablas")
        }
        findViewById<TextView>(R.id.tabEntradas).setOnClickListener {
            abrirHub("entradas")
        }
        findViewById<TextView>(R.id.tabReaccion).setOnClickListener {
            abrirHub("reaccion")
        }
        findViewById<TextView>(R.id.tabNosotros).setOnClickListener {
            // Ya estamos en Nosotros
        }

        findViewById<LinearLayout>(R.id.cardIrNews).setOnClickListener {
            abrirHub("news")
        }
        findViewById<LinearLayout>(R.id.cardIrTablas).setOnClickListener {
            abrirHub("tablas")
        }

        val avisoRedSocial = { red: String ->
            AppDialog.proximamente(this, "Nuestra cuenta de $red")
        }
        findViewById<TextView>(R.id.btnInstagram).setOnClickListener { avisoRedSocial("Instagram") }
        findViewById<TextView>(R.id.btnTwitter).setOnClickListener { avisoRedSocial("X / Twitter") }
        findViewById<TextView>(R.id.btnYoutube).setOnClickListener { avisoRedSocial("YouTube") }
        findViewById<TextView>(R.id.btnTiktok).setOnClickListener { avisoRedSocial("TikTok") }
    }

    private fun abrirHub(tab: String) {
        val intent = Intent(this, HubActivity::class.java)
        intent.putExtra(HubActivity.EXTRA_TAB, tab)
        startActivity(intent)
    }

    private fun comprobarAcceso() {
        if (!SupabaseProvider.isConfigured) {
            volverAlLogin()
            return
        }
        lifecycleScope.launch {
            try {
                SupabaseProvider.client.auth.awaitInitialization()
                if (SupabaseProvider.client.auth.currentSessionOrNull() == null) {
                    volverAlLogin()
                }
            } catch (error: Exception) {
                error.registrar("Comprobar sesión al abrir la app")
                volverAlLogin()
            }
        }
    }

    private fun volverAlLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }
}
