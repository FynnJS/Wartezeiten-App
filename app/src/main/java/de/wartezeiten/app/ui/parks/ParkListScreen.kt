package de.wartezeiten.app.ui.parks

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.wartezeiten.app.R
import de.wartezeiten.app.core.i18n.localized
import de.wartezeiten.app.domain.model.Park
import de.wartezeiten.app.domain.model.ParkRecommendation
import de.wartezeiten.app.ui.components.AttributionBanner
import de.wartezeiten.app.ui.waitingtimes.AddWatchlistDialog
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun ParkListRoute(
    onParkClick: (Park) -> Unit,
    onSettingsClick: () -> Unit,
    onWatchlistClick: () -> Unit,
    onCompareClick: () -> Unit,
    onParkStatisticsClick: (String) -> Unit,
    onAttractionClick: (String, String) -> Unit,
    viewModel: ParkListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ParkListScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onSearchHistoryClick = viewModel::useSearchHistory,
        onClearSearchHistory = viewModel::clearSearchHistory,
        onCountrySelected = viewModel::onCountrySelected,
        onToggleOpenOnly = viewModel::onToggleOpenOnly,
        onToggleFavoritesOnly = viewModel::onToggleFavoritesOnly,
        onSortChange = viewModel::setSort,
        onClearFilters = viewModel::clearFilters,
        onToggleFavorite = viewModel::toggleFavorite,
        onRefreshClick = { viewModel.refresh() },
        onParkClick = { park ->
            viewModel.recordCurrentSearch()
            viewModel.recordParkOpened(park)
            onParkClick(park)
        },
        onSettingsClick = onSettingsClick,
        onWatchlistClick = onWatchlistClick,
        onCompareClick = onCompareClick,
        onParkStatisticsClick = onParkStatisticsClick,
        onAttractionClick = { parkKey, attractionId ->
            viewModel.recordCurrentSearch()
            onAttractionClick(parkKey, attractionId)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkListScreen(
    state: ParkListUiState,
    onQueryChange: (String) -> Unit,
    onSearchHistoryClick: (String) -> Unit,
    onClearSearchHistory: () -> Unit,
    onCountrySelected: (String?) -> Unit,
    onToggleOpenOnly: () -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onSortChange: (ParkSort) -> Unit,
    onClearFilters: () -> Unit,
    onToggleFavorite: (Park) -> Unit,
    onRefreshClick: () -> Unit,
    onParkClick: (Park) -> Unit,
    onSettingsClick: () -> Unit,
    onWatchlistClick: () -> Unit,
    onCompareClick: () -> Unit,
    onParkStatisticsClick: (String) -> Unit,
    onAttractionClick: (String, String) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddWatchlistDialog by remember { mutableStateOf(false) }
    var selectedParkForWatchlist by remember { mutableStateOf<Park?>(null) }

    LaunchedEffect(state.refreshTrigger) {
        if (state.refreshTrigger > 0 && !state.isLoading) {
            if (state.errorMessage == null) {
                val updatedAt = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                snackbarHostState.showSnackbar(
                    message = localized(
                        state.language,
                        de = "${state.totalParkCount} Parks aktualisiert um $updatedAt",
                        en = "${state.totalParkCount} parks updated at $updatedAt",
                        fr = "${state.totalParkCount} parcs mis à jour à $updatedAt",
                        nl = "${state.totalParkCount} parken bijgewerkt om $updatedAt",
                    ),
                    actionLabel = "OK",
                    withDismissAction = true,
                )
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                RefreshSnackbar(data = data)
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            localized(state.language, de = "Wartezeiten", en = "Wait times", fr = "Temps d'attente", nl = "Wachttijden"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            localized(state.language, de = "Freizeitparks", en = "Theme parks", fr = "Parcs à thème", nl = "Pretparken"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.isShowingOfflineData) {
                            OfflineStatusBadge(language = state.language)
                        }
                    }
                },
                actions = {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(24.dp),
                            strokeWidth = 2.5.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        IconButton(onClick = onRefreshClick) {
                            Icon(Icons.Default.Refresh, contentDescription = localized(state.language, de = "Aktualisieren", en = "Refresh", fr = "Actualiser", nl = "Vernieuwen"))
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = localized(state.language, de = "Einstellungen", en = "Settings", fr = "Paramètres", nl = "Instellingen"))
                        }
                        IconButton(onClick = onWatchlistClick) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = localized(state.language, de = "Benachrichtigungen", en = "Watchlist", fr = "Alertes", nl = "Meldingen")
                            )
                        }
                        IconButton(onClick = onCompareClick) {
                            Icon(
                                painter = painterResource(R.drawable.ic_stats_bar_chart_24),
                                contentDescription = localized(state.language, de = "Parks vergleichen", en = "Compare parks", fr = "Comparer les parcs", nl = "Parken vergelijken"),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedVisibility(
                visible = state.isLoading,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            AnimatedVisibility(
                visible = state.errorMessage != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                state.errorMessage?.let { msg ->
                    ErrorBanner(
                        message = msg,
                        onRetry = onRefreshClick,
                        language = state.language,
                    )
                }
            }

            AnimatedContent(
                targetState = state.isLoading && state.parks.isEmpty(),
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(150))
                },
                label = "park_list_content",
                modifier = Modifier.fillMaxSize(),
            ) { showFullscreenLoading ->
                if (showFullscreenLoading) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(bottom = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(
                                localized(state.language, de = "Parks werden geladen…", en = "Loading parks…", fr = "Chargement des parcs…", nl = "Parken worden geladen…"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 8.dp,
                            end = 16.dp,
                            bottom = 32.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (state.isShowingOfflineData) {
                            item {
                                OfflineDataBanner(
                                    language = state.language,
                                    ageMinutes = state.offlineDataAgeMinutes,
                                )
                            }
                        }

                        if (state.usingFallbackParkList) {
                            item {
                                FallbackParkListBanner(language = state.language)
                            }
                        }

                        item(key = "park_search_field") {
                            OutlinedTextField(
                                value = state.query,
                                onValueChange = onQueryChange,
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                placeholder = {
                                    Text(
                                        localized(
                                            state.language,
                                            de = "Park oder Attraktion suchen…",
                                            en = "Search park or attraction…",
                                            fr = "Rechercher un parc ou une attraction…",
                                            nl = "Park of attractie zoeken…",
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        if (state.recentParks.isNotEmpty() && state.query.isBlank()) {
                            item {
                                RecentParksSection(
                                    parks = state.recentParks,
                                    language = state.language,
                                    onParkClick = onParkClick,
                                )
                            }
                        }

                        if (state.favoriteDashboardItems.isNotEmpty() && state.query.isBlank()) {
                            item {
                                FavoriteDashboardSection(
                                    items = state.favoriteDashboardItems,
                                    language = state.language,
                                    onParkClick = onParkClick,
                                )
                            }
                        }

                        if (state.searchHistory.isNotEmpty()) {
                            item {
                                SearchHistoryRow(
                                    history = state.searchHistory,
                                    language = state.language,
                                    onHistoryClick = onSearchHistoryClick,
                                    onClearHistory = onClearSearchHistory,
                                )
                            }
                        }

                        if (state.availableCountries.isNotEmpty()) {
                            item {
                                CountryFilterRow(
                                    countries = state.availableCountries,
                                    selected = state.selectedCountry,
                                    showOpenOnly = state.showOpenOnly,
                                    showFavoritesOnly = state.showFavoritesOnly,
                                    sort = state.sort,
                                    language = state.language,
                                    onSelect = onCountrySelected,
                                    onToggleOpenOnly = onToggleOpenOnly,
                                    onToggleFavoritesOnly = onToggleFavoritesOnly,
                                    onSortChange = onSortChange,
                                )
                            }
                        }

                        item {
                            ParkOverviewStrip(
                                state = state,
                                onClearFilters = onClearFilters,
                            )
                        }

                        if (
                            state.query.length >= 2 &&
                            state.parks.isNotEmpty() &&
                            state.attractionSearchResults.isNotEmpty()
                        ) {
                            item {
                                Text(
                                    localized(state.language, de = "Parks", en = "Parks", fr = "Parcs", nl = "Parken"),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        if (state.query.length >= 2 && state.isStatisticsIndexLoading && state.attractionSearchResults.isEmpty()) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text(
                                        localized(
                                            state.language,
                                            de = "Attraktionsindex wird geladen",
                                            en = "Loading attraction index",
                                            fr = "Chargement de l'index des attractions",
                                            nl = "Attractie-index wordt geladen",
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        if (state.parks.isEmpty() && state.showOpenOnly && state.isOpenParkDataLoading) {
                            item {
                                OpenParksLoadingCard(language = state.language)
                            }
                        } else if (state.parks.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 96.dp, bottom = 64.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        localized(state.language, de = "Keine Parks gefunden", en = "No parks found", fr = "Aucun parc trouvé", nl = "Geen parken gevonden"),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(state.parks, key = { _, park -> park.id }) { index, park ->
                                AnimatedVisibility(
                                    visible = true,
                                    enter = fadeIn(tween(200, delayMillis = index * 30)) +
                                            slideInVertically(tween(250, delayMillis = index * 30)) { it / 3 },
                                ) {
                                    ParkCard(
                                        park = park,
                                        language = state.language,
                                        onClick = { onParkClick(park) },
                                        onQuickAddWatchlist = {
                                            selectedParkForWatchlist = park
                                            showAddWatchlistDialog = true
                                        },
                                        onStatisticsClick = {
                                            onParkStatisticsClick(
                                                state.statisticsParkKeys[park.id]
                                                    ?: state.statisticsParkKeys[park.uuid]
                                                    ?: park.id
                                            )
                                        },
                                    ) { onToggleFavorite(park) }
                                }
                            }
                        }

                        item {
                            AttributionBanner(language = state.language)
                        }

                        if (state.query.length >= 2 && state.attractionSearchResults.isNotEmpty()) {
                            item {
                                Text(
                                    localized(state.language, de = "Attraktionen", en = "Attractions", fr = "Attractions", nl = "Attracties"),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            itemsIndexed(state.attractionSearchResults, key = { _, result -> "${result.parkKey}_${result.attractionId}" }) { _, result ->
                                AttractionSearchResultCard(
                                    result = result,
                                    language = state.language,
                                    onClick = { onAttractionClick(result.parkKey, result.attractionId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedParkForWatchlist?.let { park ->
        if (showAddWatchlistDialog) {
            AddWatchlistDialog(
                parkKey = park.id,
                attractionId = null,
                attractionName = null,
                language = state.language,
                onDismiss = {
                    showAddWatchlistDialog = false
                    selectedParkForWatchlist = null
                }
            )
        }
    }
}

@Composable
private fun RecentParksSection(
    parks: List<Park>,
    language: String,
    onParkClick: (Park) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = localized(language, de = "Zuletzt angesehen", en = "Recently viewed", fr = "Récemment consultés", nl = "Recent bekeken"),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            parks.forEach { park ->
                FilterChip(
                    selected = false,
                    onClick = { onParkClick(park) },
                    label = {
                        Text(
                            "${countryToFlag(park.country)} ${park.name}",
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }
    }
}

@Composable
private fun FavoriteDashboardSection(
    items: List<FavoriteDashboardItem>,
    language: String,
    onParkClick: (Park) -> Unit,
) {
    val openCount = items.count { it.isOpen }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = localized(
                    language,
                    de = "Favoriten-Dashboard",
                    en = "Favorites dashboard",
                    fr = "Tableau de bord des favoris",
                    nl = "Favorietendashboard",
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = localized(
                    language,
                    de = "$openCount von ${items.size} geöffnet",
                    en = "$openCount of ${items.size} open",
                    fr = "$openCount sur ${items.size} ouverts",
                    nl = "$openCount van ${items.size} open",
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items
            .sortedWith(
                compareByDescending<FavoriteDashboardItem> { it.isOpen }
                    .thenBy { it.maxWaitMinutes ?: Int.MAX_VALUE }
                    .thenBy { it.park.name.lowercase() },
            )
            .chunked(2)
            .forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowItems.forEach { item ->
                        FavoriteDashboardCard(
                            item = item,
                            language = language,
                            modifier = Modifier.weight(1f),
                            onClick = { onParkClick(item.park) },
                        )
                    }
                    if (rowItems.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
    }
}

@Composable
private fun FavoriteDashboardCard(
    item: FavoriteDashboardItem,
    language: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedCard(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (item.isOpen) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                item.park.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            FavoriteStatusBadge(isOpen = item.isOpen, language = language)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashboardMetric(
                    label = localized(language, de = "Offen", en = "Open", fr = "Ouvert", nl = "Open"),
                    value = "${item.openAttractions}/${item.totalAttractions}",
                    modifier = Modifier.weight(1f),
                )
                DashboardMetric(
                    label = localized(language, de = "Max", en = "Max", fr = "Max", nl = "Max"),
                    value = item.maxWaitMinutes?.let { "$it" } ?: "-",
                    modifier = Modifier.weight(1f),
                )
                DashboardMetric(
                    label = localized(language, de = "Daten", en = "Data", fr = "Données", nl = "Data"),
                    value = item.dataAgeMinutes.shortAgeLabel(language),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FavoriteStatusBadge(isOpen: Boolean, language: String) {
    val color = if (isOpen) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
    val text = if (isOpen) {
        localized(language, de = "Geöffnet", en = "Open", fr = "Ouvert", nl = "Open")
    } else {
        localized(language, de = "Geschlossen", en = "Closed", fr = "Fermé", nl = "Gesloten")
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color = color, shape = CircleShape),
        )
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DashboardMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OfflineDataBanner(
    language: String,
    ageMinutes: Long?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localized(
                        language,
                        de = "Gecachte Daten werden angezeigt",
                        en = "Showing cached data",
                        fr = "Affichage des données en cache",
                        nl = "Gecachte gegevens worden weergegeven",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = localized(
                        language,
                        de = "Letzte erfolgreiche Park-Aktualisierung: ${ageMinutes.cacheAgeLabel(language)}. Live-Status kann veraltet sein.",
                        en = "Last successful park update: ${ageMinutes.cacheAgeLabel(language)}. Some live status may be outdated.",
                        fr = "Dernière mise à jour réussie des parcs : ${ageMinutes.cacheAgeLabel(language)}. Certains statuts en direct peuvent être obsolètes.",
                        nl = "Laatste succesvolle parkupdate: ${ageMinutes.cacheAgeLabel(language)}. Sommige livestatussen kunnen verouderd zijn.",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.86f),
                )
            }
        }
    }
}

@Composable
private fun FallbackParkListBanner(language: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localized(
                        language,
                        de = "Eingeschränkte Parkliste (Ausweichquelle)",
                        en = "Limited park list (fallback source)",
                        fr = "Liste de parcs limitée (source de secours)",
                        nl = "Beperkte parklijst (alternatieve bron)",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = localized(
                        language,
                        de = "wartezeiten.app ist aktuell nicht erreichbar. Es werden bekannte große Parks angezeigt (Quelle: queue-times.com); Wartezeiten und Öffnungszeiten der jeweiligen Parks funktionieren normal.",
                        en = "wartezeiten.app is currently unreachable. Well-known large parks are shown instead (source: queue-times.com); wait times and opening hours for these parks work normally.",
                        fr = "wartezeiten.app est actuellement injoignable. De grands parcs connus sont affichés à la place (source : queue-times.com) ; les temps d'attente et horaires de ces parcs fonctionnent normalement.",
                        nl = "wartezeiten.app is momenteel niet bereikbaar. In plaats daarvan worden bekende grote parken getoond (bron: queue-times.com); wachttijden en openingstijden van deze parken werken normaal.",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.86f),
                )
            }
        }
    }
}

@Composable
private fun SearchHistoryRow(
    history: List<String>,
    language: String,
    onHistoryClick: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = localized(language, de = "Letzte Suchen", en = "Recent searches", fr = "Recherches récentes", nl = "Recente zoekopdrachten"),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onClearHistory) {
                Text(localized(language, de = "Leeren", en = "Clear", fr = "Effacer", nl = "Wissen"))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            history.forEach { value ->
                FilterChip(
                    selected = false,
                    onClick = { onHistoryClick(value) },
                    label = {
                        Text(
                            value,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }
    }
}

@Composable
private fun QuickFavoriteParksSection(
    parks: List<Park>,
    language: String,
    onParkClick: (Park) -> Unit,
    onQuickAddWatchlist: (Park) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = localized(language, de = "Schnellzugriff", en = "Quick access", fr = "Accès rapide", nl = "Snelle toegang"),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            parks.forEach { park ->
                OutlinedCard(
                    modifier = Modifier
                        .width(220.dp)
                        .clickable { onParkClick(park) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(park.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("${countryToFlag(park.country)} ${park.country}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        IconButton(onClick = { onQuickAddWatchlist(park) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttractionSearchResultCard(
    result: AttractionSearchResult,
    language: String,
    onClick: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_stats_bar_chart_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.attractionName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = result.parkName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = result.lastValue.toWaitValueLabel(language),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (result.lastValue != null && result.lastValue >= 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                result.averageWaitMinutes?.let { average ->
                    Text(
                        text = "Ø ${String.format(java.util.Locale.GERMAN, "%.1f", average)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun Int?.toWaitValueLabel(language: String): String {
    return when (this) {
        null -> "-"
        -1 -> localized(language, de = "Geschlossen", en = "Closed", fr = "Fermé", nl = "Gesloten")
        -2 -> localized(language, de = "Wetter", en = "Weather", fr = "Météo", nl = "Weer")
        -3 -> localized(language, de = "Wartung", en = "Maint.", fr = "Maint.", nl = "Onderh.")
        -4 -> localized(language, de = "Unbekannt", en = "Unknown", fr = "Inconnu", nl = "Onbekend")
        else -> "$this Min."
    }
}

@Composable
private fun BestParkRankingSection(
    recommendations: List<ParkRecommendation>,
    language: String,
    onParkClick: (ParkRecommendation) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = localized(language, de = "Bester Wert heute", en = "Best value today", fr = "Meilleure valeur aujourd'hui", nl = "Beste waarde vandaag"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    recommendations.firstOrNull()?.let { best ->
                        Text(
                            text = best.park.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = recommendations.firstOrNull()?.score?.toString().orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) {
                        localized(language, de = "Ranking einklappen", en = "Hide ranking", fr = "Masquer le classement", nl = "Ranglijst verbergen")
                    } else {
                        localized(language, de = "Ranking ausklappen", en = "Show ranking", fr = "Afficher le classement", nl = "Ranglijst weergeven")
                    },
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    recommendations.forEachIndexed { index, recommendation ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onParkClick(recommendation) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "#${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = recommendation.park.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = recommendation.localizedReason(language),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = recommendation.score.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BestParkLoadingCard(
    scanStatus: String?,
    language: String,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localized(language, de = "Bester Park heute", en = "Best park today", fr = "Meilleur parc aujourd'hui", nl = "Beste park vandaag"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = scanStatus ?: localized(
                        language,
                        de = "Live-Daten werden abgeglichen",
                        en = "Checking live data",
                        fr = "Vérification des données en direct",
                        nl = "Livegegevens worden gecontroleerd",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun BestParkCard(
    recommendation: ParkRecommendation,
    scanStatus: String?,
    language: String,
    onClick: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localized(language, de = "Bester Park heute", en = "Best park today", fr = "Meilleur parc aujourd'hui", nl = "Beste park vandaag"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = recommendation.park.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = recommendation.localizedReason(language),
                    style = MaterialTheme.typography.bodySmall,
                )
                scanStatus?.let { status ->
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Text(
                text = "${recommendation.score}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun CountryFilterRow(
    countries: List<String>,
    selected: String?,
    showOpenOnly: Boolean,
    showFavoritesOnly: Boolean,
    sort: ParkSort,
    language: String,
    onSelect: (String?) -> Unit,
    onToggleOpenOnly: () -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onSortChange: (ParkSort) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = showFavoritesOnly,
            onClick = onToggleFavoritesOnly,
            label = { Text(localized(language, de = "Favoriten", en = "Favorites", fr = "Favoris", nl = "Favorieten"), style = MaterialTheme.typography.labelMedium) },
            leadingIcon = if (showFavoritesOnly) {
                { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else {
                { Icon(Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(16.dp)) }
            },
            shape = RoundedCornerShape(12.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        )

        FilterChip(
            selected = showOpenOnly,
            onClick = onToggleOpenOnly,
            label = { Text(localized(language, de = "Nur offen", en = "Open only", fr = "Ouverts uniquement", nl = "Alleen open"), style = MaterialTheme.typography.labelMedium) },
            leadingIcon = if (showOpenOnly) {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null,
            shape = RoundedCornerShape(12.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        )

        VerticalDivider(modifier = Modifier.height(24.dp))

        var sortExpanded by remember { mutableStateOf(false) }
        Box {
            FilterChip(
                selected = sort != ParkSort.Name,
                onClick = { sortExpanded = true },
                label = { Text(sort.label(language), style = MaterialTheme.typography.labelMedium) },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
            DropdownMenu(
                expanded = sortExpanded,
                onDismissRequest = { sortExpanded = false },
            ) {
                ParkSort.entries.forEach { value ->
                    DropdownMenuItem(
                        text = { Text(value.label(language)) },
                        onClick = {
                            onSortChange(value)
                            sortExpanded = false
                        },
                        trailingIcon = if (sort == value) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                    )
                }
            }
        }

        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(localized(language, de = "Alle Länder", en = "All countries", fr = "Tous les pays", nl = "Alle landen"), style = MaterialTheme.typography.labelMedium) },
            shape = RoundedCornerShape(12.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )

        countries.forEach { country ->
            FilterChip(
                selected = selected == country,
                onClick = { onSelect(if (selected == country) null else country) },
                label = {
                    Text(
                        "${countryToFlag(country)} $country",
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

@Composable
private fun ParkOverviewStrip(
    state: ParkListUiState,
    onClearFilters: () -> Unit,
) {
    val hasFilters = state.query.isNotBlank() ||
            state.selectedCountry != null ||
            state.showOpenOnly ||
            state.showFavoritesOnly ||
            state.sort != ParkSort.Name

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OverviewMetric(
                label = localized(state.language, de = "Angezeigt", en = "Shown", fr = "Affichés", nl = "Getoond"),
                value = "${state.parks.size}/${state.totalParkCount}",
                modifier = Modifier.weight(1f),
            )
            OverviewMetric(
                label = localized(state.language, de = "Favoriten", en = "Favorites", fr = "Favoris", nl = "Favorieten"),
                value = state.favoriteParks.size.toString(),
                modifier = Modifier.weight(1f),
            )
            OverviewMetric(
                label = localized(state.language, de = "Länder", en = "Countries", fr = "Pays", nl = "Landen"),
                value = state.visibleCountryCount.toString(),
                modifier = Modifier.weight(1f),
            )
            if (hasFilters) {
                IconButton(onClick = onClearFilters, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = localized(state.language, de = "Filter zurücksetzen", en = "Reset filters", fr = "Réinitialiser les filtres", nl = "Filters resetten"),
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun RefreshSnackbar(data: SnackbarData) {
    Surface(
        modifier = Modifier.padding(12.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.inversePrimary,
            )
            Text(
                text = data.visuals.message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
            data.visuals.actionLabel?.let { action ->
                TextButton(onClick = { data.performAction() }) {
                    Text(action)
                }
            }
        }
    }
}

@Composable
private fun OpenParksLoadingCard(language: String) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localized(
                        language,
                        de = "Offene Parks werden gesucht",
                        en = "Checking open parks",
                        fr = "Recherche des parcs ouverts",
                        nl = "Open parken worden gezocht",
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = localized(
                        language,
                        de = "Aktuelle Öffnungszeiten und Wartezeitdaten werden abgeglichen. Das kann ein paar Sekunden dauern.",
                        en = "Current opening times and wait-time data are being checked. This can take a few seconds.",
                        fr = "Les horaires et temps d'attente actuels sont vérifiés. Cela peut prendre quelques secondes.",
                        nl = "Actuele openingstijden en wachttijden worden gecontroleerd. Dit kan enkele seconden duren.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    language: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            TextButton(onClick = onRetry) {
                Text(
                    localized(language, de = "Erneut", en = "Retry", fr = "Réessayer", nl = "Opnieuw"),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun ParkSort.label(language: String) = when (this) {
    ParkSort.FavoritesFirst -> localized(language, de = "Favoriten zuerst", en = "Favorites first", fr = "Favoris en premier", nl = "Favorieten eerst")
    ParkSort.Name -> "Name A-Z"
    ParkSort.Country -> localized(language, de = "Land", en = "Country", fr = "Pays", nl = "Land")
}

private fun ParkRecommendation.localizedReason(language: String): String {
    val crowdText = crowdLevel?.let {
        localized(
            language,
            de = "ca. ${it.toInt()}% Auslastung",
            en = "approx. ${it.toInt()}% crowd level",
            fr = "environ ${it.toInt()}% de fréquentation",
            nl = "ca. ${it.toInt()}% drukte",
        )
    } ?: localized(
        language,
        de = "Auslastung unbekannt",
        en = "crowd level unknown",
        fr = "fréquentation inconnue",
        nl = "drukte onbekend",
    )
    val attractionText = if (totalAttractions > 0) {
        localized(
            language,
            de = "$openAttractions von $totalAttractions Attraktionen offen",
            en = "$openAttractions of $totalAttractions attractions open",
            fr = "$openAttractions sur $totalAttractions attractions ouvertes",
            nl = "$openAttractions van $totalAttractions attracties open",
        )
    } else {
        localized(
            language,
            de = "$openAttractions Attraktionen offen",
            en = "$openAttractions attractions open",
            fr = "$openAttractions attractions ouvertes",
            nl = "$openAttractions attracties open",
        )
    }
    return "$crowdText, $attractionText"
}

private fun Long?.cacheAgeLabel(language: String): String {
    val minutes = this ?: return localized(language, de = "unbekannt", en = "unknown", fr = "inconnu", nl = "onbekend")
    return when {
        minutes <= 1L -> localized(language, de = "gerade eben", en = "just now", fr = "à l'instant", nl = "net nu")
        minutes < 60L -> localized(
            language,
            de = "vor $minutes Minuten",
            en = "$minutes minutes ago",
            fr = "il y a $minutes minutes",
            nl = "$minutes minuten geleden",
        )
        minutes < 120L -> localized(language, de = "vor 1 Stunde", en = "1 hour ago", fr = "il y a 1 heure", nl = "1 uur geleden")
        else -> {
            val hours = minutes / 60L
            localized(
                language,
                de = "vor $hours Stunden",
                en = "$hours hours ago",
                fr = "il y a $hours heures",
                nl = "$hours uur geleden",
            )
        }
    }
}

private fun Long?.shortAgeLabel(language: String): String {
    val minutes = this ?: return "-"
    return when {
        minutes < 1L -> localized(language, de = "jetzt", en = "now", fr = "maintenant", nl = "nu")
        minutes < 60L -> "${minutes}m"
        else -> "${minutes / 60L}h"
    }
}

@Composable
private fun ParkCard(
    park: Park,
    language: String,
    onClick: () -> Unit,
    onQuickAddWatchlist: () -> Unit,
    onStatisticsClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (park.isFavorite) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onClick)
                        .padding(vertical = 2.dp),
                ) {
                    Text(
                        text = park.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val flag = countryToFlag(park.country)
                        if (flag.isNotEmpty()) {
                            Text(
                                text = flag,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                        Text(
                            text = park.country,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            CompactParkActionButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (park.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (park.isFavorite) {
                        localized(
                            language,
                            de = "Von Favoriten entfernen",
                            en = "Remove from favorites",
                            fr = "Retirer des favoris",
                            nl = "Verwijderen uit favorieten",
                        )
                    } else {
                        localized(
                            language,
                            de = "Zu Favoriten hinzufügen",
                            en = "Add to favorites",
                            fr = "Ajouter aux favoris",
                            nl = "Toevoegen aan favorieten",
                        )
                    },
                    tint = if (park.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(21.dp),
                )
            }

            CompactParkActionButton(onClick = onQuickAddWatchlist) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = localized(
                        language,
                        de = "Parkweite Benachrichtigung hinzufügen",
                        en = "Add park-wide notification",
                        fr = "Ajouter une alerte pour tout le parc",
                        nl = "Parkbrede melding toevoegen",
                    ),
                    modifier = Modifier.size(21.dp),
                )
            }

            CompactParkActionButton(onClick = onStatisticsClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_stats_bar_chart_24),
                    contentDescription = localized(
                        language,
                        de = "Parkstatistik anzeigen",
                        en = "Show park statistics",
                        fr = "Afficher les statistiques du parc",
                        nl = "Parkstatistieken weergeven",
                    ),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp),
                )
            }

            Text(
                "›",
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun OfflineStatusBadge(language: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.padding(top = 3.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(12.dp))
            Text(
                text = localized(language, de = "Cache", en = "Cached", fr = "En cache", nl = "Gecachet"),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CompactParkActionButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(38.dp),
        content = content,
    )
}

internal fun countryToFlag(country: String): String {
    return countryToIsoCode(country)?.let(::flagEmojiForCountryCode).orEmpty()
}

private fun countryToIsoCode(country: String): String? {
    return when (country.lowercase().trim()) {
        "deutschland", "germany", "de" -> "DE"
        "österreich", "austria", "at" -> "AT"
        "schweiz", "switzerland", "ch" -> "CH"
        "frankreich", "france", "fr" -> "FR"
        "niederlande", "netherlands", "nl" -> "NL"
        "belgien", "belgium", "be" -> "BE"
        "vereinigtes königreich", "united kingdom", "uk", "gb", "great britain", "großbritannien" -> "GB"
        "usa", "us", "u.s.a.", "united states", "united states of america",
        "vereinigte staaten", "vereinigte staaten von amerika" -> "US"
        "spanien", "spain", "es" -> "ES"
        "italien", "italy", "it" -> "IT"
        "dänemark", "denmark", "dk" -> "DK"
        "schweden", "sweden", "se" -> "SE"
        "norwegen", "norway", "no" -> "NO"
        "finnland", "finland", "fi" -> "FI"
        "japan", "jp" -> "JP"
        "tschechien", "czech republic", "cz" -> "CZ"
        "polen", "poland", "pl" -> "PL"
        "portugal", "pt" -> "PT"
        "luxemburg", "luxembourg", "lu" -> "LU"
        else -> null
    }
}

private fun flagEmojiForCountryCode(countryCode: String): String {
    return countryCode
        .uppercase()
        .map { letter -> Character.toChars(REGIONAL_INDICATOR_OFFSET + (letter - 'A')).concatToString() }
        .joinToString(separator = "")
}

private const val REGIONAL_INDICATOR_OFFSET = 0x1F1E6
