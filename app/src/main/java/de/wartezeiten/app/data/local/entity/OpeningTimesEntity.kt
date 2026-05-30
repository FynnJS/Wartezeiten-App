package de.wartezeiten.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "opening_times")
data class OpeningTimesEntity(
    @PrimaryKey val parkKey: String,
    val opened: Boolean,
    val from: String?,
    val to: String?,
    val updatedAtMillis: Long
)
