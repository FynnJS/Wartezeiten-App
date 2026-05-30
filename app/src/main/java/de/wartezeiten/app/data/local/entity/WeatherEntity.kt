package de.wartezeiten.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weather")
data class WeatherEntity(
    @PrimaryKey val parkKey: String,
    val temperature: Double,
    val precipitationProbability: Int,
    val weatherCode: Int,
    val updatedAtMillis: Long
)
