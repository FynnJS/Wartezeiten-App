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
                if (language == "en") {
                    "Notification permission is required for the test."
                } else {
                    "Für die Testbenachrichtigung wird die Benachrichtigungsberechtigung benötigt."
                },
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
                            contentDescription = if (language == "en") "Back" else "Zur\u00fcck",
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
                        text = if (language == "en") {
                            "Your active notifications appear here. Add them from a park or attraction view."
                        } else {
                            "Hier werden deine aktiven Benachrichtigungen angezeigt. F\u00fcge sie aus der Park- oder Attraktionsansicht hinzu."
                        },
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
                if (language == "en") "Your notifications" else "Deine Benachrichtigungen",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (language == "en") {
                    "$totalAlerts active alerts across $parkCount parks. Background checks run about every 30 minutes and open the matching park."
                } else {
                    "$totalAlerts aktive Alarme in $parkCount Parks. Hintergrundchecks laufen etwa alle 30 Minuten und \u00f6ffnen den passenden Park."
                },
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
                    Text(if (language == "en") "Test notification" else "Testbenachrichtigung")
                }
                if (pushStatus == PushDeliveryStatus.Error) {
                    TextButton(onClick = onRetryPush) {
                        Text(if (language == "en") "Retry push" else "Push erneut verbinden")
                    }
                }
            }
        }
    }
}

private fun PushDeliveryStatus.label(language: String): String {
    return when (this) {
        PushDeliveryStatus.Active -> if (language == "en") {
            "Standby push is active. Alerts are checked server-side every minute."
        } else {
            "Standby-Push ist aktiv. Alarme werden serverseitig jede Minute geprüft."
        }
        PushDeliveryStatus.Syncing -> if (language == "en") "Connecting standby push..." else "Standby-Push wird verbunden..."
        PushDeliveryStatus.Error -> if (language == "en") {
            "Standby push could not connect. The local fallback remains active."
        } else {
            "Standby-Push konnte nicht verbunden werden. Der lokale Fallback bleibt aktiv."
        }
        PushDeliveryStatus.Disabled -> if (language == "en") {
            "Standby push is not configured in this APK. Only delayed local checks are available."
        } else {
            "Standby-Push ist in dieser APK nicht konfiguriert. Es bleiben nur verzögerte lokale Prüfungen."
        }
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
                text = if (language == "en") "$alertCount alerts" else "$alertCount Alarme",
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
                        text = if (language == "en") {
                            "Last triggered: ${triggeredAt.formattedTimestamp()}"
                        } else {
                            "Zuletzt ausgelöst: ${triggeredAt.formattedTimestamp()}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            IconButton(onClick = { onEnabledChange(item, !item.enabled) }) {
                Icon(
                    imageVector = if (item.enabled) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (item.enabled) {
                        if (language == "en") "Pause" else "Pausieren"
                    } else {
                        if (language == "en") "Activate" else "Aktivieren"
                    },
                )
            }
            IconButton(onClick = { onDelete(item) }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = if (language == "en") "Delete" else "L\u00f6schen",
                )
            }
        }
    }
}

private fun Long.formattedTimestamp(): String = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")
    .format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

private fun WatchlistEntity.rulesLine(language: String): String {
    if (!enabled) return if (language == "en") "Paused" else "Pausiert"
    val rules = buildList {
        if (notifyOnce) add(if (language == "en") "once" else "einmalig")
        if (onlyWhenParkOpen) add(if (language == "en") "park open" else "nur Parköffnung")
        if (quietHoursEnabled) add(if (language == "en") "quiet 22–08" else "Ruhe 22–08")
        add(if (language == "en") "$cooldownMinutes min interval" else "$cooldownMinutes Min. Abstand")
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
        return if (language == "en") "Status-based alert" else "Statusbasierter Alarm"
    }
    val unit = if (type == WatchlistType.CROWD_LEVEL_BELOW || type == WatchlistType.CROWD_LEVEL_ABOVE) {
        "%"
    } else {
        if (language == "en") " min" else " Min."
    }
    return if (language == "en") "Threshold: $threshold$unit" else "Schwelle: $threshold$unit"
}

private fun WatchlistEntity.scopeLine(
    alert: WatchlistAlertWithParkName,
    language: String,
): String {
    return if (attractionId == null) {
        if (language == "en") "Scope: whole park" else "Bereich: ganzer Park"
    } else {
        val name = alert.attractionName ?: attractionId
        if (language == "en") "Scope: $name" else "Bereich: $name"
    }
}

private fun WatchlistEntity.behaviorLine(language: String): String {
    return when (type) {
        WatchlistType.PARK_ALL_CHANGES,
        WatchlistType.ATTRACTION_ALL_CHANGES -> if (language == "en") {
            "Notifies only after a real change from the last seen state."
        } else {
            "Benachrichtigt erst bei echter \u00c4nderung zum zuletzt gesehenen Zustand."
        }
        WatchlistType.NOW_OPENED,
        WatchlistType.ATTRACTION_OPEN -> if (language == "en") {
            "Tapping the notification opens the matching park."
        } else {
            "Antippen der Benachrichtigung \u00f6ffnet den passenden Park."
        }
        else -> if (language == "en") {
            "Checked locally on this device."
        } else {
            "Wird lokal auf diesem Ger\u00e4t gepr\u00fcft."
        }
    }
}
