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
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.wartezeiten.app.core.i18n.localized
import de.wartezeiten.app.domain.model.Park
import de.wartezeiten.app.ui.components.AttributionFooter
import java.time.Instant
import java.time.LocalTime
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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.refreshTrigger, state.isRefreshing) {
        if (state.refreshTrigger > 0 && !state.isRefreshing) {
            val message = state.refreshError ?: run {
                val updatedAt = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                localized(
                    state.language,
                    de = "Vergleich aktualisiert um $updatedAt Uhr",
                    en = "Comparison updated at $updatedAt",
                    fr = "Comparaison mise à jour à $updatedAt",
                    nl = "Vergelijking bijgewerkt om $updatedAt",
                )
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            localized(state.language, de = "Parkvergleich", en = "Park comparison", fr = "Comparaison des parcs", nl = "Parkvergelijking"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            localized(state.language, de = "Parks direkt vergleichen", en = "Compare parks side by side", fr = "Comparez les parcs côte à côte", nl = "Vergelijk parken naast elkaar"),
                            style = MaterialTheme.typography.labelMedium,
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
                    if (state.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(24.dp),
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        IconButton(onClick = onRefreshClick) {
                            Icon(Icons.Default.Refresh, contentDescription = localized(state.language, de = "Vergleich aktualisieren", en = "Refresh comparison", fr = "Actualiser la comparaison", nl = "Vergelijking vernieuwen"))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            AttributionFooter(language = state.language)
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                RefreshSnackbar(data = data)
            }
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
                        isLoading = state.isInitialLoading,
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
                if (state.isInitialLoading) {
                    item {
                        CompareLoadingState(language = state.language)
                    }
                } else if (state.comparisonParks.size < 2) {
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
            text = localized(
                language,
                de = "Wähle zwei bis vier Parks und vergleiche die aktuelle Wartezeitdaten miteinander.",
                en = "Choose two to four theme parks to compare the current wait-time data.",
                fr = "Choisissez deux à quatre parcs pour comparer les temps d'attente actuels.",
                nl = "Kies twee tot vier pretparken om de actuele wachttijden te vergelijken.",
            ),
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
    isLoading: Boolean,
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
                localized(language, de = "Ausgewählte Parks", en = "Selected parks", fr = "Parcs sélectionnés", nl = "Geselecteerde parken"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                localized(
                    language,
                    de = "${selectedParkIds.size} ausgewählt · $totalParkCount verfügbar",
                    en = "${selectedParkIds.size} selected · $totalParkCount available",
                    fr = "${selectedParkIds.size} sélectionnés · $totalParkCount disponibles",
                    nl = "${selectedParkIds.size} geselecteerd · $totalParkCount beschikbaar",
                ),
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
                        Icon(Icons.Default.Clear, contentDescription = localized(language, de = "Suche löschen", en = "Clear search", fr = "Effacer la recherche", nl = "Zoekopdracht wissen"))
                    }
                }
            } else {
                null
            },
            placeholder = {
                Text(localized(language, de = "Nach Park oder Land suchen", en = "Search by park or country", fr = "Rechercher par parc ou pays", nl = "Zoeken op park of land"))
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
            ),
        )

        Text(
            text = when {
                selectedParkIds.size >= 4 -> localized(
                    language,
                    de = "Bis zu vier Parks können gleichzeitig verglichen werden.",
                    en = "Up to four parks can be compared at once.",
                    fr = "Vous pouvez comparer jusqu'à quatre parcs à la fois.",
                    nl = "Je kunt maximaal vier parken tegelijk vergelijken.",
                )
                selectedParkIds.size < 2 -> localized(
                    language,
                    de = "Für einen Vergleich müssen mindestens zwei Parks ausgewählt sein.",
                    en = "Select at least two parks for a comparison.",
                    fr = "Sélectionnez au moins deux parcs pour une comparaison.",
                    nl = "Selecteer minstens twee parken om te vergelijken.",
                )
                else -> localized(
                    language,
                    de = "Parks aus der Liste hinzufügen oder oben aus der Auswahl entfernen.",
                    en = "Add parks from the list or remove selected parks above.",
                    fr = "Ajoutez des parcs depuis la liste ou retirez-en ci-dessus.",
                    nl = "Voeg parken toe vanuit de lijst of verwijder geselecteerde parken hierboven.",
                )
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
            if (addableParks.isEmpty() && isLoading) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        text = localized(
                            language,
                            de = "Parks werden geladen",
                            en = "Loading parks",
                            fr = "Chargement des parcs",
                            nl = "Parken worden geladen",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (addableParks.isEmpty()) {
                Text(
                    text = localized(language, de = "Keine passenden Parks gefunden", en = "No matching parks found", fr = "Aucun parc correspondant trouvé", nl = "Geen overeenkomende parken gevonden"),
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
                label = localized(language, de = "Ø Wartezeit", en = "Avg wait", fr = "Attente moy.", nl = "Gem. wachttijd"),
                value = item.averageWaitMinutes?.let { "${it.toInt()} Min." } ?: "-",
            )
            MetricLine(
                label = localized(language, de = "Höchste Zeit", en = "Peak wait", fr = "Attente max.", nl = "Piekwachttijd"),
                value = item.maxWaitMinutes?.let { "$it Min." } ?: "-",
            )
            MetricLine(
                label = localized(language, de = "Offen", en = "Open", fr = "Ouvertes", nl = "Open"),
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
                text = localized(
                    language,
                    de = "Einige Vergleichsdaten konnten nicht aktualisiert werden: $message",
                    en = "Some comparison data could not be refreshed: $message",
                    fr = "Certaines données de comparaison n'ont pas pu être actualisées : $message",
                    nl = "Sommige vergelijkingsgegevens konden niet worden vernieuwd: $message",
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CompareLoadingState(language: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localized(
                        language,
                        de = "Vergleich wird vorbereitet",
                        en = "Preparing comparison",
                        fr = "Préparation de la comparaison",
                        nl = "Vergelijking wordt voorbereid",
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = localized(
                        language,
                        de = "Parks und aktuelle Wartezeitdaten werden geladen.",
                        en = "Parks and current wait-time data are loading.",
                        fr = "Les parcs et les temps d'attente actuels sont en cours de chargement.",
                        nl = "Parken en actuele wachttijden worden geladen.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
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
            localized(
                language,
                de = "Für den Vergleich müssen mindestens zwei Parks ausgewählt sein.",
                en = "At least two parks must be selected for the comparison.",
                fr = "Au moins deux parcs doivent être sélectionnés pour la comparaison.",
                nl = "Er moeten minstens twee parken geselecteerd zijn voor de vergelijking.",
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun ParkCompareSort.label(language: String): String {
    return when (this) {
        ParkCompareSort.BestNow -> localized(language, de = "Gesamt", en = "Overall", fr = "Global", nl = "Algemeen")
        ParkCompareSort.LowestAverageWait -> localized(language, de = "Wartezeit", en = "Wait time", fr = "Temps d'attente", nl = "Wachttijd")
        ParkCompareSort.MostOpenAttractions -> localized(language, de = "Offene Attraktionen", en = "Open attractions", fr = "Attractions ouvertes", nl = "Open attracties")
        ParkCompareSort.Name -> "Name A-Z"
    }
}

private fun ParkCompareItem.headline(language: String): String {
    return when {
        isBestChoice -> localized(language, de = "Gerade die beste Wahl", en = "Best choice right now", fr = "Meilleur choix actuel", nl = "Nu de beste keuze")
        !isOpen -> localized(language, de = "Vermutlich geschlossen", en = "Probably closed", fr = "Probablement fermé", nl = "Waarschijnlijk gesloten")
        totalAttractions == 0 -> localized(language, de = "Keine aktuellen Daten", en = "No current data", fr = "Aucune donnée actuelle", nl = "Geen actuele gegevens")
        averageWaitMinutes != null && averageWaitMinutes <= 20f -> localized(language, de = "Niedrige Wartezeiten", en = "Low waits", fr = "Faible attente", nl = "Korte wachttijden")
        averageWaitMinutes != null && averageWaitMinutes >= 45f -> localized(language, de = "Hohe Wartezeiten", en = "High waits", fr = "Forte attente", nl = "Lange wachttijden")
        else -> localized(language, de = "Solide Option", en = "Balanced option", fr = "Option équilibrée", nl = "Evenwichtige optie")
    }
}

private fun ParkCompareItem.lastUpdatedText(language: String): String {
    val updated = lastUpdatedMillis ?: return localized(language, de = "Noch keine lokalen Daten", en = "No local data yet", fr = "Pas encore de données locales", nl = "Nog geen lokale gegevens")
    val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
    val time = Instant.ofEpochMilli(updated).atZone(ZoneId.systemDefault()).format(formatter)
    return localized(language, de = "Stand $time Uhr", en = "Updated $time", fr = "Mis à jour à $time", nl = "Bijgewerkt om $time")
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
