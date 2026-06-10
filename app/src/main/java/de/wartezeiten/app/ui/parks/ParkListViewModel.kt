package de.wartezeiten.app.ui.parks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.wartezeiten.app.core.network.ApiResult
import de.wartezeiten.app.core.network.NetworkError
import de.wartezeiten.app.core.network.toUserMessage
import de.wartezeiten.app.data.local.PreferencesDataSource
import de.wartezeiten.app.domain.model.AttractionStatus
import de.wartezeiten.app.domain.model.CurrentAttractionSearchEntry
import de.wartezeiten.app.domain.model.Park
import de.wartezeiten.app.domain.model.ParkRecommendation
import de.wartezeiten.app.domain.model.StatisticsIndex
import de.wartezeiten.app.domain.repository.ParkRecommendationScanProgress
import de.wartezeiten.app.domain.repository.WartezeitenRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val OPEN_PARK_FILTER_MAX_AGE_MILLIS = 30 * 60 * 1000L

data class ParkListUiState(
    val parks: List<Park> = emptyList(),
    val favoriteParks: List<Park> = emptyList(),
    val query: String = "",
    val selectedCountry: String? = null,
    val availableCountries: List<String> = emptyList(),
    val showOpenOnly: Boolean = false,
    val showFavoritesOnly: Boolean = false,
    val sort: ParkSort = ParkSort.Name,
    val recommendation: ParkRecommendation? = null,
    val recommendations: List<ParkRecommendation> = emptyList(),
    val isRecommendationLoading: Boolean = false,
    val recommendationScanStatus: String? = null,
    val language: String = PreferencesDataSource.DEFAULT_LANGUAGE,
    val totalParkCount: Int = 0,
    val visibleCountryCount: Int = 0,
    val isShowingOfflineData: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val refreshTrigger: Int = 0,
    val attractionSearchResults: List<AttractionSearchResult> = emptyList(),
    val statisticsParkKeys: Map<String, String> = emptyMap(),
    val isStatisticsIndexLoading: Boolean = false,
)

data class AttractionSearchResult(
    val parkKey: String,
    val parkName: String,
    val parkCountry: String?,
    val attractionId: String,
    val attractionName: String,
    val latestDate: String?,
    val averageWaitMinutes: Float?,
    val lastValue: Int?,
    val lastStatusCode: Int?,
)

enum class ParkSort {
    FavoritesFirst,
    Name,
    Country
}

