package com.example.activiti_main.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileApi(
    val id: String,
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    val role: String = "user",
    val saldo: Double = 0.0
) {
    val esAdmin: Boolean get() = role == "admin" || role == "superadmin"
    val esSuperadmin: Boolean get() = role == "superadmin"
}
