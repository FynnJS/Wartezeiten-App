package de.wartezeiten.app.ui.statistics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.wartezeiten.app.core.network.ApiResult
import de.wartezeiten.app.core.network.toUserMessage
import de.wartezeiten.app.domain.model.AttractionHistoryDay
import de.wartezeiten.app.domain.model.AttractionHistorySummary
import de.wartezeiten.app.domain.model.CurrentAttractionSearchEntry
import de.wartezeiten.app.domain.model.Park
import de.wartezeiten.app.domain.model.StatisticsIndex
import de.wartezeiten.app.domain.repository.WartezeitenRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class StatisticsUiState(
    val parks: List<Park> = emptyList(),
    val currentAttractions: List<CurrentAttractionSearchEntry> = emptyList(),
    val index: StatisticsIndex = StatisticsIndex(generatedAtMillis = 0L, parks = emptyList()),
    val selectedParkKey: String? = null,
    val selectedDate: String = LocalDate.now().toString(),
    val selectedAttractionId: String? = null,
    val day: AttractionHistoryDay? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val availableDates: List<String>
        get() = index.parks.firstOrNull { it.parkKey == selectedParkKey }?.dates
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(selectedDate)

    val selectedPark: Park?
        get() = parks.firstOrNull { park -> park.matchesParkKey(selectedParkKey) }

    val selectedAttraction: AttractionHistorySummary?
        get() = day?.attractions?.firstOrNull { it.id == selectedAttractionId }

    val selectedAttractionName: String?
        get() = selectedAttraction?.name
            ?: index.parks
                .firstOrNull { it.parkKey == selectedParkKey }
                ?.attractions
                ?.firstOrNull { it.id == selectedAttractionId }
                ?.name
            ?: currentAttractions
                .firstOrNull { it.parkKey == selectedParkKey && it.attractionId == selectedAttractionId }
                ?.name

    val attractionOptions: List<StatisticsAttractionOption>
        get() {
            val dayOptions = day?.attractions.orEmpty().map {
                    StatisticsAttractionOption(id = it.id, name = it.name)
            }
            val indexOptions = index.parks
                .firstOrNull { it.parkKey == selectedParkKey }
                ?.attractions
                .orEmpty()
                .map { StatisticsAttractionOption(id = it.id, name = it.name) }
            val currentOptions = currentAttractions
                .filter { it.parkKey == selectedParkKey }
                .map { StatisticsAttractionOption(id = it.attractionId, name = it.name) }
            return (dayOptions + indexOptions + currentOptions)
                .distinctBy { it.id }
                .sortedBy { it.name.lowercase() }
        }

    val selectedSeries: List<AttractionChartPoint>
        get() {
            val attractionId = selectedAttractionId ?: return emptyList()
            return day?.snapshots.orEmpty().mapNotNull { snapshot ->
                val point = snapshot.attractions.firstOrNull { it.id == attractionId } ?: return@mapNotNull null
                AttractionChartPoint(
                    capturedAtMillis = snapshot.capturedAtMillis,
                    value = point.value,
                    statusCode = point.statusCode,
                )
            }
        }

    val parkSeries: List<ParkChartPoint>
        get() = day?.snapshots.orEmpty().mapNotNull { snapshot ->
            val openValues = snapshot.attractions
                .filter { it.statusCode == 0 && it.value >= 0 }
                .map { it.value }
            if (openValues.isEmpty()) return@mapNotNull null
            ParkChartPoint(
                capturedAtMillis = snapshot.capturedAtMillis,
                averageWaitMinutes = openValues.average().toFloat(),
                openAttractionCount = openValues.size,
            )
        }

    val parkStatistics: ParkStatisticsSummary?
        get() {
            val series = parkSeries
            if (series.isEmpty()) return null
            val averages = series.map { it.averageWaitMinutes }
            return ParkStatisticsSummary(
                averageWaitMinutes = averages.average().toFloat(),
                minAverageWaitMinutes = averages.minOrNull(),
                maxAverageWaitMinutes = averages.maxOrNull(),
                latestAverageWaitMinutes = series.lastOrNull()?.averageWaitMinutes,
                latestOpenAttractionCount = series.lastOrNull()?.openAttractionCount ?: 0,
                sampleCount = series.size,
            )
        }

    val monthBuckets: List<StatisticsMonthBucket>
        get() = availableDates
            .groupBy { it.take(7) }
            .map { (month, dates) -> StatisticsMonthBucket(month = month, dayCount = dates.size) }
            .sortedByDescending { it.month }
}

data class AttractionChartPoint(
    val capturedAtMillis: Long,
    val value: Int,
    val statusCode: Int,
)

data class ParkChartPoint(
    val capturedAtMillis: Long,
    val averageWaitMinutes: Float,
    val openAttractionCount: Int,
)

data class ParkStatisticsSummary(
    val averageWaitMinutes: Float,
    val minAverageWaitMinutes: Float?,
    val maxAverageWaitMinutes: Float?,
    val latestAverageWaitMinutes: Float?,
    val latestOpenAttractionCount: Int,
    val sampleCount: Int,
)

data class StatisticsMonthBucket(
    val month: String,
    val dayCount: Int,
)