private fun String.normalizedParkKey(): String {
    return lowercase()
        .replace("\u00e4", "ae")
        .replace("\u00f6", "oe")
        .replace("\u00fc", "ue")
        .replace("\u00df", "ss")
        .filter { it.isLetterOrDigit() }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ParkListViewModel @Inject constructor(
    private val repository: WartezeitenRepository,
    private val preferences: PreferencesDataSource,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedCountry = MutableStateFlow<String?>(null)
    private val showOpenOnly = MutableStateFlow(false)
    private val showFavoritesOnly = MutableStateFlow(false)
    private val sort = MutableStateFlow(ParkSort.Name)
    private val isLoading = MutableStateFlow(value = false)
    private val isRecommendationLoading = MutableStateFlow(value = false)
    private val recommendationScanProgress = MutableStateFlow<ParkRecommendationScanProgress?>(null)
    private val currentLanguage = MutableStateFlow(PreferencesDataSource.DEFAULT_LANGUAGE)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val refreshTrigger = MutableStateFlow(0)
    private val statisticsIndex = MutableStateFlow(StatisticsIndex(generatedAtMillis = 0L, parks = emptyList()))
    private val isStatisticsIndexLoading = MutableStateFlow(false)
    private var refreshJob: Job? = null
    private var recommendationRefreshJob: Job? = null

    private val allParks = repository.observeParks(null)
    private val currentAttractions = repository.observeCurrentAttractions()
    private val openParkSnapshotCutoff = flow {
        while (true) {
            emit(System.currentTimeMillis() - OPEN_PARK_FILTER_MAX_AGE_MILLIS)
            delay(60_000L)
        }
    }
    private val latestOpenParkKeys = openParkSnapshotCutoff
        .flatMapLatest { cutoff -> repository.observeLatestOpenParkKeys(cutoff) }
    private val recommendations = repository.observeParkRecommendations(limit = 5)

    @Suppress("UNCHECKED_CAST")
    val uiState = combine(
        allParks,
        currentAttractions,
        latestOpenParkKeys,
        query,
        selectedCountry,
        showOpenOnly,
        showFavoritesOnly,
        sort,
        recommendations,
        isRecommendationLoading,
        recommendationScanProgress,
        currentLanguage,
        isLoading,
        errorMessage,
        refreshTrigger,
        statisticsIndex,
        isStatisticsIndexLoading
    ) { args: Array<Any?> ->
        val parks = args[0] as List<Park>
        val currentAttractionEntries = args[1] as List<CurrentAttractionSearchEntry>
        val openParkKeys = args[2] as Set<String>
        val q = args[3] as String
        val country = args[4] as String?
        val openOnly = args[5] as Boolean
        val favoritesOnly = args[6] as Boolean
        val currentSort = args[7] as ParkSort
        val currentRecommendations = args[8] as List<ParkRecommendation>
        val recommendationLoading = args[9] as Boolean
        val scanProgress = args[10] as ParkRecommendationScanProgress?
        val language = args[11] as String
        val loading = args[12] as Boolean
        val error = args[13] as String?
        val trigger = args[14] as Int
        val statsIndex = args[15] as StatisticsIndex
        val statsLoading = args[16] as Boolean

        val favorites = parks.filter { it.isFavorite }
        val countries = parks.map { it.country }.distinct().sorted()
        val parksByKey = parks
            .flatMap { park -> listOf(park.id to park, park.uuid to park) }
            .toMap()
        val statisticsParkKeys = parks.toStatisticsParkKeyMap(statsIndex)
        
        val normalizedQuery = q.normalizedSearchText()
        var filtered = parks
        if (normalizedQuery.isNotBlank()) {
            filtered = filtered.filter { park ->
                park.name.normalizedSearchText().contains(normalizedQuery) ||
                    park.country.normalizedSearchText().contains(normalizedQuery)
            }
        }
        if (country != null) filtered = filtered.filter { it.country == country }
        if (openOnly) filtered = filtered.filter { it.id in openParkKeys || it.uuid in openParkKeys }
        if (favoritesOnly) filtered = filtered.filter { it.isFavorite }
        filtered = filtered.sortedBy(currentSort)
        val currentAttractionResults = currentAttractionEntries.toSearchResults(
            query = q,
            parksByKey = parksByKey,
            selectedCountry = country,
            openOnly = openOnly,
        )
        val statisticsAttractionResults = statsIndex.toSearchResults(
            query = q,
            parksByKey = parksByKey,
            selectedCountry = country,
            openOnly = openOnly,
        )
        val attractionResults = (currentAttractionResults + statisticsAttractionResults)
            .distinctBy { "${it.parkKey}_${it.attractionId}" }
            .take(20)
        
        ParkListUiState(
            parks = filtered,
            favoriteParks = favorites,
            query = q,
            selectedCountry = country,
            availableCountries = countries,
            showOpenOnly = openOnly,
            showFavoritesOnly = favoritesOnly,
            sort = currentSort,
            recommendation = currentRecommendations.firstOrNull(),
            recommendations = currentRecommendations,
            isRecommendationLoading = recommendationLoading && currentRecommendations.isEmpty(),
            recommendationScanStatus = scanProgress?.toStatusText(language),
            language = language,
            totalParkCount = parks.size,
            visibleCountryCount = filtered.map { it.country }.distinct().size,
            isShowingOfflineData = error != null && parks.isNotEmpty(),
            isLoading = loading,
            errorMessage = error,
            refreshTrigger = trigger,
            attractionSearchResults = attractionResults,
            statisticsParkKeys = statisticsParkKeys,
            isStatisticsIndexLoading = statsLoading,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ParkListUiState(isLoading = true),
    )

    init {
        observeParkSort()
        observeLanguage()
        startAutoRefresh()
        refreshStatisticsIndex()
    }

    private fun observeParkSort() {
        viewModelScope.launch {
            preferences.parkSort.distinctUntilChanged().collect { savedSort ->
                sort.value = ParkSort.entries.firstOrNull { it.name == savedSort } ?: ParkSort.Name
            }
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

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onCountrySelected(country: String?) {
        selectedCountry.value = country
    }

    fun onToggleOpenOnly() {
        showOpenOnly.value = !showOpenOnly.value
    }

    fun onToggleFavoritesOnly() {
        showFavoritesOnly.value = !showFavoritesOnly.value
    }

    fun setSort(value: ParkSort) {
        sort.value = value
        viewModelScope.launch { preferences.setParkSort(value.name) }
    }

    fun clearFilters() {
        query.value = ""
        selectedCountry.value = null
        showOpenOnly.value = false
        showFavoritesOnly.value = false
        setSort(ParkSort.Name)
    }

    fun toggleFavorite(park: Park) {
        viewModelScope.launch {
            repository.toggleFavorite(park.id, !park.isFavorite)
        }
    }

    fun dismissError() {
        errorMessage.value = null
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
            when (val result = repository.refreshParks(language)) {
                is ApiResult.Success -> {
                    if (showFeedback) refreshTrigger.value += 1
                    if (!silent) {
                        refreshRecommendationsInBackground(language)
                    }
                }
                is ApiResult.Error -> {
                    val hasCachedParks = uiState.value.totalParkCount > 0
                    if (showFeedback || !hasCachedParks || result.type != NetworkError.RateLimited) {
                        errorMessage.value = result.type.toUserMessage(currentLanguage.value)
                    }
                }
            }
            isLoading.value = false
        }
    }

    private fun refreshStatisticsIndex() {
        viewModelScope.launch {
            isStatisticsIndexLoading.value = true
            when (val result = repository.getStatisticsIndex()) {
                is ApiResult.Success -> statisticsIndex.value = result.data
                is ApiResult.Error -> Unit
            }
            isStatisticsIndexLoading.value = false
        }
    }

    private fun refreshRecommendationsInBackground(language: String) {
        recommendationRefreshJob?.cancel()
        recommendationRefreshJob = viewModelScope.launch {
            isRecommendationLoading.value = true
            recommendationScanProgress.value = null
            try {
                repository.refreshParkRecommendationSnapshots(language) { progress ->
                    recommendationScanProgress.value = progress.takeIf { it.totalParks > 0 }
                }
            } finally {
                recommendationScanProgress.value = null
                isRecommendationLoading.value = false
            }
        }
    }

    private fun ParkRecommendationScanProgress.toStatusText(language: String): String {
        return if (language == "en") {
            "Scan running: $completedParks/$totalParks parks · ${estimatedRemainingMillis.toRemainingTimeText(language)} left"
        } else {
            "Scan läuft: $completedParks/$totalParks Parks · ${estimatedRemainingMillis.toRemainingTimeText(language)} verbleibend"
        }
    }

    private fun Long.toRemainingTimeText(language: String): String {
        if (this <= 0L) return if (language == "en") "almost done" else "gleich fertig"
        val seconds = ((this + 999L) / 1_000L).coerceAtLeast(1L)
        return if (seconds < 60L) {
            if (language == "en") "about ${seconds}s" else "ca. ${seconds} Sek."
        } else {
            val minutes = ((seconds + 59L) / 60L).coerceAtLeast(1L)
            if (language == "en") "about ${minutes} min" else "ca. ${minutes} Min."
        }
    }

    private fun List<Park>.sortedBy(sort: ParkSort): List<Park> {
        return when (sort) {
            ParkSort.FavoritesFirst -> sortedWith(
                compareByDescending<Park> { it.isFavorite }
                    .thenBy { it.name.lowercase() }
            )
            ParkSort.Name -> sortedBy { it.name.lowercase() }
            ParkSort.Country -> sortedWith(
                compareBy<Park> { it.country.lowercase() }
                    .thenBy { it.name.lowercase() }
            )
        }
    }

    private fun StatisticsIndex.toSearchResults(
        query: String,
        parksByKey: Map<String, Park>,
        selectedCountry: String?,
        openOnly: Boolean,
    ): List<AttractionSearchResult> {
        val normalizedQuery = query.normalizedSearchText()
        if (normalizedQuery.length < 2) return emptyList()
        return parks
            .flatMap { parkIndex ->
                val park = parksByKey[parkIndex.parkKey]
                parkIndex.attractions.map { attraction ->
                    AttractionSearchResult(
                        parkKey = parkIndex.parkKey,
                        parkName = park?.name ?: parkIndex.parkKey,
                        parkCountry = park?.country,
                        attractionId = attraction.id,
                        attractionName = attraction.name,
                        latestDate = attraction.latestDate ?: parkIndex.latestDate,
                        averageWaitMinutes = attraction.averageWaitMinutes,
                        lastValue = attraction.lastValue,
                        lastStatusCode = attraction.lastStatusCode,
                    )
                }
            }
            .filter { result -> selectedCountry == null || result.parkCountry == selectedCountry }
            .filter { result -> !openOnly || result.lastStatusCode == 0 }
            .filter { result ->
                result.attractionName.normalizedSearchText().contains(normalizedQuery) ||
                    result.parkName.normalizedSearchText().contains(normalizedQuery)
            }
            .sortedWith(
                compareBy<AttractionSearchResult> {
                    !it.attractionName.normalizedSearchText().startsWith(normalizedQuery)
                }.thenBy { it.attractionName.lowercase() }
            )
            .take(20)
    }

    private fun List<Park>.toStatisticsParkKeyMap(index: StatisticsIndex): Map<String, String> {
        val indexedByNormalizedKey = index.parks.associateBy { it.parkKey.normalizedParkKey() }
        return buildMap {
            this@toStatisticsParkKeyMap.forEach { park ->
                val directMatch = index.parks.firstOrNull { parkIndex ->
                    parkIndex.parkKey == park.id || parkIndex.parkKey == park.uuid
                }
                val normalizedCandidates = listOf(park.id, park.uuid, park.name)
                    .map { it.normalizedParkKey() }
                    .filter { it.isNotBlank() }
                    .distinct()
                val normalizedMatch = normalizedCandidates.firstNotNullOfOrNull { candidate ->
                    indexedByNormalizedKey[candidate]
                }
                val containedMatch = index.parks.firstOrNull { parkIndex ->
                    val indexKey = parkIndex.parkKey.normalizedParkKey()
                    normalizedCandidates.any { candidate ->
                        candidate.length >= 4 && (indexKey.contains(candidate) || candidate.contains(indexKey))
                    }
                }
                val statisticsKey = directMatch?.parkKey ?: normalizedMatch?.parkKey ?: containedMatch?.parkKey
                if (statisticsKey != null) {
                    put(park.id, statisticsKey)
                    put(park.uuid, statisticsKey)
                }
            }
        }
    }

    private fun List<CurrentAttractionSearchEntry>.toSearchResults(
        query: String,
        parksByKey: Map<String, Park>,
        selectedCountry: String?,
        openOnly: Boolean,
    ): List<AttractionSearchResult> {
        val normalizedQuery = query.normalizedSearchText()
        if (normalizedQuery.length < 2) return emptyList()
        return asSequence()
            .mapNotNull { entry ->
                val park = parksByKey[entry.parkKey] ?: return@mapNotNull null
                val statusCode = entry.status.toSearchStatusCode()
                AttractionSearchResult(
                    parkKey = entry.parkKey,
                    parkName = park.name,
                    parkCountry = park.country,
                    attractionId = entry.attractionId,
                    attractionName = entry.name,
                    latestDate = null,
                    averageWaitMinutes = null,
                    lastValue = if (statusCode == 0) entry.waitingTime ?: 0 else statusCode,
                    lastStatusCode = statusCode,
                )
            }
            .filter { result -> selectedCountry == null || result.parkCountry == selectedCountry }
            .filter { result -> !openOnly || result.lastStatusCode == 0 }
            .filter { result ->
                result.attractionName.normalizedSearchText().contains(normalizedQuery) ||
                    result.parkName.normalizedSearchText().contains(normalizedQuery)
            }
            .sortedWith(
                compareBy<AttractionSearchResult> {
                    !it.attractionName.normalizedSearchText().startsWith(normalizedQuery)
                }.thenBy { it.attractionName.lowercase() }
            )
            .take(20)
            .toList()
    }

    private fun AttractionStatus.toSearchStatusCode(): Int {
        return when (this) {
            AttractionStatus.Opened -> 0
            AttractionStatus.Closed -> -1
            AttractionStatus.ClosedWeather -> -2
            AttractionStatus.Maintenance -> -3
            AttractionStatus.Unknown -> -4
        }
    }
}

private fun String.normalizedSearchText(): String {
    return lowercase()
        .replace("ä", "ae")
        .replace("ö", "oe")
        .replace("ü", "ue")
        .replace("ß", "ss")
}
