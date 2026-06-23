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
import de.wartezeiten.app.domain.model.AttractionStatus
import de.wartezeiten.app.domain.model.CrowdLevel
import de.wartezeiten.app.domain.model.HolidayInfo
import de.wartezeiten.app.domain.model.OpeningTimes
import de.wartezeiten.app.domain.model.Park
import de.wartezeiten.app.domain.model.WaitingTime
import de.wartezeiten.app.domain.model.WeatherInfo
import de.wartezeiten.app.ui.components.AttributionBanner
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
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddWatchlistDialog by remember { mutableStateOf(false) }
    var selectedAttractionId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.refreshTrigger) {
        if ((state.refreshTrigger > 0) && !state.isLoading) {
            if (state.errorMessage == null) {
                val updatedAt = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                val openCount = state.allWaitingTimes.count { it.status == AttractionStatus.Opened }
                snackbarHostState.showSnackbar(
                    message = if (state.language == "en") {
                        "$openCount open attractions updated at $updatedAt"
                    } else {
                        "$openCount offene Attraktionen aktualisiert um $updatedAt"
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
        bottomBar = {
            AttributionBanner(language = state.language)
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.park?.name ?: if (state.language == "en") "Loadingâ€¦" else "Ladenâ€¦",
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = if (state.language == "en") "Back" else "Zurück")
                    }
                },
                actions = {
                    state.park?.let { park ->
                        IconButton(onClick = { shareParkDetail(context, state) }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = if (state.language == "en") "Share park overview" else "Parkübersicht teilen",
                            )
                        }
                        IconButton(onClick = { onParkStatisticsClick(park.id) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_stats_bar_chart_24),
                                contentDescription = if (state.language == "en") {
                                    "Show park statistics"
                                } else {
                                    "Parkstatistik anzeigen"
                                },
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    val isFavorite = state.park?.isFavorite == true
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (state.language == "en") {
                                if (isFavorite) "Remove from favorites" else "Add to favorites"
                            } else {
                                if (isFavorite) "Von Favoriten entfernen" else "Zu Favoriten hinzufügen"
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
                            Icon(Icons.Default.Refresh, contentDescription = if (state.language == "en") "Refresh" else "Aktualisieren")
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
                        onRetry = onRefreshClick,
                        language = state.language,
                    )
                }
            }

            WaitingTimesContent(
                state = state,
                onSortChange = onSortChange,
                onFilterChange = onFilterChange,
                onAttractionQueryChange = onAttractionQueryChange,
                onMaxWaitChange = onMaxWaitChange,
                onTogglePlannedAttraction = onTogglePlannedAttraction,
                onSaveAttractionNote = onSaveAttractionNote,
                onDeleteAttractionNote = onDeleteAttractionNote,
                onAddWatchlist = {
                    selectedAttractionId = null
                    showAddWatchlistDialog = true
                },
                onAddWatchlistForAttraction = { attractionId ->
                    selectedAttractionId = attractionId
                    showAddWatchlistDialog = true
                },
                onAttractionStatisticsClick = { attractionId ->
                    state.park?.let { park -> onAttractionStatisticsClick(park.id, attractionId) }
                },
                onAttractionClick = { attractionId ->
                    state.park?.let { park -> onAttractionClick(park.id, attractionId) }
                },
            )
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
                Text(if (language == "en") "Reload" else "Neu laden")
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
) {
    if (state.isLoading && (state.lastRefreshed == 0L)) {
        LoadingDetailState()
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
                        waitAdvice = state.waitAdviceByAttractionId[highlightedId],
                        forecast = state.forecastByAttractionId[highlightedId],
                        history = state.historyByAttractionId[highlightedId].orEmpty(),
                        note = state.highlightedAttractionNote,
                        language = state.language,
                        onSaveNote = onSaveAttractionNote,
                        onDeleteNote = onDeleteAttractionNote,
                        onAddWatchlist = { onAddWatchlistForAttraction(highlightedId) },
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

        if (state.waitingTimes.isEmpty() && !state.isLoading) {
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
                    waitAdvice = state.waitAdviceByAttractionId[state.waitingTimes[index].attractionId],
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
                    text = if (language == "en") "Opened from notification" else "Aus Benachrichtigung geöffnet",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(plannerLine(item, language), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onAddWatchlist) {
                Icon(Icons.Default.Notifications, contentDescription = if (language == "en") "Add notification" else "Benachrichtigung hinzufügen")
            }
        }
    }
}

@Composable
private fun AttractionDetailSection(
    park: Park?,
    item: WaitingTime,
    waitAdvice: AttractionWaitAdvice?,
    forecast: AttractionWaitForecast?,
    history: List<AttractionWaitForecastPoint>,
    note: String,
    language: String,
    onSaveNote: (String) -> Unit,
    onDeleteNote: () -> Unit,
    onAddWatchlist: () -> Unit,
) {
    val context = LocalContext.current
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
                Text(
                    text = item.waitingTime?.let { "$it Min." } ?: item.status.label(language),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = item.status.indicatorColor(),
                )
                waitAdvice?.let { WaitAdviceLabel(advice = it, language = language) }
            }

            WaitForecastChart(
                title = if (language == "en") "Next hours" else "Nächste Stunden",
                points = forecast?.points.orEmpty(),
                emptyText = if (language == "en") {
                    "Not enough comparable history for a forecast yet."
                } else {
                    "Noch zu wenig vergleichbare Historie für eine Prognose."
                },
            )
            WaitForecastChart(
                title = if (language == "en") "Today's history" else "Heutiger Verlauf",
                points = history,
                emptyText = if (language == "en") {
                    "No central measurements for this attraction today yet."
                } else {
                    "Für diese Attraktion liegen heute noch keine zentralen Messpunkte vor."
                },
            )

            OutlinedTextField(
                value = draftNote,
                onValueChange = { draftNote = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (language == "en") "Personal note" else "Persönliche Notiz") },
                placeholder = { Text(if (language == "en") "Minimum height, meeting point..." else "Mindestgröße, Treffpunkt...") },
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
                    Text(if (language == "en") "Save note" else "Notiz speichern")
                }
                TextButton(onClick = onAddWatchlist) {
                    Text(if (language == "en") "Notification" else "Alarm")
                }
                TextButton(onClick = { park?.let { shareAttractionDetail(context, it, item, language) } }) {
                    Text(if (language == "en") "Share link" else "Link teilen")
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
                        Text(if (language == "en") "Delete" else "Löschen")
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
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (points.size < 2) {
            Text(emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
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
            drawPath(path, Color(0xFF2E7D32), style = Stroke(width = 3.dp.toPx()))
            sorted.forEach { point ->
                drawCircle(Color(0xFF2E7D32), 3.5.dp.toPx(), Offset(xFor(point), yFor(point.expectedWaitMinutes)))
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
private fun LoadingDetailState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
            if (language == "en") "Filters & sorting" else "Filter & Sortierung",
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
                        Icon(Icons.Default.Clear, contentDescription = if (language == "en") "Clear search" else "Suche leeren")
                    }
                }
            } else null,
            placeholder = { Text(if (language == "en") "Search attraction" else "Attraktion suchen") },
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
            Text(if (language == "en") "Max wait:" else "Max. Warten:", style = MaterialTheme.typography.labelMedium)
            val maxWaitOptions = listOf(null, 15, 30, 45, 60)
            maxWaitOptions.forEach { minutes ->
                FilterChip(
                    selected = maxWaitMinutes == minutes,
                    onClick = { onMaxWaitChange(minutes) },
                    label = {
                        Text(
                            if (minutes == null) {
                                if (language == "en") "All" else "Alle"
                            } else {
                                "â‰¤ $minutes"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    shape = RoundedCornerShape(8.dp)
                )
            }
            
            TextButton(onClick = onAddWatchlist) {
                Text(if (language == "en") "Notification" else "Benachrichtigung")
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
                text = if (language == "en") "Day plan" else "Tagesplan",
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
                        Icon(Icons.Default.Clear, contentDescription = if (language == "en") "Remove from day plan" else "Aus Tagesplan entfernen")
                    }
                }
            }
            if (plannedWaitingTimes.size > 5) {
                Text(
                    text = if (language == "en") "+${plannedWaitingTimes.size - 5} more" else "+${plannedWaitingTimes.size - 5} weitere",
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
            if (language == "en") "EEE, MMM d yyyy 'â€¢' HH:mm" else "EEE, dd.MM.yyyy 'â€¢' HH:mm 'Uhr'",
            if (language == "en") Locale.ENGLISH else Locale.GERMAN,
        )
    }
    val formattedDateTime = remember(currentTime, zoneId, language) {
        dateTimeFormatter.format(currentZonedDateTime) + if (language == "en") " (local time)" else " (Ortszeit)"
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
                        text = if (language == "en") {
                            "Weather: ${String.format(Locale.getDefault(), "%.0f", it.temperature)}°C · Rain: ${it.precipitationProbability}%"
                        } else {
                            "Wetter: ${String.format(Locale.getDefault(), "%.0f", it.temperature)}°C · Regen: ${it.precipitationProbability}%"
                        },
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
                    text = if (language == "en") "Holiday: ${h.name}" else "Feiertag: ${h.name}",
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
    waitAdvice: AttractionWaitAdvice?,
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
                waitAdvice?.let { advice ->
                    WaitAdviceLabel(
                        advice = advice,
                        language = language,
                    )
                }
            }

            IconButton(
                onClick = { onStatisticsClick(item.attractionId) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_stats_bar_chart_24),
                    contentDescription = if (language == "en") {
                        "Show statistics for ${item.name}"
                    } else {
                        "Statistik für ${item.name} anzeigen"
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { onTogglePlanned(item.attractionId) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isPlanned) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = if (language == "en") {
                        if (isPlanned) "Remove from day plan" else "Add to day plan"
                    } else {
                        if (isPlanned) "Aus Tagesplan entfernen" else "Zum Tagesplan hinzufügen"
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
                    contentDescription = if (language == "en") {
                        "Add notification for ${item.name}"
                    } else {
                        "Benachrichtigung für ${item.name} hinzufügen"
                    },
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
                        if (language == "en") "min" else "Min.",
                        style = MaterialTheme.typography.labelSmall,
                        color = waitTimeColor
                    )
                }
            }
        }
    }
}

