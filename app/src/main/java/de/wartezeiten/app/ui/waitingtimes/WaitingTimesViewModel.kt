package de.wartezeiten.app.ui.waitingtimes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.wartezeiten.app.core.network.ApiResult
import de.wartezeiten.app.core.network.toUserMessage
import de.wartezeiten.app.data.local.PreferencesDataSource
import de.wartezeiten.app.data.local.dao.AttractionNoteDao
import de.wartezeiten.app.data.local.entity.AttractionNoteEntity
import de.wartezeiten.app.domain.model.AttractionStatus
import de.wartezeiten.app.domain.model.AttractionHistoryDay
import de.wartezeiten.app.domain.model.CrowdLevel
import de.wartezeiten.app.domain.model.CrowdLevelEstimate
import de.wartezeiten.app.domain.model.CrowdLevelSource
import de.wartezeiten.app.domain.model.DataFreshness
import de.wartezeiten.app.domain.model.DataQuality
import de.wartezeiten.app.domain.model.HolidayInfo
import de.wartezeiten.app.domain.model.OpeningTimes
import de.wartezeiten.app.domain.model.Park
import de.wartezeiten.app.domain.model.WaitingTime
import de.wartezeiten.app.domain.model.estimateCrowdLevel
import de.wartezeiten.app.domain.model.isParkOpenWithoutWaitingTimeData
import de.wartezeiten.app.domain.model.WeatherInfo
import de.wartezeiten.app.domain.repository.WartezeitenRepository
import de.wartezeiten.app.domain.usecase.RefreshParkDetailUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.inject.Inject

enum class WaitingTimesSort {
    WaitAscending,
    WaitDescending,
    Name
}

enum class AttractionFilter {
    All,
    OpenOnly,
    Maintenance,
    Closed
}

data class WaitingTimesUiState(
    val park: Park? = null,
    val openingTimes: OpeningTimes? = null,
    val crowdLevel: CrowdLevel? = null,
    val crowdEstimate: CrowdLevelEstimate? = null,
    val allWaitingTimes: List<WaitingTime> = emptyList(),
    val waitingTimes: List<WaitingTime> = emptyList(),
    val plannedWaitingTimes: List<WaitingTime> = emptyList(),
    val sort: WaitingTimesSort = WaitingTimesSort.WaitDescending,
    val filter: AttractionFilter = AttractionFilter.All,
    val attractionQuery: String = "",
    val maxWaitMinutes: Int? = null,
    val plannedAttractionIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val lastRefreshed: Long = 0L,
    val dataUpdatedAtMillis: Long = 0L,
    val isShowingOfflineData: Boolean = false,
    val offlineDataAgeMinutes: Long? = null,
    val refreshTrigger: Int = 0,
    val currentLocalTime: Long = System.currentTimeMillis(),
    val localTimeOffsetSeconds: Int? = null,
    val dataQuality: DataQuality? = null,
    val weather: WeatherInfo? = null,
    val holidays: List<HolidayInfo> = emptyList(),
    val parkStatistics: ParkWaitStatistics? = null,
    val forecastByAttractionId: Map<String, AttractionWaitForecast> = emptyMap(),
    val historyByAttractionId: Map<String, List<AttractionWaitForecastPoint>> = emptyMap(),
    val language: String = PreferencesDataSource.DEFAULT_LANGUAGE,
    val highlightedAttractionId: String? = null,
    val highlightedAttractionNote: String = "",
    val isWaitingTimeDataLikelyMissing: Boolean = false,
    val usedFallbackWaitTimeSource: Boolean = false,
    val refreshError: String? = null,
    val isStatisticsLoading: Boolean = false,
)

private data class DetailAuxState(
    val parkStatistics: ParkWaitStatistics?,
    val historyDays: List<AttractionHistoryDay>,
    val note: AttractionNoteEntity?,
)

