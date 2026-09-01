package de.wartezeiten.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ParkDto(
    @SerializedName("id")
    val id: String?,
    @SerializedName("uuid")
    val uuid: String?,
    @SerializedName("name")
    val name: String?,
    @SerializedName("land")
    val country: String?
)
