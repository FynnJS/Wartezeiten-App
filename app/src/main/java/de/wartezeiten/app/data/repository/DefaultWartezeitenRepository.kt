package de.wartezeiten.app.data.repository

import de.wartezeiten.app.core.dispatcher.IoDispatcher
import de.wartezeiten.app.core.network.ApiResult
import de.wartezeiten.app.core.network.NetworkError
import de.wartezeiten.app.core.network.safeApiCall
import de.wartezeiten.app.core.network.toApiLanguage
import de.wartezeiten.app.data.local.dao.ParkDao
import de.wartezeiten.app.data.local.dao.ParkDetailDao
import de.wartezeiten.app.data.local.dao.ParkSnapshotDao
import de.wartezeiten.app.data.local.PreferencesDataSource
import de.wartezeiten.app.data.local.entity.HolidayEntity
import de.wartezeiten.app.data.local.entity.ParkEntity
import de.wartezeiten.app.data.local.entity.ParkSnapshotEntity
import de.wartezeiten.app.data.local.entity.WaitingTimeEntity
import de.wartezeiten.app.data.local.entity.WeatherEntity
import de.wartezeiten.app.data.local.entity.WeatherForecastEntity
import de.wartezeiten.app.data.mapper.toDomain
import de.wartezeiten.app.data.mapper.toCurrentAttractionSearchEntry
import de.wartezeiten.app.data.mapper.toEntity
import de.wartezeiten.app.data.remote.HolidayApiService
import de.wartezeiten.app.data.remote.PublicAppDataApiService
import de.wartezeiten.app.data.remote.QueueTimesApiService
import de.wartezeiten.app.data.remote.WartezeitenApiService
import de.wartezeiten.app.data.remote.WeatherApiService
import de.wartezeiten.app.data.remote.dto.WaitingTimeDto
import de.wartezeiten.app.data.remote.dto.toDomain
import de.wartezeiten.app.data.remote.fallback.QueueTimesParkMapping
import de.wartezeiten.app.domain.model.Park
import de.wartezeiten.app.domain.model.ParkDetail
import de.wartezeiten.app.domain.model.ParkRecommendation
import de.wartezeiten.app.domain.model.isParkCurrentlyOpen
import de.wartezeiten.app.domain.repository.ParkRecommendationScanProgress
import de.wartezeiten.app.domain.repository.WartezeitenRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val RECOMMENDATION_CURRENT_MAX_AGE_MILLIS = 30 * 60 * 1000L
private const val RECOMMENDATION_SNAPSHOT_MAX_AGE_MILLIS = 6 * 60 * 60 * 1000L
private const val RECOMMENDATION_REQUEST_DELAY_MILLIS = 1_500L
private const val RECOMMENDATION_ESTIMATED_PARK_SCAN_MILLIS = 4_500L
private const val OPTIONAL_DETAIL_TIMEOUT_MILLIS = 3_000L
private const val LOCAL_PARK_SNAPSHOT_RETENTION_MILLIS = 7 * 24 * 60 * 60 * 1000L

