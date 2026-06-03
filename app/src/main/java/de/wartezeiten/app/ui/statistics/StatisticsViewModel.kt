package de.wartezeiten.app.ui.statistics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.wartezeiten.app.core.network.ApiResult
import de.wartezeiten.app.core.network.toUserMessage
import de.wartezeiten.app.domain.model.AttractionHistoryDay
import de.wartezeiten.app.domain.model.AttractionHistorySummary
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
    val index: StatisticsIndex = StatisticsIndex(generatedAtMillis = 0L, parks = emptyList()),
    val selectedParkKey: String? = null,
    val selectedDate: String = LocalDate.now().toString(),
    val selectedAttractionId: String? = null,
    val day: AttractionHistoryDay? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val availableDates: List<String>
        get() = index.parks.firstOrNull { it.parkKey == selectedParkKey }?.dates.orEmpty()

    val selectedPark: Park?
        get() = parks.firstOrNull { it.id == selectedParkKey || it.uuid == selectedParkKey }

    val selectedAttraction: AttractionHistorySummary?
        get() = day?.attractions?.firstOrNull { it.id == selectedAttractionId }

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

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val repository: WartezeitenRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val initialParkKey: String? = savedStateHandle["parkKey"]
    private val initialAttractionId: String? = savedStateHandle["attractionId"]
    private val parks = repository.observeParks(null)
    private val mutableState = MutableStateFlow(StatisticsUiState(isLoading = true))

    val uiState = combine(parks, mutableState) { parkList, state ->
        state.copy(parks = parkList)
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
                        ?.takeIf { key -> result.data.parks.any { it.parkKey == key } }
                        ?: initialParkKey?.takeIf { key -> result.data.parks.any { it.parkKey == key } }
                        ?: result.data.parks.firstOrNull()?.parkKey
                    val selectedDate = selectedParkKey
                        ?.let { key -> result.data.parks.firstOrNull { it.parkKey == key }?.latestDate }
                        ?: LocalDate.now().toString()
                    mutableState.update {
                        it.copy(
                            index = result.data,
                            selectedParkKey = selectedParkKey,
                            selectedDate = selectedDate,
                            day = null,
                            selectedAttractionId = current.selectedAttractionId ?: initialAttractionId,
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
        val parkIndex = mutableState.value.index.parks.firstOrNull { it.parkKey == parkKey }
        mutableState.update {
            it.copy(
                selectedParkKey = parkKey,
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
            it.copy(selectedDate = date, selectedAttractionId = null, day = null, errorMessage = null)
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
                            selectedAttractionId = state.selectedAttractionId
                                ?.takeIf { id -> result.data.attractions.any { it.id == id } }
                                ?: result.data.attractions.maxByOrNull { it.sampleCount }?.id,
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
}
