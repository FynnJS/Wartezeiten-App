package de.wartezeiten.app.ui.parks

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.wartezeiten.app.core.i18n.localized
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val OPEN_PARK_FILTER_MAX_AGE_MILLIS = 30 * 60 * 1000L

data class ParkListUiState(
    val parks: List<Park> = emptyList(),
    val favoriteParks: List<Park> = emptyList(),
    val favoriteDashboardItems: List<FavoriteDashboardItem> = emptyList(),
    val recentParks: List<Park> = emptyList(),
    val query: String = "",
    val searchHistory: List<String> = emptyList(),
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
    val offlineDataAgeMinutes: Long? = null,
    val isLoading: Boolean = false,
    val isOpenParkDataLoading: Boolean = false,
    val errorMessage: String? = null,
    val refreshTrigger: Int = 0,
    val attractionSearchResults: List<AttractionSearchResult> = emptyList(),
    val statisticsParkKeys: Map<String, String> = emptyMap(),
    val isStatisticsIndexLoading: Boolean = false,
)

data class FavoriteDashboardItem(
    val park: Park,
    val isOpen: Boolean,
    val openAttractions: Int,
    val totalAttractions: Int,
    val maxWaitMinutes: Int?,
    val dataAgeMinutes: Long?,
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
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val searchHistory = MutableStateFlow<List<String>>(emptyList())
    private val recentParkKeys = MutableStateFlow<List<String>>(emptyList())
    private val selectedCountry = MutableStateFlow<String?>(null)
    private val showOpenOnly = MutableStateFlow(false)
    private val showFavoritesOnly = MutableStateFlow(false)
    private val sort = MutableStateFlow(ParkSort.Name)
    private val isLoading = MutableStateFlow(value = false)
    private val openParkDataLoadCount = MutableStateFlow(0)
    private val isRecommendationLoading = MutableStateFlow(value = false)
    private val recommendationScanProgress = MutableStateFlow<ParkRecommendationScanProgress?>(null)
    private val currentLanguage = MutableStateFlow(PreferencesDataSource.DEFAULT_LANGUAGE)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val refreshTrigger = MutableStateFlow(0)
    private val statisticsIndex = MutableStateFlow(StatisticsIndex(generatedAtMillis = 0L, parks = emptyList()))
    private val isStatisticsIndexLoading = MutableStateFlow(false)
    private val parkAliases = MutableStateFlow<Map<String, List<String>>>(emptyMap())
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
        searchHistory,
        recentParkKeys,
        selectedCountry,
        showOpenOnly,
        showFavoritesOnly,
        sort,
        recommendations,
        isRecommendationLoading,
        recommendationScanProgress,
        currentLanguage,
        isLoading,
        openParkDataLoadCount,
        errorMessage,
        refreshTrigger,
        statisticsIndex,
        isStatisticsIndexLoading,
        parkAliases
    ) { args: Array<Any?> ->
        val parks = args[0] as List<Park>
        val currentAttractionEntries = args[1] as List<CurrentAttractionSearchEntry>
        val openParkKeys = args[2] as Set<String>
        val q = args[3] as String
        val currentSearchHistory = args[4] as List<String>
        val currentRecentParkKeys = args[5] as List<String>
        val country = args[6] as String?
        val openOnly = args[7] as Boolean
        val favoritesOnly = args[8] as Boolean
        val currentSort = args[9] as ParkSort
        val currentRecommendations = args[10] as List<ParkRecommendation>
        val recommendationLoading = args[11] as Boolean
        val scanProgress = args[12] as ParkRecommendationScanProgress?
        val language = args[13] as String
        val loading = args[14] as Boolean
        val openParkDataLoading = (args[15] as Int) > 0
        val error = args[16] as String?
        val trigger = args[17] as Int
        val statsIndex = args[18] as StatisticsIndex
        val statsLoading = args[19] as Boolean
        val aliases = args[20] as Map<String, List<String>>

        val favorites = parks.filter { it.isFavorite }
        val recent = currentRecentParkKeys.mapNotNull { key ->
            parks.firstOrNull { it.id == key || it.uuid == key }
        }
        val countries = parks.map { it.country }.distinct().sorted()
        val parksByKey = parks
            .flatMap { park -> listOf(park.id to park, park.uuid to park) }
            .toMap()
        val statisticsParkKeys = parks.toStatisticsParkKeyMap(statsIndex)
        
        val normalizedQuery = q.normalizedSearchText()
        var filtered = parks
        if (normalizedQuery.isNotBlank()) {
            filtered = filtered.filter { park ->
                park.matchesSearchQuery(normalizedQuery, aliases)
            }
        }
        if (country != null) filtered = filtered.filter { it.country == country }
        if (openOnly) filtered = filtered.filter { it.matchesOpenParkKey(openParkKeys) }
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
            favoriteDashboardItems = favorites.toFavoriteDashboardItems(currentAttractionEntries, openParkKeys),
            recentParks = recent,
            query = q,
            searchHistory = currentSearchHistory.filterNot { it.equals(q, ignoreCase = true) },
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
            offlineDataAgeMinutes = parks.latestCacheAgeMinutes(),
            isLoading = loading,
            isOpenParkDataLoading = openParkDataLoading,
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
        observeParkSearchState()
        observeParkSort()
        observeLanguage()
        refreshPublicOpenSnapshots()
        startAutoRefresh()
        refreshStatisticsIndex()
        loadParkAliases()
    }

    private fun loadParkAliases() {
        viewModelScope.launch {
            parkAliases.value = runCatching {
                context.assets.open("park_aliases.csv").bufferedReader().useLines { lines ->
                    lines.drop(1)
                        .mapNotNull { line -> line.toAliasEntryOrNull() }
                        .toMap()
                }
            }.getOrElse { emptyMap() }
        }
    }

    private fun observeParkSearchState() {
        viewModelScope.launch {
            preferences.parkSearchHistory.distinctUntilChanged().collect { savedHistory ->
                searchHistory.value = savedHistory
            }
        }
        viewModelScope.launch {
            preferences.recentParkKeys.distinctUntilChanged().collect { savedKeys ->
                recentParkKeys.value = savedKeys
            }
        }
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

    fun recordCurrentSearch() {
        recordSearch(query.value)
    }

    fun recordParkOpened(park: Park) {
        viewModelScope.launch {
            preferences.addRecentParkKey(park.id)
        }
    }

    fun useSearchHistory(value: String) {
        onQueryChange(value)
        recordSearch(value)
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            preferences.clearParkSearchHistory()
        }
    }

    private fun recordSearch(value: String) {
        viewModelScope.launch {
            preferences.addParkSearchHistory(value)
        }
    }

    fun onCountrySelected(country: String?) {
        selectedCountry.value = country
    }

    fun onToggleOpenOnly() {
        val enabled = !showOpenOnly.value
        showOpenOnly.value = enabled
        if (enabled) refreshRecommendationsInBackground(currentLanguage.value)
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
                    repository.refreshPublicAppData()
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

    private fun refreshPublicOpenSnapshots() {
        viewModelScope.launch {
            preferences.setParkSearchQuery("")
            beginOpenParkDataLoad()
            try {
                repository.refreshParkRecommendationSnapshots(currentLanguage.value)
            } finally {
                endOpenParkDataLoad()
            }
        }
    }

    private fun refreshRecommendationsInBackground(language: String) {
        recommendationRefreshJob?.cancel()
        recommendationRefreshJob = viewModelScope.launch {
            isRecommendationLoading.value = true
            beginOpenParkDataLoad()
            recommendationScanProgress.value = null
            try {
                repository.refreshParkRecommendationSnapshots(language) { progress ->
                    recommendationScanProgress.value = progress.takeIf { it.totalParks > 0 }
                }
            } finally {
                recommendationScanProgress.value = null
                endOpenParkDataLoad()
                isRecommendationLoading.value = false
            }
        }
    }

    private fun beginOpenParkDataLoad() {
        openParkDataLoadCount.update { it + 1 }
    }

    private fun endOpenParkDataLoad() {
        openParkDataLoadCount.update { (it - 1).coerceAtLeast(0) }
    }

    private fun ParkRecommendationScanProgress.toStatusText(language: String): String {
        val remaining = estimatedRemainingMillis.toRemainingTimeText(language)
        return localized(
            language,
            de = "Scan läuft: $completedParks/$totalParks Parks · $remaining verbleibend",
            en = "Scan running: $completedParks/$totalParks parks · $remaining left",
            fr = "Analyse en cours : $completedParks/$totalParks parcs · $remaining restant",
            nl = "Scan loopt: $completedParks/$totalParks parken · $remaining resterend",
        )
    }

    private fun Long.toRemainingTimeText(language: String): String {
        if (this <= 0L) {
            return localized(language, de = "gleich fertig", en = "almost done", fr = "presque terminé", nl = "bijna klaar")
        }
        val seconds = ((this + 999L) / 1_000L).coerceAtLeast(1L)
        return if (seconds < 60L) {
            localized(
                language,
                de = "ca. ${seconds} Sek.",
                en = "about ${seconds}s",
                fr = "environ ${seconds} s",
                nl = "ca. ${seconds} sec.",
            )
        } else {
            val minutes = ((seconds + 59L) / 60L).coerceAtLeast(1L)
            localized(
                language,
                de = "ca. ${minutes} Min.",
                en = "about ${minutes} min",
                fr = "environ ${minutes} min",
                nl = "ca. ${minutes} min.",
            )
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

    private fun List<Park>.latestCacheAgeMinutes(): Long? {
        val latestUpdate = maxOfOrNull { it.updatedAtMillis }?.takeIf { it > 0L } ?: return null
        return ((System.currentTimeMillis() - latestUpdate).coerceAtLeast(0L) / 60_000L).coerceAtLeast(0L)
    }

    private fun List<Park>.toFavoriteDashboardItems(
        attractions: List<CurrentAttractionSearchEntry>,
        openParkKeys: Set<String>,
    ): List<FavoriteDashboardItem> {
        return map { park ->
            val parkAttractions = attractions.filter { it.parkKey == park.id || it.parkKey == park.uuid }
            val openAttractions = parkAttractions.filter { it.status == AttractionStatus.Opened }
            val isOpen = park.matchesOpenParkKey(openParkKeys)
            val currentOpenAttractions = openAttractions.takeIf { isOpen }.orEmpty()
            val latestUpdate = listOfNotNull(
                park.updatedAtMillis.takeIf { it > 0L },
                parkAttractions.maxOfOrNull { it.updatedAtMillis },
            ).maxOrNull()
            FavoriteDashboardItem(
                park = park,
                isOpen = isOpen,
                openAttractions = currentOpenAttractions.size,
                totalAttractions = parkAttractions.size,
                maxWaitMinutes = currentOpenAttractions.mapNotNull { it.waitingTime }.maxOrNull(),
                dataAgeMinutes = latestUpdate?.let { ((System.currentTimeMillis() - it).coerceAtLeast(0L) / 60_000L) },
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

private fun Park.matchesSearchQuery(normalizedQuery: String, aliasMap: Map<String, List<String>>): Boolean {
    val candidates = buildList {
        add(name)
        add(country)
        add(id)
        add(uuid)
        addAll(assetAliases(aliasMap))
        addAll(searchAliases())
    }
    return candidates.any { it.normalizedSearchText().contains(normalizedQuery) }
}

private fun Park.assetAliases(aliasMap: Map<String, List<String>>): List<String> {
    val candidates = listOf(id, uuid, name).map { it.normalizedSearchText().filter(Char::isLetterOrDigit) }
    return aliasMap
        .filterKeys { key -> candidates.any { candidate -> candidate.contains(key) || key.contains(candidate) } }
        .values
        .flatten()
}

private fun Park.matchesOpenParkKey(openParkKeys: Set<String>): Boolean {
    if (id in openParkKeys || uuid in openParkKeys) return true
    val candidates = listOf(id, uuid, name)
        .map { it.normalizedParkKey() }
        .filter { it.isNotBlank() }
    val normalizedOpenKeys = openParkKeys
        .map { it.normalizedParkKey() }
        .filter { it.isNotBlank() }
        .toSet()
    return candidates.any { candidate ->
        candidate in normalizedOpenKeys ||
            normalizedOpenKeys.any { openKey ->
                candidate.length >= 4 && openKey.length >= 4 &&
                    (candidate.contains(openKey) || openKey.contains(candidate))
            }
    }
}

private fun Park.searchAliases(): List<String> {
    val normalizedId = id.normalizedSearchText()
    val normalizedName = name.normalizedSearchText()
    return buildList {
        if ("europapark" in listOf(normalizedId, normalizedName)) addAll(listOf("ep", "europa park", "europapark"))
        if ("phantasialand" in listOf(normalizedId, normalizedName)) addAll(listOf("pl", "phantasia land"))
        if ("heidepark" in listOf(normalizedId, normalizedName)) addAll(listOf("heide park", "hp"))
        if ("hansapark" in listOf(normalizedId, normalizedName)) addAll(listOf("hansa park"))
        if ("legoland" in normalizedId || "legoland" in normalizedName) addAll(listOf("lego land", "lego"))
        if ("disney" in normalizedId || "disney" in normalizedName) addAll(listOf("dlp", "disney", "disneyland"))
        if ("efteling" in normalizedId || "efteling" in normalizedName) add("eft")
    }
}

private fun String.toAliasEntryOrNull(): Pair<String, List<String>>? {
    val separator = indexOf(',')
    if (separator <= 0) return null
    val match = substring(0, separator).trim().normalizedSearchText().filter(Char::isLetterOrDigit)
    val aliases = substring(separator + 1)
        .trim()
        .trim('"')
        .split('|')
        .map { it.trim() }
        .filter { it.isNotBlank() }
    return match.takeIf { it.isNotBlank() }?.let { it to aliases }
}
