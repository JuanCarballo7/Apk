package com.example.activiti_main

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/** Evita que el contenido quede debajo de la barra de estado del sistema. */
fun View.applyTopSystemBarPadding() {
    val basePaddingTop = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
        view.updatePadding(top = basePaddingTop + topInset)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
