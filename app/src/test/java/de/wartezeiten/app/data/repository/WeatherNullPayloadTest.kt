package de.wartezeiten.app.data.repository

import de.wartezeiten.app.data.remote.DailyData
import de.wartezeiten.app.data.remote.HourlyData
import de.wartezeiten.app.data.remote.WeatherResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherNullPayloadTest {
    @Test
    fun errorBodyWithoutHourlyReturnsNull() {
        val response = WeatherResponse(current_weather = null, hourly = null, daily = null)
        assertNull(response.toWeatherEntities("europapark", 1L))
    }

    @Test
    fun fullyNullHourlyListsAreTreatedAsNoData() {
        val response = WeatherResponse(
            current_weather = null,
            hourly = HourlyData(
                time = null,
                temperature_2m = null,
                precipitation_probability = null,
                weather_code = null,
            ),
            daily = null,
        )
        assertNull(response.toWeatherEntities("europapark", 1L))
    }

    @Test
    fun partialHourlyDataFallsBackToDefaults() {
        val response = WeatherResponse(
            current_weather = null,
            hourly = HourlyData(
                time = listOf("2026-01-01T00:00"),
                temperature_2m = listOf(21.5),
                precipitation_probability = null,
                weather_code = null,
            ),
            daily = null,
        )
        val result = response.toWeatherEntities("europapark", 1L)
        assertNotNull(result)
        assertEquals(21.5, result!!.first.temperature, 0.001)
        assertEquals(0, result.first.precipitationProbability)
        assertEquals(0, result.first.weatherCode)
    }

    @Test
    fun dailyForecastIsMappedWithNullGuards() {
        val response = WeatherResponse(
            current_weather = null,
            hourly = HourlyData(
                time = null,
                temperature_2m = null,
                precipitation_probability = null,
                weather_code = null,
            ),
            daily = DailyData(
                time = listOf("2026-01-01", "2026-01-02"),
                temperature_2m_max = listOf(12.0, 14.0),
                temperature_2m_min = listOf(3.0, 5.0),
                precipitation_probability_max = listOf(10, 20),
                weathercode = listOf(1, 2),
            ),
        )
        val result = response.toWeatherEntities("europapark", 1L)
        assertNotNull(result)
        assertEquals(2, result!!.second.size)
        assertEquals("2026-01-01", result.second[0].date)
        assertEquals(12.0, result.second[0].maxTemperature, 0.001)
    }
}
