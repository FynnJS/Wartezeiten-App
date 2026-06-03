package de.wartezeiten.app.data.mapper

import de.wartezeiten.app.data.local.entity.CrowdLevelEntity
import de.wartezeiten.app.data.local.entity.HolidayEntity
import de.wartezeiten.app.data.local.entity.OpeningTimesEntity
import de.wartezeiten.app.data.local.entity.ParkEntity
import de.wartezeiten.app.data.local.entity.WaitingTimeEntity
import de.wartezeiten.app.data.local.entity.WeatherEntity
import de.wartezeiten.app.data.local.entity.WeatherForecastEntity
import de.wartezeiten.app.data.remote.dto.CrowdLevelDto
import de.wartezeiten.app.data.remote.dto.OpeningTimesDto
import de.wartezeiten.app.data.remote.dto.ParkDto
import de.wartezeiten.app.data.remote.dto.WaitingTimeDto
import de.wartezeiten.app.domain.model.AttractionStatus
import de.wartezeiten.app.domain.model.CrowdLevel
import de.wartezeiten.app.domain.model.CurrentAttractionSearchEntry
import de.wartezeiten.app.domain.model.HolidayInfo
import de.wartezeiten.app.domain.model.OpeningTimes
import de.wartezeiten.app.domain.model.Park
import de.wartezeiten.app.domain.model.WeatherForecastDay
import de.wartezeiten.app.domain.model.WaitingTime
import de.wartezeiten.app.domain.model.WeatherInfo

fun ParkDto.toEntity(updatedAtMillis: Long): ParkEntity {
    return ParkEntity(
        id = id,
        uuid = uuid,
        name = name,
        country = country,
        isFavorite = false, // Initial value, will be merged in repository if needed
        updatedAtMillis = updatedAtMillis,
    )
}

fun ParkEntity.toDomain(): Park {
    return Park(
        id = id,
        uuid = uuid,
        name = name,
        country = country,
        isFavorite = isFavorite
    )
}

/**
 * FIX: Now accepts List<OpeningTimesDto> (API returns array).
 * Takes the first element if available, returns a "closed" entity otherwise.
 */
fun List<OpeningTimesDto>.toEntity(parkKey: String, updatedAtMillis: Long): OpeningTimesEntity {
    val dto = firstOrNull()
    val isOpen = dto?.openedToday ?: false
    return OpeningTimesEntity(
        parkKey = parkKey,
        opened = isOpen,
        from = dto?.opening,
        to = dto?.closing,
        updatedAtMillis = updatedAtMillis,
    )
}

fun OpeningTimesEntity.toDomain(): OpeningTimes {
    return OpeningTimes(
        opened = opened,
        from = from,
        to = to,
    )
}

fun WaitingTimeDto.toEntity(parkKey: String, updatedAtMillis: Long): WaitingTimeEntity {
    val stableId = id ?: name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-")
    return WaitingTimeEntity(
        parkKey = parkKey,
        attractionId = stableId,
        name = name,
        waitingTime = waitingTime,
        status = status,
        updatedAtMillis = updatedAtMillis,
    )
}

fun WaitingTimeEntity.toDomain(): WaitingTime {
    return WaitingTime(
        attractionId = attractionId,
        name = name,
        waitingTime = waitingTime,
        status = AttractionStatus.fromApi(status),
    )
}

fun WaitingTimeEntity.toCurrentAttractionSearchEntry(): CurrentAttractionSearchEntry {
    return CurrentAttractionSearchEntry(
        parkKey = parkKey,
        attractionId = attractionId,
        name = name,
        waitingTime = waitingTime,
        status = AttractionStatus.fromApi(status),
        updatedAtMillis = updatedAtMillis,
    )
}

fun CrowdLevelDto.toEntity(parkKey: String, updatedAtMillis: Long): CrowdLevelEntity {
    val parsedLevel = crowdLevel?.replace(",", ".")?.toFloatOrNull()
    return CrowdLevelEntity(
        parkKey = parkKey,
        crowdLevel = parsedLevel,
        timestamp = timestamp,
        updatedAtMillis = updatedAtMillis,
    )
}

fun CrowdLevelEntity.toDomain(): CrowdLevel {
    return CrowdLevel(
        level = crowdLevel,
        timestamp = timestamp,
    )
}

fun WeatherEntity.toDomain(forecast: List<WeatherForecastEntity> = emptyList()): WeatherInfo {
    return WeatherInfo(
        temperature = temperature,
        precipitationProbability = precipitationProbability,
        weatherCode = weatherCode,
        forecast = forecast.map { it.toDomain() }
    )
}

fun WeatherForecastEntity.toDomain(): WeatherForecastDay {
    return WeatherForecastDay(
        date = date,
        minTemperature = minTemperature,
        maxTemperature = maxTemperature,
        precipitationProbability = precipitationProbability,
        weatherCode = weatherCode
    )
}

fun HolidayEntity.toDomain(): HolidayInfo {
    return HolidayInfo(
        date = date,
        name = name
    )
}