@Composable
private fun WaitAdviceLabel(
    advice: AttractionWaitAdvice,
    language: String,
) {
    val colors = advice.colors()
    Surface(
        modifier = Modifier.padding(top = 5.dp),
        shape = RoundedCornerShape(6.dp),
        color = colors.container,
        contentColor = colors.content,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
    ) {
        Text(
            text = advice.label(language),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun AttractionWaitAdvice.label(language: String): String {
    return when (type) {
        AttractionWaitAdviceType.GoNow -> if (language == "en") {
            "NOW: Good time · typically $typicalWaitMinutes min"
        } else {
            "JETZT: Günstig · üblich $typicalWaitMinutes Min."
        }
        AttractionWaitAdviceType.WaitUntil -> {
            val time = suggestedLocalTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: return ""
            if (language == "en") {
                "LATER: Around $time about ${expectedWaitMinutes ?: typicalWaitMinutes} min"
            } else {
                "SPÄTER: Gegen $time etwa ${expectedWaitMinutes ?: typicalWaitMinutes} Min."
            }
        }
        AttractionWaitAdviceType.Typical -> if (language == "en") {
            "TYPICAL: Usually about $typicalWaitMinutes min"
        } else {
            "ÜBLICH: Meist etwa $typicalWaitMinutes Min."
        }
    }
}

private data class WaitAdviceColors(
    val container: Color,
    val content: Color,
    val border: Color,
)

@Composable
private fun AttractionWaitAdvice.colors(): WaitAdviceColors = when (type) {
    AttractionWaitAdviceType.GoNow -> WaitAdviceColors(
        container = Color(0xFFE2F4E3),
        content = Color(0xFF163A18),
        border = Color(0xFF4CAF50),
    )
    AttractionWaitAdviceType.WaitUntil -> WaitAdviceColors(
        container = Color(0xFFFFEFC2),
        content = Color(0xFF493100),
        border = Color(0xFFFFB300),
    )
    AttractionWaitAdviceType.Typical -> WaitAdviceColors(
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer,
        border = MaterialTheme.colorScheme.secondary,
    )
}

private fun plannerLine(item: WaitingTime, language: String): String {
    return when (item.status) {
        AttractionStatus.Opened -> if (language == "en") {
            "${item.waitingTime ?: 0} min wait"
        } else {
            "${item.waitingTime ?: 0} Min. Wartezeit"
        }
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
                    text = if (language == "en") "Showing cached attraction data" else "Gecachte Attraktionsdaten",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (language == "en") {
                        "Last successful detail update: ${ageMinutes.cacheAgeLabel(language)}."
                    } else {
                        "Letzte erfolgreiche Detail-Aktualisierung: ${ageMinutes.cacheAgeLabel(language)}."
                    },
                    style = MaterialTheme.typography.labelSmall,
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
                        state.isShowingOfflineData -> if (language == "en") "Cached data" else "Cache-Daten"
                        state.dataUpdatedAtMillis > 0L && (ageText ?: Long.MAX_VALUE) <= 5L -> if (language == "en") "Live-like data" else "Aktuelle Daten"
                        state.dataUpdatedAtMillis > 0L -> if (language == "en") "Older local data" else "Ältere lokale Daten"
                        else -> if (language == "en") "Data status unknown" else "Datenstatus unbekannt"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (state.dataUpdatedAtMillis > 0L) {
                        if (language == "en") "Data age: ${ageText.cacheAgeLabel(language)}" else "Datenalter: ${ageText.cacheAgeLabel(language)}"
                    } else {
                        if (language == "en") "No successful update stored yet" else "Noch keine erfolgreiche Aktualisierung gespeichert"
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
            if (state.language == "en") {
                "$openAttractions of ${state.allWaitingTimes.size} attractions open"
            } else {
                "$openAttractions von ${state.allWaitingTimes.size} Attraktionen offen"
            }
        )
        state.crowdEstimate?.level?.let { level ->
            appendLine(if (state.language == "en") "Crowd estimate: ${level.toInt()}%" else "Auslastung geschätzt: ${level.toInt()}%")
        }
        if (topWaits.isNotEmpty()) {
            appendLine()
            appendLine(if (state.language == "en") "Longest waits:" else "Längste Wartezeiten:")
            topWaits.forEach { item ->
                appendLine("- ${item.name}: ${item.waitingTime ?: 0} Min.")
            }
        }
        appendLine()
        appendLine(liveParkLink(park.id))
        appendLine("Web: https://wartezeiten-app.tutorialfynn.workers.dev/")
        appendLine()
        append(if (state.language == "en") "Shared from Wartezeiten App" else "Geteilt aus der Wartezeiten App")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, park.name)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, if (state.language == "en") "Share park overview" else "Parkübersicht teilen"))
}

private fun shareAttractionDetail(context: Context, park: Park, item: WaitingTime, language: String) {
    val text = buildString {
        appendLine(item.name)
        appendLine(park.name)
        appendLine("Status: ${item.status.label(language)}")
        item.waitingTime?.let { wait ->
            appendLine(if (language == "en") "Current wait: $wait min" else "Aktuelle Wartezeit: $wait Min.")
        }
        appendLine()
        appendLine(liveAttractionLink(park.id, item.attractionId))
        appendLine("Web: https://wartezeiten-app.tutorialfynn.workers.dev/")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "${park.name} · ${item.name}")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, if (language == "en") "Share attraction link" else "Attraktionslink teilen"))
}

