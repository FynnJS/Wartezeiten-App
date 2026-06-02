package de.wartezeiten.app.data.repository

import de.wartezeiten.app.core.dispatcher.IoDispatcher
import de.wartezeiten.app.core.network.ApiResult
import de.wartezeiten.app.core.network.NetworkError
import de.wartezeiten.app.core.network.safeApiCall
import de.wartezeiten.app.data.local.dao.ParkDao
import de.wartezeiten.app.data.local.dao.ParkDetailDao
import de.wartezeiten.app.data.local.dao.ParkSnapshotDao
import de.wartezeiten.app.data.local.entity.HolidayEntity
import de.wartezeiten.app.data.local.entity.ParkSnapshotEntity
import de.wartezeiten.app.data.local.entity.WeatherEntity
import de.wartezeiten.app.data.local.entity.WeatherForecastEntity
import de.wartezeiten.app.data.mapper.toDomain
import de.wartezeiten.app.data.mapper.toEntity
import de.wartezeiten.app.data.remote.HolidayApiService
import de.wartezeiten.app.data.remote.WartezeitenApiService
import de.wartezeiten.app.data.remote.WeatherApiService
import de.wartezeiten.app.domain.model.Park
import de.wartezeiten.app.domain.model.ParkDetail
import de.wartezeiten.app.domain.repository.WartezeitenRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultWartezeitenRepository @Inject constructor(
    private val api: WartezeitenApiService,
    private val parkDao: ParkDao,
    private val parkDetailDao: ParkDetailDao,
    private val parkSnapshotDao: ParkSnapshotDao,
    private val weatherApi: WeatherApiService,
    private val holidayApi: HolidayApiService,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : WartezeitenRepository {

    override fun observeParks(query: String?): Flow<List<Park>> {
        return parkDao.observeParks(query.takeUnless { it.isNullOrBlank() })
            .map { parks -> parks.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override fun observeLatestOpenParkKeys(): Flow<Set<String>> {
        return parkSnapshotDao.observeLatestOpenParkKeys()
            .map { it.toSet() }
            .flowOn(ioDispatcher)
    }

    override suspend fun refreshParks(language: String): ApiResult<Unit> = withContext(ioDispatcher) {
        when (val result = safeApiCall { api.getParks(language) }) {
            is ApiResult.Success -> {
                val now = System.currentTimeMillis()
                val currentParks = parkDao.observeParks(null).first()
                val favoritesMap = currentParks.associateBy({ it.id }, { it.isFavorite })
                
                val entities = result.data.map { dto ->
                    dto.toEntity(now).copy(isFavorite = favoritesMap[dto.id] ?: false)
                }
                parkDao.upsertParks(entities)
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> result
        }
    }

    override fun observeParkDetail(parkKey: String): Flow<ParkDetail> {
        val parkFlow = parkDao.observePark(parkKey)
        val openingFlow = parkDetailDao.observeOpeningTimes(parkKey)
        val crowdFlow = parkDetailDao.observeCrowdLevel(parkKey)
        val waitingFlow = parkDetailDao.observeWaitingTimes(parkKey)
        val weatherFlow = parkDetailDao.observeWeather(parkKey)
        val weatherForecastFlow = parkDetailDao.observeWeatherForecast(parkKey)
        val holidayFlow = parkDetailDao.observeHolidays(parkKey)

        return kotlinx.coroutines.flow.combine(
            parkFlow, openingFlow, crowdFlow, waitingFlow,
            kotlinx.coroutines.flow.combine(weatherFlow, weatherForecastFlow, holidayFlow) { w, wf, h -> Triple(w, wf, h) }
        ) { park, opening, crowd, waiting, weatherTriple ->
            val (weather, weatherForecast, holidays) = weatherTriple
            ParkDetail(
                park = park?.toDomain(),
                openingTimes = opening?.toDomain(),
                crowdLevel = crowd?.toDomain(),
                waitingTimes = waiting.map { it.toDomain() },
                weather = weather?.toDomain(weatherForecast),
                holidays = holidays.map { it.toDomain() }
            )
        }.flowOn(ioDispatcher)
    }

    override suspend fun refreshParkDetail(
        parkKey: String,
        language: String,
    ): ApiResult<Unit> = withContext(ioDispatcher) {
        coroutineScope {
            // FIX: openingTimes now returns List<OpeningTimesDto> - matches updated API service
            val openingTimes = async { safeApiCall { api.getOpeningTimes(parkKey) } }
            val waitingTimes = async { safeApiCall { api.getWaitingTimes(parkKey, language) } }
            val crowdLevel = async { safeApiCall { api.getCrowdLevel(parkKey) } }
            val park = parkDao.observePark(parkKey).first()
            val (latitude, longitude) = park?.let { countryToCoordinates(it.id, it.country) } ?: Pair(48.137, 11.575)
            val weather = async { safeApiCall { weatherApi.getForecast(latitude, longitude) } }
            val holidays = async { safeApiCall { holidayApi.getNextHolidays(park?.country?.let(::countryToIsoCode) ?: "DE") } }

            val now = System.currentTimeMillis()
            val openingResult = openingTimes.await()
            val waitingResult = waitingTimes.await()
            val crowdResult = crowdLevel.await()
            val weatherResult = weather.await()
            val holidayResult = holidays.await()

            (openingResult as? ApiResult.Success)?.let {
                parkDetailDao.upsertOpeningTimes(it.data.toEntity(parkKey, now))
            }
            (waitingResult as? ApiResult.Success)?.let {
                parkDetailDao.replaceWaitingTimes(
                    parkKey = parkKey,
                    waitingTimes = it.data.map { dto -> dto.toEntity(parkKey, now) },
                )
            }
            (crowdResult as? ApiResult.Success)?.let {
                val openedToday = if (openingResult is ApiResult.Success && openingResult.data.isNotEmpty()) {
                    openingResult.data.first().openedToday
                } else {
                    null
                }
                val openFrom = if (openingResult is ApiResult.Success && openingResult.data.isNotEmpty()) openingResult.data.first().opening else null
                val closedFrom = if (openingResult is ApiResult.Success && openingResult.data.isNotEmpty()) openingResult.data.first().closing else null
                val openAttractions = if (waitingResult is ApiResult.Success) {
                    waitingResult.data.count { it.status.equals("opened", ignoreCase = true) }
                } else {
                    0
                }
                val canDisplayCrowdLevel = openedToday != false && openAttractions > 0
                val apiCrowdLevel = it.data.crowdLevel?.replace(",", ".")?.toFloatOrNull()
                val displayCrowdLevel = apiCrowdLevel.takeIf { canDisplayCrowdLevel }

                parkDetailDao.upsertCrowdLevel(it.data.toEntity(parkKey, now))
                
                parkSnapshotDao.insert(
                    ParkSnapshotEntity(
                        parkKey = parkKey,
                        capturedAtMillis = now,
                        apiCrowdLevel = apiCrowdLevel,
                        calculatedCrowdLevel = null,
                        displayCrowdLevel = displayCrowdLevel,
                        openedToday = openedToday,
                        openFrom = openFrom,
                        closedFrom = closedFrom,
                        openAttractions = openAttractions,
                        totalAttractions = if (waitingResult is ApiResult.Success) waitingResult.data.size else 0
                    )
                )
            }
            
            (weatherResult as? ApiResult.Success)?.data?.let { response ->
                val temperature = response.current_weather?.temperature ?: response.hourly.temperature_2m.firstOrNull() ?: 0.0
                val precipitationProbability = response.hourly.precipitation_probability.firstOrNull() ?: 0
                val weatherCode = response.current_weather?.weathercode ?: response.hourly.weather_code.firstOrNull() ?: 0

                parkDetailDao.upsertWeather(
                    WeatherEntity(
                        parkKey = parkKey,
                        temperature = temperature,
                        precipitationProbability = precipitationProbability,
                        weatherCode = weatherCode,
                        updatedAtMillis = now
                    )
                )

                response.daily?.let { daily ->
                    val forecast = daily.time.indices
                        .take(7)
                        .mapNotNull { index ->
                            val date = daily.time.getOrNull(index)
                            val minTemp = daily.temperature_2m_min.getOrNull(index)
                            val maxTemp = daily.temperature_2m_max.getOrNull(index)
                            val precip = daily.precipitation_probability_max.getOrNull(index)
                            val code = daily.weathercode.getOrNull(index)
                            if (date != null && minTemp != null && maxTemp != null && precip != null && code != null) {
                                WeatherForecastEntity(
                                    parkKey = parkKey,
                                    date = date,
                                    minTemperature = minTemp,
                                    maxTemperature = maxTemp,
                                    precipitationProbability = precip,
                                    weatherCode = code,
                                    updatedAtMillis = now
                                )
                            } else null
                        }
                    parkDetailDao.replaceWeatherForecast(parkKey, forecast)
                }
            }

            (holidayResult as? ApiResult.Success)?.data?.let { holidaysDto ->
                parkDetailDao.replaceHolidays(
                    parkKey = parkKey,
                    holidays = holidaysDto.map {
                        HolidayEntity(
                            parkKey = parkKey,
                            date = it.date,
                            name = it.name
                        )
                    }
                )
            }

            listOf(openingResult, waitingResult, crowdResult, weatherResult, holidayResult)
                .asSequence()
                .filterIsInstance<ApiResult.Error>()
                .sortedBy { errorPriority(it.type) }
                .firstOrNull()
                ?: ApiResult.Success(Unit)
        }
    }

    private fun errorPriority(type: NetworkError): Int {
        return when (type) {
            NetworkError.RateLimited -> 0
            NetworkError.Network -> 1
            NetworkError.NotFound -> 2
            NetworkError.Server -> 3
            NetworkError.EmptyBody -> 4
            NetworkError.Unknown -> 5
        }
    }

    private fun countryToCoordinates(parkKey: String, country: String): Pair<Double, Double> {
        // Granular mapping for major parks
        return when (parkKey.lowercase().trim()) {
            "europapark" -> 48.2673 to 7.7224
            "phantasialand" -> 50.7999 to 6.8778
            "heidepark" -> 53.0185 to 9.8785
            "hansapark" -> 54.0769 to 10.7936
            "legoland-de" -> 48.4233 to 10.2979
            "disneyland-paris" -> 48.8674 to 2.7836
            "efteling" -> 51.6482 to 5.0449
            else -> when (country.lowercase().trim()) {
                "de", "germany", "deutschland" -> 52.5200 to 13.4050
                "at", "austria", "österreich", "oesterreich" -> 48.2082 to 16.3738
                "ch", "switzerland", "schweiz" -> 46.9480 to 7.4474
                "nl", "netherlands", "niederlande" -> 52.3702 to 4.8952
                "fr", "france", "frankreich" -> 48.8566 to 2.3522
                "es", "spain", "spanien" -> 40.4168 to -3.7038
                "it", "italy", "italien" -> 41.9028 to 12.4964
                "uk", "gb", "great britain", "britain", "united kingdom" -> 51.5074 to -0.1278
                "be", "belgium", "belgien" -> 50.8503 to 4.3517
                "se", "sweden", "schweden" -> 59.3293 to 18.0686
                "pl", "poland", "polen" -> 52.2297 to 21.0122
                "cz", "czech republic", "tschechien" -> 50.0755 to 14.4378
                "no", "norway", "norwegen" -> 59.9139 to 10.7522
                "dk", "denmark", "dänemark", "danmark" -> 55.6761 to 12.5683
                "fi", "finland", "finnland" -> 60.1699 to 24.9384
                "us", "usa", "united states", "vereinigte staaten" -> 38.9072 to -77.0369
                else -> 48.1370 to 11.5750
            }
        }
    }

    private fun countryToIsoCode(country: String): String {
        return when (country.lowercase().trim()) {
            "deutschland", "germany", "de" -> "DE"
            "austria", "österreich", "oesterreich", "at" -> "AT"
            "switzerland", "schweiz", "ch" -> "CH"
            "netherlands", "niederlande", "nl" -> "NL"
            "france", "frankreich", "fr" -> "FR"
            "spain", "spanien", "es" -> "ES"
            "italy", "italien", "it" -> "IT"
            "united kingdom", "great britain", "britain", "uk", "gb" -> "GB"
            "belgium", "belgien", "be" -> "BE"
            "sweden", "schweden", "se" -> "SE"
            "poland", "polen", "pl" -> "PL"
            "czech republic", "tschechien", "cz" -> "CZ"
            "norway", "norwegen", "no" -> "NO"
            "denmark", "dänemark", "danmark", "dk" -> "DK"
            "finland", "finnland", "fi" -> "FI"
            "united states", "usa", "vereinigte staaten", "us" -> "US"
            else -> "DE"
        }
    }

    override suspend fun toggleFavorite(parkId: String, isFavorite: Boolean) {
        withContext(ioDispatcher) {
            parkDao.updateFavorite(parkId, isFavorite)
        }
    }

    override fun getParkTrendSummary(parkKey: String): Flow<de.wartezeiten.app.domain.model.ParkTrendSummary> {
        return parkSnapshotDao.getSnapshotsByParkKey(parkKey)
            .map { snapshots ->
                val crowdSnapshots = snapshots.map {
                    de.wartezeiten.app.domain.model.ParkCrowdSnapshot(
                        capturedAtMillis = it.capturedAtMillis,
                        apiCrowdLevel = it.apiCrowdLevel,
                        calculatedCrowdLevel = it.calculatedCrowdLevel,
                        displayCrowdLevel = it.displayCrowdLevel,
                        openedToday = it.openedToday,
                        openAttractions = it.openAttractions,
                        totalAttractions = it.totalAttractions
                    )
                }
                de.wartezeiten.app.domain.model.buildParkTrendSummary(crowdSnapshots, System.currentTimeMillis())
            }
            .flowOn(ioDispatcher)
    }
}
