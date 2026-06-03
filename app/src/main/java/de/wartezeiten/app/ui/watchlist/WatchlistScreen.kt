package de.wartezeiten.app.ui.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.wartezeiten.app.data.local.entity.WatchlistEntity
import de.wartezeiten.app.ui.components.AttributionFooter
import de.wartezeiten.app.ui.waitingtimes.WatchlistAlertWithParkName
import de.wartezeiten.app.ui.waitingtimes.WatchlistViewModel
import de.wartezeiten.app.ui.waitingtimes.label
import de.wartezeiten.app.ui.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistRoute(
    onBackClick: () -> Unit,
    viewModel: WatchlistViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val watchlistItems by viewModel.watchlistItems.collectAsState(initial = emptyList())
    val settingsState by settingsViewModel.uiState.collectAsState()
    val language = settingsState.language
    val groupedWatchlist = watchlistItems.groupBy { it.parkName ?: it.alert.parkKey }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watchlist") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = if (language == "en") "Back" else "Zurück")
                    }
                }
            )
        },
        bottomBar = {
            AttributionFooter(language = language)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    if (language == "en") "Your notifications" else "Deine Benachrichtigungen",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
            }

            if (watchlistItems.isEmpty()) {
                item {
                    Text(
                        text = if (language == "en") {
                            "Your active notifications appear here. Add them from a park or attraction view."
                        } else {
                            "Hier werden deine aktiven Benachrichtigungen angezeigt. Füge sie aus der Park- oder Attraktionsansicht hinzu."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                groupedWatchlist.forEach { (parkKey, alerts) ->
                    item {
                        Text(
                            text = "Park: $parkKey",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    items(alerts) { alert ->
                        WatchlistAlertCard(alert = alert, language = language, onDelete = viewModel::deleteAlert)
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchlistAlertCard(
    alert: WatchlistAlertWithParkName,
    language: String,
    onDelete: (WatchlistEntity) -> Unit
) {
    val item = alert.alert
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.type.label(language), style = MaterialTheme.typography.titleMedium)
                    if (item.attractionId != null) {
                        Text(
                            text = if (language == "en") {
                                "Attraction: ${alert.attractionName ?: item.attractionId}"
                            } else {
                                "Attraktion: ${alert.attractionName ?: item.attractionId}"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (item.type == de.wartezeiten.app.data.local.entity.WatchlistType.WAIT_TIME_BELOW ||
                        item.type == de.wartezeiten.app.data.local.entity.WatchlistType.WAIT_TIME_ABOVE ||
                        item.type == de.wartezeiten.app.data.local.entity.WatchlistType.CROWD_LEVEL_BELOW ||
                        item.type == de.wartezeiten.app.data.local.entity.WatchlistType.CROWD_LEVEL_ABOVE
                    ) {
                        Text(
                            text = if (language == "en") "Threshold: ${item.threshold}" else "Schwellenwert: ${item.threshold}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (alert.parkName != null) {
                        Text(
                            text = "Park: ${alert.parkName}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                IconButton(onClick = { onDelete(item) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = if (language == "en") "Delete" else "Löschen"
                    )
                }
            }
        }
    }
}
