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
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

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
                    )
                }

                state.errorMessage?.let { message ->
                    item { ErrorCard(message = message, onRetry = onRefreshClick) }
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
                            points = state.selectedSeries,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                        )
                    }
                } else if (!state.isLoading) {
                    item { EmptyStatisticsState() }
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
                ) {
                    state.index.parks.forEach { parkIndex ->
                        val park = state.parks.firstOrNull { it.id == parkIndex.parkKey || it.uuid == parkIndex.parkKey }
                        DropdownMenuItem(
                            text = { Text(park?.name ?: parkIndex.parkKey) },
                            onClick = { onParkSelected(parkIndex.parkKey) },
                        )
                    }
                }
                DropdownField(
                    label = "Datum",
                    value = formatDateLabel(state.selectedDate),
                    modifier = Modifier.weight(1f),
                ) {
                    state.availableDates.sortedDescending().forEach { date ->
                        DropdownMenuItem(
                            text = { Text(formatDateLabel(date)) },
                            onClick = { onDateSelected(date) },
                        )
                    }
                }
            }

            val attractions = state.day?.attractions.orEmpty()
            DropdownField(
                label = "Attraktion",
                value = state.selectedAttraction?.name ?: "-",
                modifier = Modifier.fillMaxWidth(),
            ) {
                attractions.forEach { attraction ->
                    DropdownMenuItem(
                        text = { Text(attraction.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { onAttractionSelected(attraction.id) },
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
    content: @Composable () -> Unit,
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
                    content()
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
    val statusText = attraction.lastValue?.let(::valueLabel) ?: "-"
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(attraction.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("Ø Wartezeit", attraction.averageWaitMinutes?.let { "${String.format(Locale.GERMAN, "%.1f", it)} Min." } ?: "-", Modifier.weight(1f))
                StatTile("Min/Max", "${attraction.minWaitMinutes ?: "-"} / ${attraction.maxWaitMinutes ?: "-"}", Modifier.weight(1f))
                StatTile("Zuletzt", statusText, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("Messpunkte", series.size.toString(), Modifier.weight(1f))
                StatTile("Offen", "${attraction.openSampleCount}", Modifier.weight(1f))
                StatTile("Geschlossen", "${attraction.closedSampleCount}", Modifier.weight(1f))
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
private fun AttractionHistoryChart(
    points: List<AttractionChartPoint>,
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
    val minTime = sortedPoints.first().capturedAtMillis
    val maxTime = sortedPoints.last().capturedAtMillis.coerceAtLeast(minTime + 1)
    val midTime = minTime + ((maxTime - minTime) / 2)
    val maxWait = sortedPoints.maxOf { it.value.coerceAtLeast(0) }
    val yMax = ceil(maxWait / 10f).toInt().times(10).coerceAtLeast(10)
    val yMin = sortedPoints.minOf { it.value }.coerceAtMost(0)
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
                    Text("$yMax", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(yMin.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    val range = (yMax - yMin).coerceAtLeast(1).toFloat()

                    repeat(4) { index ->
                        val y = verticalPadding + (height * index / 3f)
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.18f),
                            start = Offset(horizontalPadding, y),
                            end = Offset(horizontalPadding + width, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }

                    val path = Path()
                    sortedPoints.forEachIndexed { index, point ->
                        val x = horizontalPadding + ((point.capturedAtMillis - minTime).toFloat() / (maxTime - minTime).toFloat()) * width
                        val y = verticalPadding + (1f - ((point.value - yMin) / range).coerceIn(0f, 1f)) * height
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFF1565C0),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )
                    sortedPoints.forEach { point ->
                        val x = horizontalPadding + ((point.capturedAtMillis - minTime).toFloat() / (maxTime - minTime).toFloat()) * width
                        val y = verticalPadding + (1f - ((point.value - yMin) / range).coerceIn(0f, 1f)) * height
                        drawCircle(
                            color = if (point.value >= 0) Color(0xFF1565C0) else Color(0xFFC62828),
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
                "-1 geschlossen, -2 wetterbedingt, -3 Wartung",
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
private fun EmptyStatisticsState() {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Für diese Auswahl liegen noch keine zentralen Statistikdaten vor.")
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
