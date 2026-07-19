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
    val language: String = PreferencesDataSource.DEFAULT_LANGUAGE,
    val isLoading: Boolean = false,
    val isDataFallbackToPreviousDay: Boolean = false,
    val errorMessage: String? = null,
    val refreshTrigger: Int = 0,
    val refreshError: String? = null,
    val attractionListQuery: String = "",
) {
    val availableDates: List<String>
        get() {
            val indexedDates = index.parks.firstOrNull { it.parkKey == selectedParkKey }?.dates.orEmpty()
            val deviceToday = LocalDate.now().toString()
            val parkIdx = index.parks.firstOrNull { it.parkKey == selectedParkKey }
            val parkToday = if (parkIdx != null) {
                val cands = listOf(deviceToday, LocalDate.now().minusDays(1).toString(), LocalDate.now().plusDays(1).toString())
                cands.firstOrNull { it in parkIdx.dates } ?: parkIdx.latestDate ?: deviceToday
            } else deviceToday
            return (indexedDates + selectedDate + deviceToday + parkToday)
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
            .filter { it.length >= 7 }
            .groupBy { it.take(7) }
            .map { (month, dates) -> 
                StatisticsMonthBucket(
                    month = month, 
                    dayCount = dates.size,
                    availableDates = dates.sortedDescending()
                ) 
            }
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
    val availableDates: List<String> = emptyList(),
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
    private val initialParkKey: String? = savedStateHandle.get<String>("parkKey")?.toString()
    private val initialAttractionId: String? = savedStateHandle.get<String>("attractionId")?.toString()
    private val parks = repository.observeParks(null)
    private val currentAttractions = repository.observeCurrentAttractions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
    private val mutableState = MutableStateFlow(StatisticsUiState(isLoading = true))

    val uiState = combine(parks, currentAttractions, preferences.language, mutableState) { parkList, attractionList, language, state ->
        state.copy(
            parks = parkList,
            currentAttractions = attractionList,
            language = language,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatisticsUiState(isLoading = true),
    )

    init {
        refresh(isManual = false)
    }

    fun refresh(isManual: Boolean = true) {
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true, errorMessage = null, refreshError = null) }
            val publicResult = repository.refreshPublicAppData(forceRefresh = isManual)
            val indexResult = repository.getStatisticsIndex()
            
            val language = preferences.language.first()
            
            when (indexResult) {
                is ApiResult.Success -> {
                    val current = mutableState.value
                    val parkList = parks.first()
                    val selectedParkKey = current.selectedParkKey
                        ?: initialParkKey
                        ?: indexResult.data.parks.firstOrNull()?.parkKey
                    val resolvedParkKey = selectedParkKey.resolveIndexedParkKey(indexResult.data, parkList)
                    val selectedDate = selectedParkKey
                        ?.let { resolvedParkKey }
                        ?.let { key ->
                            val parkIndex = indexResult.data.parks.firstOrNull { it.parkKey == key }
                            chooseInitialDate(
                                parkKey = key,
                                parkIndexDates = parkIndex?.dates.orEmpty(),
                                latestDate = parkIndex?.latestDate,
                                currentDate = current.selectedDate,
                                currentAttractions = currentAttractions.value,
                            )
                        }
                        ?: LocalDate.now().toString()
                    
                    val refreshError = if (publicResult is ApiResult.Error) {
                        publicResult.type.toUserMessage(language)
                    } else null

                    if (resolvedParkKey == null) {
                        mutableState.update {
                            it.copy(
                                index = indexResult.data,
                                selectedParkKey = null,
                                selectedDate = selectedDate,
                                day = null,
                                selectedAttractionId = null,
                                isLoading = false,
                                refreshTrigger = if (isManual) it.refreshTrigger + 1 else it.refreshTrigger,
                                refreshError = if (isManual) refreshError else null,
                            )
                        }
                        return@launch
                    }
                    mutableState.update {
                        it.copy(
                            index = indexResult.data,
                            selectedParkKey = resolvedParkKey,
                            selectedDate = selectedDate,
                            day = null,
                            selectedAttractionId = selectKnownAttractionId(
                                parkKey = resolvedParkKey,
                                preferredId = current.selectedAttractionId ?: initialAttractionId,
                                index = indexResult.data,
                                currentAttractions = currentAttractions.value,
                            ),
                            refreshError = if (isManual) refreshError else null,
                        )
                    }
                    loadSelectedDay(isManualRefresh = isManual)
                }
                is ApiResult.Error -> {
                    mutableState.update {
                        it.copy(
                            isLoading = false, 
                            errorMessage = if (it.index.parks.isEmpty()) indexResult.type.toUserMessage(language) else null,
                            refreshError = if (isManual) indexResult.type.toUserMessage(language) else null,
                            refreshTrigger = if (isManual) it.refreshTrigger + 1 else it.refreshTrigger,
                        )
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
            it.copy(
                selectedDate = date,
                day = null,
                errorMessage = null,
                isDataFallbackToPreviousDay = false // Reset before loading
            )
        }
        loadSelectedDay()
    }

    fun selectAttraction(attractionId: String) {
        mutableState.update { it.copy(selectedAttractionId = attractionId) }
    }

    fun selectParkStatistics() {
        mutableState.update { it.copy(selectedAttractionId = null) }
    }

    fun updateAttractionListQuery(query: String) {
        mutableState.update { it.copy(attractionListQuery = query) }
    }

    private fun loadSelectedDay(isManualRefresh: Boolean = false) {
        val parkKey = mutableState.value.selectedParkKey ?: return
        val date = mutableState.value.selectedDate
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true, errorMessage = if (it.day == null) null else it.errorMessage) }
            when (val result = repository.getAttractionHistoryDay(parkKey, date)) {
                is ApiResult.Success -> {
                    mutableState.update { state ->
                        val indexedLatest = state.index.parks.firstOrNull { it.parkKey == parkKey }?.latestDate
                        val fetchedDay = result.data.takeIf { it.hasMeasurements() }
                        val currentDay = buildCurrentDaySnapshot(
                            parkKey = parkKey,
                            date = date,
                            parks = state.parks,
                            currentAttractions = currentAttractions.value,
                            latestDate = indexedLatest,
                        )
                        
                        val day = when {
                            fetchedDay != null && currentDay != null -> fetchedDay.mergeWith(currentDay)
                            fetchedDay != null -> fetchedDay
                            else -> currentDay
                        }

                        val deviceToday = LocalDate.now().toString()
                        val isTodaySelected = date == deviceToday || date == "Heute" || date == "Today"
                        val isFallback = day != null && day.date != deviceToday && isTodaySelected
                        
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
                            isDataFallbackToPreviousDay = isFallback,
                            refreshError = if (isManualRefresh) state.refreshError else null,
                            refreshTrigger = if (isManualRefresh) state.refreshTrigger + 1 else state.refreshTrigger,
                        )
                    }
                }
                is ApiResult.Error -> {
                    val indexedLatest = mutableState.value.index.parks.firstOrNull { it.parkKey == parkKey }?.latestDate
                    val deviceToday = LocalDate.now().toString()
                    val isTodaySelected = date == deviceToday || date == "Heute" || date == "Today"
                    val isMissingToday = result.type == NetworkError.NotFound &&
                        (date == deviceToday || date == indexedLatest || isTodaySelected)
                    val language = preferences.language.first()
                    mutableState.update { state ->
                        if (isMissingToday) {
                            val day = buildCurrentDaySnapshot(
                                parkKey = parkKey,
                                date = date,
                                parks = state.parks,
                                currentAttractions = currentAttractions.value,
                                latestDate = indexedLatest,
                            )
                            val isFallback = day != null && day.date != deviceToday && isTodaySelected
                            state.copy(
                                day = day,
                                isLoading = false,
                                errorMessage = null,
                                isDataFallbackToPreviousDay = isFallback,
                                refreshError = if (isManualRefresh) result.type.toUserMessage(language) else state.refreshError,
                                refreshTrigger = if (isManualRefresh) state.refreshTrigger + 1 else state.refreshTrigger,
                            )
                        } else {
                            state.copy(
                                isLoading = false, 
                                errorMessage = if (state.day == null) result.type.toUserMessage(language) else null,
                                refreshError = if (isManualRefresh) result.type.toUserMessage(language) else state.refreshError,
                                refreshTrigger = if (isManualRefresh) state.refreshTrigger + 1 else state.refreshTrigger,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun AttractionHistoryDay.mergeWith(current: AttractionHistoryDay): AttractionHistoryDay {
        if (date != current.date) return this
        val lastSnapshotTime = snapshots.maxOfOrNull { it.capturedAtMillis } ?: 0L
        val newSnapshots = current.snapshots.filter { it.capturedAtMillis > lastSnapshotTime + 60_000L }
        if (newSnapshots.isEmpty()) return this
        
        return copy(
            generatedAtMillis = maxOf(generatedAtMillis, current.generatedAtMillis),
            snapshots = (snapshots + newSnapshots).sortedBy { it.capturedAtMillis }
        )
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

private fun chooseInitialDate(
    parkKey: String,
    parkIndexDates: List<String>,
    latestDate: String?,
    currentDate: String?,
    currentAttractions: List<CurrentAttractionSearchEntry>,
): String {
    val deviceToday = LocalDate.now().toString()
    val hasCurrentAttractions = currentAttractions.any { it.matchesParkKey(parkKey) }
    val parkToday = parkCurrentDateFromIndex(parkIndexDates, latestDate)
    return when {
        currentDate != null && currentDate in parkIndexDates -> currentDate
        currentDate == deviceToday && hasCurrentAttractions -> currentDate
        parkToday in parkIndexDates -> parkToday
        hasCurrentAttractions && latestDate == null -> deviceToday
        latestDate != null -> latestDate
        else -> deviceToday
    }
}

private fun AttractionHistoryDay.hasMeasurements(): Boolean {
    return snapshots.any { snapshot -> snapshot.attractions.isNotEmpty() }
}

private fun AttractionHistoryDay.operatingSnapshots(): List<AttractionHistorySnapshot> {
    val openAtMillis = openFrom?.parseInstantMillis()
    val closeAtMillis = closedFrom?.parseInstantMillis()
    if (openAtMillis == null && closeAtMillis == null) return snapshots

    val openPoints = snapshots.filter { snapshot -> snapshot.attractions.any { it.isOpenWaitPoint } }
    val firstOpenAttractionMillis = openPoints.minOfOrNull { it.capturedAtMillis }
    val lastOpenAttractionMillis = openPoints.maxOfOrNull { it.capturedAtMillis }

    val startAtMillis = when {
        openAtMillis != null && firstOpenAttractionMillis != null -> minOf(openAtMillis, firstOpenAttractionMillis)
        openAtMillis != null -> openAtMillis
        else -> firstOpenAttractionMillis
    }
    val endAtMillis = when {
        closeAtMillis != null && lastOpenAttractionMillis != null -> maxOf(closeAtMillis, lastOpenAttractionMillis)
        closeAtMillis != null -> closeAtMillis
        else -> lastOpenAttractionMillis
    }

    return snapshots.filter { snapshot ->
        (startAtMillis == null || snapshot.capturedAtMillis >= startAtMillis) &&
                (endAtMillis == null || snapshot.capturedAtMillis <= endAtMillis)
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
    latestDate: String? = null,
): AttractionHistoryDay? {
    val deviceToday = LocalDate.now().toString()
    if (date != deviceToday && date != latestDate) return null
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
