package de.wartezeiten.app.ui.statistics

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
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
import de.wartezeiten.app.domain.model.AttractionHistorySummary
import de.wartezeiten.app.domain.model.Park
import kotlinx.coroutines.launch
import java.io.File
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
    val context = LocalContext.current
    val rootView = LocalView.current
    val scope = rememberCoroutineScope()

    Scaffold(
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

                val deviceToday = LocalDate.now().toString()
                val isTodaySelected = state.selectedDate == deviceToday || state.selectedDate == "Heute" || state.selectedDate == "Today"
                val isFallback = state.isDataFallbackToPreviousDay || (state.day != null && state.day.date != deviceToday && isTodaySelected)
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
                                        nl = "Nog geen centrale metingen beschikbaar voor heute. Gegevens van de vorige dag worden weergegeven.",
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
                            date = state.selectedDate,
                            isSpecificAttraction = state.selectedAttractionId != null,
                        )
                    }
                } else {
                    item {
                        if (state.selectedAttractionId == null) {
                            state.parkStatistics?.let { summary ->
                                SelectedParkSummary(summary, state.parkSeries)
                            }
                        } else if (state.selectedAttractionId != null && !state.isLoading && (state.day == null || state.day.snapshots.isEmpty())) {
                            EmptyStatisticsState(
                                parkName = state.selectedPark?.name,
                                date = state.selectedDate,
                                isSpecificAttraction = true,
                            )
                        } else {
                            state.selectedAttraction?.let { summary ->
                                SelectedAttractionSummary(summary, state.selectedSeries)
                            }
                        }
                    }

                    if (state.selectedAttractionId == null) {
                        item {
                            ParkAverageWaitChart(
                                day = state.day,
                                selectedDate = state.selectedDate,
                                points = state.parkSeries,
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                            )
                        }
                    }

                    item {
                        MonthOverviewSection(buckets = state.monthBuckets)
                    }

                    if (state.selectedAttractionId == null && state.day != null) {
                        items(state.day.attractions.sortedByDescending { it.averageWaitMinutes }) { attraction ->
                            AttractionSummaryRow(
                                summary = attraction,
                                selected = attraction.id == state.selectedAttractionId,
                                onClick = { onAttractionSelected(attraction.id) }
                            )
                        }
                    }
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownField(
                label = localized(state.language, de = "Park", en = "Park", fr = "Parc", nl = "Park"),
                value = state.selectedPark?.name ?: state.selectedParkKey ?: "-",
                modifier = Modifier.weight(1.3f)
            ) { onDismiss ->
                state.parks.forEach { park ->
                    DropdownMenuItem(
                        text = { Text(park.name) },
                        onClick = {
                            onParkSelected(park.id)
                            onDismiss()
                        }
                    )
                }
            }

            DropdownField(
                label = localized(state.language, de = "Datum", en = "Date", fr = "Date", nl = "Datum"),
                value = state.selectedDate,
                modifier = Modifier.weight(1f)
            ) { onDismiss ->
                state.availableDates.reversed().forEach { date ->
                    DropdownMenuItem(
                        text = { Text(formatDateLabel(date)) },
                        onClick = {
                            onDateSelected(date)
                            onDismiss()
                        }
                    )
                }
            }
        }

        DropdownField(
            label = localized(state.language, de = "Attraktion", en = "Attraction", fr = "Attraction", nl = "Attractie"),
            value = state.selectedAttraction?.name ?: localized(state.language, de = "Durchschnitt (Park)", en = "Average (Park)", fr = "Moyenne (Parc)", nl = "Gemiddelde (Park)"),
            modifier = Modifier.fillMaxWidth()
        ) { onDismiss ->
            DropdownMenuItem(
                text = { Text(localized(state.language, de = "Auslastung (gesamt)", en = "Total crowd", fr = "Fréquentation totale", nl = "Totale drukte")) },
                onClick = {
                    onParkStatisticsSelected()
                    onDismiss()
                }
            )
            state.attractionOptions.forEach { attraction ->
                DropdownMenuItem(
                    text = { Text(attraction.name) },
                    onClick = {
                        onAttractionSelected(attraction.id)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun DropdownField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    content: @Composable (onDismiss: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedCard(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
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
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            content { expanded = false }
        }
    }
}

@Composable
private fun SelectedAttractionSummary(
    summary: AttractionHistorySummary,
    points: List<AttractionChartPoint>,
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
                    label = "Durchschnitt",
                    value = summary.averageWaitMinutes.minutesText(),
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Min / Max",
                    value = "${summary.minWaitMinutes ?: "-"} / ${summary.maxWaitMinutes ?: "-"}",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Messpunkte",
                    value = "${summary.sampleCount ?: 0}",
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
    points: List<ParkChartPoint>,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Park-Durchschnitt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    label = "Ø Wartezeit",
                    value = summary.averageWaitMinutes.minutesText(),
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Messpunkte",
                    value = "${summary.sampleCount}",
                    modifier = Modifier.weight(0.8f)
                )
                StatTile(
                    label = "Offen",
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
    selectedDate: String,
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
private fun MonthOverviewSection(buckets: List<StatisticsMonthBucket>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Monats-Überblick", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            buckets.forEach { bucket ->
                OutlinedCard(shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatMonthLabel(bucket.month), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${bucket.dayCount} Tage", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
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
private fun EmptyStatisticsState(parkName: String?, date: String, isSpecificAttraction: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isSpecificAttraction) {
                "Für diese Attraktion liegen an diesem Tag keine Messpunkte vor."
            } else {
                "Für diesen Tag liegen keine zentralen Messpunkte für ${parkName ?: "den Park"} vor."
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

private fun formatMonthLabel(monthKey: String): String {
    return runCatching {
        val ym = YearMonth.parse(monthKey)
        ym.month.getDisplayName(TextStyle.FULL, Locale.GERMAN) + " " + ym.year
    }.getOrElse { monthKey }
}

private fun Park.matchesParkKey(parkKey: String?): Boolean {
    if (parkKey == null) return false
    val normKey = parkKey.lowercase().trim()
    return id.lowercase().trim() == normKey || uuid.lowercase().trim() == normKey
}
