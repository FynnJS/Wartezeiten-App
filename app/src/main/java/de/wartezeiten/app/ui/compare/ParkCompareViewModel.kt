package de.wartezeiten.app.ui.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.wartezeiten.app.core.network.ApiResult
import de.wartezeiten.app.core.network.toUserMessage
import de.wartezeiten.app.data.local.PreferencesDataSource
import de.wartezeiten.app.domain.model.AttractionStatus
import de.wartezeiten.app.domain.model.CurrentAttractionSearchEntry
import de.wartezeiten.app.domain.model.Park
import de.wartezeiten.app.domain.repository.WartezeitenRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer
import javax.inject.Inject

enum class ParkCompareSort {
    BestNow,
    LowestAverageWait,
    MostOpenAttractions,
    Name
}

data class ParkCompareUiState(
    val availableParks: List<Park> = emptyList(),
    val selectedParks: List<Park> = emptyList(),
    val selectedParkIds: List<String> = emptyList(),
    val parkSearchQuery: String = "",
    val totalParkCount: Int = 0,
    val comparisonParks: List<ParkCompareItem> = emptyList(),
    val sort: ParkCompareSort = ParkCompareSort.BestNow,
    val language: String = PreferencesDataSource.DEFAULT_LANGUAGE,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

data class ParkCompareItem(
    val park: Park,
    val isOpen: Boolean,
    val totalAttractions: Int,
    val openAttractions: Int,
    val averageWaitMinutes: Float?,
    val maxWaitMinutes: Int?,
    val lastUpdatedMillis: Long?,
    val score: Float,
    val isBestChoice: Boolean,
)

private data class CompareControls(
    val selectedParkIds: List<String>,
    val parkSearchQuery: String,
    val sort: ParkCompareSort,
    val language: String,
)

private data class CompareLoadState(
    val isRefreshing: Boolean,
    val errorMessage: String?,
)

private const val OPEN_PARK_COMPARE_MAX_AGE_MILLIS = 30 * 60 * 1000L

@HiltViewModel
class ParkCompareViewModel @Inject constructor(
    private val repository: WartezeitenRepository,
    private val preferences: PreferencesDataSource,
) : ViewModel() {
    private val selectedParkIds = MutableStateFlow<List<String>>(emptyList())
    private val parkSearchQuery = MutableStateFlow("")
    private val sort = MutableStateFlow(ParkCompareSort.BestNow)
    private val currentLanguage = MutableStateFlow(PreferencesDataSource.DEFAULT_LANGUAGE)
    private val isRefreshing = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    private val controls = combine(selectedParkIds, parkSearchQuery, sort, currentLanguage) { ids, query, sort, language ->
        CompareControls(ids, query, sort, language)
    }

    private val loadState = combine(isRefreshing, errorMessage) { refreshing, error ->
        CompareLoadState(refreshing, error)
    }

    private val openParkSnapshotCutoff = flow {
        while (true) {
            emit(System.currentTimeMillis() - OPEN_PARK_COMPARE_MAX_AGE_MILLIS)
            delay(60_000L)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val latestOpenParkKeys = openParkSnapshotCutoff.flatMapLatest { cutoff ->
        repository.observeLatestOpenParkKeys(cutoff)
    }

    val uiState = combine(
        repository.observeParks(null),
        repository.observeCurrentAttractions(),
        latestOpenParkKeys,
        controls,
        loadState,
    ) { parks, attractions, openParkKeys, controls, load ->
        val selectedIds = controls.selectedParkIds.filter { selectedId ->
            parks.any { it.matchesParkKey(selectedId) }
        }
        val selectedParks = selectedIds.mapNotNull { selectedId ->
            parks.firstOrNull { it.matchesParkKey(selectedId) }
        }
        val normalizedQuery = controls.parkSearchQuery.normalizedSearchKey()
        val availableParks = parks
            .filter { park -> normalizedQuery.isBlank() || park.matchesSearch(normalizedQuery) }
            .sortedWith(
                compareByDescending<Park> { it.id in selectedIds || it.uuid in selectedIds }
                    .thenBy { it.name.lowercase() }
            )
        val items = selectedIds
            .mapNotNull { selectedId -> parks.firstOrNull { it.matchesParkKey(selectedId) } }
            .map { park -> park.toCompareItem(attractions, openParkKeys) }
        val bestParkId = items
            .filter { it.score > 0f }
            .maxWithOrNull(compareBy<ParkCompareItem> { it.score }.thenByDescending { it.openAttractions })
            ?.park
            ?.id
        val rankedItems = items
            .map { it.copy(isBestChoice = it.park.id == bestParkId) }
            .sortedBy(controls.sort)

        ParkCompareUiState(
            availableParks = availableParks,
            selectedParks = selectedParks,
            selectedParkIds = selectedIds,
            parkSearchQuery = controls.parkSearchQuery,
            totalParkCount = parks.size,
            comparisonParks = rankedItems,
            sort = controls.sort,
            language = controls.language,
            isRefreshing = load.isRefreshing,
            errorMessage = load.errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ParkCompareUiState(isRefreshing = true),
    )

    init {
        observeLanguage()
        selectInitialParks()
    }

    fun setSort(value: ParkCompareSort) {
        sort.value = value
    }

    fun setParkSearchQuery(value: String) {
        parkSearchQuery.value = value
    }

    fun togglePark(park: Park) {
        val current = selectedParkIds.value
        val isSelected = park.id in current || park.uuid in current
        selectedParkIds.value = if (isSelected) {
            current.filterNot { selectedId -> park.matchesParkKey(selectedId) }
        } else {
            if (current.size >= 4) current else current + park.id
        }
        if (!isSelected) refreshPark(park.id)
    }

    fun refreshSelected() {
        viewModelScope.launch {
            isRefreshing.value = true
            errorMessage.value = null
            selectedParkIds.value.forEach { parkKey ->
                when (val result = repository.refreshParkDetail(parkKey, currentLanguage.value)) {
                    is ApiResult.Success -> Unit
                    is ApiResult.Error -> {
                        if (errorMessage.value == null) {
                            errorMessage.value = result.type.toUserMessage(currentLanguage.value)
                        }
                    }
                }
            }
            isRefreshing.value = false
        }
    }

    private fun observeLanguage() {
        viewModelScope.launch {
            preferences.language.distinctUntilChanged().collect { language ->
                currentLanguage.value = language
            }
        }
    }

    private fun selectInitialParks() {
        viewModelScope.launch {
            repository.observeParks(null).collect { parks ->
                if (selectedParkIds.value.isNotEmpty() || parks.size < 2) return@collect
                selectedParkIds.value = parks
                    .sortedWith(compareByDescending<Park> { it.isFavorite }.thenBy { it.name.lowercase() })
                    .take(2)
                    .map { it.id }
                refreshSelected()
            }
        }
    }

    private fun refreshPark(parkKey: String) {
        viewModelScope.launch {
            repository.refreshParkDetail(parkKey, currentLanguage.value)
        }
    }

    private fun Park.toCompareItem(
        attractions: List<CurrentAttractionSearchEntry>,
        openParkKeys: Set<String>,
    ): ParkCompareItem {
        val parkAttractions = attractions.filter { it.parkKey == id || it.parkKey == uuid }
        val openAttractions = parkAttractions.filter { it.status == AttractionStatus.Opened }
        val waitValues = openAttractions.mapNotNull { it.waitingTime }
        val averageWait = waitValues.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        val openShare = if (parkAttractions.isNotEmpty()) {
            openAttractions.size.toFloat() / parkAttractions.size.toFloat()
        } else {
            0f
        }
        val isOpen = id in openParkKeys || uuid in openParkKeys || openAttractions.isNotEmpty()
        val waitScore = (100f - (averageWait ?: 70f)).coerceIn(0f, 100f)
        val score = if (!isOpen || parkAttractions.isEmpty()) {
            0f
        } else {
            (waitScore * 0.6f) + (openShare * 100f * 0.4f)
        }
        return ParkCompareItem(
            park = this,
            isOpen = isOpen,
            totalAttractions = parkAttractions.size,
            openAttractions = openAttractions.size,
            averageWaitMinutes = averageWait,
            maxWaitMinutes = waitValues.maxOrNull(),
            lastUpdatedMillis = parkAttractions.maxOfOrNull { it.updatedAtMillis },
            score = score,
            isBestChoice = false,
        )
    }

    private fun List<ParkCompareItem>.sortedBy(sort: ParkCompareSort): List<ParkCompareItem> {
        return when (sort) {
            ParkCompareSort.BestNow -> sortedWith(
                compareByDescending<ParkCompareItem> { it.score }
                    .thenBy { it.averageWaitMinutes ?: Float.MAX_VALUE }
                    .thenBy { it.park.name.lowercase() }
            )
            ParkCompareSort.LowestAverageWait -> sortedWith(
                compareBy<ParkCompareItem> { it.averageWaitMinutes ?: Float.MAX_VALUE }
                    .thenByDescending { it.openAttractions }
                    .thenBy { it.park.name.lowercase() }
            )
            ParkCompareSort.MostOpenAttractions -> sortedWith(
                compareByDescending<ParkCompareItem> { it.openAttractions }
                    .thenBy { it.averageWaitMinutes ?: Float.MAX_VALUE }
                    .thenBy { it.park.name.lowercase() }
            )
            ParkCompareSort.Name -> sortedBy { it.park.name.lowercase() }
        }
    }

    private fun Park.matchesParkKey(value: String): Boolean {
        return id == value || uuid == value
    }

    private fun Park.matchesSearch(normalizedQuery: String): Boolean {
        return name.normalizedSearchKey().contains(normalizedQuery) ||
            country.normalizedSearchKey().contains(normalizedQuery)
    }
}

private fun String.normalizedSearchKey(): String {
    val expandedGerman = lowercase()
        .replace("\u00e4", "ae")
        .replace("\u00f6", "oe")
        .replace("\u00fc", "ue")
        .replace("\u00df", "ss")
    return Normalizer.normalize(expandedGerman, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
}
