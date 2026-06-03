package de.wartezeiten.app.ui.parks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.wartezeiten.app.core.network.ApiResult
import de.wartezeiten.app.core.network.NetworkError
import de.wartezeiten.app.core.network.toUserMessage
import de.wartezeiten.app.data.local.PreferencesDataSource
import de.wartezeiten.app.domain.model.Park
import de.wartezeiten.app.domain.model.ParkRecommendation
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ParkListUiState(
    val parks: List<Park> = emptyList(),
    val favoriteParks: List<Park> = emptyList(),
    val query: String = "",
    val selectedCountry: String? = null,
    val availableCountries: List<String> = emptyList(),
    val showOpenOnly: Boolean = false,
    val showFavoritesOnly: Boolean = false,
    val sort: ParkSort = ParkSort.FavoritesFirst,
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
)

enum class ParkSort {
    FavoritesFirst,
    Name,
    Country
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
    private val sort = MutableStateFlow(ParkSort.FavoritesFirst)
    private val isLoading = MutableStateFlow(value = false)
    private val isRecommendationLoading = MutableStateFlow(value = false)
    private val recommendationScanProgress = MutableStateFlow<ParkRecommendationScanProgress?>(null)
    private val currentLanguage = MutableStateFlow(PreferencesDataSource.DEFAULT_LANGUAGE)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val refreshTrigger = MutableStateFlow(0)
    private var refreshJob: Job? = null
    private var recommendationRefreshJob: Job? = null

    private val allParks = query.flatMapLatest { repository.observeParks(it) }
    private val latestOpenParkKeys = repository.observeLatestOpenParkKeys()
    private val recommendations = repository.observeParkRecommendations(limit = 5)

    val uiState = combine(
        allParks,
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
        refreshTrigger
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val parks = args[0] as List<Park>
        val openParkKeys = args[1] as Set<String>
        val q = args[2] as String
        val country = args[3] as String?
        val openOnly = args[4] as Boolean
        val favoritesOnly = args[5] as Boolean
        val currentSort = args[6] as ParkSort
        val currentRecommendations = args[7] as List<ParkRecommendation>
        val recommendationLoading = args[8] as Boolean
        val scanProgress = args[9] as ParkRecommendationScanProgress?
        val language = args[10] as String
        val loading = args[11] as Boolean
        val error = args[12] as String?
        val trigger = args[13] as Int

        val favorites = parks.filter { it.isFavorite }
        val countries = parks.map { it.country }.distinct().sorted()
        
        var filtered = parks
        if (country != null) filtered = filtered.filter { it.country == country }
        if (openOnly) filtered = filtered.filter { it.id in openParkKeys || it.uuid in openParkKeys }
        if (favoritesOnly) filtered = filtered.filter { it.isFavorite }
        filtered = filtered.sortedBy(currentSort)
        
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
            refreshTrigger = trigger
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ParkListUiState(isLoading = true),
    )

    init {
        observeLanguage()
        startAutoRefresh()
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
    }

    fun clearFilters() {
        query.value = ""
        selectedCountry.value = null
        showOpenOnly.value = false
        showFavoritesOnly.value = false
        sort.value = ParkSort.FavoritesFirst
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
                        errorMessage.value = result.type.toUserMessage()
                    }
                }
            }
            isLoading.value = false
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
}
