package de.wartezeiten.app.ui.parks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.wartezeiten.app.core.network.ApiResult
import de.wartezeiten.app.core.network.toUserMessage
import de.wartezeiten.app.domain.model.Park
import de.wartezeiten.app.domain.repository.WartezeitenRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
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
    val totalParkCount: Int = 0,
    val visibleCountryCount: Int = 0,
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
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedCountry = MutableStateFlow<String?>(null)
    private val showOpenOnly = MutableStateFlow(false)
    private val showFavoritesOnly = MutableStateFlow(false)
    private val sort = MutableStateFlow(ParkSort.FavoritesFirst)
    private val isLoading = MutableStateFlow(value = false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val refreshTrigger = MutableStateFlow(0)

    private val allParks = query.flatMapLatest { repository.observeParks(it) }
    private val latestOpenParkKeys = repository.observeLatestOpenParkKeys()

    val uiState = combine(
        allParks,
        latestOpenParkKeys,
        query,
        selectedCountry,
        showOpenOnly,
        showFavoritesOnly,
        sort,
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
        val loading = args[7] as Boolean
        val error = args[8] as String?
        val trigger = args[9] as Int

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
            totalParkCount = parks.size,
            visibleCountryCount = filtered.map { it.country }.distinct().size,
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
        language: String = "de",
        silent: Boolean = false,
        showFeedback: Boolean = !silent
    ) {
        viewModelScope.launch {
            if (!silent) isLoading.value = true
            errorMessage.value = null
            when (val result = repository.refreshParks(language)) {
                is ApiResult.Success -> {
                    if (showFeedback) refreshTrigger.value += 1
                }
                is ApiResult.Error -> errorMessage.value = result.type.toUserMessage()
            }
            isLoading.value = false
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
