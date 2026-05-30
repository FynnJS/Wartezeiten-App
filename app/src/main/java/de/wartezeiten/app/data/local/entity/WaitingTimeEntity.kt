package de.wartezeiten.app.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "waiting_times",
    primaryKeys = ["parkKey", "attractionId"]
)
data class WaitingTimeEntity(
    val parkKey: String,
    val attractionId: String,
    val name: String,
    val waitingTime: Int?,
    val status: String,
    val updatedAtMillis: Long
)
