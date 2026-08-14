package com.example.activiti_main

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.activiti_main.data.SupabaseProvider
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        comprobarAcceso()
        findViewById<android.view.View>(R.id.btnLogout).setOnClickListener {
            cerrarSesion()
        }

        findViewById<TextView>(R.id.tabNews).setOnClickListener {
            abrirHub("news")
        }
        findViewById<TextView>(R.id.tabTablas).setOnClickListener {
            abrirHub("tablas")
        }
        findViewById<TextView>(R.id.tabVideos).setOnClickListener {
            Toast.makeText(this, "Videos próximamente", Toast.LENGTH_SHORT).show()
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

        val toastSocial = { red: String ->
            Toast.makeText(this, "$red próximamente", Toast.LENGTH_SHORT).show()
        }
        findViewById<TextView>(R.id.btnInstagram).setOnClickListener { toastSocial("Instagram") }
        findViewById<TextView>(R.id.btnTwitter).setOnClickListener { toastSocial("X / Twitter") }
        findViewById<TextView>(R.id.btnYoutube).setOnClickListener { toastSocial("YouTube") }
        findViewById<TextView>(R.id.btnTiktok).setOnClickListener { toastSocial("TikTok") }
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
            } catch (_: Exception) {
                volverAlLogin()
            }
        }
    }

    private fun cerrarSesion() {
        lifecycleScope.launch {
            try {
                SupabaseProvider.client.auth.signOut()
            } finally {
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
