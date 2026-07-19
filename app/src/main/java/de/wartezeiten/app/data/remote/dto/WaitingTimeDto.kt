package de.wartezeiten.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class WaitingTimeDto(
    @SerializedName(value = "id", alternate = ["uuid", "attractionId"])
    val id: String?,
    @SerializedName(value = "name", alternate = ["title"])
    val name: String?,
    @SerializedName(value = "code")
    val code: String?,
    @SerializedName(value = "waitingtime", alternate = ["waitingTime", "waitTime", "wait_time"])
    val waitingTime: Int?,
    @SerializedName(value = "status", alternate = ["state"])
    val status: String?
)
