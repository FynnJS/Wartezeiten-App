package de.wartezeiten.app.ui.watchlist

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import de.wartezeiten.app.core.i18n.localized
import de.wartezeiten.app.data.local.entity.WatchlistEntity
import de.wartezeiten.app.data.local.entity.WatchlistType
import de.wartezeiten.app.push.NotificationDiagnostics
import de.wartezeiten.app.push.PushDeliveryStatus
import de.wartezeiten.app.ui.settings.SettingsViewModel
import de.wartezeiten.app.ui.waitingtimes.WatchlistAlertWithParkName
import de.wartezeiten.app.ui.waitingtimes.WatchlistViewModel
import de.wartezeiten.app.ui.waitingtimes.label
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistRoute(
    onBackClick: () -> Unit,
    viewModel: WatchlistViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val watchlistItems by viewModel.watchlistItems.collectAsState(initial = emptyList())
    val pushStatus by viewModel.pushStatus.collectAsState()
    val settingsState by settingsViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val language = settingsState.language
    val groupedWatchlist = watchlistItems.groupBy { it.parkName ?: it.alert.parkKey }
    val testNotificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            NotificationDiagnostics.showTestNotification(context)
        } else {
            Toast.makeText(
                context,
                localized(
                    language,
                    de = "Für die Testbenachrichtigung wird die Benachrichtigungsberechtigung benötigt.",
                    en = "Notification permission is required for the test.",
                    fr = "L'autorisation de notification est nécessaire pour la notification de test.",
                    nl = "Voor de testmelding is meldingsmachtiging vereist.",
                ),
                Toast.LENGTH_LONG,
            ).show()
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watchlist") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = localized(language, de = "Zur\u00fcck", en = "Back", fr = "Retour", nl = "Terug"),
                        )
                    }
                }
            )
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
                WatchlistSummaryCard(
                    totalAlerts = watchlistItems.size,
                    parkCount = groupedWatchlist.size,
                    pushStatus = pushStatus,
                    language = language,
                    onTestNotification = {
                        val permissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        val permissionGranted = !permissionRequired || ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (permissionGranted) {
                            NotificationDiagnostics.showTestNotification(context)
                        } else {
                            testNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onRetryPush = viewModel::retryPushSync,
                )
            }

            if (watchlistItems.isEmpty()) {
                item {
                    Text(
                        text = localized(
                            language,
                            de = "Hier werden deine aktiven Benachrichtigungen angezeigt. F\u00fcge sie aus der Park- oder Attraktionsansicht hinzu.",
                            en = "Your active notifications appear here. Add them from a park or attraction view.",
                            fr = "Tes notifications actives s'affichent ici. Ajoute-les depuis la vue d'un parc ou d'une attraction.",
                            nl = "Hier verschijnen je actieve meldingen. Voeg ze toe vanuit een park- of attractieweergave.",
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                groupedWatchlist.forEach { (parkName, alerts) ->
                    item {
                        WatchlistParkHeader(
                            parkName = parkName,
                            alertCount = alerts.size,
                            language = language,
                        )
                    }

                    items(alerts, key = { it.alert.id }) { alert ->
                        WatchlistAlertCard(
                            alert = alert,
                            language = language,
                            onDelete = viewModel::deleteAlert,
                            onEnabledChange = viewModel::setAlertEnabled,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchlistSummaryCard(
    totalAlerts: Int,
    parkCount: Int,
    pushStatus: PushDeliveryStatus,
    language: String,
    onTestNotification: () -> Unit,
    onRetryPush: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                localized(language, de = "Deine Benachrichtigungen", en = "Your notifications", fr = "Tes notifications", nl = "Jouw meldingen"),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = localized(
                    language,
                    de = "$totalAlerts aktive Alarme in $parkCount Parks. Hintergrundchecks laufen etwa alle 30 Minuten und \u00f6ffnen den passenden Park.",
                    en = "$totalAlerts active alerts across $parkCount parks. Background checks run about every 30 minutes and open the matching park.",
                    fr = "$totalAlerts alertes actives dans $parkCount parcs. Les v\u00e9rifications en arri\u00e8re-plan tournent environ toutes les 30 minutes et ouvrent le parc correspondant.",
                    nl = "$totalAlerts actieve meldingen in $parkCount parken. Achtergrondcontroles draaien ongeveer elke 30 minuten en openen het bijbehorende park.",
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = pushStatus.label(language),
                style = MaterialTheme.typography.bodySmall,
                color = when (pushStatus) {
                    PushDeliveryStatus.Active -> MaterialTheme.colorScheme.primary
                    PushDeliveryStatus.Disabled,
                    PushDeliveryStatus.Error -> MaterialTheme.colorScheme.error
                    PushDeliveryStatus.Syncing -> MaterialTheme.colorScheme.onPrimaryContainer
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onTestNotification) {
                    Text(localized(language, de = "Testbenachrichtigung", en = "Test notification", fr = "Notification de test", nl = "Testmelding"))
                }
                if (pushStatus == PushDeliveryStatus.Error) {
                    TextButton(onClick = onRetryPush) {
                        Text(localized(language, de = "Push erneut verbinden", en = "Retry push", fr = "Relancer le push", nl = "Push opnieuw proberen"))
                    }
                }
            }
        }
    }
}

private fun PushDeliveryStatus.label(language: String): String {
    return when (this) {
        PushDeliveryStatus.Active -> localized(
            language,
            de = "Standby-Push ist aktiv. Alarme werden serverseitig jede Minute geprüft.",
            en = "Standby push is active. Alerts are checked server-side every minute.",
            fr = "Le push en veille est actif. Les alertes sont vérifiées côté serveur chaque minute.",
            nl = "Standby-push is actief. Meldingen worden server-side elke minuut gecontroleerd.",
        )
        PushDeliveryStatus.Syncing -> localized(
            language,
            de = "Standby-Push wird verbunden...",
            en = "Connecting standby push...",
            fr = "Connexion du push en veille...",
            nl = "Standby-push wordt verbonden...",
        )
        PushDeliveryStatus.Error -> localized(
            language,
            de = "Standby-Push konnte nicht verbunden werden. Der lokale Fallback bleibt aktiv.",
            en = "Standby push could not connect. The local fallback remains active.",
            fr = "Le push en veille n'a pas pu se connecter. Le repli local reste actif.",
            nl = "Standby-push kon geen verbinding maken. De lokale fallback blijft actief.",
        )
        PushDeliveryStatus.Disabled -> localized(
            language,
            de = "Standby-Push ist in dieser APK nicht konfiguriert. Es bleiben nur verzögerte lokale Prüfungen.",
            en = "Standby push is not configured in this APK. Only delayed local checks are available.",
            fr = "Le push en veille n'est pas configuré dans cet APK. Seules des vérifications locales différées sont disponibles.",
            nl = "Standby-push is niet geconfigureerd in deze APK. Er zijn alleen vertraagde lokale controles beschikbaar.",
        )
    }
}

@Composable
private fun WatchlistParkHeader(
    parkName: String,
    alertCount: Int,
    language: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = parkName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
        )
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Text(
                text = localized(
                    language,
                    de = "$alertCount Alarme",
                    en = "$alertCount alerts",
                    fr = "$alertCount alertes",
                    nl = "$alertCount meldingen",
                ),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun WatchlistAlertCard(
    alert: WatchlistAlertWithParkName,
    language: String,
    onDelete: (WatchlistEntity) -> Unit,
    onEnabledChange: (WatchlistEntity, Boolean) -> Unit,
) {
    val item = alert.alert
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.type.label(language), style = MaterialTheme.typography.titleMedium)
                Text(item.scopeLine(alert, language), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = item.statusLine(language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = item.behaviorLine(language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = item.rulesLine(language),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
                alert.history?.lastTriggeredAtMillis?.takeIf { it > 0L }?.let { triggeredAt ->
                    Text(
                        text = localized(
                            language,
                            de = "Zuletzt ausgelöst: ${triggeredAt.formattedTimestamp()}",
                            en = "Last triggered: ${triggeredAt.formattedTimestamp()}",
                            fr = "Dernière déclenchée : ${triggeredAt.formattedTimestamp()}",
                            nl = "Laatst geactiveerd: ${triggeredAt.formattedTimestamp()}",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            IconButton(onClick = { onEnabledChange(item, !item.enabled) }) {
                Icon(
                    imageVector = if (item.enabled) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (item.enabled) {
                        localized(language, de = "Pausieren", en = "Pause", fr = "Mettre en pause", nl = "Pauzeren")
                    } else {
                        localized(language, de = "Aktivieren", en = "Activate", fr = "Activer", nl = "Activeren")
                    },
                )
            }
            IconButton(onClick = { onDelete(item) }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = localized(language, de = "L\u00f6schen", en = "Delete", fr = "Supprimer", nl = "Verwijderen"),
                )
            }
        }
    }
}

private fun Long.formattedTimestamp(): String = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")
    .format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

private fun WatchlistEntity.rulesLine(language: String): String {
    if (!enabled) return localized(language, de = "Pausiert", en = "Paused", fr = "En pause", nl = "Gepauzeerd")
    val rules = buildList {
        if (notifyOnce) add(localized(language, de = "einmalig", en = "once", fr = "une fois", nl = "eenmalig"))
        if (onlyWhenParkOpen) {
            add(localized(language, de = "nur Parköffnung", en = "park open", fr = "parc ouvert", nl = "park open"))
        }
        if (quietHoursEnabled) {
            add(localized(language, de = "Ruhe 22–08", en = "quiet 22–08", fr = "silence 22–08", nl = "stil 22–08"))
        }
        add(
            localized(
                language,
                de = "$cooldownMinutes Min. Abstand",
                en = "$cooldownMinutes min interval",
                fr = "intervalle de $cooldownMinutes min",
                nl = "interval van $cooldownMinutes min.",
            ),
        )
    }
    return rules.joinToString(" · ")
}

private fun WatchlistEntity.statusLine(language: String): String {
    val thresholdTypes = setOf(
        WatchlistType.WAIT_TIME_BELOW,
        WatchlistType.WAIT_TIME_ABOVE,
        WatchlistType.CROWD_LEVEL_BELOW,
        WatchlistType.CROWD_LEVEL_ABOVE,
    )
    if (type !in thresholdTypes) {
        return localized(
            language,
            de = "Statusbasierter Alarm",
            en = "Status-based alert",
            fr = "Alerte basée sur le statut",
            nl = "Statusgebaseerde melding",
        )
    }
    val unit = if (type == WatchlistType.CROWD_LEVEL_BELOW || type == WatchlistType.CROWD_LEVEL_ABOVE) {
        "%"
    } else {
        localized(language, de = " Min.", en = " min", fr = " min", nl = " min.")
    }
    return localized(
        language,
        de = "Schwelle: $threshold$unit",
        en = "Threshold: $threshold$unit",
        fr = "Seuil : $threshold$unit",
        nl = "Drempel: $threshold$unit",
    )
}

private fun WatchlistEntity.scopeLine(
    alert: WatchlistAlertWithParkName,
    language: String,
): String {
    return if (attractionId == null) {
        localized(
            language,
            de = "Bereich: ganzer Park",
            en = "Scope: whole park",
            fr = "Portée : tout le parc",
            nl = "Bereik: hele park",
        )
    } else {
        val name = alert.attractionName ?: attractionId
        localized(language, de = "Bereich: $name", en = "Scope: $name", fr = "Portée : $name", nl = "Bereik: $name")
    }
}

private fun WatchlistEntity.behaviorLine(language: String): String {
    return when (type) {
        WatchlistType.PARK_ALL_CHANGES,
        WatchlistType.ATTRACTION_ALL_CHANGES -> localized(
            language,
            de = "Benachrichtigt erst bei echter \u00c4nderung zum zuletzt gesehenen Zustand.",
            en = "Notifies only after a real change from the last seen state.",
            fr = "Notifie uniquement en cas de changement r\u00e9el par rapport au dernier \u00e9tat observ\u00e9.",
            nl = "Meldt alleen bij een echte verandering ten opzichte van de laatst gezien status.",
        )
        WatchlistType.NOW_OPENED,
        WatchlistType.ATTRACTION_OPEN -> localized(
            language,
            de = "Antippen der Benachrichtigung \u00f6ffnet den passenden Park.",
            en = "Tapping the notification opens the matching park.",
            fr = "Toucher la notification ouvre le parc correspondant.",
            nl = "Tikken op de melding opent het bijbehorende park.",
        )
        else -> localized(
            language,
            de = "Wird lokal auf diesem Ger\u00e4t gepr\u00fcft.",
            en = "Checked locally on this device.",
            fr = "V\u00e9rifi\u00e9 localement sur cet appareil.",
            nl = "Wordt lokaal op dit toestel gecontroleerd.",
        )
    }
}
