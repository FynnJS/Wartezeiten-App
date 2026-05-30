package de.wartezeiten.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class OpeningTimesDto(
    @SerializedName(value = "opened_today", alternate = ["opened", "open"])
    val openedToday: Boolean?,
    @SerializedName("status")
    val status: String?,
    @SerializedName(value = "open_from", alternate = ["opening", "from", "opensAt"])
    val opening: String?,
    @SerializedName(value = "closed_from", alternate = ["closing", "to", "closesAt"])
    val closing: String?
)
