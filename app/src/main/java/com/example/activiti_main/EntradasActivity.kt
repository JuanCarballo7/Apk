package com.example.activiti_main

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class EntradasActivity : AppCompatActivity() {

    private lateinit var entradasUi: EntradasUi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_entradas)

        findViewById<View>(R.id.headerEntradas).applyTopSystemBarPadding()
        findViewById<View>(R.id.btnEntradasBack).setOnClickListener { finish() }

        entradasUi = EntradasUi(this, findViewById(R.id.entradasPanelRoot))
        entradasUi.bind()
    }

    override fun onResume() {
        super.onResume()
        if (::entradasUi.isInitialized) entradasUi.onResume()
    }
}