@HiltViewModel
class WaitingTimesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WartezeitenRepository,
    private val refreshParkDetail: RefreshParkDetailUseCase,
    private val preferences: PreferencesDataSource,
    private val attractionNoteDao: AttractionNoteDao,
) : ViewModel() {
    private val parkKey: String = checkNotNull(savedStateHandle["parkKey"])
    private val highlightedAttractionId: String? = savedStateHandle["attractionId"]
    private val clearedAttractionDetail = MutableStateFlow(false)
    private val sort = MutableStateFlow(WaitingTimesSort.WaitDescending)
    private val filter = MutableStateFlow(AttractionFilter.All)
    private val attractionQuery = MutableStateFlow("")
    private val maxWaitMinutes = MutableStateFlow<Int?>(null)
    private val plannedAttractionIds = MutableStateFlow<Set<String>>(emptySet())
    private val isLoading = MutableStateFlow(false)
    private val isStatisticsLoading = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val refreshError = MutableStateFlow<String?>(null)
    private val lastRefreshed = MutableStateFlow(0L)
    private val refreshTrigger = MutableStateFlow(0)
    private val currentLanguage = MutableStateFlow(PreferencesDataSource.DEFAULT_LANGUAGE)
    private val parkStatistics = MutableStateFlow<ParkWaitStatistics?>(null)
    private val historyDays = MutableStateFlow<List<AttractionHistoryDay>>(emptyList())
    private val highlightedAttractionNote = highlightedAttractionId?.let {
        attractionNoteDao.observeNote(parkKey, it)
    } ?: flowOf(null)
    private var refreshJob: Job? = null

    // Aktuelle Uhrzeit Flow (aktualisiert jede Minute)
    private val currentLocalTime = flow {
        while (currentCoroutineContext().isActive) {
            emit(System.currentTimeMillis())
            val now = System.currentTimeMillis()
            val delayMs = (60_000 - (now % 60_000)).coerceAtLeast(1000L)
            delay(delayMs)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    // Combine in zwei Stufen (Flow.combine unterstützt max. 5 Parameter direkt)
    private val filterState = combine(sort, filter, attractionQuery, maxWaitMinutes, plannedAttractionIds) { s, f, query, maxWait, plan ->
        FilterState(s, f, query, maxWait, plan)
    }

    private val timeAndLanguageState = combine(
        currentLocalTime,
        currentLanguage,
        repository.observeFallbackWaitTimeSourceParkKeys(),
    ) { currentTime, language, fallbackKeys ->
        Triple(currentTime, language, parkKey in fallbackKeys)
    }

    private val refreshState = combine(lastRefreshed, refreshTrigger, refreshError) { r, t, re ->
        object { val lastRefreshed = r; val refreshTrigger = t; val refreshError = re }
    }

    private val loadState = combine(
        isLoading,
        isStatisticsLoading,
        errorMessage,
        refreshState,
        timeAndLanguageState
    ) { l, sl, e, rs, tl ->
        object {
            val isLoading = l
            val isStatisticsLoading = sl
            val errorMessage = e
            val refreshError = rs.refreshError
            val lastRefreshed = rs.lastRefreshed
            val refreshTrigger = rs.refreshTrigger
            val currentTime = tl.first
            val language = tl.second
            val usedFallbackWaitTimeSource = tl.third
        }
    }

    private val detailAuxState = combine(parkStatistics, historyDays, highlightedAttractionNote) { statistics, days, note ->
        DetailAuxState(statistics, days, note)
    }

    val uiState = combine(
        repository.observeParkDetail(parkKey),
        filterState,
        loadState,
        clearedAttractionDetail,
        detailAuxState,
    ) { detail, filterState, status, cleared, aux ->
        val (sort, filter, query, maxWait, plannedIds) = filterState
        val normalizedQuery = query.normalizedSearchText()
        val filtered = detail.waitingTimes
            .filter { wt ->
                when (filter) {
                    AttractionFilter.All -> true
                    AttractionFilter.OpenOnly -> wt.status == AttractionStatus.Opened
                    AttractionFilter.Maintenance -> wt.status == AttractionStatus.Maintenance
                    AttractionFilter.Closed -> wt.status == AttractionStatus.Closed ||
                            wt.status == AttractionStatus.ClosedWeather
                }
            }
            .filter { wt -> normalizedQuery.isBlank() || wt.name.normalizedSearchText().contains(normalizedQuery) }
            .filter { wt -> maxWait == null || (wt.waitingTime ?: Int.MAX_VALUE) <= maxWait }
        val plannedAttractions = detail.waitingTimes
            .filter { it.attractionId in plannedIds }
            .sortedWith(
                compareBy<WaitingTime> { it.status != AttractionStatus.Opened }
                    .thenBy { it.waitingTime ?: Int.MAX_VALUE }
                    .thenBy { it.name.lowercase() }
            )

        if (detail.park == null) {
            val dataUpdatedAtMillis = detail.latestDataUpdatedAtMillis()
            val effectiveErrorMessage = status.errorMessage
            WaitingTimesUiState(
                isLoading = status.isLoading,
                errorMessage = effectiveErrorMessage,
                lastRefreshed = status.lastRefreshed,
                dataUpdatedAtMillis = dataUpdatedAtMillis,
                isShowingOfflineData = effectiveErrorMessage != null && dataUpdatedAtMillis > 0L,
                offlineDataAgeMinutes = dataUpdatedAtMillis.toAgeMinutes(),
                refreshTrigger = status.refreshTrigger,
                currentLocalTime = status.currentTime,
                parkStatistics = aux.parkStatistics,
                language = status.language,
                highlightedAttractionId = if (clearedAttractionDetail.value) null else highlightedAttractionId,
                highlightedAttractionNote = aux.note?.note.orEmpty(),
                refreshError = status.refreshError,
                isStatisticsLoading = status.isStatisticsLoading,
            )
        } else {
            val dataUpdatedAtMillis = detail.latestDataUpdatedAtMillis()
            val hasOpenAttraction = detail.waitingTimes.any { it.status == AttractionStatus.Opened }
            val canCalculateCrowdLevel = detail.openingTimes?.opened == true && hasOpenAttraction
            val crowdEstimate = if (canCalculateCrowdLevel) {
                estimateCrowdLevel(
                    waitingTimes = detail.waitingTimes,
                    apiCrowdLevel = detail.crowdLevel?.level
                )
            } else {
                CrowdLevelEstimate(level = null, source = CrowdLevelSource.None)
            }
            val isWaitingTimeDataLikelyMissing = isParkOpenWithoutWaitingTimeData(
                openedToday = detail.openingTimes?.opened,
                openFrom = detail.openingTimes?.from,
                closedFrom = detail.openingTimes?.to,
                hasOpenAttraction = hasOpenAttraction,
                now = Instant.ofEpochMilli(status.currentTime),
            )
            val today = detail.localTimeOffsetSeconds()?.let { sec ->
                val zone = ZoneOffset.ofTotalSeconds(sec)
                Instant.ofEpochMilli(status.currentTime).atZone(zone).toLocalDate().toString()
            } ?: LocalDate.now().toString()
            val openWaitingTimes = detail.waitingTimes.filter { it.status == AttractionStatus.Opened }
            val suppressServerErrorForFallback = status.usedFallbackWaitTimeSource && detail.waitingTimes.isNotEmpty()
            val effectiveErrorMessage = status.errorMessage.takeUnless { suppressServerErrorForFallback }
            val forecasts = buildAttractionWaitForecasts(
                waitingTimes = openWaitingTimes,
                historyDays = aux.historyDays.filter { it.date != today },
                currentTimeMillis = status.currentTime,
                localTimeOffsetSeconds = detail.localTimeOffsetSeconds(),
            )
            WaitingTimesUiState(
                park = detail.park,
                openingTimes = detail.openingTimes,
                crowdLevel = detail.crowdLevel,
                crowdEstimate = crowdEstimate,
                allWaitingTimes = detail.waitingTimes,
                waitingTimes = filtered.sortedBy(sort),
                plannedWaitingTimes = plannedAttractions,
                weather = detail.weather,
                holidays = detail.holidays,
                sort = sort,
                filter = filter,
                attractionQuery = query,
                maxWaitMinutes = maxWait,
                plannedAttractionIds = plannedIds,
                isLoading = status.isLoading,
                errorMessage = effectiveErrorMessage,
                lastRefreshed = status.lastRefreshed,
                dataUpdatedAtMillis = dataUpdatedAtMillis,
                isShowingOfflineData = effectiveErrorMessage != null && dataUpdatedAtMillis > 0L,
                offlineDataAgeMinutes = dataUpdatedAtMillis.toAgeMinutes(),
                refreshTrigger = status.refreshTrigger,
                currentLocalTime = status.currentTime,
                localTimeOffsetSeconds = detail.localTimeOffsetSeconds(),
                dataQuality = DataQuality(
                    lastUpdated = dataUpdatedAtMillis,
                    freshness = if (System.currentTimeMillis() - dataUpdatedAtMillis < 300_000) DataFreshness.Fresh else DataFreshness.Stale,
                    confidenceScore = if (detail.crowdLevel != null) 0.9f else 0.7f
                ),
                parkStatistics = aux.parkStatistics,
                forecastByAttractionId = forecasts,
                historyByAttractionId = detail.waitingTimes.associate { waitingTime ->
                    waitingTime.attractionId to buildAttractionHistorySeries(waitingTime.attractionId, aux.historyDays, today)
                },
                language = status.language,
                highlightedAttractionId = if (clearedAttractionDetail.value) null else highlightedAttractionId,
                highlightedAttractionNote = aux.note?.note.orEmpty(),
                isWaitingTimeDataLikelyMissing = isWaitingTimeDataLikelyMissing,
                usedFallbackWaitTimeSource = status.usedFallbackWaitTimeSource,
                refreshError = status.refreshError,
                isStatisticsLoading = status.isStatisticsLoading,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WaitingTimesUiState(isLoading = true)
    )

    init {
        recordRecentPark()
        restoreSavedFilters()
        observeLanguage()
        refreshParkStatistics()
        startAutoRefresh()
    }

    private fun recordRecentPark() {
        viewModelScope.launch {
            preferences.addRecentParkKey(parkKey)
        }
    }

    private fun refreshParkStatistics() {
        viewModelScope.launch {
            isStatisticsLoading.value = true
            val indexResult = repository.getStatisticsIndex()
            if (indexResult !is ApiResult.Success) {
                isStatisticsLoading.value = false
                return@launch
            }
            val parks = repository.observeParks(null).first()
            val selectedPark = parks.firstOrNull { it.id == parkKey || it.uuid == parkKey }
            val candidates = listOfNotNull(parkKey, selectedPark?.id, selectedPark?.uuid, selectedPark?.name)
                .map { it.normalizedParkKey() }
                .toSet()
            val indexedPark = indexResult.data.parks.firstOrNull {
                it.parkKey.normalizedParkKey() in candidates
            }
            if (indexedPark == null) {
                isStatisticsLoading.value = false
                return@launch
            }
            val today = parkCurrentDateFromIndex(indexedPark.dates, indexedPark.latestDate)
            val statisticDate = today.takeIf { it in indexedPark.dates }
            val comparisonDates = indexedPark.dates.asReversed()
                .filter { it != today }
                .take(7)
            val datesToLoad = (listOfNotNull(statisticDate) + comparisonDates).distinct()
            val loadedDays = coroutineScope {
                datesToLoad.map { historyDate ->
                    async { repository.getAttractionHistoryDay(indexedPark.parkKey, historyDate) }
                }.awaitAll()
            }.filterIsInstance<ApiResult.Success<AttractionHistoryDay>>().map { it.data }
            parkStatistics.value = statisticDate?.let { date ->
                loadedDays.firstOrNull { it.date == date }?.toParkWaitStatistics()
            }
            historyDays.value = loadedDays
            isStatisticsLoading.value = false
        }
    }

    private fun observeLanguage() {
        viewModelScope.launch {
            preferences.language.distinctUntilChanged().collect { language ->
                currentLanguage.value = language
                refresh(language = language, showFeedback = false)
            }
        }
    }

    /** Automatische Aktualisierung jede Minute */
    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (isActive) {
                delay(60_000L)
                refresh(language = currentLanguage.value, silent = true)
            }
        }
    }

    fun setSort(value: WaitingTimesSort) {
        sort.value = value
        viewModelScope.launch { preferences.setWaitingTimesSort(value.name) }
    }

    fun setFilter(value: AttractionFilter) {
        filter.value = value
        viewModelScope.launch { preferences.setWaitingTimesFilter(value.name) }
    }

    fun setAttractionQuery(value: String) {
        attractionQuery.value = value
    }

    fun setMaxWait(value: Int?) {
        maxWaitMinutes.value = value
        viewModelScope.launch { preferences.setWaitingTimesMaxWait(value) }
    }

    fun togglePlannedAttraction(attractionId: String) {
        plannedAttractionIds.value = if (attractionId in plannedAttractionIds.value) {
            plannedAttractionIds.value - attractionId
        } else {
            plannedAttractionIds.value + attractionId
        }
    }

    fun dismissError() {
        errorMessage.value = null
    }

    fun toggleFavorite() {
        val currentPark = uiState.value.park ?: return
        viewModelScope.launch {
            repository.toggleFavorite(currentPark.id, !currentPark.isFavorite)
        }
    }

    fun saveAttractionNote(note: String) {
        val attractionId = highlightedAttractionId ?: return
        viewModelScope.launch {
            val cleaned = note.trim()
            if (cleaned.isBlank()) {
                attractionNoteDao.deleteNote(parkKey, attractionId)
            } else {
                attractionNoteDao.upsertNote(
                    AttractionNoteEntity(
                        parkKey = parkKey,
                        attractionId = attractionId,
                        note = cleaned,
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    fun deleteAttractionNote() {
        val attractionId = highlightedAttractionId ?: return
        viewModelScope.launch {
            attractionNoteDao.deleteNote(parkKey, attractionId)
        }
    }

    fun clearAttractionDetail() {
        clearedAttractionDetail.value = true
    }

    fun refresh(
        language: String = currentLanguage.value,
        silent: Boolean = false,
        showFeedback: Boolean = !silent
    ) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (!silent) isLoading.value = true
            errorMessage.value = null
            refreshError.value = null
            
            val result = refreshParkDetail(parkKey, language, forceRefresh = showFeedback)
            isLoading.value = false // Set to false before trigger
            
            when (result) {
                is ApiResult.Success -> {
                    lastRefreshed.value = System.currentTimeMillis()
                    if (showFeedback) {
                        refreshTrigger.value += 1
                    }
                    if (!silent) refreshParkStatistics()
                }
                is ApiResult.Error -> {
                    val currentState = uiState.value
                    val hasFallbackWaitTimes = currentState.usedFallbackWaitTimeSource && currentState.allWaitingTimes.isNotEmpty()
                    val userMessage = result.type.toUserMessage(currentLanguage.value)
                    
                    if (!hasFallbackWaitTimes && (!silent || currentState.allWaitingTimes.isEmpty())) {
                        errorMessage.value = userMessage
                    }
                    
                    if (showFeedback) {
                        refreshError.value = userMessage
                        refreshTrigger.value += 1
                    }
                }
            }
        }
    }

    private fun List<WaitingTime>.sortedBy(sort: WaitingTimesSort): List<WaitingTime> {
        return when (sort) {
            WaitingTimesSort.WaitAscending -> sortedWith(
                compareBy<WaitingTime> { it.status != AttractionStatus.Opened }
                    .thenBy { it.waitingTime ?: Int.MAX_VALUE }
                    .thenBy { it.name.lowercase() }
            )
            WaitingTimesSort.WaitDescending -> sortedWith(
                compareBy<WaitingTime> { it.status != AttractionStatus.Opened }
                    .thenByDescending { it.waitingTime ?: -1 }
                    .thenBy { it.name.lowercase() }
            )
            WaitingTimesSort.Name -> sortedBy { it.name.lowercase() }
        }
    }

    private fun restoreSavedFilters() {
        viewModelScope.launch {
            combine(
                preferences.waitingTimesSort,
                preferences.waitingTimesFilter,
                preferences.waitingTimesMaxWait,
            ) { savedSort, savedFilter, savedMaxWait ->
                SavedFilterState(savedSort, savedFilter, savedMaxWait)
            }
                .take(1)
                .collect { saved ->
                    saved.sort?.let { value ->
                        sort.value = WaitingTimesSort.entries.firstOrNull { it.name == value } ?: WaitingTimesSort.WaitDescending
                    }
                    saved.filter?.let { value ->
                        filter.value = AttractionFilter.entries.firstOrNull { it.name == value } ?: AttractionFilter.All
                    }
                    maxWaitMinutes.value = saved.maxWait
                }
        }
    }

    private fun de.wartezeiten.app.domain.model.ParkDetail.localTimeOffsetSeconds(): Int? {
        val candidates = listOfNotNull(
            openingTimes?.from,
            openingTimes?.to,
            crowdLevel?.timestamp,
        )
        return candidates.firstNotNullOfOrNull { value ->
            runCatching { OffsetDateTime.parse(value).offset.totalSeconds }.getOrNull()
        }
    }

    private fun de.wartezeiten.app.domain.model.ParkDetail.latestDataUpdatedAtMillis(): Long {
        return listOfNotNull(
            park?.updatedAtMillis?.takeIf { it > 0L },
            waitingTimes.maxOfOrNull { it.updatedAtMillis }?.takeIf { it > 0L },
        ).maxOrNull() ?: 0L
    }

    private fun Long.toAgeMinutes(): Long? {
        if (this <= 0L) return null
        return ((System.currentTimeMillis() - this).coerceAtLeast(0L) / 60_000L)
    }

    private fun parkCurrentDateFromIndex(dates: List<String>, latestDate: String?): String {
        val now = LocalDate.now()
        val candidates = listOf(
            now.toString(),
            now.minusDays(1).toString(),
            now.plusDays(1).toString()
        )
        val datesSet = dates.toSet()
        return candidates.firstOrNull { it in datesSet } ?: latestDate ?: now.toString()
    }
}

private fun String.normalizedParkKey(): String {
    return lowercase()
        .replace("ä", "ae")
        .replace("ö", "oe")
        .replace("ü", "ue")
        .replace("ß", "ss")
        .filter { it.isLetterOrDigit() }
}

private data class FilterState(
    val sort: WaitingTimesSort,
    val filter: AttractionFilter,
    val query: String,
    val maxWait: Int?,
    val plannedAttractionIds: Set<String>,
)

private data class SavedFilterState(
    val sort: String?,
    val filter: String?,
    val maxWait: Int?,
)

private fun String.normalizedSearchText(): String {
    return lowercase().filter { it.isLetterOrDigit() }
}
