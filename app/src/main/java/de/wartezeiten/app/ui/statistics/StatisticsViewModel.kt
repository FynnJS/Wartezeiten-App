package de.wartezeiten.app.ui.statistics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.wartezeiten.app.core.network.ApiResult
import de.wartezeiten.app.core.network.NetworkError
import de.wartezeiten.app.core.network.toUserMessage
import de.wartezeiten.app.data.local.PreferencesDataSource
import de.wartezeiten.app.domain.model.AttractionHistoryPoint
import de.wartezeiten.app.domain.model.AttractionHistoryDay
import de.wartezeiten.app.domain.model.AttractionHistorySnapshot
import de.wartezeiten.app.domain.model.AttractionHistorySummary
import de.wartezeiten.app.domain.model.AttractionStatus
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
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
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
        get() {
            val indexedDates = index.parks.firstOrNull { it.parkKey == selectedParkKey }?.dates.orEmpty()
            val today = LocalDate.now().toString()
            return (indexedDates + selectedDate + today)
                .filter { it.isNotBlank() }
                .distinct()
        }

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
            return day?.operatingSnapshots().orEmpty().mapNotNull { snapshot ->
                val point = snapshot.attractions.firstOrNull { it.id == attractionId } ?: return@mapNotNull null
                AttractionChartPoint(
                    capturedAtMillis = snapshot.capturedAtMillis,
                    value = point.value,
                    statusCode = point.statusCode,
                )
            }
        }

    val parkSeries: List<ParkChartPoint>
        get() = day?.operatingSnapshots().orEmpty().mapNotNull { snapshot ->
            val openValues = snapshot.attractions
                .filter { it.isOpenWaitPoint }
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
    private val preferences: PreferencesDataSource,
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
            repository.refreshPublicAppData()
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
                        ?.let { key ->
                            val parkIndex = result.data.parks.firstOrNull { it.parkKey == key }
                            chooseInitialDate(
                                parkKey = key,
                                parkIndexDates = parkIndex?.dates.orEmpty(),
                                latestDate = parkIndex?.latestDate,
                                currentDate = current.selectedDate,
                                currentAttractions = currentAttractions.value,
                            )
                        }
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
                is ApiResult.Error -> {
                    val language = preferences.language.first()
                    mutableState.update {
                        it.copy(isLoading = false, errorMessage = result.type.toUserMessage(language))
                    }
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
                selectedDate = chooseInitialDate(
                    parkKey = resolvedParkKey,
                    parkIndexDates = parkIndex?.dates.orEmpty(),
                    latestDate = parkIndex?.latestDate,
                    currentDate = null,
                    currentAttractions = currentAttractions.value,
                ),
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
                        val day = result.data.takeIf { it.hasMeasurements() }
                            ?: buildCurrentDaySnapshot(
                                parkKey = parkKey,
                                date = date,
                                parks = state.parks,
                                currentAttractions = currentAttractions.value,
                            )
                        state.copy(
                            day = day,
                            selectedAttractionId = selectKnownAttractionId(
                                parkKey = parkKey,
                                preferredId = state.selectedAttractionId,
                                index = state.index,
                                currentAttractions = currentAttractions.value,
                                day = day,
                            ),
                            isLoading = false,
                        )
                    }
                }
                is ApiResult.Error -> {
                    val isMissingToday = result.type == NetworkError.NotFound && date == LocalDate.now().toString()
                    val language = preferences.language.first()
                    mutableState.update { state ->
                        if (isMissingToday) {
                            state.copy(
                                day = buildCurrentDaySnapshot(
                                    parkKey = parkKey,
                                    date = date,
                                    parks = state.parks,
                                    currentAttractions = currentAttractions.value,
                                ),
                                isLoading = false,
                                errorMessage = null,
                            )
                        } else {
                            state.copy(isLoading = false, errorMessage = result.type.toUserMessage(language))
                        }
                    }
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

private fun chooseInitialDate(
    parkKey: String,
    parkIndexDates: List<String>,
    latestDate: String?,
    currentDate: String?,
    currentAttractions: List<CurrentAttractionSearchEntry>,
): String {
    val today = LocalDate.now().toString()
    val hasCurrentAttractions = currentAttractions.any { it.matchesParkKey(parkKey) }
    return when {
        currentDate != null && (currentDate in parkIndexDates || currentDate == today) -> currentDate
        today in parkIndexDates -> today
        hasCurrentAttractions && latestDate == null -> today
        latestDate != null -> latestDate
        else -> today
    }
}

private fun AttractionHistoryDay.hasMeasurements(): Boolean {
    return snapshots.any { snapshot -> snapshot.attractions.isNotEmpty() }
}

private fun AttractionHistoryDay.operatingSnapshots(): List<AttractionHistorySnapshot> {
    val openAtMillis = openFrom?.parseInstantMillis()
    val closeAtMillis = closedFrom?.parseInstantMillis()
    if (openAtMillis == null && closeAtMillis == null) return snapshots

    val firstOpenAttractionMillis = snapshots
        .filter { snapshot -> snapshot.attractions.any { it.isOpenWaitPoint } }
        .minOfOrNull { it.capturedAtMillis }
    val startAtMillis = when {
        openAtMillis != null && firstOpenAttractionMillis != null -> minOf(openAtMillis, firstOpenAttractionMillis)
        openAtMillis != null -> openAtMillis
        else -> firstOpenAttractionMillis
    }
    return snapshots.filter { snapshot ->
        (startAtMillis == null || snapshot.capturedAtMillis >= startAtMillis) &&
                (closeAtMillis == null || snapshot.capturedAtMillis <= closeAtMillis)
    }
}

private fun String.parseInstantMillis(): Long? {
    return runCatching { OffsetDateTime.parse(this).toInstant().toEpochMilli() }
        .getOrElse { runCatching { Instant.parse(this).toEpochMilli() }.getOrNull() }
}

private fun buildCurrentDaySnapshot(
    parkKey: String,
    date: String,
    parks: List<Park>,
    currentAttractions: List<CurrentAttractionSearchEntry>,
): AttractionHistoryDay? {
    val today = LocalDate.now().toString()
    if (date != today) return null
    val selectedPark = parks.firstOrNull { it.matchesParkKey(parkKey) }
    val entries = currentAttractions
        .filter { entry ->
            entry.matchesParkKey(parkKey) ||
                    selectedPark?.let { park -> entry.matchesParkKey(park.id) || entry.matchesParkKey(park.uuid) } == true
        }
        .sortedBy { it.name.lowercase() }
    if (entries.isEmpty()) return null
    if (entries.none { it.status == AttractionStatus.Opened }) return null

    val capturedAtMillis = entries.maxOf { it.updatedAtMillis }
    val points = entries.map { entry ->
        val statusCode = entry.status.toStatusCode()
        AttractionHistoryPoint(
            id = entry.attractionId,
            name = entry.name,
            value = if (statusCode == 0) entry.waitingTime?.coerceAtLeast(0) ?: 0 else statusCode,
            statusCode = statusCode,
            status = entry.status.toApiStatus(),
        )
    }
    return AttractionHistoryDay(
        generatedAtMillis = capturedAtMillis,
        parkKey = parkKey,
        date = date,
        openFrom = null,
        closedFrom = null,
        snapshots = listOf(
            AttractionHistorySnapshot(
                capturedAtMillis = capturedAtMillis,
                attractions = points,
            ),
        ),
        attractions = points.map { point ->
            val openValue = point.value.takeIf { point.statusCode == 0 && it >= 0 }
            AttractionHistorySummary(
                id = point.id,
                name = point.name,
                sampleCount = 1,
                openSampleCount = if (openValue != null) 1 else 0,
                closedSampleCount = if (openValue == null) 1 else 0,
                averageWaitMinutes = openValue?.toFloat(),
                minWaitMinutes = openValue,
                maxWaitMinutes = openValue,
                lastValue = point.value,
                lastStatusCode = point.statusCode,
            )
        },
    )
}

private fun CurrentAttractionSearchEntry.matchesParkKey(parkKey: String): Boolean {
    val normalizedKey = parkKey.normalizedParkKey()
    return this.parkKey == parkKey || this.parkKey.normalizedParkKey() == normalizedKey
}

private fun AttractionStatus.toStatusCode(): Int {
    return when (this) {
        AttractionStatus.Opened -> 0
        AttractionStatus.Closed -> -1
        AttractionStatus.ClosedWeather -> -2
        AttractionStatus.Maintenance -> -3
        AttractionStatus.Unknown -> -4
    }
}

private fun AttractionStatus.toApiStatus(): String {
    return when (this) {
        AttractionStatus.Opened -> "opened"
        AttractionStatus.Closed -> "closed"
        AttractionStatus.ClosedWeather -> "closed_weather"
        AttractionStatus.Maintenance -> "maintenance"
        AttractionStatus.Unknown -> "unknown"
    }
}

private val de.wartezeiten.app.domain.model.AttractionHistoryPoint.isOpenWaitPoint: Boolean
    get() = value >= 0 && (statusCode == 0 || status.equals("opened", ignoreCase = true) || status.equals("open", ignoreCase = true))

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