data class StatisticsAttractionOption(
    val id: String,
    val name: String,
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val repository: WartezeitenRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val initialParkKey: String? = savedStateHandle["parkKey"]
    private val initialAttractionId: String? = savedStateHandle["attractionId"]
    private val parks = repository.observeParks(null)
    private val currentAttractions = repository.observeCurrentAttractions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
    private val mutableState = MutableStateFlow(StatisticsUiState(isLoading = true))

    val uiState = combine(parks, currentAttractions, mutableState) { parkList, attractionList, state ->
        state.copy(parks = parkList, currentAttractions = attractionList)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatisticsUiState(isLoading = true),
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.getStatisticsIndex()) {
                is ApiResult.Success -> {
                    val current = mutableState.value
                    val parkList = parks.first()
                    val selectedParkKey = current.selectedParkKey
                        ?: initialParkKey
                        ?: result.data.parks.firstOrNull()?.parkKey
                    val resolvedParkKey = selectedParkKey.resolveIndexedParkKey(result.data, parkList)
                    val selectedDate = selectedParkKey
                        ?.let { resolvedParkKey }
                        ?.let { key -> result.data.parks.firstOrNull { it.parkKey == key }?.latestDate }
                        ?: LocalDate.now().toString()
                    if (resolvedParkKey == null) {
                        mutableState.update {
                            it.copy(
                                index = result.data,
                                selectedParkKey = null,
                                selectedDate = selectedDate,
                                day = null,
                                selectedAttractionId = null,
                                isLoading = false,
                            )
                        }
                        return@launch
                    }
                    mutableState.update {
                        it.copy(
                            index = result.data,
                            selectedParkKey = resolvedParkKey,
                            selectedDate = selectedDate,
                            day = null,
                            selectedAttractionId = selectKnownAttractionId(
                                parkKey = resolvedParkKey,
                                preferredId = current.selectedAttractionId ?: initialAttractionId,
                                index = result.data,
                                currentAttractions = currentAttractions.value,
                            ),
                        )
                    }
                    loadSelectedDay()
                }
                is ApiResult.Error -> mutableState.update {
                    it.copy(isLoading = false, errorMessage = result.type.toUserMessage())
                }
            }
        }
    }

    fun selectPark(parkKey: String) {
        val state = mutableState.value
        val resolvedParkKey = parkKey.resolveIndexedParkKey(state.index, state.parks) ?: parkKey
        val parkIndex = state.index.parks.firstOrNull { it.parkKey == resolvedParkKey }
        mutableState.update {
            it.copy(
                selectedParkKey = resolvedParkKey,
                selectedDate = parkIndex?.latestDate ?: LocalDate.now().toString(),
                selectedAttractionId = null,
                day = null,
                errorMessage = null,
            )
        }
        loadSelectedDay()
    }

    fun selectDate(date: String) {
        mutableState.update {
            it.copy(selectedDate = date, day = null, errorMessage = null)
        }
        loadSelectedDay()
    }

    fun selectAttraction(attractionId: String) {
        mutableState.update { it.copy(selectedAttractionId = attractionId) }
    }

    fun selectParkStatistics() {
        mutableState.update { it.copy(selectedAttractionId = null) }
    }

    private fun loadSelectedDay() {
        val parkKey = mutableState.value.selectedParkKey ?: return
        val date = mutableState.value.selectedDate
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.getAttractionHistoryDay(parkKey, date)) {
                is ApiResult.Success -> {
                    mutableState.update { state ->
                        state.copy(
                            day = result.data,
                            selectedAttractionId = selectKnownAttractionId(
                                parkKey = parkKey,
                                preferredId = state.selectedAttractionId,
                                index = state.index,
                                currentAttractions = currentAttractions.value,
                                day = result.data,
                            ),
                            isLoading = false,
                        )
                    }
                }
                is ApiResult.Error -> mutableState.update {
                    it.copy(isLoading = false, errorMessage = result.type.toUserMessage())
                }
            }
        }
    }

    private fun selectKnownAttractionId(
        parkKey: String?,
        preferredId: String?,
        index: StatisticsIndex,
        currentAttractions: List<CurrentAttractionSearchEntry>,
        day: AttractionHistoryDay? = null,
    ): String? {
        if (parkKey == null) return preferredId
        if (preferredId == null) return null
        val availableIds = buildSet {
            day?.attractions.orEmpty().forEach { add(it.id) }
            index.parks
                .firstOrNull { it.parkKey == parkKey }
                ?.attractions
                .orEmpty()
                .forEach { add(it.id) }
            currentAttractions
                .filter { it.parkKey == parkKey }
                .forEach { add(it.attractionId) }
        }
        return preferredId.takeIf { it in availableIds }
    }

    private fun String?.resolveIndexedParkKey(
        index: StatisticsIndex,
        parks: List<Park>,
    ): String? {
        if (this == null) return null
        if (index.parks.any { it.parkKey == this }) return this
        val park = parks.firstOrNull { it.id == this || it.uuid == this }
        val candidates = listOfNotNull(this, park?.id, park?.uuid, park?.name)
            .map { it.normalizedParkKey() }
            .toSet()
        return index.parks.firstOrNull { parkIndex ->
            parkIndex.parkKey.normalizedParkKey() in candidates
        }?.parkKey
    }
}

private fun Park.matchesParkKey(parkKey: String?): Boolean {
    if (parkKey == null) return false
    val normalizedKey = parkKey.normalizedParkKey()
    return id == parkKey ||
            uuid == parkKey ||
            id.normalizedParkKey() == normalizedKey ||
            uuid.normalizedParkKey() == normalizedKey ||
            name.normalizedParkKey() == normalizedKey
}

private fun String.normalizedParkKey(): String {
    return lowercase()
        .replace("ä", "ae")
        .replace("ö", "oe")
        .replace("ü", "ue")
        .replace("ß", "ss")
        .filter { it.isLetterOrDigit() }
}
