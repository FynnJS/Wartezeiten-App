package de.wartezeiten.app.ui.waitingtimes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.wartezeiten.app.domain.model.AttractionStatus
import de.wartezeiten.app.domain.model.CrowdLevel
import de.wartezeiten.app.domain.model.DataFreshness
import de.wartezeiten.app.domain.model.DataQuality
import de.wartezeiten.app.domain.model.HolidayInfo
import de.wartezeiten.app.domain.model.CrowdLevelSource
import de.wartezeiten.app.domain.model.CrowdLevelEstimate
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
        onMaxWaitChange = viewModel::setMaxWait,
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
    onMaxWaitChange: (Int?) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddWatchlistDialog by remember { mutableStateOf(false) }
    var selectedAttractionId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.refreshTrigger) {
        if ((state.refreshTrigger > 0) && !state.isLoading) {
            if (state.errorMessage == null) {
                val updatedAt = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                val openCount = state.allWaitingTimes.count { it.status == AttractionStatus.Opened }
                snackbarHostState.showSnackbar(
                    message = "$openCount offene Attraktionen aktualisiert um $updatedAt",
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
                            state.park?.name ?: "Laden…",
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    val isFavorite = state.park?.isFavorite == true
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isFavorite) "Von Favoriten entfernen" else "Zu Favoriten hinzufügen",
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
                            Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            AttributionFooter()
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
                    )
                }
            }

            WaitingTimesContent(
                state = state,
                onSortChange = onSortChange,
                onFilterChange = onFilterChange,
                onMaxWaitChange = onMaxWaitChange,
                onAddWatchlist = {
                    selectedAttractionId = null
                    showAddWatchlistDialog = true
                },
                onAddWatchlistForAttraction = { attractionId ->
                    selectedAttractionId = attractionId
                    showAddWatchlistDialog = true
                }
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
                Text("Neu laden")
            }
        }
    }
}

