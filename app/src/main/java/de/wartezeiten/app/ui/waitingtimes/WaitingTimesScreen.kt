package de.wartezeiten.app.ui.waitingtimes

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.wartezeiten.app.R
import de.wartezeiten.app.core.i18n.localized
import de.wartezeiten.app.domain.model.AttractionStatus
import de.wartezeiten.app.domain.model.CrowdLevel
import de.wartezeiten.app.domain.model.HolidayInfo
import de.wartezeiten.app.domain.model.OpeningTimes
import de.wartezeiten.app.domain.model.Park
import de.wartezeiten.app.domain.model.WaitingTime
import de.wartezeiten.app.domain.model.WeatherInfo
import de.wartezeiten.app.ui.components.AttributionFooter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WaitingTimesRoute(
    onBackClick: () -> Unit,
    onAttractionClick: (String, String) -> Unit,
    onParkStatisticsClick: (String) -> Unit,
    onAttractionStatisticsClick: (String, String) -> Unit,
    viewModel: WaitingTimesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WaitingTimesScreen(
        state = state,
        onBackClick = onBackClick,
        onRefreshClick = { viewModel.refresh() },
        onToggleFavorite = { viewModel.toggleFavorite() },
        onSortChange = viewModel::setSort,
        onFilterChange = viewModel::setFilter,
        onAttractionQueryChange = viewModel::setAttractionQuery,
        onMaxWaitChange = viewModel::setMaxWait,
        onTogglePlannedAttraction = viewModel::togglePlannedAttraction,
        onSaveAttractionNote = viewModel::saveAttractionNote,
        onDeleteAttractionNote = viewModel::deleteAttractionNote,
        onAttractionClick = onAttractionClick,
        onParkStatisticsClick = onParkStatisticsClick,
        onAttractionStatisticsClick = onAttractionStatisticsClick,
        onClearAttractionDetail = viewModel::clearAttractionDetail,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaitingTimesScreen(
    state: WaitingTimesUiState,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSortChange: (WaitingTimesSort) -> Unit,
    onFilterChange: (AttractionFilter) -> Unit,
    onAttractionQueryChange: (String) -> Unit,
    onMaxWaitChange: (Int?) -> Unit,
    onTogglePlannedAttraction: (String) -> Unit,
    onSaveAttractionNote: (String) -> Unit,
    onDeleteAttractionNote: () -> Unit,
    onAttractionClick: (String, String) -> Unit,
    onParkStatisticsClick: (String) -> Unit,
    onAttractionStatisticsClick: (String, String) -> Unit,
    onClearAttractionDetail: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddWatchlistDialog by remember { mutableStateOf(false) }
    var selectedAttractionId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.refreshTrigger, state.isLoading) {
        if ((state.refreshTrigger > 0) && !state.isLoading) {
            val message = state.refreshError ?: run {
                val updatedAt = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                val openCount = state.allWaitingTimes.count { it.status == AttractionStatus.Opened }
                localized(
                    state.language,
                    de = "$openCount offene Attraktionen aktualisiert um $updatedAt Uhr",
                    en = "$openCount open attractions updated at $updatedAt",
                    fr = "$openCount attractions ouvertes mises à jour à $updatedAt",
                    nl = "$openCount open attracties bijgewerkt om $updatedAt",
                )
            }
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "OK",
                withDismissAction = true,
            )
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                RefreshSnackbar(data = data)
            }
        },
        bottomBar = {
            AttributionFooter(language = state.language)
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.park?.name ?: localized(state.language, de = "Laden…", en = "Loading…", fr = "Chargement…", nl = "Laden…"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            state.park?.country ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = localized(state.language, de = "Zurück", en = "Back", fr = "Retour", nl = "Terug"))
                    }
                },
                actions = {
                    state.park?.let { park ->
                        IconButton(onClick = { shareParkDetail(context, state) }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = localized(
                                    state.language,
                                    de = "Parkübersicht teilen",
                                    en = "Share park overview",
                                    fr = "Partager l'aperçu du parc",
                                    nl = "Parkoverzicht delen",
                                ),
                            )
                        }
                        IconButton(onClick = { onParkStatisticsClick(park.id) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_stats_bar_chart_24),
                                contentDescription = localized(
                                    state.language,
                                    de = "Parkstatistik anzeigen",
                                    en = "Show park statistics",
                                    fr = "Afficher les statistiques du parc",
                                    nl = "Parkstatistieken weergeven",
                                ),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    val isFavorite = state.park?.isFavorite == true
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isFavorite) {
                                localized(
                                    state.language,
                                    de = "Von Favoriten entfernen",
                                    en = "Remove from favorites",
                                    fr = "Retirer des favoris",
                                    nl = "Verwijderen aus favorieten",
                                )
                            } else {
                                localized(
                                    state.language,
                                    de = "Zu Favoriten hinzufügen",
                                    en = "Add to favorites",
                                    fr = "Ajouter aux favoris",
                                    nl = "Toevoegen aan favorieten",
                                )
                            },
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
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
                        IconButton(onClick = onRefreshClick) {
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
            // Fehler-Banner
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

            val language = state.language
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Ladebalken direkt als erstes Element in der Liste, um kein padding zu verschwenden
                        AnimatedVisibility(
                            visible = state.isLoading && state.waitingTimes.isEmpty(),
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }

                        state.highlightedAttractionId?.let { highlightedId ->
                            val highlighted = state.allWaitingTimes.firstOrNull { it.attractionId == highlightedId }
                            if (highlighted != null) {
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    AttractionDetailSection(
                                        park = state.park,
                                        item = highlighted,
                                        forecast = state.forecastByAttractionId[highlightedId],
                                        history = state.historyByAttractionId[highlightedId].orEmpty(),
                                        note = state.highlightedAttractionNote,
                                        language = language,
                                        onSaveNote = onSaveAttractionNote,
                                        onDeleteNote = onDeleteAttractionNote,
                                        onAddWatchlist = {
                                            selectedAttractionId = highlightedId
                                            showAddWatchlistDialog = true
                                        },
                                        onClearDetail = onClearAttractionDetail,
                                    )
                                }
                            }
                        }

                        if (state.isShowingOfflineData) {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                OfflineDetailBanner(
                                    language = language,
                                    ageMinutes = state.offlineDataAgeMinutes,
                                )
                            }
                        }

                        if (state.isWaitingTimeDataLikelyMissing) {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                WaitingTimeDataGapBanner(language = language)
                            }
                        }

                        if (state.usedFallbackWaitTimeSource) {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                FallbackWaitTimeSourceBanner(language = language)
                            }
                        }

                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            ParkHeaderSection(
                                currentTime = state.currentLocalTime,
                                openingTimes = state.openingTimes,
                                crowdLevel = state.crowdLevel,
                                waitingTimes = state.allWaitingTimes,
                                localTimeOffsetSeconds = state.localTimeOffsetSeconds,
                                weather = state.weather,
                                holidays = state.holidays,
                                language = language,
                            )
                        }
                    }
                }

                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        DataQualityCard(state = state)
                    }
                }

                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        ParkStatisticsDashboard(
                            statistics = state.parkStatistics,
                            currentCrowdLevel = state.crowdEstimate?.level,
                            language = language,
                            isLoading = state.isLoading || state.isStatisticsLoading,
                        )
                    }
                }

                // Filter & Sortierung
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        FilterSection(
                            sort = state.sort,
                            filter = state.filter,
                            attractionQuery = state.attractionQuery,
                            maxWaitMinutes = state.maxWaitMinutes,
                            onSortChange = onSortChange,
                            onFilterChange = onFilterChange,
                            onAttractionQueryChange = onAttractionQueryChange,
                            onMaxWaitChange = onMaxWaitChange,
                            onAddWatchlist = {
                                selectedAttractionId = null
                                showAddWatchlistDialog = true
                            },
                            language = language,
                        )
                    }
                }

                if (state.plannedWaitingTimes.isNotEmpty()) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            VisitPlannerSection(
                                plannedWaitingTimes = state.plannedWaitingTimes,
                                onRemove = onTogglePlannedAttraction,
                                language = language,
                            )
                        }
                    }
                }

                if (state.waitingTimes.isEmpty() && state.isLoading) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            WaitingTimesLoadingCard(language = language)
                        }
                    }
                } else if (state.waitingTimes.isEmpty()) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            EmptyState(
                                state = state,
                            )
                        }
                    }
                } else {
                    items(
                        count = state.waitingTimes.size,
                        key = { state.waitingTimes[it].attractionId }
                    ) { index ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            WaitingTimeRow(
                                item = state.waitingTimes[index],
                                isPlanned = state.waitingTimes[index].attractionId in state.plannedAttractionIds,
                                onTogglePlanned = onTogglePlannedAttraction,
                                onAddWatchlist = { attractionId ->
                                    selectedAttractionId = attractionId
                                    showAddWatchlistDialog = true
                                },
                                onStatisticsClick = { attractionId ->
                                    state.park?.let { park -> onAttractionStatisticsClick(park.id, attractionId) }
                                },
                                onOpenDetail = { attractionId ->
                                    state.park?.let { park -> onAttractionClick(park.id, attractionId) }
                                },
                                language = language,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddWatchlistDialog && state.park != null) {
        val attractionName = selectedAttractionId?.let { id ->
            state.allWaitingTimes.firstOrNull { it.attractionId == id }?.name
        }
        AddWatchlistDialog(
            parkKey = state.park.id,
            attractionId = selectedAttractionId,
            attractionName = attractionName,
            language = state.language,
            onDismiss = {
                showAddWatchlistDialog = false
                selectedAttractionId = null
            }
        )
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

@Composable
private fun WaitingTimesContent(
    state: WaitingTimesUiState,
    onSortChange: (WaitingTimesSort) -> Unit,
    onFilterChange: (AttractionFilter) -> Unit,
    onAttractionQueryChange: (String) -> Unit,
    onMaxWaitChange: (Int?) -> Unit,
    onTogglePlannedAttraction: (String) -> Unit,
    onSaveAttractionNote: (String) -> Unit,
    onDeleteAttractionNote: () -> Unit,
    onAddWatchlist: () -> Unit,
    onAddWatchlistForAttraction: (String) -> Unit,
    onAttractionStatisticsClick: (String) -> Unit,
    onAttractionClick: (String) -> Unit,
    onClearAttractionDetail: () -> Unit,
) {
    if (state.isLoading && (state.lastRefreshed == 0L)) {
        LoadingDetailState(language = state.language)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        state.highlightedAttractionId?.let { highlightedId ->
            val highlighted = state.allWaitingTimes.firstOrNull { it.attractionId == highlightedId }
            if (highlighted != null) {
                item {
                    AttractionDetailSection(
                        park = state.park,
                        item = highlighted,
                        forecast = state.forecastByAttractionId[highlightedId],
                        history = state.historyByAttractionId[highlightedId].orEmpty(),
                        note = state.highlightedAttractionNote,
                        language = state.language,
                        onSaveNote = onSaveAttractionNote,
                        onDeleteNote = onDeleteAttractionNote,
                        onAddWatchlist = { onAddWatchlistForAttraction(highlightedId) },
                        onClearDetail = onClearAttractionDetail,
                    )
                }
            }
        }

        item {
            if (state.isShowingOfflineData) {
                OfflineDetailBanner(
                    language = state.language,
                    ageMinutes = state.offlineDataAgeMinutes,
                )
            }
        }

        item {
            if (state.isWaitingTimeDataLikelyMissing) {
                WaitingTimeDataGapBanner(language = state.language)
            }
        }

        item {
            if (state.usedFallbackWaitTimeSource) {
                FallbackWaitTimeSourceBanner(language = state.language)
            }
        }

        item {
            ParkHeaderSection(
                currentTime = state.currentLocalTime,
                openingTimes = state.openingTimes,
                crowdLevel = state.crowdLevel,
                waitingTimes = state.allWaitingTimes,
                localTimeOffsetSeconds = state.localTimeOffsetSeconds,
                weather = state.weather,
                holidays = state.holidays,
                language = state.language,
            )
        }

        item {
            DataQualityCard(state = state)
        }

        item {
            ParkStatisticsDashboard(
                statistics = state.parkStatistics,
                currentCrowdLevel = state.crowdEstimate?.level,
                language = state.language,
                isLoading = state.isLoading || state.isStatisticsLoading,
            )
        }

        // Filter & Sortierung
        item {
            FilterSection(
                sort = state.sort,
                filter = state.filter,
                attractionQuery = state.attractionQuery,
                maxWaitMinutes = state.maxWaitMinutes,
                onSortChange = onSortChange,
                onFilterChange = onFilterChange,
                onAttractionQueryChange = onAttractionQueryChange,
                onMaxWaitChange = onMaxWaitChange,
                onAddWatchlist = onAddWatchlist,
                language = state.language,
            )
        }

        if (state.plannedWaitingTimes.isNotEmpty()) {
            item {
                VisitPlannerSection(
                    plannedWaitingTimes = state.plannedWaitingTimes,
                    onRemove = onTogglePlannedAttraction,
                    language = state.language,
                )
            }
        }

        if (state.waitingTimes.isEmpty() && state.isLoading) {
            item {
                WaitingTimesLoadingCard(language = state.language)
            }
        } else if (state.waitingTimes.isEmpty()) {
            item {
                EmptyState(
                    state = state,
                )
            }
        } else {
            items(
                count = state.waitingTimes.size,
                key = { state.waitingTimes[it].attractionId }
            ) { index ->
                WaitingTimeRow(
                    item = state.waitingTimes[index],
                    isPlanned = state.waitingTimes[index].attractionId in state.plannedAttractionIds,
                    onTogglePlanned = onTogglePlannedAttraction,
                    onAddWatchlist = onAddWatchlistForAttraction,
                    onStatisticsClick = onAttractionStatisticsClick,
                    onOpenDetail = onAttractionClick,
                    language = state.language,
                )
            }
        }
    }
}

@Composable
private fun HighlightedAttractionCard(
    item: WaitingTime,
    language: String,
    onAddWatchlist: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localized(
                        language,
                        de = "Aus Benachrichtigung geöffnet",
                        en = "Opened from notification",
                        fr = "Ouvert depuis la notification",
                        nl = "Geopend via melding",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(plannerLine(item, language), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onAddWatchlist) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = localized(
                        language,
                        de = "Benachrichtigung hinzufügen",
                        en = "Add notification",
                        fr = "Ajouter une alerte",
                        nl = "Melding toevoegen",
                    ),
                )
            }
        }
    }
}