@Singleton
class DefaultWartezeitenRepository @Inject constructor(
    private val api: WartezeitenApiService,
    private val parkDao: ParkDao,
    private val parkDetailDao: ParkDetailDao,
    private val parkSnapshotDao: ParkSnapshotDao,
    private val weatherApi: WeatherApiService,
    private val holidayApi: HolidayApiService,
    private val publicAppDataApi: PublicAppDataApiService,
    private val queueTimesApi: QueueTimesApiService,
    private val preferences: PreferencesDataSource,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : WartezeitenRepository {

    // Rein transient: welche Parks aktuell Wartezeiten ueber die queue-times.com-Ausweichquelle
    // beziehen, weil /v1/waitingtimes fuer sie fehlschlaegt. Bewusst nicht in Room persistiert, da es
    // sich um einen Sitzungszustand fuer die UI handelt, keine dauerhaft relevante Information.
    private val fallbackWaitTimeSourceParkKeys = MutableStateFlow<Set<String>>(emptySet())

    override fun observeFallbackWaitTimeSourceParkKeys(): Flow<Set<String>> =
        fallbackWaitTimeSourceParkKeys.asStateFlow()

    // Rein transient: ist die aktuell in Room gecachte Parkliste die kuratierte queue-times.com-
    // Ausweichliste (weil /v1/parks bei leerem Cache fehlschlug)? Solange das zutrifft, unterdrueckt
    // refreshParks() die generische "Serverfehler"-Meldung, da bereits nutzbare Daten angezeigt werden.
    private val usingFallbackParkList = MutableStateFlow(false)

    override fun observeUsingFallbackParkList(): Flow<Boolean> = usingFallbackParkList.asStateFlow()

    override fun observeParks(query: String?): Flow<List<Park>> {
        return parkDao.observeParks(query.takeUnless { it.isNullOrBlank() })
            .map { parks -> parks.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override fun observeCurrentAttractions() =
        parkDetailDao.observeAllWaitingTimes()
            .map { attractions -> attractions.map { it.toCurrentAttractionSearchEntry() } }
            .flowOn(ioDispatcher)

    override fun observeLatestOpenParkKeys(capturedAfterMillis: Long): Flow<Set<String>> {
        return parkSnapshotDao.observeLatestSnapshots()
            .map { snapshots ->
                val now = System.currentTimeMillis()
                snapshots
                    .asSequence()
                    .filter { it.capturedAtMillis >= capturedAfterMillis }
                    .filter {
                        isParkCurrentlyOpen(
                            openedToday = it.openedToday,
                            openFrom = it.openFrom,
                            closedFrom = it.closedFrom,
                            now = Instant.ofEpochMilli(now),
                        )
                    }
                    .map { it.parkKey }
                    .toSet()
            }
            .flowOn(ioDispatcher)
    }

    override fun observeBestParkRecommendation(): Flow<ParkRecommendation?> {
        return observeParkRecommendations(limit = 1)
            .map { it.firstOrNull() }
            .flowOn(ioDispatcher)
    }

    override fun observeParkRecommendations(limit: Int): Flow<List<ParkRecommendation>> {
        return combine(
            parkDao.observeParks(null),
            parkSnapshotDao.observeLatestSnapshots(),
        ) { parks, snapshots ->
            val now = System.currentTimeMillis()
            val parksByKey = parks
                .flatMap { park -> listOf(park.id to park, park.uuid to park) }
                .toMap()

            snapshots
                .asSequence()
                .filter { now - it.capturedAtMillis <= RECOMMENDATION_CURRENT_MAX_AGE_MILLIS }
                .filter {
                    it.openAttractions > 0 && isParkCurrentlyOpen(
                        openedToday = it.openedToday,
                        openFrom = it.openFrom,
                        closedFrom = it.closedFrom,
                        now = Instant.ofEpochMilli(now),
                    )
                }
                .mapNotNull { snapshot ->
                    val park = parksByKey[snapshot.parkKey] ?: return@mapNotNull null
                    val crowd = snapshot.displayCrowdLevel
                    val openRatio = if (snapshot.totalAttractions > 0) {
                        snapshot.openAttractions.toFloat() / snapshot.totalAttractions
                    } else {
                        0f
                    }
                    val crowdScore = (100 - (crowd ?: 65f)).coerceIn(0f, 100f)
                    val attractionScore = (openRatio * 100).coerceIn(0f, 100f)
                    val favoriteBonus = if (park.isFavorite) 8 else 0
                    val score = ((crowdScore * 0.55f) + (attractionScore * 0.45f) + favoriteBonus)
                        .toInt()
                        .coerceIn(0, 100)
                    ParkRecommendation(
                        park = park.toDomain(),
                        score = score,
                        crowdLevel = crowd,
                        openAttractions = snapshot.openAttractions,
                        totalAttractions = snapshot.totalAttractions,
                        reason = buildRecommendationReason(crowd, snapshot.openAttractions, snapshot.totalAttractions),
                    )
                }
                .sortedByDescending { it.score }
                .take(limit.coerceAtLeast(1))
                .toList()
        }.flowOn(ioDispatcher)
    }

    override suspend fun refreshParks(language: String): ApiResult<Unit> = withContext(ioDispatcher) {
        when (val result = safeApiCall { api.getParks(language.toApiLanguage()) }) {
            is ApiResult.Success -> {
                val now = System.currentTimeMillis()
                val currentParks = parkDao.observeParks(null).first()
                val favoritesMap = currentParks.associateBy({ it.id }, { it.isFavorite })

                val entities = result.data.map { dto ->
                    dto.toEntity(now).copy(isFavorite = favoritesMap[dto.id] ?: false)
                }
                parkDao.upsertParks(entities)
                usingFallbackParkList.value = false
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> {
                // Neuinstallation waehrend eines Ausfalls von /v1/parks: ohne diesen Seed waere die
                // Parkliste komplett leer. Solange die aktuell gecachte Liste noch die zuletzt
                // gesaete Ausweichliste ist (die echte API liefert weiterhin einen Fehler), gilt der
                // Ausfall bereits als abgefedert - es wird kein generischer "Serverfehler" mehr
                // zurueckgegeben, nur noch das Ausweichquelle-Flag gesetzt. Ist die gecachte Liste
                // hingegen eine echte, zuvor erfolgreich geladene Parkliste, bleibt das bisherige
                // Verhalten (Fehler zurueckgeben, bestehender Offline-Banner mit Cache-Alter greift).
                val currentParks = parkDao.observeParks(null).first()
                if (currentParks.isEmpty()) {
                    val now = System.currentTimeMillis()
                    val fallbackEntities = QueueTimesParkMapping.ENTRIES.map { info ->
                        ParkEntity(
                            id = info.parkKey,
                            uuid = info.parkKey,
                            name = info.name,
                            country = info.country,
                            isFavorite = false,
                            updatedAtMillis = now,
                        )
                    }
                    parkDao.upsertParks(fallbackEntities)
                    usingFallbackParkList.value = true
                    ApiResult.Success(Unit)
                } else if (usingFallbackParkList.value) {
                    ApiResult.Success(Unit)
                } else {
                    result
                }
            }
        }
    }

    override suspend fun refreshParkRecommendationSnapshots(
        language: String,
        allowLocalFallbackScan: Boolean,
        onProgress: (ParkRecommendationScanProgress) -> Unit,
    ): ApiResult<Unit> = withContext(ioDispatcher) {
        val publicResult = refreshPublicAppData()
        val now = System.currentTimeMillis()
        val hasFreshOpenSnapshot = parkSnapshotDao.observeLatestSnapshots()
            .first()
            .any { snapshot ->
                now - snapshot.capturedAtMillis <= RECOMMENDATION_CURRENT_MAX_AGE_MILLIS &&
                    isParkCurrentlyOpen(
                        openedToday = snapshot.openedToday,
                        openFrom = snapshot.openFrom,
                        closedFrom = snapshot.closedFrom,
                        now = Instant.ofEpochMilli(now),
                    )
            }
        if (publicResult is ApiResult.Success && hasFreshOpenSnapshot) {
            onProgress(ParkRecommendationScanProgress(completedParks = 0, totalParks = 0, estimatedRemainingMillis = 0L))
            return@withContext publicResult
        }
        if (!allowLocalFallbackScan) {
            onProgress(ParkRecommendationScanProgress(completedParks = 0, totalParks = 0, estimatedRemainingMillis = 0L))
            return@withContext publicResult
        }

        pruneOldLocalParkSnapshots()
        val parks = parkDao.observeParks(null).first()
        if (parks.isEmpty()) return@withContext ApiResult.Success(Unit)
        val freshParkKeys = parkSnapshotDao.observeLatestSnapshots()
            .first()
            .filter { now - it.capturedAtMillis <= RECOMMENDATION_SNAPSHOT_MAX_AGE_MILLIS }
            .filter { snapshot ->
                snapshot.openedToday == false || isParkCurrentlyOpen(
                    openedToday = snapshot.openedToday,
                    openFrom = snapshot.openFrom,
                    closedFrom = snapshot.closedFrom,
                    now = Instant.ofEpochMilli(now),
                )
            }
            .map { it.parkKey }
            .toSet()
        val parksToRefresh = parks.filter { park ->
            park.id !in freshParkKeys && park.uuid !in freshParkKeys
        }
        if (parksToRefresh.isEmpty()) {
            onProgress(ParkRecommendationScanProgress(completedParks = 0, totalParks = 0, estimatedRemainingMillis = 0L))
            return@withContext ApiResult.Success(Unit)
        }

        var firstError: ApiResult.Error? = null
        onProgress(parkRecommendationScanProgress(completedParks = 0, totalParks = parksToRefresh.size))
        parksToRefresh.forEachIndexed { index, park ->
            if (index > 0) delay(RECOMMENDATION_REQUEST_DELAY_MILLIS)
            when (val result = refreshParkRecommendationSnapshot(park.id, language)) {
                is ApiResult.Success -> {
                    onProgress(parkRecommendationScanProgress(completedParks = index + 1, totalParks = parksToRefresh.size))
                }
                is ApiResult.Error -> {
                    if (firstError == null) firstError = result
                    onProgress(parkRecommendationScanProgress(completedParks = index + 1, totalParks = parksToRefresh.size))
                    if (result.type == NetworkError.RateLimited) return@withContext result
                }
            }
        }

        firstError ?: ApiResult.Success(Unit)
    }

    private fun parkRecommendationScanProgress(
        completedParks: Int,
        totalParks: Int,
    ): ParkRecommendationScanProgress {
        val remainingParks = (totalParks - completedParks).coerceAtLeast(0)
        return ParkRecommendationScanProgress(
            completedParks = completedParks,
            totalParks = totalParks,
            estimatedRemainingMillis = remainingParks * RECOMMENDATION_ESTIMATED_PARK_SCAN_MILLIS,
        )
    }

    private suspend fun refreshParkRecommendationSnapshot(
        parkKey: String,
        language: String,
    ): ApiResult<Unit> {
        val now = System.currentTimeMillis()
        val openingResult = safeApiCall { api.getOpeningTimes(parkKey) }
        val openedToday = (openingResult as? ApiResult.Success)
            ?.data
            ?.firstOrNull()
            ?.openedToday
        val openFrom = (openingResult as? ApiResult.Success)
            ?.data
            ?.firstOrNull()
            ?.opening
        val closedFrom = (openingResult as? ApiResult.Success)
            ?.data
            ?.firstOrNull()
            ?.closing
        val currentlyOpen = isParkCurrentlyOpen(
            openedToday = openedToday,
            openFrom = openFrom,
            closedFrom = closedFrom,
            now = Instant.ofEpochMilli(now),
        )

        if (openingResult is ApiResult.Success && !currentlyOpen) {
            pruneOldLocalParkSnapshots(now)
            parkSnapshotDao.insert(
                ParkSnapshotEntity(
                    parkKey = parkKey,
                    capturedAtMillis = now,
                    apiCrowdLevel = null,
                    calculatedCrowdLevel = null,
                    displayCrowdLevel = null,
                    openedToday = openedToday ?: false,
                    openFrom = openFrom,
                    closedFrom = closedFrom,
                    openAttractions = 0,
                    totalAttractions = 0,
                )
            )
            return ApiResult.Success(Unit)
        }

        if (openingResult is ApiResult.Error && openingResult.type == NetworkError.RateLimited) {
            return openingResult
        }

        delay(RECOMMENDATION_REQUEST_DELAY_MILLIS)
        val waitingResult = safeApiCall { api.getWaitingTimes(parkKey, language.toApiLanguage()) }
        if (waitingResult is ApiResult.Error && waitingResult.type == NetworkError.RateLimited) {
            return waitingResult
        }

        delay(RECOMMENDATION_REQUEST_DELAY_MILLIS)
        val crowdResult = safeApiCall { api.getCrowdLevel(parkKey) }
        val waitingData = (waitingResult as? ApiResult.Success)?.data.orEmpty()
        val openAttractions = waitingData.count { it.status.equals("opened", ignoreCase = true) }
        val totalAttractions = waitingData.size
        val apiCrowdLevel = (crowdResult as? ApiResult.Success)
            ?.data
            ?.crowdLevel
            ?.replace(",", ".")
            ?.toFloatOrNull()
        val canDisplayCrowdLevel = openedToday == true && openAttractions > 0

        if (openingResult is ApiResult.Success || waitingResult is ApiResult.Success || crowdResult is ApiResult.Success) {
            pruneOldLocalParkSnapshots(now)
            parkSnapshotDao.insert(
                ParkSnapshotEntity(
                    parkKey = parkKey,
                    capturedAtMillis = now,
                    apiCrowdLevel = apiCrowdLevel,
                    calculatedCrowdLevel = null,
                    displayCrowdLevel = apiCrowdLevel.takeIf { canDisplayCrowdLevel },
                    openedToday = openedToday,
                    openFrom = openFrom,
                    closedFrom = closedFrom,
                    openAttractions = openAttractions,
                    totalAttractions = totalAttractions,
                )
            )
        }

        return listOf(openingResult, waitingResult, crowdResult)
            .asSequence()
            .filterIsInstance<ApiResult.Error>()
            .sortedBy { errorPriority(it.type) }
            .firstOrNull()
            ?: ApiResult.Success(Unit)
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
        supervisorScope {
            // FIX: openingTimes now returns List<OpeningTimesDto> - matches updated API service
            val openingTimes = async { safeApiCall { api.getOpeningTimes(parkKey) } }
            val waitingTimes = async { safeApiCall { api.getWaitingTimes(parkKey, language.toApiLanguage()) } }
            val crowdLevel = async { safeApiCall { api.getCrowdLevel(parkKey) } }
            val park = parkDao.observePark(parkKey).first()
            val (latitude, longitude) = park?.let { countryToCoordinates(it.id, it.country) } ?: Pair(48.137, 11.575)
            val weather = async {
                withTimeoutOrNull(OPTIONAL_DETAIL_TIMEOUT_MILLIS) {
                    safeApiCall { weatherApi.getForecast(latitude, longitude) }
                }
            }
            val holidays = async {
                withTimeoutOrNull(OPTIONAL_DETAIL_TIMEOUT_MILLIS) {
                    safeApiCall { holidayApi.getNextHolidays(park?.country?.let(::countryToIsoCode) ?: "DE") }
                }
            }

            val now = System.currentTimeMillis()
            val openingResult = openingTimes.await()
            val waitingResult = waitingTimes.await()
            val crowdResult = crowdLevel.await()
            val weatherResult = weather.await()
            val holidayResult = holidays.await()

            // Ausweichquelle fuer Wartezeiten (queue-times.com), wenn /v1/waitingtimes mit einem
            // Serverfehler fehlschlaegt und fuer diesen Park eine kuratierte Zuordnung existiert.
            // Oeffnungszeiten/Crowd-Level bleiben unberuehrt, da queue-times.com dafuer keine Daten hat.
            val fallbackParkInfo = QueueTimesParkMapping.findByParkKey(parkKey)
            val fallbackWaitingResult: ApiResult<List<WaitingTimeDto>>? =
                if (waitingResult is ApiResult.Error && waitingResult.type == NetworkError.Server && fallbackParkInfo != null) {
                    fetchQueueTimesWaitingTimes(fallbackParkInfo.queueTimesId)
                } else {
                    null
                }
            val effectiveWaitingResult = fallbackWaitingResult ?: waitingResult
            fallbackWaitTimeSourceParkKeys.update { keys ->
                if (fallbackWaitingResult is ApiResult.Success) keys + parkKey else keys - parkKey
            }

            (openingResult as? ApiResult.Success)?.let {
                parkDetailDao.upsertOpeningTimes(it.data.toEntity(parkKey, now))
            }
            val openingDto = (openingResult as? ApiResult.Success)?.data?.firstOrNull()
            val openedToday = openingDto?.openedToday
            val openFrom = openingDto?.opening
            val closedFrom = openingDto?.closing
            val currentlyOpen = if (openingResult is ApiResult.Success) {
                isParkCurrentlyOpen(
                    openedToday = openedToday,
                    openFrom = openFrom,
                    closedFrom = closedFrom,
                    now = Instant.ofEpochMilli(now),
                )
            } else {
                true
            }
            (effectiveWaitingResult as? ApiResult.Success)?.let {
                parkDetailDao.replaceWaitingTimes(
                    parkKey = parkKey,
                    waitingTimes = it.data.map { dto -> dto.toEntity(parkKey, now) },
                )
            }
            (crowdResult as? ApiResult.Success)?.let {
                parkDetailDao.upsertCrowdLevel(it.data.toEntity(parkKey, now))
            }

            if (openingResult is ApiResult.Success || effectiveWaitingResult is ApiResult.Success || crowdResult is ApiResult.Success) {
                val openAttractions = (effectiveWaitingResult as? ApiResult.Success)
                    ?.data
                    ?.count { currentlyOpen && it.status.equals("opened", ignoreCase = true) }
                    ?: 0
                val totalAttractions = (effectiveWaitingResult as? ApiResult.Success)?.data?.size ?: 0
                val canDisplayCrowdLevel = currentlyOpen && openAttractions > 0
                val apiCrowdLevel = (crowdResult as? ApiResult.Success)
                    ?.data
                    ?.crowdLevel
                    ?.replace(",", ".")
                    ?.toFloatOrNull()
                val displayCrowdLevel = apiCrowdLevel.takeIf { canDisplayCrowdLevel }

                pruneOldLocalParkSnapshots(now)
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
                        totalAttractions = totalAttractions
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

            val hasUsableCoreData = openingResult is ApiResult.Success ||
                    effectiveWaitingResult is ApiResult.Success ||
                    crowdResult is ApiResult.Success
            if (hasUsableCoreData) {
                ApiResult.Success(Unit)
            } else {
                listOf(openingResult, effectiveWaitingResult, crowdResult)
                    .asSequence()
                    .filterIsInstance<ApiResult.Error>()
                    .sortedBy { errorPriority(it.type) }
                    .firstOrNull()
                    ?: ApiResult.Success(Unit)
            }
        }
    }

    private suspend fun fetchQueueTimesWaitingTimes(queueTimesId: Int): ApiResult<List<WaitingTimeDto>> {
        return when (val result = safeApiCall { queueTimesApi.getQueueTimes(queueTimesId) }) {
            is ApiResult.Success -> {
                val rides = result.data.rides + result.data.lands.flatMap { it.rides }
                ApiResult.Success(
                    rides.map { ride ->
                        WaitingTimeDto(
                            id = ride.id.toString(),
                            name = ride.name,
                            waitingTime = ride.wait_time,
                            status = if (ride.is_open) "opened" else "closed",
                        )
                    }
                )
            }
            is ApiResult.Error -> result
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

    override suspend fun refreshPublicAppData(): ApiResult<Unit> = withContext(ioDispatcher) {
        val markersResult = safeApiCall { publicAppDataApi.getGlobalMarkers() }
        (markersResult as? ApiResult.Success)?.let { result ->
            val snapshots = result.data.markers.mapNotNull { marker ->
                val capturedAt = marker.capturedAtMillis.takeIf { it > 0L }
                    ?: result.data.generatedAtMillis
                    ?: return@mapNotNull null
                ParkSnapshotEntity(
                    parkKey = marker.parkKey,
                    capturedAtMillis = capturedAt,
                    apiCrowdLevel = null,
                    calculatedCrowdLevel = null,
                    displayCrowdLevel = null,
                    openedToday = marker.openedToday,
                    openFrom = marker.openFrom,
                    closedFrom = marker.closedFrom,
                    openAttractions = marker.openAttractions ?: 0,
                    totalAttractions = marker.totalAttractions ?: marker.attractionCount ?: 0,
                    source = "public",
                )
            }
            if (snapshots.isNotEmpty()) {
                parkSnapshotDao.insertAll(snapshots)
            }
        }

        when (val result = safeApiCall { publicAppDataApi.getLatestAppData() }) {
            is ApiResult.Success -> {
                val now = System.currentTimeMillis()
                val snapshots = result.data.parks.mapNotNull { snapshot ->
                    val capturedAt = snapshot.capturedAtMillis.takeIf { it > 0L }
                        ?: result.data.generatedAtMillis
                        ?: return@mapNotNull null
                    val currentlyOpen = isParkCurrentlyOpen(
                        openedToday = snapshot.openedToday,
                        openFrom = snapshot.openFrom,
                        closedFrom = snapshot.closedFrom,
                        now = Instant.ofEpochMilli(now),
                    )
                    ParkSnapshotEntity(
                        parkKey = snapshot.parkKey,
                        capturedAtMillis = capturedAt,
                        apiCrowdLevel = snapshot.apiCrowdLevel,
                        calculatedCrowdLevel = snapshot.calculatedCrowdLevel,
                        displayCrowdLevel = snapshot.displayCrowdLevel?.takeIf {
                            currentlyOpen && it in 0f..100f
                        },
                        openedToday = snapshot.openedToday,
                        openFrom = snapshot.openFrom,
                        closedFrom = snapshot.closedFrom,
                        openAttractions = snapshot.openAttractions.takeIf { currentlyOpen } ?: 0,
                        totalAttractions = snapshot.totalAttractions,
                        source = "public",
                    )
                }
                if (snapshots.isNotEmpty()) {
                    parkSnapshotDao.insertAll(snapshots)
                }
                result.data.parks.forEach { snapshot ->
                    val capturedAt = snapshot.capturedAtMillis.takeIf { it > 0L }
                        ?: result.data.generatedAtMillis
                        ?: 0L
                    val canUseAttractions = snapshot.attractions.isNotEmpty() &&
                            now - capturedAt <= RECOMMENDATION_CURRENT_MAX_AGE_MILLIS
                    if (canUseAttractions) {
                        parkDetailDao.replaceWaitingTimes(
                            parkKey = snapshot.parkKey,
                            waitingTimes = snapshot.attractions.map { attraction ->
                                WaitingTimeEntity(
                                    parkKey = snapshot.parkKey,
                                    attractionId = attraction.id,
                                    name = attraction.name,
                                    waitingTime = attraction.value?.takeIf { (attraction.statusCode ?: 0) == 0 },
                                    status = attraction.status ?: statusCodeToApiStatus(attraction.statusCode),
                                    updatedAtMillis = capturedAt,
                                )
                            },
                        )
                    } else if (snapshot.attractions.isNotEmpty()) {
                        parkDetailDao.deleteWaitingTimesForPark(snapshot.parkKey)
                    }
                }
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> {
                when (markersResult) {
                    is ApiResult.Success -> ApiResult.Success(Unit)
                    is ApiResult.Error -> result
                }
            }
        }
    }

    private suspend fun pruneOldLocalParkSnapshots(nowMillis: Long = System.currentTimeMillis()) {
        parkSnapshotDao.deleteOldSnapshots(nowMillis - LOCAL_PARK_SNAPSHOT_RETENTION_MILLIS)
    }

    private fun statusCodeToApiStatus(statusCode: Int?): String {
        return when (statusCode) {
            0 -> "opened"
            -1 -> "closed"
            -2 -> "closed_weather"
            -3 -> "maintenance"
            else -> "unknown"
        }
    }

    private fun buildRecommendationReason(
        crowdLevel: Float?,
        openAttractions: Int,
        totalAttractions: Int,
    ): String {
        val crowdText = crowdLevel?.let { "ca. ${it.toInt()}% Auslastung" } ?: "Auslastung unbekannt"
        val attractionText = if (totalAttractions > 0) {
            "$openAttractions von $totalAttractions Attraktionen offen"
        } else {
            "$openAttractions Attraktionen offen"
        }
        return "$crowdText, $attractionText"
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

    override suspend fun clearCachedData() {
        withContext(ioDispatcher) {
            parkDetailDao.clearCachedDetails()
            parkSnapshotDao.deleteAllSnapshots()
            parkDao.deleteNonFavoriteParks()
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
                        openFrom = it.openFrom,
                        closedFrom = it.closedFrom,
                        openAttractions = it.openAttractions,
                        totalAttractions = it.totalAttractions
                    )
                }
                val summary = de.wartezeiten.app.domain.model.buildParkTrendSummary(crowdSnapshots, System.currentTimeMillis())
                summary.copy(
                    points = summary.points.map { point ->
                        val source = snapshots.firstOrNull { it.capturedAtMillis == point.capturedAtMillis }?.source
                        point.copy(
                            source = if (source == "public") {
                                de.wartezeiten.app.domain.model.ParkTrendSource.PublicHistory
                            } else {
                                de.wartezeiten.app.domain.model.ParkTrendSource.Local
                            }
                        )
                    },
                    hasPublicHistory = snapshots.any { it.source == "public" },
                )
            }
            .flowOn(ioDispatcher)
    }

    override suspend fun refreshPublicTrendHistory(parkKey: String): ApiResult<Unit> = withContext(ioDispatcher) {
        when (val result = safeApiCall { publicAppDataApi.getTrendHistory(parkKey) }) {
            is ApiResult.Success -> {
                val remoteParks = result.data.parks
                    .filter { it.parkKey == parkKey }
                if (remoteParks.isEmpty()) return@withContext ApiResult.Success(Unit)

                val snapshots = remoteParks
                    .flatMap { parkHistory ->
                        parkHistory.snapshots.mapNotNull { snapshot ->
                            val currentlyOpen = isParkCurrentlyOpen(
                                openedToday = snapshot.openedToday,
                                openFrom = snapshot.openFrom,
                                closedFrom = snapshot.closedFrom,
                                now = Instant.ofEpochMilli(snapshot.capturedAtMillis),
                            )
                            val displayLevel = snapshot.displayCrowdLevel?.takeIf {
                                currentlyOpen && it in 0f..100f
                            }
                            if (displayLevel == null) return@mapNotNull null
                            ParkSnapshotEntity(
                                parkKey = parkHistory.parkKey,
                                capturedAtMillis = snapshot.capturedAtMillis,
                                apiCrowdLevel = snapshot.apiCrowdLevel,
                                calculatedCrowdLevel = snapshot.calculatedCrowdLevel,
                                displayCrowdLevel = displayLevel,
                                openedToday = snapshot.openedToday,
                                openFrom = snapshot.openFrom,
                                closedFrom = snapshot.closedFrom,
                                openAttractions = snapshot.openAttractions.takeIf { currentlyOpen } ?: 0,
                                totalAttractions = snapshot.totalAttractions,
                                source = "public",
                            )
                        }
                    }
                if (snapshots.isNotEmpty()) {
                    parkSnapshotDao.insertAll(snapshots)
                }
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> result
        }
    }

    override suspend fun getStatisticsIndex(): ApiResult<de.wartezeiten.app.domain.model.StatisticsIndex> = withContext(ioDispatcher) {
        when (val result = safeApiCall { publicAppDataApi.getStatisticsIndex() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Error -> result
        }
    }

    override suspend fun getAttractionHistoryDay(
        parkKey: String,
        date: String,
    ): ApiResult<de.wartezeiten.app.domain.model.AttractionHistoryDay> = withContext(ioDispatcher) {
        when (val result = safeApiCall { publicAppDataApi.getAttractionHistoryDay(parkKey, date) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Error -> result
        }
    }
}
