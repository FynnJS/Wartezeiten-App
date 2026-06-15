package de.wartezeiten.app.ui.waitingtimes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.wartezeiten.app.data.local.dao.ParkDao
import de.wartezeiten.app.data.local.dao.AlertHistoryDao
import de.wartezeiten.app.data.local.dao.WatchlistDao
import de.wartezeiten.app.data.local.entity.WatchlistEntity
import de.wartezeiten.app.data.local.entity.AlertHistoryEntity
import de.wartezeiten.app.data.local.entity.WatchlistType
import de.wartezeiten.app.push.PushRegistrationManager
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
    language: String = "de",
    onDismiss: () -> Unit,
    viewModel: WatchlistViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
                if (language == "en") {
                    "Allow notifications so alerts can be triggered."
                } else {
                    "Erlaube Benachrichtigungen, damit Alarme ausgelöst werden können."
                },
                Toast.LENGTH_LONG
            ).show()
        }
    }

    DisposableEffect(lifecycleOwner, isNotificationPermissionRequired) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionGranted.value =
                    !isNotificationPermissionRequired || ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
            else WatchlistType.NOW_OPENED
        )
    }
    var notifyOnce by remember { mutableStateOf(false) }
    var onlyWhenParkOpen by remember { mutableStateOf(false) }
    var quietHoursEnabled by remember { mutableStateOf(false) }
    var cooldownMinutes by remember { mutableIntStateOf(30) }

    val availableTypes = if (attractionId != null) {
        listOf(
            WatchlistType.WAIT_TIME_BELOW,
            WatchlistType.WAIT_TIME_ABOVE,
            WatchlistType.ATTRACTION_ALL_CHANGES,
            WatchlistType.ATTRACTION_STATUS_CHANGE,
            WatchlistType.ATTRACTION_OPEN,
            WatchlistType.ATTRACTION_CLOSED,
            WatchlistType.ATTRACTION_MAINTENANCE
        )
    } else {
        listOf(
            WatchlistType.WAIT_TIME_BELOW,
            WatchlistType.WAIT_TIME_ABOVE,
            WatchlistType.PARK_ALL_CHANGES,
            WatchlistType.DAILY_SUMMARY,
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
        WatchlistType.WAIT_TIME_BELOW -> if (language == "en") "Wait time below (min)" else "Wartezeit unter (Min.)"
        WatchlistType.WAIT_TIME_ABOVE -> if (language == "en") "Wait time above (min)" else "Wartezeit über (Min.)"
        WatchlistType.CROWD_LEVEL_BELOW -> if (language == "en") "Crowd level below (%)" else "Auslastung unter (%)"
        WatchlistType.CROWD_LEVEL_ABOVE -> if (language == "en") "Crowd level above (%)" else "Auslastung über (%)"
        else -> ""
    }
    val thresholdValue = threshold.toIntOrNull()
    val effectiveThreshold = if (showThresholdField) thresholdValue ?: 0 else 0
    val canSave = permissionGranted.value && (!showThresholdField || thresholdValue != null)
    val thresholdPresets = when (selectedType) {
        WatchlistType.WAIT_TIME_BELOW -> listOf(15, 30, 45)
        WatchlistType.WAIT_TIME_ABOVE -> listOf(45, 60, 90)
        WatchlistType.CROWD_LEVEL_BELOW -> listOf(30, 50, 70)
        WatchlistType.CROWD_LEVEL_ABOVE -> listOf(60, 75, 90)
        else -> emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (language == "en") "Create park alert" else "Park-Alarm erstellen") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (attractionName != null) {
                    Text(
                        text = if (language == "en") "Attraction: $attractionName" else "Attraktion: $attractionName",
                        maxLines = 2
                    )
                } else {
                    Text(if (language == "en") "Park-wide alert" else "Parkweiter Alarm")
                }

                Text(
                    if (language == "en") "What should trigger the alert?" else "Wobei soll ich dich anstupsen?",
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableTypes.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.label(language), maxLines = 1) },
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
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        thresholdPresets.forEach { preset ->
                            AssistChip(
                                onClick = { threshold = preset.toString() },
                                label = {
                                    Text(
                                        if (selectedType == WatchlistType.CROWD_LEVEL_BELOW ||
                                            selectedType == WatchlistType.CROWD_LEVEL_ABOVE
                                        ) {
                                            "$preset%"
                                        } else {
                                            "$preset Min"
                                        }
                                    )
                                },
                            )
                        }
                    }
                }

                if (isNotificationPermissionRequired && !permissionGranted.value) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (language == "en") {
                                "Allow notifications so the app can alert you in time."
                            } else {
                                "Erlaube Benachrichtigungen, damit dich die App vor Ort rechtzeitig anstupsen kann."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                )
                            }
                        ) {
                            Text(if (language == "en") "Open app settings" else "App-Einstellungen öffnen")
                        }
                    }
                }

                Text(
                    text = selectedType.description(language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()
                Text(
                    if (language == "en") "Delivery rules" else "Zustellregeln",
                    style = MaterialTheme.typography.titleSmall,
                )
                RuleSwitch(
                    checked = notifyOnce,
                    onCheckedChange = { notifyOnce = it },
                    title = if (language == "en") "Notify only once" else "Nur einmal benachrichtigen",
                    description = if (language == "en") "The alert is paused after its first notification." else "Der Alarm wird nach der ersten Meldung pausiert.",
                )
                RuleSwitch(
                    checked = onlyWhenParkOpen,
                    onCheckedChange = { onlyWhenParkOpen = it },
                    title = if (language == "en") "Only while the park is open" else "Nur während der Parköffnung",
                    description = if (language == "en") "Suppresses alerts before opening and after closing." else "Unterdrückt Meldungen vor Öffnung und nach Schließung.",
                )
                RuleSwitch(
                    checked = quietHoursEnabled,
                    onCheckedChange = { quietHoursEnabled = it },
                    title = if (language == "en") "Quiet hours 22:00–08:00" else "Ruhezeit 22:00–08:00",
                    description = if (language == "en") "No alerts are delivered overnight." else "Über Nacht werden keine Alarme zugestellt.",
                )
                Text(
                    if (language == "en") "Minimum interval" else "Mindestabstand",
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(15, 30, 60, 120).forEach { minutes ->
                        FilterChip(
                            selected = cooldownMinutes == minutes,
                            onClick = { cooldownMinutes = minutes },
                            label = { Text(if (minutes < 60) "$minutes Min." else "${minutes / 60} Std.") },
                        )
                    }
                }
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
                        threshold = effectiveThreshold,
                        notifyOnce = notifyOnce,
                        onlyWhenParkOpen = onlyWhenParkOpen,
                        quietHoursEnabled = quietHoursEnabled,
                        cooldownMinutes = cooldownMinutes,
                        onSaved = { saved ->
                            if (saved) {
                                NotificationScheduler.runSoonAndKeepChecking(context)
                            } else {
                                Toast.makeText(
                                    context,
                                    if (language == "en") "This alert already exists." else "Diesen Alarm gibt es schon.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    )
                    onDismiss()
                }) { Text(if (language == "en") "Save" else "Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (language == "en") "Cancel" else "Abbrechen") }
        }
    )
}

@Composable
private fun RuleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    description: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun WatchlistType.label(language: String = "de"): String = when (this) {
    WatchlistType.WAIT_TIME_BELOW -> if (language == "en") "Ride window" else "Ride-Fenster"
    WatchlistType.WAIT_TIME_ABOVE -> if (language == "en") "Too crowded" else "Zu voll"
    WatchlistType.NOW_OPENED -> if (language == "en") "Entry ready" else "Einlass bereit"
    WatchlistType.CROWD_LEVEL_BELOW -> if (language == "en") "Relaxed park" else "Entspannter Park"
    WatchlistType.CROWD_LEVEL_ABOVE -> if (language == "en") "Crowd alert" else "Andrang-Warnung"
    WatchlistType.PARK_ALL_CHANGES -> if (language == "en") "Park changes" else "Park-Änderungen"
    WatchlistType.ATTRACTION_ALL_CHANGES -> if (language == "en") "All changes" else "Alle Änderungen"
    WatchlistType.ATTRACTION_STATUS_CHANGE -> if (language == "en") "Status radar" else "Status-Radar"
    WatchlistType.ATTRACTION_OPEN -> if (language == "en") "Open again" else "Wieder offen"
    WatchlistType.ATTRACTION_CLOSED -> if (language == "en") "Just closed" else "Gerade zu"
    WatchlistType.ATTRACTION_MAINTENANCE -> if (language == "en") "Tech break" else "Technikpause"
    WatchlistType.PARK_STATUS_CHANGED -> if (language == "en") "Park ticker" else "Park-Ticker"
    WatchlistType.DAILY_SUMMARY -> if (language == "en") "Daily summary" else "Tageszusammenfassung"
}

private fun WatchlistType.description(language: String): String = when (this) {
    WatchlistType.WAIT_TIME_BELOW -> if (language == "en") "For quick chances: you will know when a wait is short enough to head over." else "Für spontane Chancen: Du erfährst, wenn eine Wartezeit kurz genug zum Loslaufen ist."
    WatchlistType.WAIT_TIME_ABOVE -> if (language == "en") "For route changes: you will know when a queue exceeds your limit." else "Für Planwechsel: Du erfährst, wenn eine Schlange deinen Grenzwert sprengt."
    WatchlistType.NOW_OPENED -> if (language == "en") "For the start of the day: you will know when the park is reported open today." else "Für den Start in den Tag: Du erfährst, wenn der Park heute als geöffnet gemeldet ist."
    WatchlistType.CROWD_LEVEL_BELOW -> if (language == "en") "For quieter moments: you will know when the park is relaxed enough for a good round." else "Für ruhige Momente: Du erfährst, wenn der Park entspannt genug für einen guten Rundgang ist."
    WatchlistType.CROWD_LEVEL_ABOVE -> if (language == "en") "For break planning: you will know when the park gets busier than you want." else "Für Pausenplanung: Du erfährst, wenn der Park voller wird als gewünscht."
    WatchlistType.PARK_ALL_CHANGES -> if (language == "en") "For the full picture: you will know when opening state, crowd level, or attraction availability changes." else "Für den Gesamtblick: Du erfährst, wenn sich Öffnung, Auslastung oder Attraktionsverfügbarkeit ändern."
    WatchlistType.ATTRACTION_ALL_CHANGES -> if (language == "en") "For favorites: you will know when status or wait time changes." else "Für Favoriten: Du erfährst, wenn sich Status oder Wartezeit ändern."
    WatchlistType.ATTRACTION_STATUS_CHANGE -> if (language == "en") "For favorites: you will know when something changes for this attraction." else "Für Favoriten: Du erfährst, wenn sich bei dieser Attraktion etwas ändert."
    WatchlistType.ATTRACTION_OPEN -> if (language == "en") "For second chances: you will know when the attraction opens again." else "Für zweite Chancen: Du erfährst, wenn die Attraktion wieder offen ist."
    WatchlistType.ATTRACTION_CLOSED -> if (language == "en") "To avoid wasted walks: you will know when the attraction closes." else "Für unnötige Wege: Du erfährst, wenn die Attraktion gerade schließt."
    WatchlistType.ATTRACTION_MAINTENANCE -> if (language == "en") "For detours: you will know when the attraction enters maintenance." else "Für Umwege: Du erfährst, wenn die Attraktion in eine Technikpause geht."
    WatchlistType.PARK_STATUS_CHANGED -> if (language == "en") "For day planning: you will know when the park status changes." else "Für Tagesplanung: Du erfährst, wenn sich der Parkstatus ändert."
    WatchlistType.DAILY_SUMMARY -> if (language == "en") "Once per day around 18:00 park time: opening state, crowd level, and open attractions at a glance." else "Einmal täglich gegen 18 Uhr Parkzeit: Öffnung, Auslastung und offene Attraktionen auf einen Blick."
}

data class WatchlistAlertWithParkName(
    val alert: WatchlistEntity,
    val parkName: String?,
    val attractionName: String?,
    val history: AlertHistoryEntity?,
)

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val watchlistDao: WatchlistDao,
    private val parkDao: ParkDao,
    private val parkDetailDao: de.wartezeiten.app.data.local.dao.ParkDetailDao,
    private val alertHistoryDao: AlertHistoryDao,
    private val pushRegistrationManager: PushRegistrationManager,
) : ViewModel() {
    val pushStatus = pushRegistrationManager.status

    fun retryPushSync() {
        viewModelScope.launch {
            pushRegistrationManager.syncCurrentWatchlist()
        }
    }

    val watchlistItems: Flow<List<WatchlistAlertWithParkName>> = combine(
        watchlistDao.observeWatchlist(),
        parkDao.observeParks(null),
        parkDetailDao.observeAllWaitingTimes(),
        alertHistoryDao.observeAll(),
    ) { alerts, parks, waitingTimes, histories ->
        val parkNamesById = parks.associate { it.id to it.name }
        val parkNamesByUuid = parks.associate { it.uuid to it.name }
        val parkKeysByKey = parks.flatMap { park ->
            listOf(
                park.id to setOf(park.id, park.uuid),
                park.uuid to setOf(park.id, park.uuid),
            )
        }.toMap()
        val attractionNames = waitingTimes.associate { "${it.parkKey}:${it.attractionId}" to it.name }
        val historiesByAlertId = histories.associateBy { it.alertId }

        alerts.map { alert ->
            val parkName = parkNamesById[alert.parkKey] ?: parkNamesByUuid[alert.parkKey]
            val attractionName = alert.attractionId?.let { attractionId ->
                (parkKeysByKey[alert.parkKey] ?: setOf(alert.parkKey))
                    .firstNotNullOfOrNull { parkKey -> attractionNames["$parkKey:$attractionId"] }
            }

            WatchlistAlertWithParkName(
                alert = alert,
                parkName = parkName,
                attractionName = attractionName,
                history = historiesByAlertId[alert.id],
            )
        }
    }

    fun addAlert(
        parkKey: String,
        attractionId: String?,
        type: WatchlistType,
        threshold: Int,
        notifyOnce: Boolean,
        onlyWhenParkOpen: Boolean,
        quietHoursEnabled: Boolean,
        cooldownMinutes: Int,
        onSaved: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            val alreadyExists = watchlistDao.countMatching(
                parkKey = parkKey,
                attractionId = attractionId,
                type = type,
                threshold = threshold,
            ) > 0
            if (alreadyExists) {
                onSaved(false)
                return@launch
            }

            watchlistDao.insert(
                WatchlistEntity(
                    parkKey = parkKey,
                    attractionId = attractionId,
                    type = type,
                    threshold = threshold,
                    notifyOnce = notifyOnce,
                    onlyWhenParkOpen = onlyWhenParkOpen,
                    quietHoursEnabled = quietHoursEnabled,
                    cooldownMinutes = cooldownMinutes,
                )
            )
            onSaved(true)
            pushRegistrationManager.syncCurrentWatchlist()
        }
    }

    fun deleteAlert(item: WatchlistEntity) {
        viewModelScope.launch {
            watchlistDao.delete(item)
            pushRegistrationManager.syncCurrentWatchlist()
        }
    }

    fun setAlertEnabled(item: WatchlistEntity, enabled: Boolean) {
        viewModelScope.launch {
            watchlistDao.setEnabled(item.id, enabled)
            pushRegistrationManager.syncCurrentWatchlist()
        }
    }
}
