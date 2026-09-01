package de.wartezeiten.app.ui.statistics

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.wartezeiten.app.core.i18n.localized
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import de.wartezeiten.app.core.utils.countryToFlag
import de.wartezeiten.app.core.utils.parkLocalToday
import de.wartezeiten.app.domain.model.AttractionHistorySummary
import de.wartezeiten.app.domain.model.Park
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

@Composable
fun StatisticsRoute(
    onBackClick: () -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatisticsScreen(
        state = state,
        onBackClick = onBackClick,
        onRefreshClick = viewModel::refresh,
        onParkSelected = viewModel::selectPark,
        onDateSelected = viewModel::selectDate,
        onAttractionSelected = viewModel::selectAttraction,
        onParkStatisticsSelected = viewModel::selectParkStatistics,
        onAttractionListQueryChange = viewModel::updateAttractionListQuery,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    state: StatisticsUiState,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onParkSelected: (String) -> Unit,
    onDateSelected: (String) -> Unit,
    onAttractionSelected: (String) -> Unit,
    onParkStatisticsSelected: () -> Unit,
    onAttractionListQueryChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val rootView = LocalView.current
    val scope = rememberCoroutineScope()
    var selectedMonthBucket by remember { mutableStateOf<StatisticsMonthBucket?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.refreshTrigger, state.isLoading) {
        if (state.refreshTrigger > 0 && !state.isLoading) {
            val message = state.refreshError ?: run {
                val updatedAt = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                localized(
                    state.language,
                    de = "Statistiken aktualisiert um $updatedAt Uhr",
                    en = "Statistics updated at $updatedAt",
                    fr = "Statistiques mises à jour à $updatedAt",
                    nl = "Statistieken bijgewerkt om $updatedAt",
                )
            }
            snackbarHostState.showSnackbar(message)
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
                            localized(state.language, de = "Statistik", en = "Statistics", fr = "Statistiques", nl = "Statistieken"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            state.selectedPark?.name ?: localized(state.language, de = "Zentrale Wartezeiten", en = "Central wait times", fr = "Temps d'attente centraux", nl = "Centrale wachttijden"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        Spacer(Modifier.width(8.dp))
                    } else {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    shareStatisticsScreenshot(
                                        context = context,
                                        view = rootView,
                                        title = state.selectedPark?.name ?: localized(state.language, de = "Wartezeiten Statistik", en = "Wait-time statistics", fr = "Statistiques d'attente", nl = "Wachttijdstatistieken"),
                                        language = state.language,
                                    )
                                }
                            },
                        ) {
                            Icon(Icons.Default.Share, contentDescription = localized(state.language, de = "Statistik teilen", en = "Share statistics", fr = "Partager les statistiques", nl = "Statistieken delen"))
                        }
                        IconButton(onClick = onRefreshClick) {
                            Icon(Icons.Default.Refresh, contentDescription = localized(state.language, de = "Aktualisieren", en = "Refresh", fr = "Actualiser", nl = "Vernieuwen"))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    StatisticsControlPanel(
                        state = state,
                        onParkSelected = onParkSelected,
                        onDateSelected = onDateSelected,
                        onAttractionSelected = onAttractionSelected,
                        onParkStatisticsSelected = onParkStatisticsSelected,
                    )
                }

                val parkToday = parkLocalToday(state.day?.openFrom)
                val isTodaySelected = state.selectedDate == parkToday || state.selectedDate == "Heute" || state.selectedDate == "Today"
                val isFallback = state.isDataFallbackToPreviousDay || (state.day != null && state.day.date != parkToday && isTodaySelected)
                if (isFallback) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text(
                                    text = localized(
                                        state.language,
                                        de = "Für heute liegen noch keine zentralen Messpunkte vor. Es werden die Daten vom Vortag angezeigt.",
                                        en = "No central measurements available for today yet. Data from the previous day is shown.",
                                        fr = "Aucune mesure centrale disponible pour aujourd'hui pour le moment. Les données de la veille sont affichées.",
                                        nl = "Nog geen centrale metingen beschikbaar voor vandaag. Gegevens van de vorige dag worden weergegeven.",
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }

                if (state.errorMessage != null) {
                    item {
                        ErrorCard(
                            message = state.errorMessage,
                            onRetry = onRefreshClick,
                        )
                    }
                } else if (state.day == null && !state.isLoading) {
                    item {
                        EmptyStatisticsState(
                            parkName = state.selectedPark?.name,
                            isSpecificAttraction = state.selectedAttractionId != null,
                            language = state.language,
                        )
                    }
                } else {
                    item {
                        if (state.selectedAttractionId == null) {
                            state.parkStatistics?.let { summary ->
                                SelectedParkSummary(summary, state.language)
                            }
                        } else if (!state.isLoading && (state.day == null || state.day.snapshots.isEmpty())) {
                            EmptyStatisticsState(
                                parkName = state.selectedPark?.name,
                                isSpecificAttraction = true,
                                language = state.language
                            )
                        } else {
                            state.selectedAttraction?.let { summary ->
                                SelectedAttractionSummary(summary, state.language)
                            }
                        }
                    }

                    if (state.selectedAttractionId == null) {
                        item {
                            ParkAverageWaitChart(
                                day = state.day,
                                selectedDate = state.selectedDate,
                                points = state.parkSeries,
                                language = state.language,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                            )
                        }
                    } else {
                        item {
                            AttractionHistoryChart(
                                day = state.day,
                                selectedDate = state.selectedDate,
                                points = state.selectedSeries,
                                language = state.language,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                            )
                        }
                    }


                    item {
                        MonthOverviewSection(
                        buckets = state.monthBuckets,
                        language = state.language,
                        onMonthClick = { selectedMonthBucket = it }
                    )
                    }

                    if (state.selectedAttractionId == null && state.day != null) {
                        item {
                            OutlinedTextField(
                                value = state.attractionListQuery,
                                onValueChange = onAttractionListQueryChange,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(localized(state.language, de = "Attraktion suchen...", en = "Search attraction...", fr = "Rechercher une attraction...", nl = "Attractie zoeken...")) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = if (state.attractionListQuery.isNotEmpty()) {
                                    {
                                        IconButton(onClick = { onAttractionListQueryChange("") }) {
                                            Icon(Icons.Default.Clear, contentDescription = null)
                                        }
                                    }
                                } else null,
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                )
                            )
                        }

                        val filteredAttractions = state.day.attractions
                            .distinctBy { it.id }
                            .filter { it.name.contains(state.attractionListQuery, ignoreCase = true) }
                            .sortedByDescending { it.averageWaitMinutes }

                        items(filteredAttractions, key = { it.id }) { attraction ->
                            AttractionSummaryRow(
                                summary = attraction,
                                selected = false,
                                onClick = { onAttractionSelected(attraction.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedMonthBucket != null) {
        DateSelectorSheet(
            title = formatMonthLabel(selectedMonthBucket!!.month, state.language),
            buckets = listOf(selectedMonthBucket!!),
            language = state.language,
            onDateSelected = { date ->
                onDateSelected(date)
                selectedMonthBucket = null
            },
            onDismiss = { selectedMonthBucket = null }
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

private fun shareStatisticsScreenshot(
    context: Context,
    view: android.view.View,
    title: String,
    language: String,
) {
    if (view.width <= 0 || view.height <= 0) {
        Toast.makeText(
            context,
            localized(language, de = "Screenshot konnte nicht erstellt werden.", en = "Screenshot could not be created.", fr = "La capture d'écran n'a pas pu être créée.", nl = "Screenshot kon niet worden gemaakt."),
            Toast.LENGTH_SHORT,
        ).show()
        return
    }

    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    view.draw(android.graphics.Canvas(bitmap))

    val shareDirectory = File(context.cacheDir, "shared_statistics").apply { mkdirs() }
    val file = File(shareDirectory, "wartezeiten-statistik.png")
    file.outputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    }
    bitmap.recycle()

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, localized(language, de = "Statistik teilen", en = "Share statistics", fr = "Partager les statistiques", nl = "Statistieken delen")))
}

@Composable
private fun StatisticsControlPanel(
    state: StatisticsUiState,
    onParkSelected: (String) -> Unit,
    onDateSelected: (String) -> Unit,
    onAttractionSelected: (String) -> Unit,
    onParkStatisticsSelected: () -> Unit,
) {
    var showParkSelector by remember { mutableStateOf(false) }
    var showDateSelector by remember { mutableStateOf(false) }
    var showAttractionSelector by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorField(
                label = localized(state.language, de = "Park", en = "Park", fr = "Parc", nl = "Park"),
                value = state.selectedPark?.let { "${countryToFlag(it.country)} ${it.name}" } ?: state.selectedParkKey ?: "-",
                onClick = { showParkSelector = true },
                modifier = Modifier.weight(1.3f)
            )

            SelectorField(
                label = localized(state.language, de = "Datum", en = "Date", fr = "Date", nl = "Datum"),
                value = formatDateLabel(state.selectedDate),
                onClick = { showDateSelector = true },
                modifier = Modifier.weight(1f)
            )
        }

        SelectorField(
            label = localized(state.language, de = "Attraktion", en = "Attraction", fr = "Attraction", nl = "Attractie"),
            value = state.selectedAttraction?.name ?: localized(state.language, de = "Durchschnitt (Park)", en = "Average (Park)", fr = "Moyenne (Parc)", nl = "Gemiddelde (Park)"),
            onClick = { showAttractionSelector = true },
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showParkSelector) {
        SearchableSelectorSheet(
            title = localized(state.language, de = "Park auswählen", en = "Select park", fr = "Choisir un parc", nl = "Park selecteren"),
            items = state.parks.map { SelectorItem(it.id, it.name, countryToFlag(it.country)) },
            language = state.language,
            onItemSelected = { item ->
                onParkSelected(item.id)
                showParkSelector = false
            },
            onDismiss = { showParkSelector = false }
        )
    }

    if (showDateSelector) {
        DateSelectorSheet(
            title = localized(state.language, de = "Datum auswählen", en = "Select date", fr = "Choisir une date", nl = "Datum selecteren"),
            buckets = state.monthBuckets,
            language = state.language,
            onDateSelected = { date ->
                onDateSelected(date)
                showDateSelector = false
            },
            onDismiss = { showDateSelector = false }
        )
    }

    if (showAttractionSelector) {
        val attractionItems = mutableListOf<SelectorItem>()
        attractionItems.add(
            SelectorItem(
                id = "average",
                name = localized(state.language, de = "Auslastung (gesamt)", en = "Total crowd", fr = "Fréquentation totale", nl = "Totale drukte"),
                icon = "📊"
            )
        )
        attractionItems.addAll(state.attractionOptions.map { SelectorItem(it.id, it.name) })

        SearchableSelectorSheet(
            title = localized(state.language, de = "Attraktion auswählen", en = "Select attraction", fr = "Choisir une attraction", nl = "Attractie selecteren"),
            items = attractionItems,
            language = state.language,
            onItemSelected = {
                if (it.id == "average") onParkStatisticsSelected()
                else onAttractionSelected(it.id)
                showAttractionSelector = false
            },
            onDismiss = { showAttractionSelector = false }
        )
    }
}

@Composable
private fun SelectorField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SelectedAttractionSummary(
    summary: AttractionHistorySummary,
    language: String,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(summary.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    label = localized(language, de = "Durchschnitt", en = "Average", fr = "Moyenne", nl = "Gemiddelde"),
                    value = summary.averageWaitMinutes.minutesText(),
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Min / Max",
                    value = "${summary.minWaitMinutes ?: "-"} / ${summary.maxWaitMinutes ?: "-"}",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = localized(language, de = "Messpunkte", en = "Samples", fr = "Échantillons", nl = "Meetpunten"),
                    value = "${summary.sampleCount}",
                    modifier = Modifier.weight(0.8f)
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SelectedParkSummary(
    summary: ParkStatisticsSummary,
    language: String,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(localized(language, de = "Park-Durchschnitt", en = "Park average", fr = "Moyenne du parc", nl = "Parkgemiddelde"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    label = localized(language, de = "Ø Wartezeit", en = "Ø Wait time", fr = "Ø Temps d'attente", nl = "Ø Wachttijd"),
                    value = summary.averageWaitMinutes.minutesText(),
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = localized(language, de = "Messpunkte", en = "Samples", fr = "Échantillons", nl = "Meetpunten"),
                    value = "${summary.sampleCount}",
                    modifier = Modifier.weight(0.8f)
                )
                StatTile(
                    label = localized(language, de = "Offen", en = "Open", fr = "Ouvert", nl = "Open"),
                    value = "${summary.latestOpenAttractionCount}",
                    modifier = Modifier.weight(0.8f)
                )
            }
        }
    }
}

@Composable
private fun AttractionHistoryChart(
    day: de.wartezeiten.app.domain.model.AttractionHistoryDay?,
    selectedDate: String,
    points: List<AttractionChartPoint>,
    language: String,
    modifier: Modifier = Modifier,
) {
    val allWaitPoints = remember(points) {
        points
            .filter { it.value >= 0 }
            .sortedBy { it.capturedAtMillis }
    }
    if (points.size < 2) {
        OutlinedCard(modifier = modifier, shape = RoundedCornerShape(14.dp)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    localized(language, de = "Noch zu wenige Messpunkte", en = "Too few measurements yet", fr = "Encore trop peu de mesures", nl = "Nog te weinig meetpunten"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val sortedPoints = remember(points) { points.sortedBy { it.capturedAtMillis } }
    val chartZoneId = remember(day?.openFrom, day?.closedFrom) {
        day?.openFrom.toOffsetZoneIdOrNull()
            ?: day?.closedFrom.toOffsetZoneIdOrNull()
            ?: ZoneId.systemDefault()
    }
    val parkLocalToday = remember(chartZoneId) {
        Instant.ofEpochMilli(System.currentTimeMillis()).atZone(chartZoneId).toLocalDate().toString()
    }
    val axisBounds = remember(day, sortedPoints, parkLocalToday) {
        calculateAxisBounds(
            timestamps = sortedPoints.map { it.capturedAtMillis },
            openFrom = day?.openFrom,
            closedFrom = day?.closedFrom,
            firstOpenAtMillis = allWaitPoints.firstOrNull()?.capturedAtMillis,
            selectedDate = selectedDate,
            nowMillis = System.currentTimeMillis(),
            parkLocalToday = parkLocalToday,
        )
    }
    val minTime = axisBounds.first
    val maxTime = axisBounds.second.coerceAtLeast(minTime + 1)
    val midTime = minTime + ((maxTime - minTime) / 2)
    val visiblePoints = remember(sortedPoints, minTime, maxTime) {
        sortedPoints.filter { it.capturedAtMillis in minTime..maxTime }
    }
    val waitPoints = remember(visiblePoints) { visiblePoints.filter { it.value >= 0 } }
    val statusPoints = remember(visiblePoints) { visiblePoints.filter { it.value < 0 || it.statusCode < 0 } }
    val yMax = remember(waitPoints) { calculateNiceYAxisMax(waitPoints.maxOfOrNull { it.value } ?: 0) }
    val yStep = remember(yMax) { calculateNiceTickStep(yMax) }
    val yLabels = remember(yMax, yStep) { (0..yMax step yStep).toList() }
    val hasStatusLane = statusPoints.isNotEmpty()
    val timeFormatter = remember(chartZoneId) {
        DateTimeFormatter.ofPattern("HH:mm").withZone(chartZoneId)
    }

    OutlinedCard(modifier = modifier, shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(bottom = if (hasStatusLane) 18.dp else 0.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    yLabels.asReversed().forEach { label ->
                        Text("$label", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val neutralColor = MaterialTheme.colorScheme.onSurfaceVariant
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    val horizontalPadding = 4.dp.toPx()
                    val verticalPadding = 8.dp.toPx()
                    val width = size.width - horizontalPadding * 2
                    val statusLaneHeight = if (hasStatusLane) 18.dp.toPx() else 0f
                    val height = size.height - verticalPadding * 2
                    val waitHeight = (height - statusLaneHeight).coerceAtLeast(1f)
                    val range = yMax.coerceAtLeast(1).toFloat()

                    fun xFor(timestampMillis: Long): Float {
                        return horizontalPadding + ((timestampMillis - minTime).toFloat() / (maxTime - minTime).toFloat()) * width
                    }

                    fun yFor(value: Float): Float {
                        return verticalPadding + (1f - (value / range).coerceIn(0f, 1f)) * waitHeight
                    }

                    yLabels.forEach { label ->
                        val y = yFor(label.toFloat())
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.18f),
                            start = Offset(horizontalPadding, y),
                            end = Offset(horizontalPadding + width, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }

                    val path = Path()
                    waitPoints.forEachIndexed { index, point ->
                        val x = xFor(point.capturedAtMillis)
                        val y = yFor(point.value.toFloat())
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = neutralColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )
                    waitPoints.forEach { point ->
                        val x = xFor(point.capturedAtMillis)
                        val y = yFor(point.value.toFloat())
                        drawCircle(
                            color = neutralColor,
                            radius = 3.dp.toPx(),
                            center = Offset(x, y),
                        )
                    }
                    statusPoints.forEach { point ->
                        val x = xFor(point.capturedAtMillis)
                        val laneIndex = when (point.statusCode) {
                            -3 -> 2
                            -2 -> 1
                            else -> 0
                        }
                        val y = verticalPadding + waitHeight + (statusLaneHeight * (laneIndex + 1) / 4f)
                        drawCircle(
                            color = statusColor(point.statusCode),
                            radius = 3.dp.toPx(),
                            center = Offset(x, y),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 34.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val minT = timeFormatter.format(Instant.ofEpochMilli(minTime))
                val midT = timeFormatter.format(Instant.ofEpochMilli(midTime))
                val maxT = timeFormatter.format(Instant.ofEpochMilli(maxTime))
                Text(localized(language, de = "$minT Uhr", en = minT, fr = minT, nl = minT), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(localized(language, de = "$midT Uhr", en = midT, fr = midT, nl = midT), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(localized(language, de = "$maxT Uhr", en = maxT, fr = maxT, nl = maxT), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusLegend(language)
        }
    }
}

@Composable
private fun StatusLegend(language: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusLegendItem(localized(language, de = "Geschlossen", en = "Closed", fr = "Fermé", nl = "Gesloten"), statusColor(-1))
        StatusLegendItem(localized(language, de = "Wetterbedingt zu", en = "Weather-related", fr = "Lié à la météo", nl = "Weersafhankelijk"), statusColor(-2))
        StatusLegendItem(localized(language, de = "Wartung", en = "Maintenance", fr = "Entretien", nl = "Onderhoud"), statusColor(-3))
    }
}

@Composable
private fun StatusLegendItem(label: String, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(modifier = Modifier.size(8.dp), shape = RoundedCornerShape(4.dp), color = color) {}
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ParkAverageWaitChart(
    day: de.wartezeiten.app.domain.model.AttractionHistoryDay?,
    selectedDate: String,
    points: List<ParkChartPoint>,
    language: String,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) {
        OutlinedCard(modifier = modifier, shape = RoundedCornerShape(14.dp)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    localized(language, de = "Noch zu wenige Messpunkte", en = "Too few measurements yet", fr = "Encore trop peu de mesures", nl = "Nog te weinig meetpunten"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val sortedPoints = remember(points) { points.sortedBy { it.capturedAtMillis } }
    val chartZoneId = remember(day?.openFrom, day?.closedFrom) {
        day?.openFrom.toOffsetZoneIdOrNull()
            ?: day?.closedFrom.toOffsetZoneIdOrNull()
            ?: ZoneId.systemDefault()
    }
    val parkLocalToday = remember(chartZoneId) {
        Instant.ofEpochMilli(System.currentTimeMillis()).atZone(chartZoneId).toLocalDate().toString()
    }
    val axisBounds = remember(day, sortedPoints, parkLocalToday) {
        calculateAxisBounds(
            timestamps = sortedPoints.map { it.capturedAtMillis },
            openFrom = day?.openFrom,
            closedFrom = day?.closedFrom,
            firstOpenAtMillis = sortedPoints.firstOrNull()?.capturedAtMillis,
            selectedDate = selectedDate,
            nowMillis = System.currentTimeMillis(),
            parkLocalToday = parkLocalToday,
        )
    }
    val minTime = axisBounds.first
    val maxTime = axisBounds.second.coerceAtLeast(minTime + 1)
    val midTime = minTime + ((maxTime - minTime) / 2)
    val visiblePoints = remember(sortedPoints, minTime, maxTime) {
        sortedPoints.filter { it.capturedAtMillis in minTime..maxTime }
    }
    val yMax = remember(visiblePoints) {
        calculateNiceYAxisMax(ceil(visiblePoints.maxOfOrNull { it.averageWaitMinutes } ?: 0f).toInt())
    }
    val yStep = remember(yMax) { calculateNiceTickStep(yMax) }
    val yLabels = remember(yMax, yStep) { (0..yMax step yStep).toList() }
    val timeFormatter = remember(chartZoneId) {
        DateTimeFormatter.ofPattern("HH:mm").withZone(chartZoneId)
    }

    OutlinedCard(modifier = modifier, shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    yLabels.asReversed().forEach { label ->
                        Text("$label", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val neutralColor = MaterialTheme.colorScheme.onSurfaceVariant
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    val horizontalPadding = 4.dp.toPx()
                    val verticalPadding = 8.dp.toPx()
                    val width = size.width - horizontalPadding * 2
                    val height = size.height - verticalPadding * 2
                    val range = yMax.coerceAtLeast(1).toFloat()

                    fun xFor(timestampMillis: Long): Float {
                        return horizontalPadding + ((timestampMillis - minTime).toFloat() / (maxTime - minTime).toFloat()) * width
                    }

                    fun yFor(value: Float): Float {
                        return verticalPadding + (1f - (value / range).coerceIn(0f, 1f)) * height
                    }

                    yLabels.forEach { label ->
                        val y = yFor(label.toFloat())
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.18f),
                            start = Offset(horizontalPadding, y),
                            end = Offset(horizontalPadding + width, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }

                    val path = Path()
                    visiblePoints.forEachIndexed { index, point ->
                        val x = xFor(point.capturedAtMillis)
                        val y = yFor(point.averageWaitMinutes)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = neutralColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )
                    visiblePoints.forEach { point ->
                        val x = xFor(point.capturedAtMillis)
                        val y = yFor(point.averageWaitMinutes)
                        drawCircle(
                            color = neutralColor,
                            radius = 3.dp.toPx(),
                            center = Offset(x, y),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 34.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val minT = timeFormatter.format(Instant.ofEpochMilli(minTime))
                val midT = timeFormatter.format(Instant.ofEpochMilli(midTime))
                val maxT = timeFormatter.format(Instant.ofEpochMilli(maxTime))
                Text(localized(language, de = "$minT Uhr", en = minT, fr = minT, nl = minT), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(localized(language, de = "$midT Uhr", en = midT, fr = midT, nl = midT), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(localized(language, de = "$maxT Uhr", en = maxT, fr = maxT, nl = maxT), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                localized(language, de = "Durchschnittliche Wartezeit aller geöffneten Attraktionen", en = "Average wait time of all open attractions", fr = "Temps d'attente moyen de toutes les attractions ouvertes", nl = "Gemiddelde wachttijd van alle geopende attracties"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthOverviewSection(
    buckets: List<StatisticsMonthBucket>,
    language: String,
    onMonthClick: (StatisticsMonthBucket) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            localized(language, de = "Monats-Überblick", en = "Monthly overview", fr = "Aperçu mensuel", nl = "Maandoverzicht"),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            buckets.forEach { bucket ->
                OutlinedCard(
                    onClick = { onMonthClick(bucket) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            formatMonthLabel(bucket.month, language),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val daysLabel = localized(language, de = "Tage", en = "Days", fr = "Jours", nl = "Dagen")
                        Text(
                            "${bucket.dayCount} $daysLabel",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSelectorSheet(
    title: String,
    buckets: List<StatisticsMonthBucket>,
    language: String,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var selectedBucket by remember { 
        mutableStateOf(buckets.firstOrNull()) 
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            // Month selection row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                buckets.forEach { bucket ->
                    FilterChip(
                        selected = selectedBucket == bucket,
                        onClick = { selectedBucket = bucket },
                        label = { Text(formatMonthLabel(bucket.month, language)) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (selectedBucket != null) {
                Text(
                    text = localized(
                        language,
                        de = "Wähle einen Tag",
                        en = "Select a day",
                        fr = "Choisir un jour",
                        nl = "Kies een dag"
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 80.dp),
                    contentPadding = PaddingValues(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(selectedBucket!!.availableDates.reversed()) { date ->
                        OutlinedCard(
                            onClick = { onDateSelected(date) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = formatDayOfMonth(date),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = formatShortDateLabel(date),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        localized(language, de = "Keine Daten verfügbar", en = "No data available", fr = "Aucune donnée disponible", nl = "Geen gegevens beschikbaar"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AttractionSummaryRow(
    summary: AttractionHistorySummary,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(summary.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("${summary.sampleCount} Messpunkte", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                summary.averageWaitMinutes.minutesText(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRetry) { Text("Erneut versuchen", color = MaterialTheme.colorScheme.onErrorContainer) }
        }
    }
}

@Composable
private fun EmptyStatisticsState(parkName: String?, isSpecificAttraction: Boolean, language: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isSpecificAttraction) {
                localized(
                    language,
                    de = "Für diese Attraktion liegen an diesem Tag keine Messpunkte vor.",
                    en = "No measurements available for this attraction on this day.",
                    fr = "Aucune mesure disponible pour cette attraction ce jour-là.",
                    nl = "Geen metingen beschikbaar voor deze attractie op deze dag."
                )
            } else {
                localized(
                    language,
                    de = "Für diesen Tag liegen keine zentralen Messpunkte für ${parkName ?: "den Park"} vor.",
                    en = "No central measurements available for ${parkName ?: "the park"} on this day.",
                    fr = "Aucune mesure centrale disponible pour ${parkName ?: "le parc"} ce jour-là.",
                    nl = "Geen centrale metingen beschikbaar voor ${parkName ?: "het park"} op deze dag."
                )
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

private fun statusColor(statusCode: Int): Color = when (statusCode) {
    -1 -> Color(0xFFF44336)
    -2 -> Color(0xFF1565C0)
    -3 -> Color(0xFFFF9800)
    else -> Color.Gray
}

private fun Float?.minutesText(): String = if (this == null) "-" else "${this.toInt()} Min"

private fun calculateAxisBounds(
    timestamps: List<Long>,
    openFrom: String?,
    closedFrom: String?,
    firstOpenAtMillis: Long?,
    selectedDate: String,
    nowMillis: Long,
    parkLocalToday: String,
): Pair<Long, Long> {
    val openAt = openFrom?.parseInstantMillis()
    val closedAt = closedFrom?.parseInstantMillis()
    
    val min = openAt ?: firstOpenAtMillis ?: timestamps.minOrNull() ?: 0L
    
    // Default max is closing time
    var max = closedAt ?: timestamps.maxOrNull() ?: (min + 1L)
    
    // If today is selected, cap the max time at "now" (plus a small buffer)
    if (selectedDate == parkLocalToday || selectedDate == "Heute" || selectedDate == "Today") {
        max = minOf(max, nowMillis + 5 * 60_000L) // Add 5 min buffer
    }
    
    // Ensure we see all existing data points
    val maxTimestamp = timestamps.maxOrNull() ?: 0L
    max = maxOf(max, maxTimestamp)

    return min to max
}

private fun String.parseInstantMillis(): Long? = runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()

private fun String?.toOffsetZoneIdOrNull(): ZoneId? {
    return this?.let { value ->
        runCatching { OffsetDateTime.parse(value).offset }.getOrNull()
    }
}

private fun calculateNiceYAxisMax(maxValue: Int): Int {
    if (maxValue <= 0) return 60
    return ((maxValue + 19) / 20) * 20
}

private fun calculateNiceTickStep(yMax: Int): Int {
    return when {
        yMax <= 60 -> 20
        yMax <= 120 -> 30
        else -> 60
    }
}

private fun formatDateLabel(isoDate: String): String {
    return runCatching {
        val date = LocalDate.parse(isoDate)
        date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    }.getOrElse { isoDate }
}

private fun formatShortDateLabel(isoDate: String): String {
    return runCatching {
        val date = LocalDate.parse(isoDate)
        date.format(DateTimeFormatter.ofPattern("dd.MM."))
    }.getOrElse { isoDate }
}

private fun formatMonthLabel(monthKey: String, language: String): String {
    return runCatching {
        val ym = YearMonth.parse(monthKey)
        val locale = Locale.forLanguageTag(language)
        ym.month.getDisplayName(TextStyle.FULL, locale) + " " + ym.year
    }.getOrElse { monthKey }
}

private fun formatDayOfMonth(isoDate: String): String {
    return runCatching {
        val date = LocalDate.parse(isoDate)
        date.dayOfMonth.toString()
    }.getOrElse { "-" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchableSelectorSheet(
    title: String,
    items: List<SelectorItem>,
    language: String,
    onItemSelected: (SelectorItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var query by remember { mutableStateOf("") }
    val filteredItems = remember(query, items) {
        items.distinctBy { it.id }.filter { it.name.contains(query, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text(localized(language, de = "Suchen...", en = "Search...", fr = "Rechercher...", nl = "Zoeken...")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = localized(language, de = "Suche leeren", en = "Clear search", fr = "Effacer la recherche", nl = "Zoekopdracht wissen"))
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filteredItems, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemSelected(item) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (item.icon != null) {
                            Text(item.icon, style = MaterialTheme.typography.titleLarge)
                        }
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

private data class SelectorItem(
    val id: String,
    val name: String,
    val icon: String? = null,
)
