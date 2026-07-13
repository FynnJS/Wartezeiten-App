package de.wartezeiten.app.ui.parks

import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.platform.LocalContext
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
        onSearch = { viewModel.recordCurrentSearch() },
        onClearSearchHistory = viewModel::clearSearchHistory,
        onCountrySelected = viewModel::onCountrySelected,
        onToggleOpenOnly = viewModel::onToggleOpenOnly,
        onToggleFavoritesOnly = viewModel::onToggleFavoritesOnly,
        onSortChange = viewModel::setSort,
        onRefresh = { viewModel.refresh() },
        onParkClick = onParkClick,
        onToggleFavorite = viewModel::toggleFavorite,
        onRecommendationClick = { onParkClick(it.park) },
        onSettingsClick = onSettingsClick,
        onWatchlistClick = onWatchlistClick,
        onCompareClick = onCompareClick,
        onParkStatisticsClick = onParkStatisticsClick,
        onAttractionClick = onAttractionClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkListScreen(
    state: ParkListUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClearSearchHistory: () -> Unit,
    onCountrySelected: (String?) -> Unit,
    onToggleOpenOnly: () -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onSortChange: (ParkSort) -> Unit,
    onRefresh: () -> Unit,
    onParkClick: (Park) -> Unit,
    onToggleFavorite: (Park) -> Unit,
    onRecommendationClick: (ParkRecommendation) -> Unit,
    onSettingsClick: () -> Unit,
    onWatchlistClick: () -> Unit,
    onCompareClick: () -> Unit,
    onParkStatisticsClick: (String) -> Unit,
    onAttractionClick: (String, String) -> Unit,
) {
    var showAddWatchlistDialog by remember { mutableStateOf(false) }
    var selectedParkForWatchlist by remember { mutableStateOf<Park?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        localized(state.language, de = "Freizeitparks", en = "Amusement Parks", fr = "Parcs d'attractions", nl = "Pretparken"),
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onWatchlistClick) {
                        Icon(Icons.Default.Notifications, contentDescription = localized(state.language, de = "Merkliste", en = "Watchlist", fr = "Liste de surveillance", nl = "Watchlist"))
                    }
                    IconButton(onClick = onCompareClick) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = localized(state.language, de = "Vergleichen", en = "Compare", fr = "Comparer", nl = "Vergelijken"))
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = localized(state.language, de = "Einstellungen", en = "Settings", fr = "Paramètres", nl = "Instellingen"))
                    }
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(24.dp),
                            strokeWidth = 2.5.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = localized(state.language, de = "Aktualisieren", en = "Refresh", fr = "Actualiser", nl = "Vernieuwen"))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Ladebalken unter TopAppBar
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

            // Fehler-Banner
            AnimatedVisibility(
                visible = state.errorMessage != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                state.errorMessage?.let { msg ->
                    ErrorBanner(
                        message = msg,
                        onRetry = onRefresh,
                        language = state.language,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    if (state.isShowingOfflineData) {
                        OfflineDataBanner(
                            language = state.language,
                            ageMinutes = state.offlineDataAgeMinutes,
                        )
                    }
                }

                item {
                    if (state.usingFallbackParkList) {
                        FallbackParkListBanner(language = state.language)
                    }
                }

                if (state.recentParks.isNotEmpty() && state.query.isEmpty()) {
                    item {
                        RecentParksSection(
                            recentParks = state.recentParks,
                            language = state.language,
                            onParkClick = onParkClick
                        )
                    }
                }

                if (state.favoriteDashboardItems.isNotEmpty() && state.query.isEmpty()) {
                    item {
                        FavoriteDashboardSection(
                            items = state.favoriteDashboardItems,
                            language = state.language,
                            onParkClick = onParkClick
                        )
                    }
                }

                item(key = "search_bar") {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = if (state.query.isNotEmpty()) {
                            {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = localized(state.language, de = "Suche leeren", en = "Clear search", fr = "Effacer la recherche", nl = "Zoekopdracht wissen"))
                                }
                            }
                        } else null,
                        placeholder = { Text(localized(state.language, de = "Park oder Attraktion suchen", en = "Search park or attraction", fr = "Rechercher un parc ou une attraction", nl = "Park of attractie zoeken")) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }

                if (state.searchHistory.isNotEmpty() && state.query.isEmpty()) {
                    item {
                        SearchHistoryRow(
                            history = state.searchHistory,
                            language = state.language,
                            onSearchSelect = onQueryChange,
                            onClearHistory = onClearSearchHistory,
                        )
                    }
                }

                if (state.query.isEmpty()) {
                    item {
                        BestParkRankingSection(
                            recommendations = state.recommendations,
                            language = state.language,
                            onRecommendationClick = onRecommendationClick,
                        )
                    }
                }

                item {
                    CountryFilterRow(
                        countries = state.availableCountries,
                        selectedCountry = state.selectedCountry,
                        showOpenOnly = state.showOpenOnly,
                        showFavoritesOnly = state.showFavoritesOnly,
                        sort = state.sort,
                        language = state.language,
                        onCountrySelected = onCountrySelected,
                        onToggleOpenOnly = onToggleOpenOnly,
                        onToggleFavoritesOnly = onToggleFavoritesOnly,
                        onSortChange = onSortChange,
                    )
                }

                if (state.parks.isEmpty() && !state.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
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
                                onToggleFavorite = { onToggleFavorite(park) }
                            )
                        }
                    }
                }

                item {
                    AttributionBanner(language = state.language)
                }

                if (state.query.length >= 2 && state.attractionSearchResults.isNotEmpty()) {
                    item {
                        Text(
                            text = localized(
                                state.language,
                                de = "Gefundene Attraktionen",
                                en = "Found attractions",
                                fr = "Attractions trouvées",
                                nl = "Gevonden attracties",
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
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

    if (showAddWatchlistDialog && selectedParkForWatchlist != null) {
        AddWatchlistDialog(
            parkKey = selectedParkForWatchlist!!.id,
            attractionId = null,
            language = state.language,
            onDismiss = {
                showAddWatchlistDialog = false
                selectedParkForWatchlist = null
            }
        )
    }
}

@Composable
private fun RecentParksSection(
    recentParks: List<Park>,
    language: String,
    onParkClick: (Park) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = localized(language, de = "Zuletzt angesehen", en = "Recently viewed", fr = "Récemment consultés", nl = "Onlangs bekeken"),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            recentParks.forEach { park ->
                val flag = countryToFlag(park.country)
                FilterChip(
                    selected = false,
                    onClick = { onParkClick(park) },
                    label = { Text(if (flag.isNotEmpty()) "$flag ${park.name}" else park.name) },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

@Composable
private fun FavoriteDashboardSection(
    items: List<FavoriteDashboardItem>,
    language: String,
    onParkClick: (Park) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = localized(language, de = "Favoriten-Status", en = "Favorites status", fr = "Statut des favoris", nl = "Favorietenstatus"),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items.forEach { item ->
                FavoriteDashboardCard(
                    item = item,
                    language = language,
                    modifier = Modifier.width(220.dp),
                    onClick = { onParkClick(item.park) }
                )
            }
        }
    }
}

@Composable
private fun FavoriteDashboardCard(
    item: FavoriteDashboardItem,
    language: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.park.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                FavoriteStatusBadge(isOpen = item.isOpen, language = language)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardMetric(
                    label = localized(language, de = "Offen", en = "Open", fr = "Ouvert", nl = "Open"),
                    value = "${item.openAttractions}/${item.totalAttractions}",
                    modifier = Modifier.weight(1f)
                )
                DashboardMetric(
                    label = localized(language, de = "Höchste", en = "Max", fr = "Max", nl = "Hoogste"),
                    value = item.maxWaitMinutes?.let { "$it Min" } ?: "-",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FavoriteStatusBadge(isOpen: Boolean, language: String) {
    val color = if (isOpen) Color(0xFF2E7D32) else Color(0xFFC62828)
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
        contentColor = color
    ) {
        Text(
            text = if (isOpen) localized(language, de = "Offen", en = "Open", fr = "Ouvert", nl = "Open")
            else localized(language, de = "Zu", en = "Closed", fr = "Fermé", nl = "Gesloten"),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun DashboardMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black)
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
                    text = localized(language, de = "Offline-Modus", en = "Offline mode", fr = "Mode hors ligne", nl = "Offline modus"),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = localized(
                        language,
                        de = "Letzte erfolgreiche Aktualisierung: ${ageMinutes.cacheAgeLabel(language)}.",
                        en = "Last successful update: ${ageMinutes.cacheAgeLabel(language)}.",
                        fr = "Dernière mise à jour réussie : ${ageMinutes.cacheAgeLabel(language)}.",
                        nl = "Laatste succesvolle update: ${ageMinutes.cacheAgeLabel(language)}.",
                    ),
                    style = MaterialTheme.typography.labelSmall,
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
                    text = localized(language, de = "Eingeschränkte Parkliste", en = "Limited park list", fr = "Liste des parcs limitée", nl = "Beperkte parklijst"),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = localized(
                        language,
                        de = "Die vollständige Parkliste kann aktuell nicht geladen werden. Es werden nur Parks angezeigt, für die bereits Daten lokal gespeichert sind.",
                        en = "The complete park list could not be loaded. Only parks with locally cached data are shown.",
                        fr = "La liste complète des parcs n'a pas pu être chargée. Seuls les parcs ayant des données locales sont affichés.",
                        nl = "De volledige parklijst kon niet worden geladen. Alleen parken met lokaal opgeslagen gegevens worden weergegeven.",
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
    onSearchSelect: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = localized(language, de = "Letzte Suchen", en = "Recent searches", fr = "Recherches récentes", nl = "Recente zoekopdrachten"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = localized(language, de = "Leeren", en = "Clear", fr = "Effacer", nl = "Wissen"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onClearHistory)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            history.forEach { query ->
                FilterChip(
                    selected = false,
                    onClick = { onSearchSelect(query) },
                    label = { Text(query) },
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickFavoriteParksSection(
    favoriteParks: List<Park>,
    language: String,
    onParkClick: (Park) -> Unit,
    onToggleFavorite: (Park) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = localized(language, de = "Deine Favoriten", en = "Your favorites", fr = "Tes favoris", nl = "Jouw favorieten"),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            favoriteParks.forEach { park ->
                OutlinedCard(
                    onClick = { onParkClick(park) },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(park.name, style = MaterialTheme.typography.labelLarge)
                        IconButton(
                            onClick = { onToggleFavorite(park) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
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
                        text = "\u00d8 ${String.format(java.util.Locale.GERMAN, "%.1f", average)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun Int?.toWaitValueLabel(language: String): String = when {
    this == null -> "-"
    this < 0 -> when (this) {
        -1 -> localized(language, de = "Geschl.", en = "Closed", fr = "Fermé", nl = "Gesloten")
        -2 -> localized(language, de = "Wetter", en = "Weather", fr = "Météo", nl = "Weer")
        -3 -> localized(language, de = "Wartung", en = "Maint.", fr = "Maint.", nl = "Onderhoud")
        else -> "-"
    }
    else -> "$this Min"
}

@Composable
private fun BestParkRankingSection(
    recommendations: List<ParkRecommendation>,
    language: String,
    onRecommendationClick: (ParkRecommendation) -> Unit,
) {
    if (recommendations.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = localized(language, de = "Aktuelle Empfehlungen", en = "Current recommendations", fr = "Recommandations actuelles", nl = "Actuele aanbevelingen"),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            recommendations.forEach { recommendation ->
                BestParkCard(
                    recommendation = recommendation,
                    language = language,
                    onClick = { onRecommendationClick(recommendation) }
                )
            }
        }
    }
}

@Composable
private fun BestParkCard(
    recommendation: ParkRecommendation,
    language: String,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.width(260.dp),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = recommendation.park.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.tertiary
                ) {
                    Text(
                        text = "${recommendation.score}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = recommendation.localizedReason(language),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardMetric(
                    label = localized(language, de = "Auslastung", en = "Crowd", fr = "Monde", nl = "Drukte"),
                    value = recommendation.crowdLevel?.let { "${it.toInt()}%" } ?: "-",
                    modifier = Modifier.weight(1f)
                )
                DashboardMetric(
                    label = localized(language, de = "Attraktionen", en = "Rides", fr = "Attractions", nl = "Attracties"),
                    value = "${recommendation.openAttractions}/${recommendation.totalAttractions}",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryFilterRow(
    countries: List<String>,
    selectedCountry: String?,
    showOpenOnly: Boolean,
    showFavoritesOnly: Boolean,
    sort: ParkSort,
    language: String,
    onCountrySelected: (String?) -> Unit,
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
        // Sortierung
        var sortExpanded by remember { mutableStateOf(false) }
        Box {
            FilterChip(
                selected = false,
                onClick = { sortExpanded = true },
                label = { Text(sort.label(language)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null, modifier = Modifier.size(16.dp)) },
                shape = RoundedCornerShape(10.dp)
            )
            DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                ParkSort.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.label(language)) },
                        onClick = {
                            onSortChange(s)
                            sortExpanded = false
                        },
                        trailingIcon = if (sort == s) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                }
            }
        }

        VerticalDivider(modifier = Modifier.height(24.dp))

        FilterChip(
            selected = showFavoritesOnly,
            onClick = onToggleFavoritesOnly,
            label = { Text(localized(language, de = "Nur Favoriten", en = "Only favorites", fr = "Favoris uniquement", nl = "Alleen favorieten")) },
            leadingIcon = if (showFavoritesOnly) {
                { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
            } else null,
            shape = RoundedCornerShape(10.dp)
        )

        FilterChip(
            selected = showOpenOnly,
            onClick = onToggleOpenOnly,
            label = { Text(localized(language, de = "Nur offen", en = "Open only", fr = "Ouverts uniquement", nl = "Alleen open")) },
            leadingIcon = if (showOpenOnly) {
                { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
            } else null,
            shape = RoundedCornerShape(10.dp)
        )

        VerticalDivider(modifier = Modifier.height(24.dp))

        // Länder-Chips
        FilterChip(
            selected = selectedCountry == null,
            onClick = { onCountrySelected(null) },
            label = { Text(localized(language, de = "Alle Länder", en = "All countries", fr = "Tous les pays", nl = "Alle landen")) },
            shape = RoundedCornerShape(10.dp)
        )

        countries.forEach { country ->
            val flag = countryToFlag(country)
            FilterChip(
                selected = selectedCountry == country,
                onClick = { onCountrySelected(country) },
                label = { Text(if (flag.isNotEmpty()) "$flag $country" else country) },
                shape = RoundedCornerShape(10.dp)
            )
        }
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
private fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    language: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Warning, contentDescription = null)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) {
                Text(localized(language, de = "Neu laden", en = "Reload", fr = "Recharger", nl = "Opnieuw laden"))
            }
        }
    }
}

private fun ParkSort.label(language: String) = when (this) {
    ParkSort.FavoritesFirst -> localized(language, de = "Favoriten zuerst", en = "Favorites first", fr = "Favoris en premier", nl = "Favorieten eerst")
    ParkSort.Name -> localized(language, de = "Name A-Z", en = "Name A-Z", fr = "Nom A-Z", nl = "Naam A-Z")
    ParkSort.Country -> localized(language, de = "Land", en = "Country", fr = "Pays", nl = "Land")
}

private fun ParkRecommendation.localizedReason(language: String): String {
    val reasonText = reason ?: return ""
    return when {
        reasonText.contains("low wait times") || reasonText.contains("geringe Wartezeiten") ->
            localized(language, de = "Besonders kurze Wartezeiten aktuell.", en = "Exceptionately low wait times right now.", fr = "Temps d'attente particulièrement courts.", nl = "Uitzonderlijk korte wachttijden momenteel.")
        reasonText.contains("balanced") || reasonText.contains("ausgewogen") ->
            localized(language, de = "Gute Mischung aus offenen Attraktionen und moderatem Andrang.", en = "Good mix of open attractions and moderate crowd.", fr = "Bon compromis entre attractions ouvertes et affluence modérée.", nl = "Goede mix van open attracties en matige drukte.")
        reasonText.contains("many open") || reasonText.contains("viele offen") ->
            localized(language, de = "Fast alle Attraktionen sind heute geöffnet.", en = "Almost all attractions are open today.", fr = "Presque toutes les attractions sont ouvertes aujourd'hui.", nl = "Bijna alle attracties zijn vandaag open.")
        else -> reasonText
    }
}

private fun Long?.cacheAgeLabel(language: String): String {
    val minutes = this ?: return localized(language, de = "unbekannt", en = "unknown", fr = "inconnu", nl = "onbekend")
    return when {
        minutes <= 1L -> localized(language, de = "gerade eben", en = "just now", fr = "à l'instant", nl = "zojuist")
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
    val minutes = this ?: return ""
    return when {
        minutes <= 5L -> localized(language, de = "Live", en = "Live", fr = "Direct", nl = "Live")
        minutes < 60L -> "${minutes}m"
        else -> "${minutes / 60}h"
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
                            nl = "Verwijderen aus favorieten",
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
private fun CompactParkActionButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

internal fun countryToFlag(country: String): String {
    return flagEmojiForCountryCode(countryToIsoCode(country) ?: "")
}

private fun countryToIsoCode(country: String): String? {
    return when (country.lowercase().trim()) {
        "deutschland", "germany", "de" -> "DE"
        "\u00f6sterreich", "austria", "at" -> "AT"
        "schweiz", "switzerland", "ch" -> "CH"
        "frankreich", "france", "fr" -> "FR"
        "niederlande", "netherlands", "nl" -> "NL"
        "belgien", "belgium", "be" -> "BE"
        "vereinigtes k\u00f6nigreich", "united kingdom", "uk", "gb", "great britain", "gro\u00dfbritannien" -> "GB"
        "usa", "us", "u.s.a.", "united states", "united states of america", "vereinigte staaten", "vereinigte staaten von amerika" -> "US"
        "spanien", "spain", "es" -> "ES"
        "italien", "italy", "it" -> "IT"
        "d\u00e4nemark", "denmark", "dk" -> "DK"
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
    if (countryCode.length != 2) return ""
    val firstLetter = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6
    val secondLetter = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
}

private const val REGIONAL_INDICATOR_OFFSET = 0x1F1E6 - 0x41
