package de.wartezeiten.app.worker

import android.Manifest
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.wartezeiten.app.data.local.dao.AlertHistoryDao
import de.wartezeiten.app.data.local.dao.ParkDao
import de.wartezeiten.app.data.local.dao.WatchlistDao
import de.wartezeiten.app.data.local.PreferencesDataSource
import de.wartezeiten.app.data.local.entity.AlertHistoryEntity
import de.wartezeiten.app.data.local.entity.WatchlistEntity
import de.wartezeiten.app.data.local.entity.WatchlistType
import de.wartezeiten.app.data.remote.WartezeitenApiService
import de.wartezeiten.app.data.remote.dto.WaitingTimeDto
import de.wartezeiten.app.MainActivity
import de.wartezeiten.app.domain.model.isParkCurrentlyOpen
import kotlinx.coroutines.flow.first
import java.util.Locale
import java.util.logging.Level
import java.util.logging.Logger

private const val CHANNEL_ID = "watchlist_alerts"
private const val CHANNEL_NAME = "Park-Alarme"
private const val SUMMARY_NOTIFICATION_ID = 1234
private const val GROUP_KEY = "de.wartezeiten.app.WATCHLIST_ALERTS"
private val logger: Logger = Logger.getLogger("NotificationWorker")

private data class WatchlistNotification(
    val title: String,
    val content: String,
    val parkKey: String,
    val parkName: String,
    val attractionId: String? = null,
)

private sealed interface WatchlistApiResult<out T> {
    data class Success<T>(val data: T) : WatchlistApiResult<T>
    data class Failure(val message: String) : WatchlistApiResult<Nothing>
}