@Composable
private fun AttractionDetailSection(
    park: Park?,
    item: WaitingTime,
    forecast: AttractionWaitForecast?,
    history: List<AttractionWaitForecastPoint>,
    note: String,
    language: String,
    onSaveNote: (String) -> Unit,
    onDeleteNote: () -> Unit,
    onAddWatchlist: () -> Unit,
    onClearDetail: () -> Unit,
) {
    var draftNote by remember(item.attractionId, note) { mutableStateOf(note) }
    val noteHasChanges = draftNote.trim() != note.trim()
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(park?.name.orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusBadge(status = item.status, language = language)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val isOpened = item.status == AttractionStatus.Opened
                val waitTimeColor = when {
                    !isOpened -> item.status.indicatorColor()
                    item.waitingTime == null -> item.status.indicatorColor()
                    item.waitingTime < 30 -> Color(0xFF4CAF50)
                    item.waitingTime < 60 -> Color(0xFFFFB300)
                    else -> Color(0xFFF44336)
                }
                Text(
                    text = if (isOpened) {
                        item.waitingTime?.let { "$it Min." } ?: item.status.label(language)
                    } else {
                        item.status.label(language)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = waitTimeColor,
                )
            }

            WaitForecastChart(
                title = localized(language, de = "Nächste Stunden", en = "Next hours", fr = "Prochaines heures", nl = "Komende uren"),
                points = forecast?.points.orEmpty(),
                emptyText = localized(
                    language,
                    de = "Noch zu wenig vergleichbare Historie für eine Prognose.",
                    en = "Not enough comparable history for a forecast yet.",
                    fr = "Pas encore assez d'historique comparable pour une prévision.",
                    nl = "Nog niet genoeg vergelijkbare historie voor eine voorspelling.",
                ),
                disclaimer = localized(
                    language,
                    de = "Geschätzte Vorschau (basiert auf Daten aus der Vergangenheit)",
                    en = "Estimated preview only estimated (based on past data)",
                    fr = "Aperçu uniquement estimé (basé sur des données passées)",
                    nl = "Voorvertoning alleen geschat (gebaseerd op historische data)",
                ),
            )
            WaitForecastChart(
                title = localized(language, de = "Heutiger Verlauf", en = "Today's history", fr = "Historique du jour", nl = "Verloop van heute"),
                points = history,
                emptyText = localized(
                    language,
                    de = "Für diese Attraktion liegen heute noch keine zentralen Messpunkte vor.",
                    en = "No central measurements for this attraction today yet.",
                    fr = "Aucune mesure centrale pour cette attraction aujourd'hui pour le moment.",
                    nl = "Nog geen centrale metingen voor diese attractie heute.",
                ),
            )

            OutlinedTextField(
                value = draftNote,
                onValueChange = { draftNote = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(localized(language, de = "Persönliche Notiz", en = "Personal note", fr = "Note personnelle", nl = "Persoonlijke notitie")) },
                placeholder = {
                    Text(
                        localized(
                            language,
                            de = "Mindestgröße, Treffpunkt...",
                            en = "Minimum height, meeting point...",
                            fr = "Taille minimale, point de rendez-vous...",
                            nl = "Minimale lengte, ontmoetingspunt...",
                        )
                    )
                },
                minLines = 2,
                shape = RoundedCornerShape(12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { onSaveNote(draftNote) },
                    enabled = noteHasChanges || draftNote.isNotBlank(),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(localized(language, de = "Notiz speichern", en = "Save note", fr = "Enregistrer la note", nl = "Notitie opslaan"))
                }
                TextButton(onClick = onAddWatchlist) {
                    Text(localized(language, de = "Alarm", en = "Notification", fr = "Alerte", nl = "Melding"))
                }
                TextButton(onClick = onClearDetail) {
                    Text(localized(language, de = "Entfernen", en = "Remove", fr = "Supprimer", nl = "Verwijderen"))
                }
                if (note.isNotBlank() || draftNote.isNotBlank()) {
                    TextButton(
                        onClick = {
                            draftNote = ""
                            onDeleteNote()
                        },
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(localized(language, de = "Löschen", en = "Delete", fr = "Supprimer", nl = "Verwijderen"))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: AttractionStatus, language: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = status.indicatorColor().copy(alpha = 0.16f),
        contentColor = status.indicatorColor(),
    ) {
        Text(
            text = status.label(language),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun WaitForecastChart(
    title: String,
    points: List<AttractionWaitForecastPoint>,
    emptyText: String,
    disclaimer: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (points.size < 2) {
            Text(emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        if (disclaimer != null) {
            Text(
                text = disclaimer,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val sorted = remember(points) { points.sortedBy { it.localTime } }
        val maxWait = remember(sorted) {
            ((sorted.maxOf { it.expectedWaitMinutes }.coerceAtLeast(10) + 9) / 10) * 10
        }
        val axisTimes = remember(sorted) {
            if (sorted.size <= 2) {
                listOf(sorted.first().localTime, sorted.last().localTime)
            } else {
                listOf(sorted.first().localTime, sorted[sorted.lastIndex / 2].localTime, sorted.last().localTime)
            }.distinct()
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .width(34.dp)
                    .height(120.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text("$maxWait", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${maxWait / 2}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val neutralColor = MaterialTheme.colorScheme.onSurfaceVariant
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp)
            ) {
            val left = 8.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val top = 10.dp.toPx()
            val bottom = size.height - 18.dp.toPx()
            repeat(3) { index ->
                val y = top + (bottom - top) * index / 2f
                drawLine(Color.Gray.copy(alpha = 0.2f), Offset(left, y), Offset(right, y), 1.dp.toPx())
            }
            val firstMinute = sorted.first().localTime.toSecondOfDay() / 60
            val lastMinute = sorted.last().localTime.toSecondOfDay() / 60
            val span = (lastMinute - firstMinute).coerceAtLeast(1)
            fun xFor(point: AttractionWaitForecastPoint): Float {
                val minute = point.localTime.toSecondOfDay() / 60
                return left + ((minute - firstMinute).toFloat() / span.toFloat()) * (right - left)
            }
            fun yFor(wait: Int): Float = bottom - (wait.toFloat() / maxWait.toFloat()).coerceIn(0f, 1f) * (bottom - top)
            val path = Path()
            sorted.forEachIndexed { index, point ->
                val x = xFor(point)
                val y = yFor(point.expectedWaitMinutes)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, neutralColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            sorted.forEach { point ->
                drawCircle(neutralColor, 3.5.dp.toPx(), Offset(xFor(point), yFor(point.expectedWaitMinutes)))
            }
        }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 42.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            axisTimes.forEach { time ->
                Text(
                    time.format(DateTimeFormatter.ofPattern("HH:mm")),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LoadingDetailState(language: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                text = localized(
                    language,
                    de = "Parkdaten werden geladen…",
                    en = "Loading park data…",
                    fr = "Chargement des données du parc…",
                    nl = "Parkgegevens worden geladen…",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WaitingTimesLoadingCard(language: String) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localized(
                        language,
                        de = "Attraktionen werden geladen",
                        en = "Loading attractions",
                        fr = "Chargement des attractions",
                        nl = "Attracties worden geladen",
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = localized(
                        language,
                        de = "Öffnungszeiten, Status und Wartezeiten werden gerade aktualisiert.",
                        en = "Opening times, status, and wait times are being updated.",
                        fr = "Les horaires, statuts et temps d'attente sont en cours d'actualisation.",
                        nl = "Openingstijden, status en wachttijden worden bijgewerkt.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSection(
    sort: WaitingTimesSort,
    filter: AttractionFilter,
    attractionQuery: String,
    maxWaitMinutes: Int?,
    onSortChange: (WaitingTimesSort) -> Unit,
    onFilterChange: (AttractionFilter) -> Unit,
    onAttractionQueryChange: (String) -> Unit,
    onMaxWaitChange: (Int?) -> Unit,
    onAddWatchlist: () -> Unit,
    language: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            localized(language, de = "Filter & Sortierung", en = "Filters & sorting", fr = "Filtres et tri", nl = "Filters & sortering"),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = attractionQuery,
            onValueChange = onAttractionQueryChange,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = if (attractionQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { onAttractionQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = localized(language, de = "Suche leeren", en = "Clear search", fr = "Effacer la recherche", nl = "Zoekopdracht wissen"))
                    }
                }
            } else null,
            placeholder = { Text(localized(language, de = "Attraktion suchen", en = "Search attraction", fr = "Rechercher une attraction", nl = "Attractie zoeken")) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
            ),
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Sortierung Dropdown
            var sortExpanded by remember { mutableStateOf(value = false) }
            Box(modifier = Modifier.weight(1f)) {
                OutlinedCard(
                    onClick = { sortExpanded = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(sort.label(language), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
                DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                    WaitingTimesSort.entries.forEach { s ->
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

            // Status Dropdown
            var filterExpanded by remember { mutableStateOf(value = false) }
            Box(modifier = Modifier.weight(1f)) {
                OutlinedCard(
                    onClick = { filterExpanded = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(filter.label(language), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
                DropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {
                    AttractionFilter.entries.forEach { f ->
                        DropdownMenuItem(
                            text = { Text(f.label(language)) },
                            onClick = {
                                onFilterChange(f)
                                filterExpanded = false
                            },
                            trailingIcon = if (filter == f) {
                                { Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) }
                            } else null
                        )
                    }
                }
            }
        }

        // Wartezeit-Chips (horizontal scrollbar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(localized(language, de = "Max. Warten:", en = "Max wait:", fr = "Attente max :", nl = "Max. wachttijd:"), style = MaterialTheme.typography.labelMedium)
            val maxWaitOptions = listOf(null, 15, 30, 45, 60)
            maxWaitOptions.forEach { minutes ->
                FilterChip(
                    selected = maxWaitMinutes == minutes,
                    onClick = { onMaxWaitChange(minutes) },
                    label = {
                        Text(
                            if (minutes == null) {
                                localized(language, de = "Alle", en = "All", fr = "Toutes", nl = "Alle")
                            } else {
                                "≤ $minutes"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    shape = RoundedCornerShape(8.dp)
                )
            }
            
            TextButton(onClick = onAddWatchlist) {
                Text(localized(language, de = "Benachrichtigung", en = "Notification", fr = "Alerte", nl = "Melding"))
            }
        }
    }
}

@Composable
private fun VisitPlannerSection(
    plannedWaitingTimes: List<WaitingTime>,
    onRemove: (String) -> Unit,
    language: String,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = localized(language, de = "Tagesplan", en = "Day plan", fr = "Planning du jour", nl = "Dagplanning"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            plannedWaitingTimes.take(5).forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = plannerLine(item, language),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        )
                    }
                    IconButton(onClick = { onRemove(item.attractionId) }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = localized(
                                language,
                                de = "Aus Tagesplan entfernen",
                                en = "Remove from day plan",
                                fr = "Retirer du planning du jour",
                                nl = "Verwijderen aus dagplanning",
                            ),
                        )
                    }
                }
            }
            if (plannedWaitingTimes.size > 5) {
                Text(
                    text = localized(
                        language,
                        de = "+${plannedWaitingTimes.size - 5} weitere",
                        en = "+${plannedWaitingTimes.size - 5} more",
                        fr = "+${plannedWaitingTimes.size - 5} de plus",
                        nl = "+${plannedWaitingTimes.size - 5} meer",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun ParkHeaderSection(
    currentTime: Long,
    openingTimes: OpeningTimes?,
    crowdLevel: CrowdLevel?,
    waitingTimes: List<WaitingTime>,
    localTimeOffsetSeconds: Int?,
    weather: WeatherInfo?,
    holidays: List<HolidayInfo>,
    language: String,
) {
    val zoneId = remember(localTimeOffsetSeconds) {
        localTimeOffsetSeconds?.let { ZoneOffset.ofTotalSeconds(it) } ?: ZoneId.systemDefault()
    }
    
    val currentZonedDateTime = Instant.ofEpochMilli(currentTime).atZone(zoneId)
    val displayState = parkOpeningDisplayState(
        openingTimes = openingTimes,
        crowdLevel = crowdLevel,
        waitingTimes = waitingTimes,
        currentTimeMillis = currentTime,
        localTimeOffsetSeconds = localTimeOffsetSeconds,
        language = language,
    )

    val containerColor = when (displayState.tone) {
        ParkOpeningTone.Open -> Color(0xFF2E7D32)
        ParkOpeningTone.OpenOtherTimeToday -> Color(0xFFF57C00)
        ParkOpeningTone.ClosedToday -> Color(0xFFC62828)
        ParkOpeningTone.Unknown -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (displayState.tone == ParkOpeningTone.Unknown) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        Color.White
    }

    val dateTimeFormatter = remember(language) {
        DateTimeFormatter.ofPattern(
            if (language == "en") "EEE, MMM d yyyy '•' HH:mm" else "EEE, dd.MM.yyyy '•' HH:mm 'Uhr'",
            if (language == "en") Locale.ENGLISH else Locale.GERMAN,
        )
    }
    val formattedDateTime = remember(currentTime, zoneId, language) {
        dateTimeFormatter.format(currentZonedDateTime) + localized(language, de = " (Ortszeit)", en = " (local time)", fr = " (heure locale)", nl = " (lokale tijd)")
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(contentColor.copy(alpha = 0.2f))
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = formattedDateTime,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = displayState.statusText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Kompakte Wetter-Anzeige innerhalb des Headers
            weather?.let {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = localized(
                            language,
                            de = "Wetter: ${String.format(Locale.getDefault(), "%.0f", it.temperature)}°C · Regen: ${it.precipitationProbability}%",
                            en = "Weather: ${String.format(Locale.getDefault(), "%.0f", it.temperature)}°C · Rain: ${it.precipitationProbability}%",
                            fr = "Météo : ${String.format(Locale.getDefault(), "%.0f", it.temperature)}°C · Pluie : ${it.precipitationProbability}%",
                            nl = "Weer: ${String.format(Locale.getDefault(), "%.0f", it.temperature)}°C · Regen: ${it.precipitationProbability}%",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                    Text(
                        text = weatherInsight(it, language),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
            }

            // Show holiday only if there's a public holiday today
            val todayIso = currentZonedDateTime.toLocalDate().toString()
            val todayHoliday = holidays.firstOrNull { it.date == todayIso }
            todayHoliday?.let { h ->
                Text(
                    text = localized(
                        language,
                        de = "Feiertag: ${h.name}",
                        en = "Holiday: ${h.name}",
                        fr = "Jour férié : ${h.name}",
                        nl = "Feestdag: ${h.name}",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun WaitingTimeRow(
    item: WaitingTime,
    isPlanned: Boolean,
    onTogglePlanned: (String) -> Unit,
    onAddWatchlist: (String) -> Unit,
    onStatisticsClick: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
    language: String,
) {
    val waitTimeColor = when {
        item.waitingTime == null -> MaterialTheme.colorScheme.primary
        item.waitingTime < 30 -> Color(0xFF4CAF50)
        item.waitingTime < 60 -> Color(0xFFFFB300)
        else -> Color(0xFFF44336)
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDetail(item.attractionId) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status-Punkt
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(item.status.indicatorColor())
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    item.status.label(language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { onStatisticsClick(item.attractionId) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_stats_bar_chart_24),
                    contentDescription = localized(
                        language,
                        de = "Statistik für ${item.name} anzeigen",
                        en = "Show statistics for ${item.name}",
                        fr = "Afficher les statistiques de ${item.name}",
                        nl = "Statistieken voor ${item.name} weergeven",
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { onTogglePlanned(item.attractionId) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isPlanned) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = if (isPlanned) {
                        localized(
                            language,
                            de = "Aus Tagesplan entfernen",
                            en = "Remove from day plan",
                            fr = "Retirer du planning du jour",
                            nl = "Verwijderen uit dagplanning",
                        )
                    } else {
                        localized(
                            language,
                            de = "Zum Tagesplan hinzufügen",
                            en = "Add to day plan",
                            fr = "Ajouter au planning du jour",
                            nl = "Toevoegen aan dagplanning",
                        )
                    },
                    tint = if (isPlanned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { onAddWatchlist(item.attractionId) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = localized(
                        language,
                        de = "Benachrichtigung für ${item.name} hinzufügen",
                        en = "Add notification for ${item.name}",
                        fr = "Ajouter une alerte pour ${item.name}",
                        nl = "Melding toevoegen voor ${item.name}",
                    ),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            if (item.status == AttractionStatus.Opened) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = (item.waitingTime ?: 0).toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = waitTimeColor
                    )
                    Text(
                        localized(language, de = "Min.", en = "min", fr = "min", nl = "min."),
                        style = MaterialTheme.typography.labelSmall,
                        color = waitTimeColor
                    )
                }
            }
        }
    }
}

private fun plannerLine(item: WaitingTime, language: String): String {
    return when (item.status) {
        AttractionStatus.Opened -> localized(
            language,
            de = "${item.waitingTime ?: 0} Min. Wartezeit",
            en = "${item.waitingTime ?: 0} min wait",
            fr = "${item.waitingTime ?: 0} min d'attente",
            nl = "${item.waitingTime ?: 0} min. wachttijd",
        )
        else -> item.status.label(language)
    }
}

@Composable
private fun OfflineDetailBanner(
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
                        de = "Gecachte Attraktionsdaten",
                        en = "Showing cached attraction data",
                        fr = "Données d'attraction en cache affichées",
                        nl = "Gecachte attractiegegevens weergegeven",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = localized(
                        language,
                        de = "Letzte erfolgreiche Detail-Aktualisierung: ${ageMinutes.cacheAgeLabel(language)}.",
                        en = "Last successful detail update: ${ageMinutes.cacheAgeLabel(language)}.",
                        fr = "Dernière mise à jour détaillée réussie : ${ageMinutes.cacheAgeLabel(language)}.",
                        nl = "Laatste succesvolle detailupdate: ${ageMinutes.cacheAgeLabel(language)}.",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun WaitingTimeDataGapBanner(language: String) {
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
                        de = "Wartezeiten momentan nicht verfügbar",
                        en = "Wait times currently unavailable",
                        fr = "Temps d'attente actuellement indisponibles",
                        nl = "Wachttijden momenteel niet verfügbar",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = localized(
                        language,
                        de = "Der Park ist laut Öffnungszeiten geöffnet, liefert aber seit der Öffnung keine Wartezeiten. Das liegt vermutlich an einer technischen Störung bei der Datenquelle, nicht an der App.",
                        en = "The park is reported as open, but no wait times have come in since opening. This is likely a temporary issue with the data source, not the app.",
                        fr = "Le parc est annoncé comme ouvert, aber aucun temps d'attente n'a été reçu depuis l'ouverture. Il s'agit probablement d'un problème temporaire de la source de données, pas de l'application.",
                        nl = "Het park wordt als open gemeld, maar er zijn sinds de opening geen wachttijden binnengekomen. Dit is waarschijnlijk een tijdelijk probleem bij de databron, niet bij de app.",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.86f),
                )
            }
        }
    }
}

@Composable
private fun FallbackWaitTimeSourceBanner(language: String) {
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
                        de = "Ausweichquelle aktiv",
                        en = "Fallback source active",
                        fr = "Source de secours active",
                        nl = "Alternatieve bron actief",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = localized(
                        language,
                        de = "wartezeiten.app ist aktuell nicht erreichbar. Die Wartezeiten stammen momentan von queue-times.com; Öffnungszeiten und Auslastung sind währenddessen nicht verfügbar.",
                        en = "wartezeiten.app is currently unreachable. Wait times are temporarily sourced from queue-times.com; opening hours and crowd level are unavailable until it recovers.",
                        fr = "wartezeiten.app est actuellement injoignable. Les temps d'attente proviennent temporairement de queue-times.com ; les horaires d'ouverture et l'affluence ne sont pas disponibles en attendant.",
                        nl = "wartezeiten.app is momenteel niet bereikbaar. Wachttijden komen tijdelijk van queue-times.com; openingstijden en drukte zijn ondertussen niet beschikbaar.",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.86f),
                )
            }
        }
    }
}

@Composable
private fun DataQualityCard(state: WaitingTimesUiState) {
    val language = state.language
    val ageText = state.offlineDataAgeMinutes
        ?: state.dataUpdatedAtMillis.takeIf { it > 0L }?.let { ((System.currentTimeMillis() - it).coerceAtLeast(0L) / 60_000L) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (state.isShowingOfflineData) Icons.Default.Warning else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (state.isShowingOfflineData) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        state.isShowingOfflineData -> localized(language, de = "Cache-Daten", en = "Cached data", fr = "Données en cache", nl = "Cachegegevens")
                        state.dataUpdatedAtMillis > 0L && (ageText ?: Long.MAX_VALUE) <= 5L -> localized(language, de = "Aktuelle Daten", en = "Live-like data", fr = "Données quasi en direct", nl = "Bijna live gegevens")
                        state.dataUpdatedAtMillis > 0L -> localized(language, de = "Ältere lokale Daten", en = "Older local data", fr = "Données locales plus anciennes", nl = "Oudere lokale gegevens")
                        else -> localized(language, de = "Datenstatus unbekannt", en = "Data status unknown", fr = "État des données inconnu", nl = "Status van gegevens onbekend")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (state.dataUpdatedAtMillis > 0L) {
                        localized(
                            language,
                            de = "Datenalter: ${ageText.cacheAgeLabel(language)}",
                            en = "Data age: ${ageText.cacheAgeLabel(language)}",
                            fr = "Ancienneté des données : ${ageText.cacheAgeLabel(language)}",
                            nl = "Leeftijd van gegevens: ${ageText.cacheAgeLabel(language)}",
                        )
                    } else {
                        localized(
                            language,
                            de = "Noch keine erfolgreiche Aktualisierung gespeichert",
                            en = "No successful update stored yet",
                            fr = "Aucune mise à jour réussie enregistrée pour le moment",
                            nl = "Nog geen succesvolle update opgeslagen",
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun shareParkDetail(context: Context, state: WaitingTimesUiState) {
    val park = state.park ?: return
    val openAttractions = state.allWaitingTimes.count { it.status == AttractionStatus.Opened }
    val topWaits = state.allWaitingTimes
        .filter { it.status == AttractionStatus.Opened && it.waitingTime != null }
        .sortedByDescending { it.waitingTime ?: 0 }
        .take(5)
    val text = buildString {
        appendLine(park.name)
        appendLine(park.country)
        appendLine(
            localized(
                state.language,
                de = "$openAttractions von ${state.allWaitingTimes.size} Attraktionen offen",
                en = "$openAttractions of ${state.allWaitingTimes.size} attractions open",
                fr = "$openAttractions attractions ouvertes sur ${state.allWaitingTimes.size}",
                nl = "$openAttractions van ${state.allWaitingTimes.size} attracties open",
            )
        )
        state.crowdEstimate?.level?.let { level ->
            appendLine(
                localized(
                    state.language,
                    de = "Auslastung geschätzt: ${level.toInt()}%",
                    en = "Crowd estimate: ${level.toInt()}%",
                    fr = "Fréquentation estimée : ${level.toInt()}%",
                    nl = "Geschatte drukte: ${level.toInt()}%",
                )
            )
        }
        if (topWaits.isNotEmpty()) {
            appendLine()
            appendLine(localized(state.language, de = "Längste Wartezeiten:", en = "Longest waits:", fr = "Temps d'attente les plus longs :", nl = "Langste wachttijden:"))
            topWaits.forEach { item ->
                appendLine("- ${item.name}: ${item.waitingTime ?: 0} Min.")
            }
        }
        appendLine()
        appendLine(liveParkLink(park.id))
        appendLine("Web: https://wartezeiten-app.tutorialfynn.workers.dev/")
        appendLine()
        append(
            localized(
                state.language,
                de = "Geteilt aus der Wartezeiten App",
                en = "Shared from Wartezeiten App",
                fr = "Partagé depuis l'application Wartezeiten",
                nl = "Gedeeld vanuit de Wartezeiten-app",
            )
        )
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, park.name)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(
        Intent.createChooser(
            intent,
            localized(state.language, de = "Parkübersicht teilen", en = "Share park overview", fr = "Partager l'aperçu du parc", nl = "Parkoverzicht delen"),
        )
    )
}

private fun liveParkLink(parkKey: String): String = "wartezeiten://parks/${Uri.encode(parkKey)}"

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

private fun weatherInsight(weather: WeatherInfo, language: String): String {
    return when {
        weather.precipitationProbability >= 70 -> localized(
            language,
            de = "Regenrisiko hoch - wetterbedingte Schließungen möglich",
            en = "High rain risk - weather closures possible",
            fr = "Risque de pluie élevé - fermetures possibles à cause du temps",
            nl = "Hoog regenrisico - weergerelateerde sluitingen möglich",
        )
        weather.temperature >= 30 -> localized(
            language,
            de = "Sehr warm - Pausen einplanen",
            en = "Very warm - plan breaks",
            fr = "Très chaud - prévoyez des pauses",
            nl = "Erg warm - plan pauzes in",
        )
        weather.temperature <= 3 -> localized(
            language,
            de = "Sehr kalt - Outdoor-Attraktionen prüfen",
            en = "Very cold - check outdoor attractions",
            fr = "Très froid - vérifiez les attractions en extérieur",
            nl = "Erg koud - controleer attracties buiten",
        )
        weather.weatherCode in 95..99 -> localized(
            language,
            de = "Gewitterrisiko - Statusänderungen beobachten",
            en = "Thunderstorm risk - watch status changes",
            fr = "Risque d'orage - surveillez les changements de statut",
            nl = "Risico op onweer - let op statuswijzigingen",
        )
        weather.precipitationProbability <= 20 && weather.temperature in 12.0..26.0 -> localized(
            language,
            de = "Gutes Besuchswetter",
            en = "Good weather for a visit",
            fr = "Bon temps pour une visite",
            nl = "Goed weer voor een bezoek",
        )
        else -> localized(
            language,
            de = "Wetter im Blick behalten",
            en = "Keep an eye on the weather",
            fr = "Surveillez la météo",
            nl = "Houd het weer in de gaten",
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
private fun EmptyState(state: WaitingTimesUiState) {
    val message = emptyAttractionMessage(state)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun emptyAttractionMessage(state: WaitingTimesUiState): String {
    val language = state.language
    if (state.allWaitingTimes.isNotEmpty()) {
        return localized(
            language,
            de = "Keine Attraktionen passen zu den aktuellen Filtern.",
            en = "No attractions match the current filters.",
            fr = "Aucune attraction ne correspond aux filtres actuels.",
            nl = "Geen attracties komen overeen met de huidige filters.",
        )
    }

    val displayState = parkOpeningDisplayState(
        openingTimes = state.openingTimes,
        crowdLevel = state.crowdLevel,
        waitingTimes = state.allWaitingTimes,
        currentTimeMillis = state.currentLocalTime,
        localTimeOffsetSeconds = state.localTimeOffsetSeconds,
        language = language,
    )

    return when (displayState.tone) {
        ParkOpeningTone.ClosedToday -> localized(
            language,
            de = "Der Park ist heute geschlossen. Es werden keine aktuellen Attraktionen angezeigt.",
            en = "The park is closed today. No current attractions are shown.",
            fr = "Le parc est fermé aujourd'hui. Aucune attraction actuelle n'est affichée.",
            nl = "Het park is vandaag gesloten. Er worden geen actuele attracties weergegeven.",
        )
        ParkOpeningTone.OpenOtherTimeToday -> localized(
            language,
            de = "Der Park ist aktuell geschlossen. Aktuelle Attraktionen erscheinen während der Öffnungszeiten.",
            en = "The park is currently closed. Current attractions will appear during opening hours.",
            fr = "Le parc est actuellement fermé. Les attractions actuelles apparaîtront pendant les heures d'ouverture.",
            nl = "Het park is momenteel gesloten. Actuele attracties verschijnen tijdens openingstijden.",
        )
        ParkOpeningTone.Unknown -> localized(
            language,
            de = "Noch keine aktuellen Attraktionsdaten verfügbar.",
            en = "No current attraction data is available yet.",
            fr = "Aucune donnée d'attraction actuelle disponible pour le moment.",
            nl = "Nog geen actuele attractiegegevens verfügbar.",
        )
        ParkOpeningTone.Open -> localized(
            language,
            de = "Noch keine aktuellen Attraktionsdaten verfügbar.",
            en = "No current attraction data is available yet.",
            fr = "Aucune donnée d'attraction actuelle disponible pour le moment.",
            nl = "Nog geen actuele attractiegegevens verfügbar.",
        )
    }
}

private fun WaitingTimesSort.label(language: String) = when (this) {
    WaitingTimesSort.WaitAscending -> localized(language, de = "Wartezeit ↑", en = "Wait time ↑", fr = "Temps d'attente ↑", nl = "Wachttijd ↑")
    WaitingTimesSort.WaitDescending -> localized(language, de = "Wartezeit ↓", en = "Wait time ↓", fr = "Temps d'attente ↓", nl = "Wachttijd ↓")
    WaitingTimesSort.Name -> localized(language, de = "Name A-Z", en = "Name A-Z", fr = "Nom A-Z", nl = "Naam A-Z")
}

private fun AttractionFilter.label(language: String) = when (this) {
    AttractionFilter.All -> localized(language, de = "Alle", en = "All", fr = "Toutes", nl = "Alle")
    AttractionFilter.OpenOnly -> localized(language, de = "Nur offen", en = "Open only", fr = "Ouvertes uniquement", nl = "Alleen open")
    AttractionFilter.Maintenance -> localized(language, de = "Wartung", en = "Maintenance", fr = "Maintenance", nl = "Onderhoud")
    AttractionFilter.Closed -> localized(language, de = "Geschlossen", en = "Closed", fr = "Fermé", nl = "Gesloten")
}

private fun formatForecastDate(isoDate: String): String {
    return runCatching {
        val date = LocalDate.parse(isoDate)
        val dayOfWeek = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
        "$dayOfWeek ${date.dayOfMonth}."
    }.getOrElse { isoDate }
}

private fun AttractionStatus.label(language: String) = when (this) {
    AttractionStatus.Opened -> localized(language, de = "Geöffnet", en = "Open", fr = "Ouvert", nl = "Open")
    AttractionStatus.Closed -> localized(language, de = "Geschlossen", en = "Closed", fr = "Fermé", nl = "Gesloten")
    AttractionStatus.Maintenance -> localized(language, de = "Wartung", en = "Maintenance", fr = "Maintenance", nl = "Onderhoud")
    AttractionStatus.ClosedWeather -> localized(language, de = "Wetterbedingt zu", en = "Weather closure", fr = "Fermé (météo)", nl = "Gesloten (weer)")
    AttractionStatus.Unknown -> localized(language, de = "Unbekannt", en = "Unknown", fr = "Inconnu", nl = "Onbekend")
}

private fun AttractionStatus.indicatorColor() = when (this) {
    AttractionStatus.Opened -> Color(0xFF4CAF50)
    AttractionStatus.Closed, AttractionStatus.ClosedWeather -> Color(0xFFF44336)
    AttractionStatus.Maintenance -> Color(0xFFFF9800)
    AttractionStatus.Unknown -> Color.Gray
}

private fun formatLocalTime(timestampMillis: Long, zoneId: ZoneId): String {
    return Instant.ofEpochMilli(timestampMillis)
        .atZone(zoneId)
        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN))
}
