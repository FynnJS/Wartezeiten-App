package de.wartezeiten.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weather_forecast")
data class WeatherForecastEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val parkKey: String,
    val date: String,
    val minTemperature: Double,
    val maxTemperature: Double,
    val precipitationProbability: Int,
    val weatherCode: Int,
    val updatedAtMillis: Long
)
