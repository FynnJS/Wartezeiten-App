package de.wartezeiten.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parks")
data class ParkEntity(
    @PrimaryKey val id: String,
    val uuid: String,
    val name: String,
    val country: String,
    val isFavorite: Boolean = false,
    val updatedAtMillis: Long
)
