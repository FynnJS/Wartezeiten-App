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
        get() = parks.firstOrNull { it.id == selectedParkKey || it.uuid == selectedParkKey }

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
                    val selectedParkKey = current.selectedParkKey
                        ?: initialParkKey
                        ?: result.data.parks.firstOrNull()?.parkKey
                    val selectedDate = selectedParkKey
                        ?.let { key -> result.data.parks.firstOrNull { it.parkKey == key }?.latestDate }
                        ?: LocalDate.now().toString()
                    if (selectedParkKey == null) {
                        mutableState.update {
                            it.copy(
                                index = result.data,
                                selectedParkKey = initialParkKey,
                                selectedDate = selectedDate,
                                day = null,
                                selectedAttractionId = initialAttractionId,
                                isLoading = false,
                            )
                        }
                        return@launch
                    }
                    mutableState.update {
                        it.copy(
                            index = result.data,
                            selectedParkKey = selectedParkKey,
                            selectedDate = selectedDate,
                            day = null,
                            selectedAttractionId = selectKnownAttractionId(
                                parkKey = selectedParkKey,
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
        val parkIndex = state.index.parks.firstOrNull { it.parkKey == parkKey }
        mutableState.update {
            it.copy(
                selectedParkKey = parkKey,
                selectedDate = parkIndex?.latestDate ?: LocalDate.now().toString(),
                selectedAttractionId = selectKnownAttractionId(
                    parkKey = parkKey,
                    preferredId = null,
                    index = state.index,
                    currentAttractions = currentAttractions.value,
                ),
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
        if (preferredId != null && preferredId in availableIds) return preferredId
        return day?.attractions?.maxByOrNull { it.sampleCount }?.id
            ?: index.parks
                .firstOrNull { it.parkKey == parkKey }
                ?.attractions
                ?.firstOrNull()
                ?.id
            ?: currentAttractions
                .firstOrNull { it.parkKey == parkKey }
                ?.attractionId
            ?: preferredId
    }
}
