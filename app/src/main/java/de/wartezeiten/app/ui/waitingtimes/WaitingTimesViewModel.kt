package de.wartezeiten.app.ui.waitingtimes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.wartezeiten.app.core.network.ApiResult
import de.wartezeiten.app.core.network.toUserMessage
import de.wartezeiten.app.domain.model.AttractionStatus
import de.wartezeiten.app.domain.model.CrowdLevel
import de.wartezeiten.app.domain.model.CrowdLevelEstimate
import de.wartezeiten.app.domain.model.CrowdLevelSource
import de.wartezeiten.app.domain.model.DataFreshness
import de.wartezeiten.app.domain.model.DataQuality
import de.wartezeiten.app.domain.model.HolidayInfo
import de.wartezeiten.app.domain.model.OpeningTimes
import de.wartezeiten.app.domain.model.Park
import de.wartezeiten.app.domain.model.ParkTrendSummary
import de.wartezeiten.app.domain.model.WaitingTime
import de.wartezeiten.app.domain.model.estimateCrowdLevel
import de.wartezeiten.app.domain.model.WeatherInfo
import de.wartezeiten.app.domain.repository.WartezeitenRepository
import de.wartezeiten.app.domain.usecase.RefreshParkDetailUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
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
    val sort: WaitingTimesSort = WaitingTimesSort.WaitDescending,
    val filter: AttractionFilter = AttractionFilter.All,
    val maxWaitMinutes: Int? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val lastRefreshed: Long = 0L,
    val refreshTrigger: Int = 0,
    val currentLocalTime: Long = System.currentTimeMillis(),
    val localTimeOffsetSeconds: Int? = null,
    val dataQuality: DataQuality? = null,
    val weather: WeatherInfo? = null,
    val holidays: List<HolidayInfo> = emptyList(),
    val trendSummary: ParkTrendSummary = ParkTrendSummary.Empty,
)

@HiltViewModel
class WaitingTimesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WartezeitenRepository,
    private val refreshParkDetail: RefreshParkDetailUseCase
) : ViewModel() {
    private val parkKey: String = checkNotNull(savedStateHandle["parkKey"])
    private val sort = MutableStateFlow(WaitingTimesSort.WaitDescending)
    private val filter = MutableStateFlow(AttractionFilter.All)
    private val maxWaitMinutes = MutableStateFlow<Int?>(null)
    private val isLoading = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val lastRefreshed = MutableStateFlow(0L)
    private val refreshTrigger = MutableStateFlow(0)

    // Aktuelle Uhrzeit Flow (aktualisiert jede Minute)
    private val currentLocalTime = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000 - (System.currentTimeMillis() % 60_000))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    // Combine in zwei Stufen (Flow.combine unterstützt max. 5 Parameter direkt)
    private val filterState = combine(sort, filter, maxWaitMinutes) { s, f, maxWait ->
        Triple(s, f, maxWait)
    }

    private val loadState = combine(isLoading, errorMessage, lastRefreshed, refreshTrigger, currentLocalTime) { l, e, r, t, c ->
        object {
            val isLoading = l
            val errorMessage = e
            val lastRefreshed = r
            val refreshTrigger = t
            val currentTime = c
        }
    }

    val uiState = combine(
        repository.observeParkDetail(parkKey),
        filterState,
        loadState,
        repository.getParkTrendSummary(parkKey)
    ) { detail, (sort, filter, maxWait), status, trendSummary ->
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
            .filter { wt -> maxWait == null || (wt.waitingTime ?: Int.MAX_VALUE) <= maxWait }

        if (detail.park == null) {
            WaitingTimesUiState(
                isLoading = status.isLoading,
                errorMessage = status.errorMessage,
                lastRefreshed = status.lastRefreshed,
                refreshTrigger = status.refreshTrigger,
                currentLocalTime = status.currentTime,
                trendSummary = trendSummary,
            )
        } else {
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
            WaitingTimesUiState(
                park = detail.park,
                openingTimes = detail.openingTimes,
                crowdLevel = detail.crowdLevel,
                crowdEstimate = crowdEstimate,
                allWaitingTimes = detail.waitingTimes,
                waitingTimes = filtered.sortedBy(sort),
                weather = detail.weather,
                holidays = detail.holidays,
                sort = sort,
                filter = filter,
                maxWaitMinutes = maxWait,
                isLoading = status.isLoading,
                errorMessage = status.errorMessage,
                lastRefreshed = status.lastRefreshed,
                refreshTrigger = status.refreshTrigger,
                currentLocalTime = status.currentTime,
                localTimeOffsetSeconds = detail.localTimeOffsetSeconds(),
                dataQuality = DataQuality(
                    lastUpdated = status.lastRefreshed,
                    freshness = if (System.currentTimeMillis() - status.lastRefreshed < 300_000) DataFreshness.Fresh else DataFreshness.Stale,
                    confidenceScore = if (detail.crowdLevel != null) 0.9f else 0.7f
                ),
                trendSummary = if (canCalculateCrowdLevel) trendSummary else ParkTrendSummary.Empty,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WaitingTimesUiState(isLoading = true)
    )

    init {
        refresh(showFeedback = false)
        startAutoRefresh()
    }

    /** Automatische Aktualisierung jede Minute */
    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (isActive) {
                delay(60_000L)
                refresh(silent = true)
            }
        }
    }

    fun setSort(value: WaitingTimesSort) { sort.value = value }
    fun setFilter(value: AttractionFilter) { filter.value = value }
    fun setMaxWait(value: Int?) { maxWaitMinutes.value = value }

    fun dismissError() {
        errorMessage.value = null
    }

    fun toggleFavorite() {
        val currentPark = uiState.value.park ?: return
        viewModelScope.launch {
            repository.toggleFavorite(currentPark.id, !currentPark.isFavorite)
        }
    }

    fun refresh(
        language: String = "de",
        silent: Boolean = false,
        showFeedback: Boolean = !silent
    ) {
        viewModelScope.launch {
            if (!silent) isLoading.value = true
            errorMessage.value = null
            when (val result = refreshParkDetail(parkKey, language)) {
                is ApiResult.Success -> {
                    lastRefreshed.value = System.currentTimeMillis()
                    if (showFeedback) refreshTrigger.value += 1
                }
                is ApiResult.Error -> errorMessage.value = result.type.toUserMessage()
            }
            isLoading.value = false
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
}
