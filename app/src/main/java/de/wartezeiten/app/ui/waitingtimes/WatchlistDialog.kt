package de.wartezeiten.app.ui.waitingtimes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.wartezeiten.app.data.local.dao.ParkDao
import de.wartezeiten.app.data.local.dao.WatchlistDao
import de.wartezeiten.app.data.local.entity.WatchlistEntity
import de.wartezeiten.app.data.local.entity.WatchlistType
import de.wartezeiten.app.worker.NotificationScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun AddWatchlistDialog(
    parkKey: String,
    attractionId: String?,
    attractionName: String? = null,
    onDismiss: () -> Unit,
    viewModel: WatchlistViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isNotificationPermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val permissionGranted = remember {
        mutableStateOf(
            !isNotificationPermissionRequired || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted.value = granted
        if (!granted) {
            Toast.makeText(
                context,
                "Erlaube Benachrichtigungen, damit Alarme ausgelöst werden können.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        if (isNotificationPermissionRequired && !permissionGranted.value) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var threshold by remember { mutableStateOf("30") }
    var selectedType by remember(attractionId) {
        mutableStateOf(
            if (attractionId != null) WatchlistType.WAIT_TIME_BELOW
            else WatchlistType.WAIT_TIME_BELOW
        )
    }

    val availableTypes = if (attractionId != null) {
        listOf(
            WatchlistType.WAIT_TIME_BELOW,
            WatchlistType.WAIT_TIME_ABOVE,
            WatchlistType.ATTRACTION_STATUS_CHANGE,
            WatchlistType.ATTRACTION_OPEN,
            WatchlistType.ATTRACTION_CLOSED,
            WatchlistType.ATTRACTION_MAINTENANCE
        )
    } else {
        listOf(
            WatchlistType.WAIT_TIME_BELOW,
            WatchlistType.WAIT_TIME_ABOVE,
            WatchlistType.NOW_OPENED,
            WatchlistType.PARK_STATUS_CHANGED,
            WatchlistType.CROWD_LEVEL_BELOW,
            WatchlistType.CROWD_LEVEL_ABOVE
        )
    }

    LaunchedEffect(availableTypes) {
        if (selectedType !in availableTypes) {
            selectedType = availableTypes.first()
        }
    }

    val showThresholdField = selectedType in listOf(
        WatchlistType.WAIT_TIME_BELOW,
        WatchlistType.WAIT_TIME_ABOVE,
        WatchlistType.CROWD_LEVEL_BELOW,
        WatchlistType.CROWD_LEVEL_ABOVE
    )

    val thresholdLabel = when (selectedType) {
        WatchlistType.WAIT_TIME_BELOW -> "Wartezeit unter (Min)"
        WatchlistType.WAIT_TIME_ABOVE -> "Wartezeit über (Min)"
        WatchlistType.CROWD_LEVEL_BELOW -> "Crowd unter (%)"
        WatchlistType.CROWD_LEVEL_ABOVE -> "Crowd über (%)"
        else -> ""
    }
    val thresholdValue = threshold.toIntOrNull()
    val canSave = permissionGranted.value && (!showThresholdField || thresholdValue != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Benachrichtigung erstellen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (attractionName != null) {
                    Text(
                        text = "Attraktion: $attractionName",
                        maxLines = 2
                    )
                } else {
                    Text("Parkweite Benachrichtigung")
                }

                Text("Regel auswählen", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableTypes.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.label(), maxLines = 1) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                if (showThresholdField) {
                    OutlinedTextField(
                        value = threshold,
                        onValueChange = { threshold = it.filter { char -> char.isDigit() } },
                        label = { Text(thresholdLabel) }
                    )
                }

                if (isNotificationPermissionRequired && !permissionGranted.value) {
                    Text(
                        text = "Um Benachrichtigungen zu erhalten, muss die Erlaubnis erteilt werden.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Text(
                    text = selectedType.description(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    viewModel.addAlert(
                        parkKey = parkKey,
                        attractionId = attractionId,
                        type = selectedType,
                        threshold = thresholdValue ?: 0
                    )
                    NotificationScheduler.runSoonAndKeepChecking(context)
                    onDismiss()
                }) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

fun WatchlistType.label(): String = when (this) {
    WatchlistType.WAIT_TIME_BELOW -> "Unter Limit"
    WatchlistType.WAIT_TIME_ABOVE -> "Über Limit"
    WatchlistType.NOW_OPENED -> "Park öffnet"
    WatchlistType.CROWD_LEVEL_BELOW -> "Crowd niedrig"
    WatchlistType.CROWD_LEVEL_ABOVE -> "Crowd hoch"
    WatchlistType.ATTRACTION_STATUS_CHANGE -> "Statuswechsel"
    WatchlistType.ATTRACTION_OPEN -> "Öffnet"
    WatchlistType.ATTRACTION_CLOSED -> "Schließt"
    WatchlistType.ATTRACTION_MAINTENANCE -> "Wartung"
    WatchlistType.PARK_STATUS_CHANGED -> "Parkstatus"
}

private fun WatchlistType.description(): String = when (this) {
    WatchlistType.WAIT_TIME_BELOW -> "Du wirst informiert, sobald die Wartezeit den Grenzwert erreicht oder unterschreitet."
    WatchlistType.WAIT_TIME_ABOVE -> "Du wirst informiert, sobald die Wartezeit den Grenzwert erreicht oder überschreitet."
    WatchlistType.NOW_OPENED -> "Du bekommst einen Hinweis, wenn der Park heute geöffnet ist."
    WatchlistType.CROWD_LEVEL_BELOW -> "Du bekommst einen Hinweis, wenn das Besucheraufkommen unter deinem Grenzwert liegt."
    WatchlistType.CROWD_LEVEL_ABOVE -> "Du bekommst einen Hinweis, wenn das Besucheraufkommen über deinem Grenzwert liegt."
    WatchlistType.ATTRACTION_STATUS_CHANGE -> "Du bekommst einen Hinweis, wenn sich der Status ändert."
    WatchlistType.ATTRACTION_OPEN -> "Du bekommst einen Hinweis, wenn die Attraktion öffnet."
    WatchlistType.ATTRACTION_CLOSED -> "Du bekommst einen Hinweis, wenn die Attraktion schließt."
    WatchlistType.ATTRACTION_MAINTENANCE -> "Du bekommst einen Hinweis, wenn die Attraktion in Wartung geht."
    WatchlistType.PARK_STATUS_CHANGED -> "Du bekommst einen Hinweis, wenn sich der Parkstatus ändert."
}

data class WatchlistAlertWithParkName(
    val alert: WatchlistEntity,
    val parkName: String?,
    val attractionName: String?
)

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val watchlistDao: WatchlistDao,
    private val parkDao: ParkDao,
    private val parkDetailDao: de.wartezeiten.app.data.local.dao.ParkDetailDao
) : ViewModel() {
    val watchlistItems: Flow<List<WatchlistAlertWithParkName>> = combine(
        watchlistDao.observeWatchlist(),
        parkDao.observeParks(null)
    ) { alerts, parks ->
        val parkNamesById = parks.associate { it.id to it.name }
        val parkNamesByUuid = parks.associate { it.uuid to it.name }

        alerts.map { alert ->
            val parkName = parkNamesById[alert.parkKey] ?: parkNamesByUuid[alert.parkKey]
            
            // Fetch waiting times to get attraction name
            val attractionName = alert.attractionId // Need to map this correctly later
            
            WatchlistAlertWithParkName(alert = alert, parkName = parkName, attractionName = attractionName)
        }
    }

    fun addAlert(parkKey: String, attractionId: String?, type: WatchlistType, threshold: Int) {
        viewModelScope.launch {
            watchlistDao.insert(
                WatchlistEntity(
                    parkKey = parkKey,
                    attractionId = attractionId,
                    type = type,
                    threshold = threshold
                )
            )
        }
    }

    fun deleteAlert(item: WatchlistEntity) {
        viewModelScope.launch {
            watchlistDao.delete(item)
        }
    }
}
