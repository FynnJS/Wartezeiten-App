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
import de.wartezeiten.app.core.i18n.localized
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
                localized(
                    language,
                    de = "Erlaube Benachrichtigungen, damit Alarme ausgelöst werden können.",
                    en = "Allow notifications so alerts can be triggered.",
                    fr = "Autorise les notifications pour que les alertes puissent se déclencher.",
                    nl = "Sta meldingen toe zodat alerts kunnen worden geactiveerd.",
                ),
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
        WatchlistType.WAIT_TIME_BELOW -> localized(language, de = "Wartezeit unter (Min.)", en = "Wait time below (min)", fr = "Temps d'attente sous (min)", nl = "Wachttijd onder (min.)")
        WatchlistType.WAIT_TIME_ABOVE -> localized(language, de = "Wartezeit über (Min.)", en = "Wait time above (min)", fr = "Temps d'attente au-dessus (min)", nl = "Wachttijd boven (min.)")
        WatchlistType.CROWD_LEVEL_BELOW -> localized(language, de = "Auslastung unter (%)", en = "Crowd level below (%)", fr = "Niveau de fréquentation sous (%)", nl = "Drukte onder (%)")
        WatchlistType.CROWD_LEVEL_ABOVE -> localized(language, de = "Auslastung über (%)", en = "Crowd level above (%)", fr = "Niveau de fréquentation au-dessus (%)", nl = "Drukte boven (%)")
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
        title = { Text(localized(language, de = "Park-Alarm erstellen", en = "Create park alert", fr = "Créer une alerte de parc", nl = "Parkmelding maken")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (attractionName != null) {
                    Text(
                        text = localized(language, de = "Attraktion: $attractionName", en = "Attraction: $attractionName", fr = "Attraction : $attractionName", nl = "Attractie: $attractionName"),
                        maxLines = 2
                    )
                } else {
                    Text(localized(language, de = "Parkweiter Alarm", en = "Park-wide alert", fr = "Alerte pour tout le parc", nl = "Parkbrede melding"))
                }

                Text(
                    localized(language, de = "Wobei soll ich dich anstupsen?", en = "What should trigger the alert?", fr = "Qu'est-ce qui doit déclencher l'alerte ?", nl = "Wat moet de melding activeren?"),
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
                            text = localized(
                                language,
                                de = "Erlaube Benachrichtigungen, damit dich die App vor Ort rechtzeitig anstupsen kann.",
                                en = "Allow notifications so the app can alert you in time.",
                                fr = "Autorise les notifications pour que l'app puisse t'alerter à temps.",
                                nl = "Sta meldingen toe zodat de app je op tijd kan waarschuwen.",
                            ),
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
                            Text(localized(language, de = "App-Einstellungen öffnen", en = "Open app settings", fr = "Ouvrir les réglages de l'app", nl = "App-instellingen openen"))
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
                    localized(language, de = "Zustellregeln", en = "Delivery rules", fr = "Règles de diffusion", nl = "Bezorgregels"),
                    style = MaterialTheme.typography.titleSmall,
                )
                RuleSwitch(
                    checked = notifyOnce,
                    onCheckedChange = { notifyOnce = it },
                    title = localized(language, de = "Nur einmal benachrichtigen", en = "Notify only once", fr = "Notifier une seule fois", nl = "Slechts één keer melden"),
                    description = localized(language, de = "Der Alarm wird nach der ersten Meldung pausiert.", en = "The alert is paused after its first notification.", fr = "L'alerte est mise en pause après sa première notification.", nl = "De melding wordt na de eerste notificatie gepauzeerd."),
                )
                RuleSwitch(
                    checked = onlyWhenParkOpen,
                    onCheckedChange = { onlyWhenParkOpen = it },
                    title = localized(language, de = "Nur während der Parköffnung", en = "Only while the park is open", fr = "Uniquement pendant l'ouverture du parc", nl = "Alleen tijdens openingstijden van het park"),
                    description = localized(language, de = "Unterdrückt Meldungen vor Öffnung und nach Schließung.", en = "Suppresses alerts before opening and after closing.", fr = "Bloque les alertes avant l'ouverture et après la fermeture.", nl = "Onderdrukt meldingen vóór opening en na sluiting."),
                )
                RuleSwitch(
                    checked = quietHoursEnabled,
                    onCheckedChange = { quietHoursEnabled = it },
                    title = localized(language, de = "Ruhezeit 22:00–08:00", en = "Quiet hours 22:00–08:00", fr = "Heures calmes 22:00–08:00", nl = "Stille uren 22:00–08:00"),
                    description = localized(language, de = "Über Nacht werden keine Alarme zugestellt.", en = "No alerts are delivered overnight.", fr = "Aucune alerte n'est envoyée pendant la nuit.", nl = "'s Nachts worden geen meldingen verzonden."),
                )
                Text(
                    localized(language, de = "Mindestabstand", en = "Minimum interval", fr = "Intervalle minimum", nl = "Minimale tussentijd"),
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
                                    localized(language, de = "Diesen Alarm gibt es schon.", en = "This alert already exists.", fr = "Cette alerte existe déjà.", nl = "Deze melding bestaat al."),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    )
                    onDismiss()
                }) { Text(localized(language, de = "Speichern", en = "Save", fr = "Enregistrer", nl = "Opslaan")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(localized(language, de = "Abbrechen", en = "Cancel", fr = "Annuler", nl = "Annuleren")) }
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
    WatchlistType.WAIT_TIME_BELOW -> localized(language, de = "Ride-Fenster", en = "Ride window", fr = "Fenêtre d'attraction", nl = "Rit-venster")
    WatchlistType.WAIT_TIME_ABOVE -> localized(language, de = "Zu voll", en = "Too crowded", fr = "Trop de monde", nl = "Te druk")
    WatchlistType.NOW_OPENED -> localized(language, de = "Einlass bereit", en = "Entry ready", fr = "Entrée prête", nl = "Toegang klaar")
    WatchlistType.CROWD_LEVEL_BELOW -> localized(language, de = "Entspannter Park", en = "Relaxed park", fr = "Parc tranquille", nl = "Rustig park")
    WatchlistType.CROWD_LEVEL_ABOVE -> localized(language, de = "Andrang-Warnung", en = "Crowd alert", fr = "Alerte affluence", nl = "Drukte-melding")
    WatchlistType.PARK_ALL_CHANGES -> localized(language, de = "Park-Änderungen", en = "Park changes", fr = "Changements du parc", nl = "Parkwijzigingen")
    WatchlistType.ATTRACTION_ALL_CHANGES -> localized(language, de = "Alle Änderungen", en = "All changes", fr = "Tous les changements", nl = "Alle wijzigingen")
    WatchlistType.ATTRACTION_STATUS_CHANGE -> localized(language, de = "Status-Radar", en = "Status radar", fr = "Radar de statut", nl = "Statusradar")
    WatchlistType.ATTRACTION_OPEN -> localized(language, de = "Wieder offen", en = "Open again", fr = "De nouveau ouvert", nl = "Weer open")
    WatchlistType.ATTRACTION_CLOSED -> localized(language, de = "Gerade zu", en = "Just closed", fr = "Vient de fermer", nl = "Net gesloten")
    WatchlistType.ATTRACTION_MAINTENANCE -> localized(language, de = "Technikpause", en = "Tech break", fr = "Pause technique", nl = "Technische pauze")
    WatchlistType.PARK_STATUS_CHANGED -> localized(language, de = "Park-Ticker", en = "Park ticker", fr = "Ticker du parc", nl = "Parkticker")
    WatchlistType.DAILY_SUMMARY -> localized(language, de = "Tageszusammenfassung", en = "Daily summary", fr = "Résumé quotidien", nl = "Dagelijks overzicht")
}

