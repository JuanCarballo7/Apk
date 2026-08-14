package com.example.activiti_main

import android.widget.ImageView
import coil.load
import com.example.activiti_main.data.DriverApi

object MediaHelper {

    fun loadFlexible(imageView: ImageView, keyOrUrl: String?, fallbackRes: Int) {
        when {
            keyOrUrl.isNullOrBlank() -> imageView.setImageResource(fallbackRes)
            keyOrUrl.startsWith("http://") || keyOrUrl.startsWith("https://") -> {
                imageView.load(keyOrUrl) {
                    crossfade(true)
                    placeholder(fallbackRes)
                    error(fallbackRes)
                }
            }
            else -> {
                val resId = imageView.resources.getIdentifier(
                    keyOrUrl,
                    "drawable",
                    imageView.context.packageName
                )
                imageView.setImageResource(if (resId != 0) resId else fallbackRes)
            }
        }
    }

    fun driverPhotoRes(code: String): Int = when (code.uppercase()) {
        "VER" -> R.drawable.verstappen
        "LEC" -> R.drawable.leclerc
        else -> R.drawable.verstappen
    }

    fun newsFallbackRes(index: Int): Int = when (index % 3) {
        0 -> R.drawable.news_featured
        1 -> R.drawable.news_galvez
        else -> R.drawable.news_motogp
    }
}

fun DriverApi.displayName(): String = "$firstName $lastName"

fun DriverApi.detailName(): String = "${firstName.uppercase()}\n${lastName.uppercase()}"

fun DriverApi.infoLine(): String = "#$driverNumber · $country"

fun DriverApi.teamName(): String = teams?.name?.uppercase() ?: "SIN EQUIPO"
