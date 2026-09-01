package de.wartezeiten.app.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String = "temperature_2m,precipitation_probability,weather_code",
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min,precipitation_probability_max,weathercode",
        @Query("current_weather") currentWeather: Boolean = true,
        @Query("timezone") timezone: String = "auto"
    ): Response<WeatherResponse>
}

data class WeatherResponse(
    val current_weather: CurrentWeather?,
    val hourly: HourlyData?,
    val daily: DailyData?
)

data class CurrentWeather(
    val temperature: Double,
    val weathercode: Int
)

data class HourlyData(
    val time: List<String>?,
    val temperature_2m: List<Double>?,
    val precipitation_probability: List<Int>?,
    val weather_code: List<Int>?
)

data class DailyData(
    val time: List<String>?,
    val temperature_2m_max: List<Double>?,
    val temperature_2m_min: List<Double>?,
    val precipitation_probability_max: List<Int>?,
    val weathercode: List<Int>?
)
