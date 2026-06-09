package de.wartezeiten.app.ui.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.wartezeiten.app.domain.model.Park
import de.wartezeiten.app.ui.components.AttributionBanner
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ParkCompareRoute(
    onBackClick: () -> Unit,
    onParkClick: (Park) -> Unit,
    viewModel: ParkCompareViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ParkCompareScreen(
        state = state,
        onBackClick = onBackClick,
        onRefreshClick = viewModel::refreshSelected,
        onParkSearchChange = viewModel::setParkSearchQuery,
        onSortChange = viewModel::setSort,
        onTogglePark = viewModel::togglePark,
        onParkClick = onParkClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkCompareScreen(
    state: ParkCompareUiState,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onParkSearchChange: (String) -> Unit,
    onSortChange: (ParkCompareSort) -> Unit,
    onTogglePark: (Park) -> Unit,
    onParkClick: (Park) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (state.language == "en") "Park comparison" else "Parkvergleich",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (state.language == "en") "Compare parks side by side" else "Parks direkt vergleichen",
                            style = MaterialTheme.typography.labelMedium,
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
                    if (state.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(24.dp),
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        IconButton(onClick = onRefreshClick) {
                            Icon(Icons.Default.Refresh, contentDescription = if (state.language == "en") "Refresh comparison" else "Vergleich aktualisieren")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            AttributionBanner(language = state.language)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            state.errorMessage?.let { error ->
                ErrorStrip(message = error, language = state.language)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    CompareIntro(language = state.language)
                }
                item {
                    ParkPicker(
                        parks = state.availableParks,
                        selectedParks = state.selectedParks,
                        selectedParkIds = state.selectedParkIds,
                        query = state.parkSearchQuery,
                        totalParkCount = state.totalParkCount,
                        language = state.language,
                        onQueryChange = onParkSearchChange,
                        onTogglePark = onTogglePark,
                    )
                }
                item {
                    SortChips(
                        sort = state.sort,
                        language = state.language,
                        onSortChange = onSortChange,
                    )
                }
                if (state.comparisonParks.size < 2) {
                    item {
                        EmptyComparison(language = state.language)
                    }
                } else {
                    item {
                        ComparisonRow(
                            items = state.comparisonParks,
                            language = state.language,
                            onParkClick = onParkClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareIntro(language: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = if (language == "en") {
                "Choose two to four theme parks to compare the current wait-time data."
            } else {
                "Wähle zwei bis vier Parks und vergleiche die aktuelle Wartezeitdaten miteinander."
            },
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ParkPicker(
    parks: List<Park>,
    selectedParks: List<Park>,
    selectedParkIds: List<String>,
    query: String,
    totalParkCount: Int,
    language: String,
    onQueryChange: (String) -> Unit,
    onTogglePark: (Park) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                if (language == "en") "Selected parks" else "Ausgewählte Parks",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (language == "en") {
                    "${selectedParkIds.size} selected · $totalParkCount available"
                } else {
                    "${selectedParkIds.size} ausgewählt · $totalParkCount verfügbar"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (selectedParks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                selectedParks.forEach { park ->
                    FilterChip(
                        selected = true,
                        onClick = { onTogglePark(park) },
                        label = { Text(park.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        shape = RoundedCornerShape(10.dp),
                    )
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = if (query.isNotBlank()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = if (language == "en") "Clear search" else "Suche löschen")
                    }
                }
            } else {
                null
            },
            placeholder = {
                Text(if (language == "en") "Search by park or country" else "Nach Park oder Land suchen")
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
            ),
        )

        Text(
            text = when {
                selectedParkIds.size >= 4 -> if (language == "en") "Up to four parks can be compared at once." else "Bis zu vier Parks können gleichzeitig verglichen werden."
                selectedParkIds.size < 2 -> if (language == "en") "Select at least two parks for a comparison." else "Für einen Vergleich müssen mindestens zwei Parks ausgewählt sein."
                else -> if (language == "en") "Add parks from the list or remove selected parks above." else "Parks aus der Liste hinzufügen oder oben aus der Auswahl entfernen."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val addableParks = parks.filter { park -> park.id !in selectedParkIds && park.uuid !in selectedParkIds }
            addableParks.forEach { park ->
                val selected = park.id in selectedParkIds || park.uuid in selectedParkIds
                val enabled = selected || selectedParkIds.size < 4
                FilterChip(
                    selected = selected,
                    enabled = enabled,
                    onClick = { onTogglePark(park) },
                    label = {
                        Text(
                            text = park.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    },
                    shape = RoundedCornerShape(10.dp),
                )
            }
            if (addableParks.isEmpty()) {
                Text(
                    text = if (language == "en") "No matching parks found" else "Keine passenden Parks gefunden",
                    modifier = Modifier.padding(vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SortChips(
    sort: ParkCompareSort,
    language: String,
    onSortChange: (ParkCompareSort) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ParkCompareSort.entries.forEach { option ->
            FilterChip(
                selected = sort == option,
                onClick = { onSortChange(option) },
                label = { Text(option.label(language), maxLines = 1) },
                shape = RoundedCornerShape(10.dp),
            )
        }
    }
}

@Composable
private fun ComparisonRow(
    items: List<ParkCompareItem>,
    language: String,
    onParkClick: (Park) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.park.id }) { item ->
            CompareParkCard(
                item = item,
                language = language,
                onClick = { onParkClick(item.park) },
            )
        }
    }
}

@Composable
private fun CompareParkCard(
    item: ParkCompareItem,
    language: String,
    onClick: () -> Unit,
) {
    val statusColor = when {
        item.isBestChoice -> Color(0xFF2E7D32)
        item.isOpen -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    OutlinedCard(
        modifier = Modifier
            .width(228.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (item.isBestChoice) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(statusColor.copy(alpha = 0.55f)),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                Text(
                    text = item.park.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = item.headline(language),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            MetricLine(
                label = if (language == "en") "Avg wait" else "Ø Wartezeit",
                value = item.averageWaitMinutes?.let { "${it.toInt()} Min." } ?: "-",
            )
            MetricLine(
                label = if (language == "en") "Peak wait" else "Höchste Zeit",
                value = item.maxWaitMinutes?.let { "$it Min." } ?: "-",
            )
            MetricLine(
                label = if (language == "en") "Open" else "Offen",
                value = "${item.openAttractions}/${item.totalAttractions}",
            )
            LinearProgressIndicator(
                progress = {
                    if (item.totalAttractions > 0) {
                        item.openAttractions.toFloat() / item.totalAttractions.toFloat()
                    } else {
                        0f
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Text(
                text = item.lastUpdatedText(language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MetricLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ErrorStrip(
    message: String,
    language: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.Warning, contentDescription = null)
            Text(
                text = if (language == "en") "Some comparison data could not be refreshed: $message" else "Einige Vergleichsdaten konnten nicht aktualisiert werden: $message",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EmptyComparison(language: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (language == "en") {
                "At least two parks must be selected for the comparison."
            } else {
                "Für den Vergleich müssen mindestens zwei Parks ausgewählt sein."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun ParkCompareSort.label(language: String): String {
    return when (this) {
        ParkCompareSort.BestNow -> if (language == "en") "Overall" else "Gesamt"
        ParkCompareSort.LowestAverageWait -> if (language == "en") "Wait time" else "Wartezeit"
        ParkCompareSort.MostOpenAttractions -> if (language == "en") "Open attractions" else "Offene Attraktionen"
        ParkCompareSort.Name -> "Name A-Z"
    }
}

private fun ParkCompareItem.headline(language: String): String {
    return when {
        isBestChoice -> if (language == "en") "Best choice right now" else "Gerade die beste Wahl"
        !isOpen -> if (language == "en") "Probably closed" else "Vermutlich geschlossen"
        totalAttractions == 0 -> if (language == "en") "No current data" else "Keine aktuellen Daten"
        averageWaitMinutes != null && averageWaitMinutes <= 20f -> if (language == "en") "Low waits" else "Niedrige Wartezeiten"
        averageWaitMinutes != null && averageWaitMinutes >= 45f -> if (language == "en") "High waits" else "Hohe Wartezeiten"
        else -> if (language == "en") "Balanced option" else "Solide Option"
    }
}

private fun ParkCompareItem.lastUpdatedText(language: String): String {
    val updated = lastUpdatedMillis ?: return if (language == "en") "No local data yet" else "Noch keine lokalen Daten"
    val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
    val time = Instant.ofEpochMilli(updated).atZone(ZoneId.systemDefault()).format(formatter)
    return if (language == "en") "Updated $time" else "Stand $time Uhr"
}
