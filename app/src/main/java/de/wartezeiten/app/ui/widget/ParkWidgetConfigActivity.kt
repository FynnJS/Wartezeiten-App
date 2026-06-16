package de.wartezeiten.app.ui.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.wartezeiten.app.core.network.ApiResult
import de.wartezeiten.app.data.local.PreferencesDataSource
import de.wartezeiten.app.domain.model.AttractionStatus
import de.wartezeiten.app.domain.model.Park
import de.wartezeiten.app.domain.model.WaitingTime
import de.wartezeiten.app.domain.repository.WartezeitenRepository
import de.wartezeiten.app.ui.theme.WartezeitenTheme
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ParkWidgetConfigActivity : ComponentActivity() {
    @Inject
    lateinit var repository: WartezeitenRepository

    @Inject
    lateinit var preferencesDataSource: PreferencesDataSource

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            WartezeitenTheme {
                ParkWidgetConfigScreen(
                    repository = repository,
                    preferencesDataSource = preferencesDataSource,
                    onCancel = { finish() },
                    onSave = { park, attractionIds ->
                        saveWidgetConfiguration(park, attractionIds)
                    },
                )
            }
        }
    }

    private fun saveWidgetConfiguration(
        park: Park,
        attractionIds: List<String>,
    ) {
        lifecycleScope.launch {
            ParkWidgetConfigStore.save(
                context = this@ParkWidgetConfigActivity,
                appWidgetId = appWidgetId,
                parkKey = park.id,
                parkName = park.name,
                attractionIds = attractionIds,
            )
            runCatching {
                val glanceId = GlanceAppWidgetManager(this@ParkWidgetConfigActivity)
                    .getGlanceIdBy(appWidgetId)
                updateAppWidgetState(
                    context = this@ParkWidgetConfigActivity,
                    definition = PreferencesGlanceStateDefinition,
                    glanceId = glanceId,
                ) { preferences ->
                    mutablePreferencesOf(
                        ParkWidgetState.PARK_KEY to park.id,
                        ParkWidgetState.PARK_NAME to park.name,
                        ParkWidgetState.ATTRACTION_IDS to ParkWidgetState.encodeAttractionIds(attractionIds),
                    )
                }
                ParkHomeWidget().update(this@ParkWidgetConfigActivity, glanceId)
            }
            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, result)
            finish()
        }
    }
}

@Composable
private fun ParkWidgetConfigScreen(
    repository: WartezeitenRepository,
    preferencesDataSource: PreferencesDataSource,
    onCancel: () -> Unit,
    onSave: (Park, List<String>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var language by remember { mutableStateOf(PreferencesDataSource.DEFAULT_LANGUAGE) }
    var parks by remember { mutableStateOf<List<Park>>(emptyList()) }
    var selectedPark by remember { mutableStateOf<Park?>(null) }
    var attractions by remember { mutableStateOf<List<WaitingTime>>(emptyList()) }
    var selectedAttractionIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var loadingParks by remember { mutableStateOf(true) }
    var loadingAttractions by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun loadAttractions(park: Park) {
        scope.launch {
            loadingAttractions = true
            errorText = null
            repository.refreshParkDetail(park.id, language)
            val detail = repository.observeParkDetail(park.id).first()
            attractions = detail.waitingTimes.sortedWith(
                compareByDescending<WaitingTime> { it.status == AttractionStatus.Opened }
                    .thenByDescending { it.waitingTime ?: -1 }
                    .thenBy { it.name.lowercase() },
            )
            selectedAttractionIds = attractions.take(3).map { it.attractionId }
            loadingAttractions = false
        }
    }

    LaunchedEffect(Unit) {
        language = preferencesDataSource.language.first()
        when (val result = repository.refreshParks(language)) {
            is ApiResult.Error -> errorText = result.message ?: "Parks konnten nicht aktualisiert werden."
            is ApiResult.Success -> Unit
        }
        val loadedParks = repository.observeParks(null).first()
            .sortedWith(compareByDescending<Park> { it.isFavorite }.thenBy { it.name.lowercase() })
        parks = loadedParks
        selectedPark = loadedParks.firstOrNull()
        loadingParks = false
        selectedPark?.let(::loadAttractions)
    }

    val visibleParks = parks.filter { park ->
        val query = searchQuery.trim()
        query.isBlank() ||
            park.name.contains(query, ignoreCase = true) ||
            park.country.contains(query, ignoreCase = true)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(
                text = "Widget einrichten",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            WidgetPreviewCard(
                parkName = selectedPark?.name ?: "Lieblingspark",
                selectedAttractionCount = selectedAttractionIds.size,
            )
            errorText?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Park suchen") },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (loadingParks) {
                LoadingRow("Parks werden geladen")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visibleParks, key = { it.id }) { park ->
                        ParkChoiceRow(
                            park = park,
                            selected = selectedPark?.id == park.id,
                            onClick = {
                                selectedPark = park
                                loadAttractions(park)
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Attraktionen (${selectedAttractionIds.size}/3)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (loadingAttractions) {
                LoadingRow("Attraktionen werden geladen")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .height(170.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(attractions, key = { it.attractionId }) { attraction ->
                        val checked = attraction.attractionId in selectedAttractionIds
                        AttractionChoiceRow(
                            attraction = attraction,
                            checked = checked,
                            enabled = checked || selectedAttractionIds.size < 3,
                            onCheckedChange = { isChecked ->
                                selectedAttractionIds = when {
                                    isChecked && selectedAttractionIds.size < 3 -> {
                                        selectedAttractionIds + attraction.attractionId
                                    }
                                    !isChecked -> selectedAttractionIds - attraction.attractionId
                                    else -> selectedAttractionIds
                                }
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) {
                    Text("Abbrechen")
                }
                Button(
                    enabled = selectedPark != null,
                    onClick = {
                        selectedPark?.let { onSave(it, selectedAttractionIds) }
                    },
                ) {
                    Text("Speichern")
                }
            }
        }
    }
}

@Composable
private fun WidgetPreviewCard(
    parkName: String,
    selectedAttractionCount: Int,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = parkName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Beispielansicht des Widgets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "geöffnet",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                PreviewMetric("Schnitt", "24 min")
                PreviewMetric("Höchste", "55 min")
                PreviewMetric("Attr.", selectedAttractionCount.coerceAtLeast(3).toString())
            }
        }
    }
}

@Composable
private fun PreviewMetric(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ParkChoiceRow(
    park: Park,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        tonalElevation = if (selected) 2.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = park.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (park.isFavorite) "${park.country} · Favorit" else park.country,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (selected) "Ausgewählt" else "Wählen",
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun AttractionChoiceRow(
    attraction: WaitingTime,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled || checked) {
                onCheckedChange(!checked)
            },
        shape = RoundedCornerShape(10.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = checked,
                enabled = enabled || checked,
                onCheckedChange = onCheckedChange,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attraction.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    text = attraction.status.widgetLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = attraction.waitingTime?.let { "$it min" } ?: "-",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun AttractionStatus.widgetLabel(): String {
    return when (this) {
        AttractionStatus.Opened -> "geöffnet"
        AttractionStatus.Maintenance -> "Wartung"
        AttractionStatus.Closed -> "geschlossen"
        AttractionStatus.ClosedWeather -> "wetterbedingt geschlossen"
        AttractionStatus.Unknown -> "unbekannt"
    }
}

@Composable
private fun LoadingRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text(text = text)
    }
}
