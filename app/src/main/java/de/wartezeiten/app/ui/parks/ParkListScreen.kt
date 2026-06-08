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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Insights
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    onStatisticsClick: () -> Unit,
    onParkStatisticsClick: (String) -> Unit,
    onAttractionClick: (String, String) -> Unit,
    viewModel: ParkListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ParkListScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onCountrySelected = viewModel::onCountrySelected,
        onToggleOpenOnly = viewModel::onToggleOpenOnly,
        onToggleFavoritesOnly = viewModel::onToggleFavoritesOnly,
        onSortChange = viewModel::setSort,
        onClearFilters = viewModel::clearFilters,
        onToggleFavorite = viewModel::toggleFavorite,
        onRefreshClick = { viewModel.refresh() },
        onParkClick = onParkClick,
        onSettingsClick = onSettingsClick,
        onWatchlistClick = onWatchlistClick,
        onStatisticsClick = onStatisticsClick,
        onParkStatisticsClick = onParkStatisticsClick,
        onAttractionClick = onAttractionClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkListScreen(
    state: ParkListUiState,
    onQueryChange: (String) -> Unit,
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
    onStatisticsClick: () -> Unit,
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
                    message = if (state.language == "en") {
                        "${state.totalParkCount} parks updated at $updatedAt"
                    } else {
                        "${state.totalParkCount} Parks aktualisiert um $updatedAt"
                    },
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
                            if (state.language == "en") "Wait times" else "Wartezeiten",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (state.language == "en") "Theme parks" else "Freizeitparks",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                            Icon(Icons.Default.Refresh, contentDescription = if (state.language == "en") "Refresh" else "Aktualisieren")
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = if (state.language == "en") "Settings" else "Einstellungen")
                        }
                        IconButton(onClick = onWatchlistClick) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Watchlist")
                        }
                        IconButton(onClick = onStatisticsClick) {
                            Icon(Icons.Default.Insights, contentDescription = if (state.language == "en") "Statistics" else "Statistik")
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
                                if (state.language == "en") "Loading parks…" else "Parks werden geladen…",
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
                                OfflineDataBanner(language = state.language)
                            }
                        }

                        item {
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
                                        if (state.language == "en") "Search park or attraction…" else "Park oder Attraktion suchen…",
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
                                    if (state.language == "en") "Parks" else "Parks",
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
                                        if (state.language == "en") "Loading attraction index" else "Attraktionsindex wird geladen",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        if (state.parks.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 96.dp, bottom = 64.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (state.language == "en") "No parks found" else "Keine Parks gefunden",
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
                                    if (state.language == "en") "Attractions" else "Attraktionen",
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
                onDismiss = {
                    showAddWatchlistDialog = false
                    selectedParkForWatchlist = null
                }
            )
        }
    }
}

@Composable
private fun OfflineDataBanner(language: String) {
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
            Text(
                text = if (language == "en") "Showing offline data" else "Offline-Daten werden angezeigt",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
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
            text = if (language == "en") "Quick access" else "Schnellzugriff",
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
                Icons.Default.Insights,
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
        -1 -> if (language == "en") "Closed" else "Geschlossen"
        -2 -> if (language == "en") "Weather" else "Wetter"
        -3 -> if (language == "en") "Maint." else "Wartung"
        -4 -> if (language == "en") "Unknown" else "Unbekannt"
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
                        text = if (language == "en") "Best value today" else "Bester Wert heute",
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
                        if (language == "en") "Hide ranking" else "Ranking einklappen"
                    } else {
                        if (language == "en") "Show ranking" else "Ranking ausklappen"
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
                    text = if (language == "en") "Best park today" else "Bester Park heute",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = scanStatus ?: if (language == "en") "Checking live data" else "Live-Daten werden abgeglichen",
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
                    text = if (language == "en") "Best park today" else "Bester Park heute",
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
            label = { Text(if (language == "en") "Favorites" else "Favoriten", style = MaterialTheme.typography.labelMedium) },
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
            label = { Text(if (language == "en") "Open only" else "Nur offen", style = MaterialTheme.typography.labelMedium) },
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
            label = { Text(if (language == "en") "All countries" else "Alle Länder", style = MaterialTheme.typography.labelMedium) },
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
                label = if (state.language == "en") "Shown" else "Angezeigt",
                value = "${state.parks.size}/${state.totalParkCount}",
                modifier = Modifier.weight(1f),
            )
            OverviewMetric(
                label = if (state.language == "en") "Favorites" else "Favoriten",
                value = state.favoriteParks.size.toString(),
                modifier = Modifier.weight(1f),
            )
            OverviewMetric(
                label = if (state.language == "en") "Countries" else "Länder",
                value = state.visibleCountryCount.toString(),
                modifier = Modifier.weight(1f),
            )
            if (hasFilters) {
                IconButton(onClick = onClearFilters, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = if (state.language == "en") "Reset filters" else "Filter zurücksetzen",
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
                    if (language == "en") "Retry" else "Erneut",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun ParkSort.label(language: String) = when (this) {
    ParkSort.FavoritesFirst -> if (language == "en") "Favorites first" else "Favoriten zuerst"
    ParkSort.Name -> "Name A-Z"
    ParkSort.Country -> if (language == "en") "Country" else "Land"
}

private fun ParkRecommendation.localizedReason(language: String): String {
    val crowdText = crowdLevel?.let {
        if (language == "en") "approx. ${it.toInt()}% crowd level" else "ca. ${it.toInt()}% Auslastung"
    } ?: if (language == "en") {
        "crowd level unknown"
    } else {
        "Auslastung unbekannt"
    }
    val attractionText = if (totalAttractions > 0) {
        if (language == "en") {
            "$openAttractions of $totalAttractions attractions open"
        } else {
            "$openAttractions von $totalAttractions Attraktionen offen"
        }
    } else {
        if (language == "en") "$openAttractions attractions open" else "$openAttractions Attraktionen offen"
    }
    return "$crowdText, $attractionText"
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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

            CompactParkActionButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (park.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (language == "en") {
                        if (park.isFavorite) "Remove from favorites" else "Add to favorites"
                    } else {
                        if (park.isFavorite) "Von Favoriten entfernen" else "Zu Favoriten hinzufügen"
                    },
                    tint = if (park.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(21.dp),
                )
            }

            CompactParkActionButton(onClick = onQuickAddWatchlist) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = if (language == "en") {
                        "Add park-wide notification"
                    } else {
                        "Parkweite Benachrichtigung hinzufügen"
                    },
                    modifier = Modifier.size(21.dp),
                )
            }

            CompactParkActionButton(onClick = onStatisticsClick) {
                Icon(
                    Icons.Default.Insights,
                    contentDescription = if (language == "en") {
                        "Show park statistics"
                    } else {
                        "Parkstatistik anzeigen"
                    },
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp),
                )
            }

            Text(
                "›",
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