private fun WatchlistType.description(language: String): String = when (this) {
    WatchlistType.WAIT_TIME_BELOW -> localized(language, de = "Für spontane Chancen: Du erfährst, wenn eine Wartezeit kurz genug zum Loslaufen ist.", en = "For quick chances: you will know when a wait is short enough to head over.", fr = "Pour les occasions rapides : tu sauras quand un temps d'attente est assez court pour y aller.", nl = "Voor snelle kansen: je weet wanneer een wachttijd kort genoeg is om erop af te gaan.")
    WatchlistType.WAIT_TIME_ABOVE -> localized(language, de = "Für Planwechsel: Du erfährst, wenn eine Schlange deinen Grenzwert sprengt.", en = "For route changes: you will know when a queue exceeds your limit.", fr = "Pour changer de plan : tu sauras quand une file dépasse ta limite.", nl = "Voor planwijzigingen: je weet wanneer een rij je grens overschrijdt.")
    WatchlistType.NOW_OPENED -> localized(language, de = "Für den Start in den Tag: Du erfährst, wenn der Park heute als geöffnet gemeldet ist.", en = "For the start of the day: you will know when the park is reported open today.", fr = "Pour bien commencer la journée : tu sauras quand le parc est annoncé ouvert aujourd'hui.", nl = "Voor de start van de dag: je weet wanneer het park vandaag als open wordt gemeld.")
    WatchlistType.CROWD_LEVEL_BELOW -> localized(language, de = "Für ruhige Momente: Du erfährst, wenn der Park entspannt genug für einen guten Rundgang ist.", en = "For quieter moments: you will know when the park is relaxed enough for a good round.", fr = "Pour les moments calmes : tu sauras quand le parc est assez tranquille pour une bonne visite.", nl = "Voor rustige momenten: je weet wanneer het park rustig genoeg is voor een goede ronde.")
    WatchlistType.CROWD_LEVEL_ABOVE -> localized(language, de = "Für Pausenplanung: Du erfährst, wenn der Park voller wird als gewünscht.", en = "For break planning: you will know when the park gets busier than you want.", fr = "Pour planifier une pause : tu sauras quand le parc devient plus fréquenté que souhaité.", nl = "Voor pauzeplanning: je weet wanneer het park drukker wordt dan gewenst.")
    WatchlistType.PARK_ALL_CHANGES -> localized(language, de = "Für den Gesamtblick: Du erfährst, wenn sich Öffnung, Auslastung oder Attraktionsverfügbarkeit ändern.", en = "For the full picture: you will know when opening state, crowd level, or attraction availability changes.", fr = "Pour la vue d'ensemble : tu sauras quand l'ouverture, le niveau de fréquentation ou la disponibilité des attractions changent.", nl = "Voor het volledige beeld: je weet wanneer openingsstatus, drukte of attractiebeschikbaarheid verandert.")
    WatchlistType.ATTRACTION_ALL_CHANGES -> localized(language, de = "Für Favoriten: Du erfährst, wenn sich Status oder Wartezeit ändern.", en = "For favorites: you will know when status or wait time changes.", fr = "Pour les favoris : tu sauras quand le statut ou le temps d'attente change.", nl = "Voor favorieten: je weet wanneer status of wachttijd verandert.")
    WatchlistType.ATTRACTION_STATUS_CHANGE -> localized(language, de = "Für Favoriten: Du erfährst, wenn sich bei dieser Attraktion etwas ändert.", en = "For favorites: you will know when something changes for this attraction.", fr = "Pour les favoris : tu sauras quand quelque chose change pour cette attraction.", nl = "Voor favorieten: je weet wanneer er iets verandert bij deze attractie.")
    WatchlistType.ATTRACTION_OPEN -> localized(language, de = "Für zweite Chancen: Du erfährst, wenn die Attraktion wieder offen ist.", en = "For second chances: you will know when the attraction opens again.", fr = "Pour une seconde chance : tu sauras quand l'attraction rouvre.", nl = "Voor een tweede kans: je weet wanneer de attractie weer opengaat.")
    WatchlistType.ATTRACTION_CLOSED -> localized(language, de = "Für unnötige Wege: Du erfährst, wenn die Attraktion gerade schließt.", en = "To avoid wasted walks: you will know when the attraction closes.", fr = "Pour éviter les trajets inutiles : tu sauras quand l'attraction ferme.", nl = "Om onnodige wandelingen te voorkomen: je weet wanneer de attractie sluit.")
    WatchlistType.ATTRACTION_MAINTENANCE -> localized(language, de = "Für Umwege: Du erfährst, wenn die Attraktion in eine Technikpause geht.", en = "For detours: you will know when the attraction enters maintenance.", fr = "Pour les détours : tu sauras quand l'attraction entre en pause technique.", nl = "Voor omwegen: je weet wanneer de attractie in technische pauze gaat.")
    WatchlistType.PARK_STATUS_CHANGED -> localized(language, de = "Für Tagesplanung: Du erfährst, wenn sich der Parkstatus ändert.", en = "For day planning: you will know when the park status changes.", fr = "Pour planifier ta journée : tu sauras quand le statut du parc change.", nl = "Voor dagplanning: je weet wanneer de parkstatus verandert.")
    WatchlistType.DAILY_SUMMARY -> localized(language, de = "Einmal täglich gegen 18 Uhr Parkzeit: Öffnung, Auslastung und offene Attraktionen auf einen Blick.", en = "Once per day around 18:00 park time: opening state, crowd level, and open attractions at a glance.", fr = "Une fois par jour vers 18h00 heure du parc : ouverture, fréquentation et attractions ouvertes en un coup d'œil.", nl = "Eenmaal per dag rond 18:00 parktijd: openingsstatus, drukte en open attracties in één overzicht.")
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
