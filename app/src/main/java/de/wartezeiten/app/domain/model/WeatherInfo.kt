package de.wartezeiten.app.domain.model

data class WeatherForecastDay(
    val date: String,
    val minTemperature: Double,
    val maxTemperature: Double,
    val precipitationProbability: Int,
    val weatherCode: Int
)

data class WeatherInfo(
    val temperature: Double,
    val precipitationProbability: Int,
    val weatherCode: Int,
    val forecast: List<WeatherForecastDay> = emptyList()
)
