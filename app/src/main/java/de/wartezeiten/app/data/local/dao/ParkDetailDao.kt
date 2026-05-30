package de.wartezeiten.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import de.wartezeiten.app.data.local.entity.CrowdLevelEntity
import de.wartezeiten.app.data.local.entity.HolidayEntity
import de.wartezeiten.app.data.local.entity.OpeningTimesEntity
import de.wartezeiten.app.data.local.entity.WaitingTimeEntity
import de.wartezeiten.app.data.local.entity.WeatherEntity
import de.wartezeiten.app.data.local.entity.WeatherForecastEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ParkDetailDao {
    @Query("SELECT * FROM opening_times WHERE parkKey = :parkKey LIMIT 1")
    fun observeOpeningTimes(parkKey: String): Flow<OpeningTimesEntity?>

    @Query("SELECT * FROM crowd_levels WHERE parkKey = :parkKey LIMIT 1")
    fun observeCrowdLevel(parkKey: String): Flow<CrowdLevelEntity?>

    @Query("SELECT * FROM waiting_times WHERE parkKey = :parkKey")
    fun observeWaitingTimes(parkKey: String): Flow<List<WaitingTimeEntity>>

    @Query("SELECT * FROM weather WHERE parkKey = :parkKey LIMIT 1")
    fun observeWeather(parkKey: String): Flow<WeatherEntity?>

    @Query("SELECT * FROM weather_forecast WHERE parkKey = :parkKey ORDER BY date ASC")
    fun observeWeatherForecast(parkKey: String): Flow<List<WeatherForecastEntity>>

    @Query("SELECT * FROM holidays WHERE parkKey = :parkKey")
    fun observeHolidays(parkKey: String): Flow<List<HolidayEntity>>

    @Upsert
    suspend fun upsertOpeningTimes(openingTimes: OpeningTimesEntity)

    @Upsert
    suspend fun upsertCrowdLevel(crowdLevel: CrowdLevelEntity)

    @Upsert
    suspend fun upsertWeather(weather: WeatherEntity)

    @Upsert
    suspend fun upsertWeatherForecast(forecast: List<WeatherForecastEntity>)

    @Upsert
    suspend fun upsertHolidays(holidays: List<HolidayEntity>)

    @Upsert
    suspend fun upsertWaitingTimes(waitingTimes: List<WaitingTimeEntity>)

    @Query("DELETE FROM waiting_times WHERE parkKey = :parkKey")
    suspend fun deleteWaitingTimesForPark(parkKey: String)

    @Query("DELETE FROM weather_forecast WHERE parkKey = :parkKey")
    suspend fun deleteWeatherForecastForPark(parkKey: String)

    @Query("DELETE FROM holidays WHERE parkKey = :parkKey")
    suspend fun deleteHolidaysForPark(parkKey: String)

    @Transaction
    suspend fun replaceWaitingTimes(parkKey: String, waitingTimes: List<WaitingTimeEntity>) {
        deleteWaitingTimesForPark(parkKey)
        upsertWaitingTimes(waitingTimes)
    }

    @Transaction
    suspend fun replaceWeatherForecast(parkKey: String, forecast: List<WeatherForecastEntity>) {
        deleteWeatherForecastForPark(parkKey)
        upsertWeatherForecast(forecast)
    }

    @Transaction
    suspend fun replaceHolidays(parkKey: String, holidays: List<HolidayEntity>) {
        deleteHolidaysForPark(parkKey)
        upsertHolidays(holidays)
    }
}