private fun liveParkLink(parkKey: String): String = "wartezeiten://parks/${Uri.encode(parkKey)}"

private fun liveAttractionLink(parkKey: String, attractionId: String): String =
    "wartezeiten://parks/${Uri.encode(parkKey)}?attractionId=${Uri.encode(attractionId)}"

private fun Long?.cacheAgeLabel(language: String): String {
    val minutes = this ?: return if (language == "en") "unknown" else "unbekannt"
    return when {
        minutes <= 1L -> if (language == "en") "just now" else "gerade eben"
        minutes < 60L -> if (language == "en") "$minutes minutes ago" else "vor $minutes Minuten"
        minutes < 120L -> if (language == "en") "1 hour ago" else "vor 1 Stunde"
        else -> {
            val hours = minutes / 60L
            if (language == "en") "$hours hours ago" else "vor $hours Stunden"
        }
    }
}

private fun weatherInsight(weather: WeatherInfo, language: String): String {
    if (language == "en") {
        return when {
            weather.precipitationProbability >= 70 -> "High rain risk - weather closures possible"
            weather.temperature >= 30 -> "Very warm - plan breaks"
            weather.temperature <= 3 -> "Very cold - check outdoor attractions"
            weather.weatherCode in 95..99 -> "Thunderstorm risk - watch status changes"
            weather.precipitationProbability <= 20 && weather.temperature in 12.0..26.0 -> "Good weather for a visit"
            else -> "Keep an eye on the weather"
        }
    }
    return when {
        weather.precipitationProbability >= 70 -> "Regenrisiko hoch - wetterbedingte Schließungen möglich"
        weather.temperature >= 30 -> "Sehr warm - Pausen einplanen"
        weather.temperature <= 3 -> "Sehr kalt - Outdoor-Attraktionen prüfen"
        weather.weatherCode in 95..99 -> "Gewitterrisiko - Statusänderungen beobachten"
        weather.precipitationProbability <= 20 && weather.temperature in 12.0..26.0 -> "Gutes Besuchswetter"
        else -> "Wetter im Blick behalten"
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
        return if (language == "en") {
            "No attractions match the current filters."
        } else {
            "Keine Attraktionen passen zu den aktuellen Filtern."
        }
    }

    val displayState = parkOpeningDisplayState(
        openingTimes = state.openingTimes,
        crowdLevel = state.crowdLevel,
        waitingTimes = state.allWaitingTimes,
        currentTimeMillis = state.currentLocalTime,
        localTimeOffsetSeconds = state.localTimeOffsetSeconds,
    )

    return when (displayState.tone) {
        ParkOpeningTone.ClosedToday -> if (language == "en") {
            "The park is closed today. No current attractions are shown."
        } else {
            "Der Park ist heute geschlossen. Es werden keine aktuellen Attraktionen angezeigt."
        }
        ParkOpeningTone.OpenOtherTimeToday -> if (language == "en") {
            "The park is currently closed. Current attractions will appear during opening hours."
        } else {
            "Der Park ist aktuell geschlossen. Aktuelle Attraktionen erscheinen während der Öffnungszeiten."
        }
        ParkOpeningTone.Unknown -> if (language == "en") {
            "No current attraction data is available yet."
        } else {
            "Noch keine aktuellen Attraktionsdaten verfügbar."
        }
        ParkOpeningTone.Open -> if (language == "en") {
            "No current attraction data is available yet."
        } else {
            "Noch keine aktuellen Attraktionsdaten verfügbar."
        }
    }
}