@HiltWorker
class NotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val watchlistDao: WatchlistDao,
    private val alertHistoryDao: AlertHistoryDao,
    private val parkDao: ParkDao,
    private val preferences: PreferencesDataSource,
    private val api: WartezeitenApiService,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        createNotificationChannel()

        val watchlistItems = watchlistDao.observeWatchlist().first()
        if (watchlistItems.isEmpty()) {
            NotificationScheduler.cancelBackgroundChecks(applicationContext)
            return Result.success()
        }

        val groupedByPark = watchlistItems.groupBy { it.parkKey }
        val parkNames = parkDao.observeParks(null).first()
            .flatMap { park -> listOf(park.id to park.name, park.uuid to park.name) }
            .toMap()
        val notifications = mutableListOf<WatchlistNotification>()
        val language = preferences.language.first()

        groupedByPark.forEach { (parkKey, alerts) ->
            runCatching {
                val parkName = parkNames[parkKey] ?: parkKey
                val openingResult = watchlistApiCall("/v1/openingtimes") { api.getOpeningTimes(parkKey) }
                val waitingResult = watchlistApiCall("/v1/waitingtimes") { api.getWaitingTimes(parkKey, language) }
                val crowdResult = watchlistApiCall("/v1/crowdlevel") { api.getCrowdLevel(parkKey) }
                val opening = (openingResult as? WatchlistApiResult.Success)?.data?.firstOrNull()
                val isParkOpen = opening?.let {
                    isParkCurrentlyOpen(
                        openedToday = it.openedToday,
                        openFrom = it.opening,
                        closedFrom = it.closing,
                    )
                }
                val liveWaitingTimes = (waitingResult as? WatchlistApiResult.Success)?.data.orEmpty()
                val hasLiveWaitingTimes = waitingResult is WatchlistApiResult.Success &&
                        liveWaitingTimes.any { it.normalizedStatus() == "opened" }
                val canUseLiveAttractionAlerts = isParkOpen != false && hasLiveWaitingTimes

                if (isParkOpen != null) {
                    collectParkNotifications(parkKey, parkName, isParkOpen, alerts, notifications)
                }

                val crowdValue = (crowdResult as? WatchlistApiResult.Success)
                    ?.data
                    ?.crowdLevel
                    ?.replace(",", ".")
                    ?.toFloatOrNull()
                collectCrowdNotifications(parkKey, parkName, isParkOpen == true, crowdValue, alerts, notifications)
                collectParkAllChangeNotifications(
                    parkKey = parkKey,
                    parkName = parkName,
                    isParkOpen = isParkOpen,
                    crowdValue = crowdValue,
                    waitingTimes = liveWaitingTimes,
                    alerts = alerts,
                    notifications = notifications,
                )

                if (canUseLiveAttractionAlerts) {
                    collectWaitBelowNotifications(parkKey, parkName, liveWaitingTimes, alerts, notifications)
                    collectWaitAboveNotifications(parkKey, parkName, liveWaitingTimes, alerts, notifications)
                    collectStatusNotifications(parkKey, parkName, liveWaitingTimes, alerts, notifications)
                }
            }.onFailure {
                logger.log(Level.WARNING, "Watchlist notification scan failed for park $parkKey", it)
            }
        }

        if (notifications.isNotEmpty()) {
            sendNotifications(notifications)
        }

        return Result.success()
    }

    private suspend fun collectParkNotifications(
        parkKey: String,
        parkName: String,
        isParkOpen: Boolean,
        alerts: List<WatchlistEntity>,
        notifications: MutableList<WatchlistNotification>,
    ) {
        alerts.filter { it.type == WatchlistType.NOW_OPENED }.forEach { alert ->
            if (shouldNotifyBoolean(alert.id, isParkOpen)) {
                notifications.add(
                    WatchlistNotification(
                        title = "Einlass bereit: $parkName",
                        content = "Der Park ist heute geöffnet. Prüfe jetzt Wartezeiten und starte mit den kurzen Wegen.",
                        parkKey = parkKey,
                        parkName = parkName,
                    )
                )
            }
        }

        alerts.filter { it.type == WatchlistType.PARK_STATUS_CHANGED }.forEach { alert ->
            val status = if (isParkOpen) "open" else "closed"
            if (shouldNotifyValue(alert.id, status, notifyOnFirstMatch = false)) {
                notifications.add(
                    WatchlistNotification(
                        title = "$parkName im Park-Ticker",
                        content = if (isParkOpen) {
                            "Heute geöffnet. Ein guter Moment, deine Route zu checken."
                        } else {
                            "Heute geschlossen. Spar dir den Weg und plane um."
                        },
                        parkKey = parkKey,
                        parkName = parkName,
                    )
                )
            }
        }
    }

    private suspend fun collectCrowdNotifications(
        parkKey: String,
        parkName: String,
        isParkOpen: Boolean,
        crowdValue: Float?,
        alerts: List<WatchlistEntity>,
        notifications: MutableList<WatchlistNotification>,
    ) {
        alerts.filter { it.type == WatchlistType.CROWD_LEVEL_BELOW }.forEach { alert ->
            val triggered = isParkOpen && crowdValue != null && crowdValue <= alert.threshold
            if (shouldNotifyBoolean(alert.id, triggered)) {
                notifications.add(
                    WatchlistNotification(
                        title = "Entspannter Park: $parkName",
                        content = "Auslastung bei ${crowdValue?.formatPercent() ?: "?"}%. Gute Zeit für Favoriten, Fotos oder die nächste Runde.",
                        parkKey = parkKey,
                        parkName = parkName,
                    )
                )
            }
        }

        alerts.filter { it.type == WatchlistType.CROWD_LEVEL_ABOVE }.forEach { alert ->
            val triggered = isParkOpen && crowdValue != null && crowdValue >= alert.threshold
            if (shouldNotifyBoolean(alert.id, triggered)) {
                notifications.add(
                    WatchlistNotification(
                        title = "Andrang-Warnung: $parkName",
                        content = "Auslastung bei ${crowdValue?.formatPercent() ?: "?"}%. Plane Snackpause, Shows oder ruhigere Ecken ein.",
                        parkKey = parkKey,
                        parkName = parkName,
                    )
                )
            }
        }
    }

    private suspend fun collectParkAllChangeNotifications(
        parkKey: String,
        parkName: String,
        isParkOpen: Boolean?,
        crowdValue: Float?,
        waitingTimes: List<WaitingTimeDto>,
        alerts: List<WatchlistEntity>,
        notifications: MutableList<WatchlistNotification>,
    ) {
        alerts.filter { it.type == WatchlistType.PARK_ALL_CHANGES }.forEach { alert ->
            val openAttractions = waitingTimes.count { it.normalizedStatus() == "opened" }
            val totalAttractions = waitingTimes.size
            val state = listOf(
                "open=${isParkOpen ?: "unknown"}",
                "crowd=${crowdValue?.toInt() ?: "unknown"}",
                "openAttractions=$openAttractions",
                "totalAttractions=$totalAttractions",
            ).joinToString("|")
            if (shouldNotifyValue(alert.id, state, notifyOnFirstMatch = false)) {
                notifications.add(
                    WatchlistNotification(
                        title = "Park-Änderung: $parkName",
                        content = buildParkChangeNotificationContent(
                            isParkOpen = isParkOpen,
                            crowdValue = crowdValue,
                            openAttractions = openAttractions,
                            totalAttractions = totalAttractions,
                        ),
                        parkKey = parkKey,
                        parkName = parkName,
                    )
                )
            }
        }
    }

    private suspend fun collectWaitBelowNotifications(
        parkKey: String,
        parkName: String,
        waitingTimes: List<WaitingTimeDto>,
        alerts: List<WatchlistEntity>,
        notifications: MutableList<WatchlistNotification>,
    ) {
        alerts.filter { it.type == WatchlistType.WAIT_TIME_BELOW }.forEach { alert ->
            val target = alert.attractionId?.let { id ->
                waitingTimes.firstOrNull { normalizeAttractionId(it) == id }
            }
            val best = target?.takeIf { it.isOpenWithWaitTime() }
                ?: waitingTimes
                    .filter { it.isOpenWithWaitTime() }
                    .minByOrNull { it.waitingTime ?: Int.MAX_VALUE }
            val triggered = best?.waitingTime != null && best.waitingTime <= alert.threshold

            if (shouldNotifyBoolean(alert.id, triggered)) {
                val waitMinutes = best?.waitingTime ?: 0
                notifications.add(
                    WatchlistNotification(
                        title = if (target != null) "Ride-Fenster: ${target.safeName()}" else "Ride-Fenster in $parkName",
                        content = "${best?.safeName() ?: "Eine Attraktion"} liegt bei $waitMinutes Min. Jetzt lohnt sich der Weg.",
                        parkKey = parkKey,
                        parkName = parkName,
                        attractionId = best?.let(::normalizeAttractionId),
                    )
                )
            }
        }
    }

    private suspend fun collectWaitAboveNotifications(
        parkKey: String,
        parkName: String,
        waitingTimes: List<WaitingTimeDto>,
        alerts: List<WatchlistEntity>,
        notifications: MutableList<WatchlistNotification>,
    ) {
        alerts.filter { it.type == WatchlistType.WAIT_TIME_ABOVE }.forEach { alert ->
            val target = alert.attractionId?.let { id ->
                waitingTimes.firstOrNull { normalizeAttractionId(it) == id }
            }
            val longest = target?.takeIf { it.isOpenWithWaitTime() }
                ?: waitingTimes
                    .filter { it.isOpenWithWaitTime() }
                    .maxByOrNull { it.waitingTime ?: Int.MIN_VALUE }
            val triggered = longest?.waitingTime != null && longest.waitingTime >= alert.threshold

            if (shouldNotifyBoolean(alert.id, triggered)) {
                val waitMinutes = longest?.waitingTime ?: 0
                notifications.add(
                    WatchlistNotification(
                        title = if (target != null) "Zu voll: ${target.safeName()}" else "Zu voll in $parkName",
                        content = "${longest?.safeName() ?: "Eine Attraktion"} steht bei $waitMinutes Min. Lieber Route ändern oder später wiederkommen.",
                        parkKey = parkKey,
                        parkName = parkName,
                        attractionId = longest?.let(::normalizeAttractionId),
                    )
                )
            }
        }
    }

    private suspend fun collectStatusNotifications(
        parkKey: String,
        parkName: String,
        waitingTimes: List<WaitingTimeDto>,
        alerts: List<WatchlistEntity>,
        notifications: MutableList<WatchlistNotification>,
    ) {
        alerts
            .filter {
                it.type == WatchlistType.ATTRACTION_OPEN ||
                        it.type == WatchlistType.ATTRACTION_CLOSED ||
                        it.type == WatchlistType.ATTRACTION_MAINTENANCE ||
                        it.type == WatchlistType.ATTRACTION_STATUS_CHANGE ||
                        it.type == WatchlistType.ATTRACTION_ALL_CHANGES
            }
            .forEach { alert ->
                val current = alert.attractionId?.let { id ->
                    waitingTimes.firstOrNull { normalizeAttractionId(it) == id }
                } ?: return@forEach

                val shouldSend = when (alert.type) {
                    WatchlistType.ATTRACTION_OPEN -> shouldNotifyBoolean(alert.id, current.normalizedStatus() == "opened")
                    WatchlistType.ATTRACTION_CLOSED -> shouldNotifyBoolean(alert.id, current.normalizedStatus() == "closed")
                    WatchlistType.ATTRACTION_MAINTENANCE -> shouldNotifyBoolean(alert.id, current.normalizedStatus() == "maintenance")
                    WatchlistType.ATTRACTION_STATUS_CHANGE -> shouldNotifyValue(
                        alert.id,
                        current.normalizedStatus(),
                        notifyOnFirstMatch = current.normalizedStatus() == "opened",
                    )
                    WatchlistType.ATTRACTION_ALL_CHANGES -> shouldNotifyValue(
                        alert.id,
                        current.changeState(),
                        notifyOnFirstMatch = false,
                    )
                    else -> false
                }

                if (shouldSend) {
                    val content = if (alert.type == WatchlistType.ATTRACTION_ALL_CHANGES) {
                        current.toAttractionChangeContent()
                    } else {
                        current.safeStatus().toReadableStatus()
                    }
                    notifications.add(
                        WatchlistNotification(
                            title = current.safeName().toStatusNotificationTitle(current.safeStatus()),
                            content = content,
                            parkKey = parkKey,
                            parkName = parkName,
                            attractionId = if (alert.type == WatchlistType.ATTRACTION_ALL_CHANGES) {
                                null
                            } else {
                                normalizeAttractionId(current)
                            },
                        )
                    )
                }
            }
    }

    private fun notificationIntent(parkKey: String, attractionId: String? = null): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            val uriBuilder = Uri.Builder()
                .scheme("wartezeiten")
                .authority("parks")
                .appendPath(parkKey)
            if (!attractionId.isNullOrBlank()) {
                uriBuilder.appendQueryParameter("attractionId", attractionId)
            }
            data = uriBuilder.build()
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        return PendingIntent.getActivity(
            applicationContext,
            (parkKey + (attractionId ?: "")).notificationId(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Vor-Ort-Hinweise für Wartezeiten, Auslastung und Attraktionsstatus"
        }
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun sendNotifications(notifications: List<WatchlistNotification>) {
        if (!canPostNotifications()) return

        val notificationManager = NotificationManagerCompat.from(applicationContext)
        val groupedByPark = notifications.groupBy { it.parkKey }

        groupedByPark.forEach { (parkKey, parkNotifications) ->
            val first = parkNotifications.first()
            val title = if (parkNotifications.size == 1) {
                first.title
            } else {
                "${parkNotifications.size} Park-Alarme für ${first.parkName}"
            }
            val content = if (parkNotifications.size == 1) {
                first.content
            } else {
                parkNotifications.joinToString(separator = "\n") { "${it.title}: ${it.content}" }
            }
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(de.wartezeiten.app.R.drawable.ic_stat_notification)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(notificationIntent(parkKey, first.attractionId))
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setGroup(GROUP_KEY)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(parkKey.notificationId(), notification)
        }

        val summaryText = if (notifications.size == 1) {
            notifications.first().content
        } else {
            notifications.joinToString(separator = "\n") { "${it.title}: ${it.content}" }
        }
        val summary = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(de.wartezeiten.app.R.drawable.ic_stat_notification)
            .setContentTitle("${notifications.size} neue Park-Alarme")
            .setContentText(groupedByPark.size.toParkCountText())
            .setContentIntent(notificationIntent(notifications.first().parkKey, notifications.first().attractionId))
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(SUMMARY_NOTIFICATION_ID, summary)
    }

    private fun normalizeAttractionId(dto: WaitingTimeDto): String {
        return dto.id ?: dto.safeName().trim().lowercase().replace(Regex("[^a-z0-9]+"), "-")
    }

    private fun String.notificationId(): Int {
        return 10_000 + hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) % 50_000 }
    }

    private suspend fun <T> watchlistApiCall(
        label: String,
        call: suspend () -> retrofit2.Response<T>,
    ): WatchlistApiResult<T> {
        return try {
            val response = call()
            if (!response.isSuccessful) {
                val message = "$label failed with HTTP ${response.code()}"
                logger.warning(message)
                WatchlistApiResult.Failure(message)
            } else {
                response.body()?.let { WatchlistApiResult.Success(it) }
                    ?: WatchlistApiResult.Failure("$label returned an empty body").also {
                        logger.warning(it.message)
                    }
            }
        } catch (exception: Exception) {
            logger.log(Level.WARNING, "$label failed", exception)
            WatchlistApiResult.Failure(exception.message ?: "$label failed")
        }
    }

    private suspend fun shouldNotifyBoolean(alertId: Int, triggered: Boolean): Boolean {
        val lastValue = alertHistoryDao.getHistory(alertId)?.lastNotifiedValue
        val currentValue = triggered.toString()
        if (lastValue != currentValue) {
            alertHistoryDao.upsertHistory(AlertHistoryEntity(alertId, currentValue, System.currentTimeMillis()))
        }
        return triggered && lastValue != currentValue
    }

    private suspend fun shouldNotifyValue(
        alertId: Int,
        currentValue: String,
        notifyOnFirstMatch: Boolean,
    ): Boolean {
        val lastValue = alertHistoryDao.getHistory(alertId)?.lastNotifiedValue
        if (lastValue != currentValue) {
            alertHistoryDao.upsertHistory(AlertHistoryEntity(alertId, currentValue, System.currentTimeMillis()))
        }
        return (lastValue != null && lastValue != currentValue) || (lastValue == null && notifyOnFirstMatch)
    }

    private fun WaitingTimeDto.isOpenWithWaitTime(): Boolean {
        return normalizedStatus() == "opened" && waitingTime != null && waitingTime >= 0
    }

    private fun WaitingTimeDto.safeName(): String {
        return (name as String?).orEmpty().ifBlank { "Eine Attraktion" }
    }

    private fun WaitingTimeDto.safeStatus(): String {
        return (status as String?).orEmpty()
    }

    private fun WaitingTimeDto.normalizedStatus(): String {
        return safeStatus().normalizedStatus()
    }

    private fun WaitingTimeDto.changeState(): String {
        val waitValue = waitingTime?.takeIf { it >= 0 }?.toString() ?: "unknown"
        return "${normalizedStatus()}|wait=$waitValue"
    }

    private fun WaitingTimeDto.toAttractionChangeContent(): String {
        val waitText = waitingTime?.takeIf { it >= 0 }?.let { "$it Min." } ?: "keine Wartezeit"
        return "${safeName()}: ${safeStatus().toReadableShortStatus()}, $waitText"
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun Float.formatPercent(): String {
        return if (this % 1f == 0f) {
            toInt().toString()
        } else {
            String.format(Locale.GERMAN, "%.1f", this)
        }
    }

    private fun buildParkChangeNotificationContent(
        isParkOpen: Boolean?,
        crowdValue: Float?,
        openAttractions: Int,
        totalAttractions: Int,
    ): String {
        val statusText = when (isParkOpen) {
            true -> "Park geoeffnet"
            false -> "Park geschlossen"
            null -> "Oeffnungsstatus unbekannt"
        }
        val crowdText = crowdValue?.let { ", Auslastung ${it.formatPercent()}%" }.orEmpty()
        val attractionText = if (totalAttractions > 0) {
            ", $openAttractions von $totalAttractions Attraktionen offen"
        } else {
            ""
        }
        return statusText + crowdText + attractionText
    }

    private fun String.toReadableShortStatus(): String {
        return when (normalizedStatus()) {
            "opened" -> "offen"
            "closed" -> "geschlossen"
            "maintenance" -> "Technikpause"
            else -> ifBlank { "Status unbekannt" }
        }
    }

    private fun String.toReadableStatus(): String {
        return when (normalizedStatus()) {
            "opened" -> "Wieder offen. Wenn sie auf deiner Liste steht: jetzt hin."
            "closed" -> "Gerade geschlossen. Spar dir den Weg und nimm eine Alternative."
            "maintenance" -> "Technikpause gemeldet. Plane die Attraktion später nochmal ein."
            else -> "Status geändert: $this"
        }
    }

    private fun String.toStatusNotificationTitle(status: String): String {
        return when (status.normalizedStatus()) {
            "opened" -> "Wieder offen: $this"
            "closed" -> "Gerade zu: $this"
            "maintenance" -> "Technikpause: $this"
            else -> "Status-Radar: $this"
        }
    }

    private fun String.normalizedStatus(): String = trim().lowercase(Locale.ROOT)

    private fun Int.toParkCountText(): String {
        return if (this == 1) {
            "1 Park mit neuen Alarmen"
        } else {
            "$this Parks mit neuen Alarmen"
        }
    }
}
