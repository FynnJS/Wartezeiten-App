package de.wartezeiten.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CrowdLevelDto(
    @SerializedName(value = "crowd_level", alternate = ["crowdlevel", "level"])
    val crowdLevel: String?,
    @SerializedName(value = "timestamp", alternate = ["updatedAt", "lastUpdated"])
    val timestamp: String?
)