private fun WaitingTimesSort.label(language: String) = when (this) {
    WaitingTimesSort.WaitAscending -> if (language == "en") "Wait time â†‘" else "Wartezeit â†‘"
    WaitingTimesSort.WaitDescending -> if (language == "en") "Wait time â†“" else "Wartezeit â†“"
    WaitingTimesSort.Name -> "Name A-Z"
}

private fun AttractionFilter.label(language: String) = when (this) {
    AttractionFilter.All -> if (language == "en") "All" else "Alle"
    AttractionFilter.OpenOnly -> if (language == "en") "Open only" else "Nur offen"
    AttractionFilter.Maintenance -> if (language == "en") "Maintenance" else "Wartung"
    AttractionFilter.Closed -> if (language == "en") "Closed" else "Geschlossen"
}

private fun formatForecastDate(isoDate: String): String {
    return runCatching {
        val date = LocalDate.parse(isoDate)
        val dayOfWeek = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
        "$dayOfWeek ${date.dayOfMonth}."
    }.getOrElse { isoDate }
}

private fun AttractionStatus.label(language: String) = when (this) {
    AttractionStatus.Opened -> if (language == "en") "Open" else "Geöffnet"
    AttractionStatus.Closed -> if (language == "en") "Closed" else "Geschlossen"
    AttractionStatus.Maintenance -> if (language == "en") "Maintenance" else "Wartung"
    AttractionStatus.ClosedWeather -> if (language == "en") "Weather" else "Wetter"
    AttractionStatus.Unknown -> if (language == "en") "Unknown" else "Unbekannt"
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
