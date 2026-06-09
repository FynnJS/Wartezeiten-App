package de.wartezeiten.app.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.wartezeiten.app.domain.model.AttractionHistorySummary
import de.wartezeiten.app.domain.model.Park
import java.time.Instant
import java.time.LocalDate
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
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Statistik", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            state.selectedPark?.name ?: "Zentrale Wartezeiten",
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
                        IconButton(onClick = onRefreshClick) {
                            Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren")
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
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

                state.errorMessage?.let { message ->
                    item { ErrorCard(message = message, onRetry = onRefreshClick) }
                }

                if (state.selectedAttractionId == null) {
                    val parkStatistics = state.parkStatistics
                    if (parkStatistics != null) {
                        item {
                            SelectedParkSummary(
                                summary = parkStatistics,
                                series = state.parkSeries,
                            )
                        }
                        item {
                            ParkAverageWaitChart(
                                day = state.day,
                                points = state.parkSeries,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                            )
                        }
                    } else if (!state.isLoading && (state.day != null || state.selectedDate == LocalDate.now().toString())) {
                        item {
                            EmptyStatisticsState(
                                selectedName = state.selectedPark?.name,
                                selectedDate = state.selectedDate,
                            )
                        }
                    }
                }

                val selectedAttraction = state.selectedAttraction
                if (state.day != null && selectedAttraction != null) {
                    item {
                        SelectedAttractionSummary(
                            attraction = selectedAttraction,
                            series = state.selectedSeries,
                        )
                    }
                    item {
                        AttractionHistoryChart(
                            day = state.day,
                            points = state.selectedSeries,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                        )
                    }
                } else if (state.selectedAttractionId != null && !state.isLoading && (state.day == null || state.day.snapshots.isEmpty())) {
                    item {
                        EmptyStatisticsState(
                            selectedName = state.selectedAttractionName ?: state.selectedPark?.name,
                            selectedDate = state.selectedDate,
                        )
                    }
                }

                if (state.monthBuckets.isNotEmpty()) {
                    item {
                        MonthOverviewSection(months = state.monthBuckets)
                    }
                }

                state.day?.attractions?.let { attractions ->
                    if (attractions.isNotEmpty()) {
                        item {
                            Text(
                                "Attraktionen an diesem Tag",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        items(attractions, key = { it.id }) { attraction ->
                            AttractionSummaryRow(
                                attraction = attraction,
                                selected = attraction.id == state.selectedAttractionId,
                                onClick = { onAttractionSelected(attraction.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsControlPanel(
    state: StatisticsUiState,
    onParkSelected: (String) -> Unit,
    onDateSelected: (String) -> Unit,
    onAttractionSelected: (String) -> Unit,
    onParkStatisticsSelected: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DropdownField(
                    label = "Park",
                    value = state.selectedPark?.name ?: state.selectedParkKey ?: "-",
                    modifier = Modifier.weight(1f),
                ) { close ->
                    val indexedParkKeys = state.index.parks.map { it.parkKey }.toSet()
                    val indexedItems = state.index.parks.map { parkIndex ->
                        val park = state.parks.firstOrNull { it.matchesParkKey(parkIndex.parkKey) }
                        parkIndex.parkKey to (park?.name ?: parkIndex.parkKey)
                    }
                    val fallbackItems = state.parks
                        .filter { it.id !in indexedParkKeys && it.uuid !in indexedParkKeys }
                        .map { it.id to it.name }
                    (indexedItems + fallbackItems).forEach { (parkKey, parkName) ->
                        DropdownMenuItem(
                            text = { Text(parkName) },
                            onClick = {
                                onParkSelected(parkKey)
                                close()
                            },
                        )
                    }
                }
                DropdownField(
                    label = "Datum",
                    value = formatDateLabel(state.selectedDate),
                    modifier = Modifier.weight(1f),
                ) { close ->
                    state.availableDates.sortedDescending().forEach { date ->
                        DropdownMenuItem(
                            text = { Text(formatDateLabel(date)) },
                            onClick = {
                                onDateSelected(date)
                                close()
                            },
                        )
                    }
                }
            }

            DropdownField(
                label = "Ansicht",
                value = state.selectedAttractionName ?: "Parkstatistik",
                modifier = Modifier.fillMaxWidth(),
            ) { close ->
                DropdownMenuItem(
                    text = { Text("Parkstatistik") },
                    onClick = {
                        onParkStatisticsSelected()
                        close()
                    },
                )
                state.attractionOptions.forEach { attraction ->
                    DropdownMenuItem(
                        text = { Text(attraction.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                            onAttractionSelected(attraction.id)
                            close()
                        },
                    )
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.availableDates.sortedDescending().take(12).forEach { date ->
                    FilterChip(
                        selected = date == state.selectedDate,
                        onClick = { onDateSelected(date) },
                        label = { Text(formatShortDateLabel(date)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DropdownField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    content: @Composable (() -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Box(modifier = Modifier.width(320.dp)) {
                Column {
                    content { expanded = false }
                }
            }
        }
    }
}

@Composable
private fun SelectedAttractionSummary(
    attraction: AttractionHistorySummary,
    series: List<AttractionChartPoint>,
) {
    val openValues = series.filter { it.value >= 0 }.map { it.value }
    val statusText = series.lastOrNull()?.value?.let(::valueLabel) ?: attraction.lastValue?.let(::valueLabel) ?: "-"
    val averageWaitText = openValues.takeIf { it.isNotEmpty() }
        ?.let { "${String.format(Locale.GERMAN, "%.1f", it.average())} Min." }
        ?: "-"
    val minWaitText = openValues.minOrNull()?.toString() ?: "-"
    val maxWaitText = openValues.maxOrNull()?.toString() ?: "-"
    val closedCount = series.count { it.value < 0 || it.statusCode < 0 }
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(attraction.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("Ø Wartezeit", averageWaitText, Modifier.weight(1f))
                StatTile("Min/Max", "$minWaitText / $maxWaitText", Modifier.weight(1f))
                StatTile("Zuletzt", statusText, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("Messpunkte", series.size.toString(), Modifier.weight(1f))
                StatTile("Offen", openValues.size.toString(), Modifier.weight(1f))
                StatTile("Geschlossen", closedCount.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SelectedParkSummary(
    summary: ParkStatisticsSummary,
    series: List<ParkChartPoint>,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Parkdurchschnitt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("Ø Wartezeit", summary.averageWaitMinutes.minutesText(), Modifier.weight(1f))
                StatTile(
                    "Min/Max",
                    "${summary.minAverageWaitMinutes.minutesText()} / ${summary.maxAverageWaitMinutes.minutesText()}",
                    Modifier.weight(1f),
                )
                StatTile("Zuletzt", summary.latestAverageWaitMinutes.minutesText(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("Messpunkte", series.size.toString(), Modifier.weight(1f))
                StatTile("Offen zuletzt", summary.latestOpenAttractionCount.toString(), Modifier.weight(1f))
                StatTile("Attraktionen", series.maxOfOrNull { it.openAttractionCount }?.toString() ?: "-", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AttractionHistoryChart(
    day: de.wartezeiten.app.domain.model.AttractionHistoryDay?,
    points: List<AttractionChartPoint>,
    modifier: Modifier = Modifier,
) {
    val allWaitPoints = remember(points) {
        points
            .filter { it.value >= 0 }
            .sortedBy { it.capturedAtMillis }
    }
    if (allWaitPoints.size < 2) {
        OutlinedCard(modifier = modifier, shape = RoundedCornerShape(14.dp)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Noch zu wenige Messpunkte", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val sortedPoints = remember(points) { points.sortedBy { it.capturedAtMillis } }
    val axisBounds = remember(day, sortedPoints) {
        calculateAxisBounds(
            timestamps = sortedPoints.map { it.capturedAtMillis },
            openFrom = day?.openFrom,
            closedFrom = day?.closedFrom,
            firstOpenAtMillis = allWaitPoints.firstOrNull()?.capturedAtMillis,
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
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()) }

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
                        color = Color(0xFF1565C0),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )
                    waitPoints.forEach { point ->
                        val x = xFor(point.capturedAtMillis)
                        val y = yFor(point.value.toFloat())
                        drawCircle(
                            color = Color(0xFF1565C0),
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
                Text("${timeFormatter.format(Instant.ofEpochMilli(minTime))} Uhr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${timeFormatter.format(Instant.ofEpochMilli(midTime))} Uhr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${timeFormatter.format(Instant.ofEpochMilli(maxTime))} Uhr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusLegend()
        }
    }
}

@Composable
private fun StatusLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusLegendItem("Geschlossen", statusColor(-1))
        StatusLegendItem("Wetter", statusColor(-2))
        StatusLegendItem("Wartung", statusColor(-3))
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
    points: List<ParkChartPoint>,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) {
        OutlinedCard(modifier = modifier, shape = RoundedCornerShape(14.dp)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Noch zu wenige Messpunkte", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val sortedPoints = remember(points) { points.sortedBy { it.capturedAtMillis } }
    val axisBounds = remember(day, sortedPoints) {
        calculateAxisBounds(
            timestamps = sortedPoints.map { it.capturedAtMillis },
            openFrom = day?.openFrom,
            closedFrom = day?.closedFrom,
            firstOpenAtMillis = sortedPoints.firstOrNull()?.capturedAtMillis,
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
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()) }

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
                        color = Color(0xFF2E7D32),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )
                    visiblePoints.forEach { point ->
                        val x = xFor(point.capturedAtMillis)
                        val y = yFor(point.averageWaitMinutes)
                        drawCircle(
                            color = Color(0xFF2E7D32),
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
                Text("${timeFormatter.format(Instant.ofEpochMilli(minTime))} Uhr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${timeFormatter.format(Instant.ofEpochMilli(midTime))} Uhr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${timeFormatter.format(Instant.ofEpochMilli(maxTime))} Uhr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Durchschnittliche Wartezeit aller geöffneten Attraktionen",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MonthOverviewSection(months: List<StatisticsMonthBucket>) {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Monatsübersicht", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                months.forEach { month ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(formatMonthLabel(month.month), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text("${month.dayCount} Tage", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttractionSummaryRow(
    attraction: AttractionHistorySummary,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(attraction.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Ø ${attraction.averageWaitMinutes?.let { String.format(Locale.GERMAN, "%.1f", it) } ?: "-"} Min. · ${attraction.sampleCount} Messpunkte",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(valueLabel(attraction.lastValue), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRetry) { Text("Neu laden") }
        }
    }
}

@Composable
private fun EmptyStatisticsState(
    selectedName: String?,
    selectedDate: String,
) {
    val isToday = selectedDate == LocalDate.now().toString()
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = selectedName ?: "Keine Attraktion ausgewählt",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (isToday) {
                    "Für heute wurden noch keine Messpunkte gesammelt. Der Park ist möglicherweise noch nicht geöffnet oder bleibt heute geschlossen."
                } else {
                    "Für diese Auswahl liegen keine zentralen Tagesdaten vor. Wurde der Park an diesem Tag nicht geöffnet, gibt es keinen Verlauf."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun valueLabel(value: Int?): String {
    return when (value) {
        null -> "-"
        -1 -> "geschlossen"
        -2 -> "Wetter"
        -3 -> "Wartung"
        -4 -> "unbekannt"
        else -> "$value Min."
    }
}

private fun statusColor(statusCode: Int): Color {
    return when (statusCode) {
        -3 -> Color(0xFF9E7D00)
        -2 -> Color(0xFFEF6C00)
        -1 -> Color(0xFFC62828)
        else -> Color(0xFF616161)
    }
}

private fun Float?.minutesText(): String {
    return this?.let { "${String.format(Locale.GERMAN, "%.1f", it)} Min." } ?: "-"
}

private fun calculateAxisBounds(
    timestamps: List<Long>,
    openFrom: String?,
    closedFrom: String?,
    firstOpenAtMillis: Long?,
): Pair<Long, Long> {
    val firstSample = timestamps.minOrNull() ?: 0L
    val lastSample = timestamps.maxOrNull() ?: (firstSample + 1)
    val parkOpen = openFrom?.parseInstantMillis()
    val parkClose = closedFrom?.parseInstantMillis()
    val start = when {
        parkOpen != null && firstOpenAtMillis != null -> minOf(parkOpen, firstOpenAtMillis)
        parkOpen != null -> parkOpen
        firstOpenAtMillis != null -> firstOpenAtMillis
        else -> firstSample
    }
    val end = parkClose ?: lastSample
    return start.coerceAtMost(lastSample) to end.coerceAtLeast(start + 1)
}

private fun String.parseInstantMillis(): Long? {
    return runCatching { OffsetDateTime.parse(this).toInstant().toEpochMilli() }
        .getOrElse { runCatching { Instant.parse(this).toEpochMilli() }.getOrNull() }
}

private fun calculateNiceYAxisMax(maxValue: Int): Int {
    val step = calculateNiceTickStep(maxValue.coerceAtLeast(1))
    return ceil(maxValue.coerceAtLeast(step).toFloat() / step).toInt()
        .times(step)
        .coerceAtLeast(step * 2)
}

private fun calculateNiceTickStep(maxValue: Int): Int {
    return when {
        maxValue <= 10 -> 5
        maxValue <= 40 -> 10
        maxValue <= 80 -> 20
        maxValue <= 200 -> 50
        else -> 100
    }
}

private fun formatDateLabel(value: String): String {
    return runCatching {
        LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN))
    }.getOrElse { value }
}

private fun formatShortDateLabel(value: String): String {
    return runCatching {
        LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMAN))
    }.getOrElse { value }
}

private fun formatMonthLabel(value: String): String {
    return runCatching {
        val month = YearMonth.parse(value)
        val name = month.month.getDisplayName(TextStyle.SHORT, Locale.GERMAN)
        "$name ${month.year}"
    }.getOrElse { value }
}

private fun Park.matchesParkKey(parkKey: String): Boolean {
    val normalizedKey = parkKey.normalizedParkKey()
    return id == parkKey ||
            uuid == parkKey ||
            id.normalizedParkKey() == normalizedKey ||
            uuid.normalizedParkKey() == normalizedKey ||
            name.normalizedParkKey() == normalizedKey
}

private fun String.normalizedParkKey(): String {
    return lowercase()
        .replace("ä", "ae")
        .replace("ö", "oe")
        .replace("ü", "ue")
        .replace("ß", "ss")
        .filter { it.isLetterOrDigit() }
}