@Composable
private fun WaitingTimesContent(
    state: WaitingTimesUiState,
    onSortChange: (WaitingTimesSort) -> Unit,
    onFilterChange: (AttractionFilter) -> Unit,
    onMaxWaitChange: (Int?) -> Unit,
    onAddWatchlist: () -> Unit,
    onAddWatchlistForAttraction: (String) -> Unit
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
        item {
            ParkHeaderSection(
                currentTime = state.currentLocalTime,
                openingTimes = state.openingTimes,
                crowdLevel = state.crowdLevel,
                crowdEstimate = state.crowdEstimate,
                waitingTimes = state.allWaitingTimes,
                localTimeOffsetSeconds = state.localTimeOffsetSeconds,
                dataQuality = state.dataQuality,
                weather = state.weather,
                holidays = state.holidays
            )
        }

        item {
            ParkTrendDashboard(summary = state.trendSummary)
        }

        // Filter & Sortierung
        item {
            FilterSection(
                sort = state.sort,
                filter = state.filter,
                maxWaitMinutes = state.maxWaitMinutes,
                onSortChange = onSortChange,
                onFilterChange = onFilterChange,
                onMaxWaitChange = onMaxWaitChange,
                onAddWatchlist = onAddWatchlist
            )
        }

        if (state.waitingTimes.isEmpty() && !state.isLoading) {
            item { EmptyState() }
        } else {
            items(
                count = state.waitingTimes.size,
                key = { state.waitingTimes[it].attractionId }
            ) { index ->
                WaitingTimeRow(
                    item = state.waitingTimes[index],
                    onAddWatchlist = onAddWatchlistForAttraction
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
    maxWaitMinutes: Int?,
    onSortChange: (WaitingTimesSort) -> Unit,
    onFilterChange: (AttractionFilter) -> Unit,
    onMaxWaitChange: (Int?) -> Unit,
    onAddWatchlist: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Filter & Sortierung",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
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
                        Text(sort.label(), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
                DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                    WaitingTimesSort.entries.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.label()) },
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
                        Text(filter.label(), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
                DropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {
                    AttractionFilter.entries.forEach { f ->
                        DropdownMenuItem(
                            text = { Text(f.label()) },
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
            Text("Max. Warten:", style = MaterialTheme.typography.labelMedium)
            val maxWaitOptions = listOf(null, 15, 30, 45, 60)
            maxWaitOptions.forEach { minutes ->
                FilterChip(
                    selected = maxWaitMinutes == minutes,
                    onClick = { onMaxWaitChange(minutes) },
                    label = {
                        Text(
                            if (minutes == null) "Alle" else "≤ $minutes",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    shape = RoundedCornerShape(8.dp)
                )
            }
            
            TextButton(onClick = onAddWatchlist) {
                Text("Benachrichtigung")
            }
        }
    }
}

@Composable
private fun ParkHeaderSection(
    currentTime: Long,
    openingTimes: OpeningTimes?,
    crowdLevel: CrowdLevel?,
    crowdEstimate: CrowdLevelEstimate?,
    waitingTimes: List<WaitingTime>,
    localTimeOffsetSeconds: Int?,
    dataQuality: DataQuality?,
    weather: WeatherInfo?,
    holidays: List<HolidayInfo>
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

    val dateTimeFormatter = remember {
        DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy '•' HH:mm 'Uhr'", Locale.GERMAN)
    }
    val formattedDateTime = remember(currentTime, zoneId) {
        dateTimeFormatter.format(currentZonedDateTime) + " (Ortszeit)"
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

            displayState.crowdText?.let { crowdText ->
                Text(
                    text = crowdText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            dataQuality?.let { quality ->
                val minutesAgo = ((currentZonedDateTime.toInstant().toEpochMilli() - quality.lastUpdated) / 60000).toInt().coerceAtLeast(0)
                Text(
                    text = "Letzte Aktualisierung vor ${minutesAgo} Minuten",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }

            // Kompakte Wetter-Anzeige innerhalb des Headers
            weather?.let {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "Wetter: ${String.format(Locale.getDefault(), "%.0f", it.temperature)}°C • Regen: ${it.precipitationProbability}%",
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
                    text = "Feiertag: ${h.name}",
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
    onAddWatchlist: (String) -> Unit
) {
    val waitTimeColor = when {
        item.waitingTime == null -> MaterialTheme.colorScheme.primary
        item.waitingTime < 30 -> Color(0xFF4CAF50)
        item.waitingTime < 60 -> Color(0xFFFFB300)
        else -> Color(0xFFF44336)
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
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
                    item.status.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { onAddWatchlist(item.attractionId) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Benachrichtigung für ${item.name} hinzufügen",
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
                        "Min.",
                        style = MaterialTheme.typography.labelSmall,
                        color = waitTimeColor
                    )
                }
            }
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
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Keine Attraktionen gefunden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun WaitingTimesSort.label() = when (this) {
    WaitingTimesSort.WaitAscending -> "Wartezeit ↑"
    WaitingTimesSort.WaitDescending -> "Wartezeit ↓"
    WaitingTimesSort.Name -> "Name A-Z"
}

private fun AttractionFilter.label() = when (this) {
    AttractionFilter.All -> "Alle"
    AttractionFilter.OpenOnly -> "Nur offen"
    AttractionFilter.Maintenance -> "Wartung"
    AttractionFilter.Closed -> "Geschlossen"
}

private fun formatForecastDate(isoDate: String): String {
    return runCatching {
        val date = LocalDate.parse(isoDate)
        val dayOfWeek = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
        "$dayOfWeek ${date.dayOfMonth}."
    }.getOrElse { isoDate }
}

private fun AttractionStatus.label() = when (this) {
    AttractionStatus.Opened -> "Geöffnet"
    AttractionStatus.Closed -> "Geschlossen"
    AttractionStatus.Maintenance -> "Wartung"
    AttractionStatus.ClosedWeather -> "Wetter"
    AttractionStatus.Unknown -> "Unbekannt"
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
