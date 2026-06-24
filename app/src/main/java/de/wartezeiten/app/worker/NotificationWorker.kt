package de.wartezeiten.app.worker

import android.Manifest
import android.annotation.SuppressLint
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
import de.wartezeiten.app.core.i18n.localized
import de.wartezeiten.app.core.network.toApiLanguage
import de.wartezeiten.app.data.remote.WartezeitenApiService
import de.wartezeiten.app.data.remote.dto.WaitingTimeDto
import de.wartezeiten.app.MainActivity
import de.wartezeiten.app.push.PushRegistrationManager
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
    val alertId: Int,
    val notifyOnce: Boolean,
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
    private val pushRegistrationManager: PushRegistrationManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        createNotificationChannel()

        val watchlistItems = watchlistDao.observeActiveWatchlist().first()
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
        var successfulParkScans = 0

        groupedByPark.forEach { (parkKey, alerts) ->
            runCatching {
                val parkName = parkNames[parkKey] ?: parkKey
                val openingResult = watchlistApiCall("/v1/openingtimes") { api.getOpeningTimes(parkKey) }
                val waitingResult = watchlistApiCall("/v1/waitingtimes") { api.getWaitingTimes(parkKey, language.toApiLanguage()) }
                val crowdResult = watchlistApiCall("/v1/crowdlevel") { api.getCrowdLevel(parkKey) }
                if (
                    openingResult is WatchlistApiResult.Failure &&
                    waitingResult is WatchlistApiResult.Failure &&
                    crowdResult is WatchlistApiResult.Failure
                ) {
                    error("All watchlist API requests failed for $parkKey")
                }
                successfulParkScans += 1
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
                val eligibleAlerts = alerts.filter { alert ->
                    !alert.isInQuietHours(System.currentTimeMillis(), opening?.opening) &&
                            (!alert.onlyWhenParkOpen || isParkOpen == true)
                }

                if (isParkOpen != null) {
                    collectParkNotifications(parkKey, parkName, isParkOpen, eligibleAlerts, notifications, language)
                }

                val crowdValue = (crowdResult as? WatchlistApiResult.Success)
                    ?.data
                    ?.crowdLevel
                    ?.replace(",", ".")
                    ?.toFloatOrNull()
                collectCrowdNotifications(parkKey, parkName, isParkOpen == true, crowdValue, eligibleAlerts, notifications, language)
                collectParkAllChangeNotifications(
                    parkKey = parkKey,
                    parkName = parkName,
                    isParkOpen = isParkOpen,
                    crowdValue = crowdValue,
                    waitingTimes = liveWaitingTimes,
                    alerts = eligibleAlerts,
                    notifications = notifications,
                    language = language,
                )
                collectDailySummaryNotifications(
                    parkKey = parkKey,
                    parkName = parkName,
                    isParkOpen = isParkOpen,
                    crowdValue = crowdValue,
                    waitingTimes = liveWaitingTimes,
                    openingOffsetSource = opening?.opening,
                    alerts = eligibleAlerts,
                    notifications = notifications,
                    language = language,
                )

                if (canUseLiveAttractionAlerts) {
                    collectWaitBelowNotifications(parkKey, parkName, liveWaitingTimes, eligibleAlerts, notifications, language)
                    collectWaitAboveNotifications(parkKey, parkName, liveWaitingTimes, eligibleAlerts, notifications, language)
                    collectStatusNotifications(parkKey, parkName, liveWaitingTimes, eligibleAlerts, notifications, language)
                }
            }.onFailure {
                logger.log(Level.WARNING, "Watchlist notification scan failed for park $parkKey", it)
            }
        }

        if (notifications.isNotEmpty()) {
            if (sendNotifications(notifications, language)) {
                val completedOneShotIds = notifications.filter { it.notifyOnce }.map { it.alertId }.distinct()
                completedOneShotIds.forEach { alertId ->
                    watchlistDao.setEnabled(alertId, false)
                }
                if (completedOneShotIds.isNotEmpty()) {
                    pushRegistrationManager.syncCurrentWatchlist()
                }
            }
        }

        return if (successfulParkScans == 0) Result.retry() else Result.success()
    }

    private suspend fun collectParkNotifications(
        parkKey: String,
        parkName: String,
        isParkOpen: Boolean,
        alerts: List<WatchlistEntity>,
        notifications: MutableList<WatchlistNotification>,
        language: String,
    ) {
        alerts.filter { it.type == WatchlistType.NOW_OPENED }.forEach { alert ->
            if (shouldNotifyBoolean(alert, isParkOpen)) {
                notifications.add(
                    WatchlistNotification(
                        alertId = alert.id,
                        notifyOnce = alert.notifyOnce,
                        title = localized(
                            language,
                            de = "Einlass bereit: $parkName",
                            en = "Ready to enter: $parkName",
                            fr = "Entrée prête : $parkName",
                            nl = "Klaar om naar binnen te gaan: $parkName",
                        ),
                        content = localized(
                            language,
                            de = "Der Park ist heute geöffnet. Prüfe jetzt Wartezeiten und starte mit den kurzen Wegen.",
                            en = "The park is open today. Check wait times now and start with the shortest ones.",
                            fr = "Le parc est ouvert aujourd'hui. Vérifie les temps d'attente et commence par les plus courts.",
                            nl = "Het park is vandaag open. Bekijk nu de wachttijden en begin met de kortste.",
                        ),
                        parkKey = parkKey,
                        parkName = parkName,
                    )
                )
            }
        }

        alerts.filter { it.type == WatchlistType.PARK_STATUS_CHANGED }.forEach { alert ->
            val status = if (isParkOpen) "open" else "closed"
            if (shouldNotifyValue(alert, status, notifyOnFirstMatch = false)) {
                notifications.add(
                    WatchlistNotification(
                        alertId = alert.id,
                        notifyOnce = alert.notifyOnce,
                        title = localized(
                            language,
                            de = "$parkName im Park-Ticker",
                            en = "$parkName status ticker",
                            fr = "$parkName : ticker du parc",
                            nl = "$parkName parkticker",
                        ),
                        content = if (isParkOpen) {
                            localized(
                                language,
                                de = "Heute geöffnet. Ein guter Moment, deine Route zu checken.",
                                en = "Open today. A good moment to check your route.",
                                fr = "Ouvert aujourd'hui. Un bon moment pour vérifier ton itinéraire.",
                                nl = "Vandaag open. Een goed moment om je route te checken.",
                            )
                        } else {
                            localized(
                                language,
                                de = "Heute geschlossen. Spar dir den Weg und plane um.",
                                en = "Closed today. Save the trip and plan around it.",
                                fr = "Fermé aujourd'hui. Évite le déplacement et prévois autre chose.",
                                nl = "Vandaag gesloten. Bespaar je de moeite en plan om.",
                            )
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
        language: String,
    ) {
        alerts.filter { it.type == WatchlistType.CROWD_LEVEL_BELOW }.forEach { alert ->
            val triggered = isParkOpen && crowdValue != null && crowdValue <= alert.threshold
            if (shouldNotifyBoolean(alert, triggered)) {
                notifications.add(
                    WatchlistNotification(
                        alertId = alert.id,
                        notifyOnce = alert.notifyOnce,
                        title = localized(
                            language,
                            de = "Entspannter Park: $parkName",
                            en = "Quiet park: $parkName",
                            fr = "Parc tranquille : $parkName",
                            nl = "Rustig park: $parkName",
                        ),
                        content = localized(
                            language,
                            de = "Auslastung bei ${crowdValue?.formatPercent() ?: "?"}%. Gute Zeit für Favoriten, Fotos oder die nächste Runde.",
                            en = "Crowd level at ${crowdValue?.formatPercent() ?: "?"}%. A good time for favorites, photos, or another round.",
                            fr = "Fréquentation à ${crowdValue?.formatPercent() ?: "?"}%. Bon moment pour tes attractions favorites, des photos ou un nouveau tour.",
                            nl = "Drukte op ${crowdValue?.formatPercent() ?: "?"}%. Goed moment voor favorieten, foto's of nog een rondje.",
                        ),
                        parkKey = parkKey,
                        parkName = parkName,
                    )
                )
            }
        }

        alerts.filter { it.type == WatchlistType.CROWD_LEVEL_ABOVE }.forEach { alert ->
            val triggered = isParkOpen && crowdValue != null && crowdValue >= alert.threshold
            if (shouldNotifyBoolean(alert, triggered)) {
                notifications.add(
                    WatchlistNotification(
                        alertId = alert.id,
                        notifyOnce = alert.notifyOnce,
                        title = localized(
                            language,
                            de = "Andrang-Warnung: $parkName",
                            en = "Crowd warning: $parkName",
                            fr = "Alerte affluence : $parkName",
                            nl = "Drukte-waarschuwing: $parkName",
                        ),
                        content = localized(
                            language,
                            de = "Auslastung bei ${crowdValue?.formatPercent() ?: "?"}%. Plane Snackpause, Shows oder ruhigere Ecken ein.",
                            en = "Crowd level at ${crowdValue?.formatPercent() ?: "?"}%. Plan a snack break, shows, or quieter corners.",
                            fr = "Fréquentation à ${crowdValue?.formatPercent() ?: "?"}%. Prévois une pause snack, des spectacles ou des coins plus calmes.",
                            nl = "Drukte op ${crowdValue?.formatPercent() ?: "?"}%. Plan een snackpauze, shows of rustigere hoekjes.",
                        ),
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
        language: String,
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
            if (shouldNotifyValue(alert, state, notifyOnFirstMatch = false)) {
                notifications.add(
                    WatchlistNotification(
                        alertId = alert.id,
                        notifyOnce = alert.notifyOnce,
                        title = localized(
                            language,
                            de = "Park-Änderung: $parkName",
                            en = "Park change: $parkName",
                            fr = "Changement au parc : $parkName",
                            nl = "Parkwijziging: $parkName",
                        ),
                        content = buildParkChangeNotificationContent(
                            isParkOpen = isParkOpen,
                            crowdValue = crowdValue,
                            openAttractions = openAttractions,
                            totalAttractions = totalAttractions,
                            language = language,
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
        language: String,
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

            if (shouldNotifyBoolean(alert, triggered)) {
                val waitMinutes = best?.waitingTime ?: 0
                val name = best?.safeName(language) ?: localized(
                    language,
                    de = "Eine Attraktion",
                    en = "An attraction",
                    fr = "Une attraction",
                    nl = "Een attractie",
                )
                notifications.add(
                    WatchlistNotification(
                        alertId = alert.id,
                        notifyOnce = alert.notifyOnce,
                        title = if (target != null) {
                            localized(
                                language,
                                de = "Ride-Fenster: ${target.safeName(language)}",
                                en = "Ride window: ${target.safeName(language)}",
                                fr = "Créneau favorable : ${target.safeName(language)}",
                                nl = "Rijvenster: ${target.safeName(language)}",
                            )
                        } else {
                            localized(
                                language,
                                de = "Ride-Fenster in $parkName",
                                en = "Ride window in $parkName",
                                fr = "Créneau favorable à $parkName",
                                nl = "Rijvenster in $parkName",
                            )
                        },
                        content = localized(
                            language,
                            de = "$name liegt bei $waitMinutes Min. Jetzt lohnt sich der Weg.",
                            en = "$name is at $waitMinutes min. Worth heading there now.",
                            fr = "$name est à $waitMinutes min. Ça vaut le détour maintenant.",
                            nl = "$name staat op $waitMinutes min. Nu de moeite waard.",
                        ),
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
        language: String,
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

            if (shouldNotifyBoolean(alert, triggered)) {
                val waitMinutes = longest?.waitingTime ?: 0
                val name = longest?.safeName(language) ?: localized(
                    language,
                    de = "Eine Attraktion",
                    en = "An attraction",
                    fr = "Une attraction",
                    nl = "Een attractie",
                )
                notifications.add(
                    WatchlistNotification(
                        alertId = alert.id,
                        notifyOnce = alert.notifyOnce,
                        title = if (target != null) {
                            localized(
                                language,
                                de = "Zu voll: ${target.safeName(language)}",
                                en = "Too crowded: ${target.safeName(language)}",
                                fr = "Trop d'affluence : ${target.safeName(language)}",
                                nl = "Te druk: ${target.safeName(language)}",
                            )
                        } else {
                            localized(
                                language,
                                de = "Zu voll in $parkName",
                                en = "Too crowded in $parkName",
                                fr = "Trop d'affluence à $parkName",
                                nl = "Te druk in $parkName",
                            )
                        },
                        content = localized(
                            language,
                            de = "$name steht bei $waitMinutes Min. Lieber Route ändern oder später wiederkommen.",
                            en = "$name is at $waitMinutes min. Better change route or come back later.",
                            fr = "$name est à $waitMinutes min. Mieux vaut changer d'itinéraire ou revenir plus tard.",
                            nl = "$name staat op $waitMinutes min. Beter van route veranderen of later terugkomen.",
                        ),
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
        language: String,
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
                    WatchlistType.ATTRACTION_OPEN -> shouldNotifyBoolean(alert, current.normalizedStatus() == "opened")
                    WatchlistType.ATTRACTION_CLOSED -> shouldNotifyBoolean(alert, current.normalizedStatus() == "closed")
                    WatchlistType.ATTRACTION_MAINTENANCE -> shouldNotifyBoolean(alert, current.normalizedStatus() == "maintenance")
                    WatchlistType.ATTRACTION_STATUS_CHANGE -> shouldNotifyValue(
                        alert,
                        current.normalizedStatus(),
                        notifyOnFirstMatch = current.normalizedStatus() == "opened",
                    )
                    WatchlistType.ATTRACTION_ALL_CHANGES -> shouldNotifyValue(
                        alert,
                        current.changeState(),
                        notifyOnFirstMatch = false,
                    )
                    else -> false
                }

                if (shouldSend) {
                    val content = if (alert.type == WatchlistType.ATTRACTION_ALL_CHANGES) {
                        current.toAttractionChangeContent(language)
                    } else {
                        current.safeStatus().toReadableStatus(language)
                    }
                    notifications.add(
                        WatchlistNotification(
                            alertId = alert.id,
                            notifyOnce = alert.notifyOnce,
                            title = current.safeName(language).toStatusNotificationTitle(current.safeStatus(), language),
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

    @SuppressLint("MissingPermission")
    private fun sendNotifications(notifications: List<WatchlistNotification>, language: String): Boolean {
        if (!canPostNotifications()) return false

        val notificationManager = NotificationManagerCompat.from(applicationContext)
        val groupedByPark = notifications.groupBy { it.parkKey }

        groupedByPark.forEach { (parkKey, parkNotifications) ->
            val first = parkNotifications.first()
            val title = if (parkNotifications.size == 1) {
                first.title
            } else {
                localized(
                    language,
                    de = "${parkNotifications.size} Park-Alarme für ${first.parkName}",
                    en = "${parkNotifications.size} park alerts for ${first.parkName}",
                    fr = "${parkNotifications.size} alertes de parc pour ${first.parkName}",
                    nl = "${parkNotifications.size} parkmeldingen voor ${first.parkName}",
                )
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
            .setContentTitle(
                localized(
                    language,
                    de = "${notifications.size} neue Park-Alarme",
                    en = "${notifications.size} new park alerts",
                    fr = "${notifications.size} nouvelles alertes de parc",
                    nl = "${notifications.size} nieuwe parkmeldingen",
                )
            )
            .setContentText(groupedByPark.size.toParkCountText(language))
            .setContentIntent(notificationIntent(notifications.first().parkKey, notifications.first().attractionId))
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(SUMMARY_NOTIFICATION_ID, summary)
        return true
    }

    private fun normalizeAttractionId(dto: WaitingTimeDto): String {
        return dto.id ?: dto.safeName("de").trim().lowercase().replace(Regex("[^a-z0-9]+"), "-")
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

    private suspend fun shouldNotifyBoolean(alert: WatchlistEntity, triggered: Boolean): Boolean {
        val history = alertHistoryDao.getHistory(alert.id)
        val lastValue = history?.lastNotifiedValue
        val currentValue = triggered.toString()
        val shouldNotify = triggered && lastValue != currentValue && alert.cooldownElapsed(history)
        if (lastValue != currentValue) {
            alertHistoryDao.upsertHistory(
                AlertHistoryEntity(
                    alertId = alert.id,
                    lastNotifiedValue = currentValue,
                    lastNotifiedAtMillis = System.currentTimeMillis(),
                    lastTriggeredAtMillis = if (shouldNotify) System.currentTimeMillis() else history?.lastTriggeredAtMillis ?: 0L,
                )
            )
        }
        return shouldNotify
    }

    private suspend fun collectDailySummaryNotifications(
        parkKey: String,
        parkName: String,
        isParkOpen: Boolean?,
        crowdValue: Float?,
        waitingTimes: List<WaitingTimeDto>,
        openingOffsetSource: String?,
        alerts: List<WatchlistEntity>,
        notifications: MutableList<WatchlistNotification>,
        language: String,
    ) {
        val dayKey = dailySummaryDayKey(System.currentTimeMillis(), openingOffsetSource) ?: return
        val openAttractions = waitingTimes.count { it.normalizedStatus() == "opened" }
        alerts.filter { it.type == WatchlistType.DAILY_SUMMARY }.forEach { alert ->
            if (shouldNotifyValue(alert, dayKey, notifyOnFirstMatch = true)) {
                notifications.add(
                    WatchlistNotification(
                        alertId = alert.id,
                        notifyOnce = alert.notifyOnce,
                        title = localized(
                            language,
                            de = "Tagesblick: $parkName",
                            en = "Daily summary: $parkName",
                            fr = "Résumé du jour : $parkName",
                            nl = "Dagoverzicht: $parkName",
                        ),
                        content = buildParkChangeNotificationContent(
                            isParkOpen = isParkOpen,
                            crowdValue = crowdValue,
                            openAttractions = openAttractions,
                            totalAttractions = waitingTimes.size,
                            language = language,
                        ),
                        parkKey = parkKey,
                        parkName = parkName,
                    )
                )
            }
        }
    }

    private suspend fun shouldNotifyValue(
        alert: WatchlistEntity,
        currentValue: String,
        notifyOnFirstMatch: Boolean,
    ): Boolean {
        val history = alertHistoryDao.getHistory(alert.id)
        val lastValue = history?.lastNotifiedValue
        val changed = (lastValue != null && lastValue != currentValue) || (lastValue == null && notifyOnFirstMatch)
        val shouldNotify = changed && alert.cooldownElapsed(history)
        if (lastValue != currentValue) {
            alertHistoryDao.upsertHistory(
                AlertHistoryEntity(
                    alertId = alert.id,
                    lastNotifiedValue = currentValue,
                    lastNotifiedAtMillis = System.currentTimeMillis(),
                    lastTriggeredAtMillis = if (shouldNotify) System.currentTimeMillis() else history?.lastTriggeredAtMillis ?: 0L,
                )
            )
        }
        return shouldNotify
    }

    private fun WaitingTimeDto.isOpenWithWaitTime(): Boolean {
        return normalizedStatus() == "opened" && waitingTime != null && waitingTime >= 0
    }

    private fun WaitingTimeDto.safeName(language: String): String {
        return (name as String?).orEmpty().ifBlank {
            localized(
                language,
                de = "Eine Attraktion",
                en = "An attraction",
                fr = "Une attraction",
                nl = "Een attractie",
            )
        }
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

    private fun WaitingTimeDto.toAttractionChangeContent(language: String): String {
        val waitText = waitingTime?.takeIf { it >= 0 }?.let {
            localized(
                language,
                de = "$it Min.",
                en = "$it min",
                fr = "$it min",
                nl = "$it min",
            )
        } ?: localized(
            language,
            de = "keine Wartezeit",
            en = "no wait time",
            fr = "pas de temps d'attente",
            nl = "geen wachttijd",
        )
        return "${safeName(language)}: ${safeStatus().toReadableShortStatus(language)}, $waitText"
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
        language: String,
    ): String {
        val statusText = when (isParkOpen) {
            true -> localized(
                language,
                de = "Park geoeffnet",
                en = "Park open",
                fr = "Parc ouvert",
                nl = "Park open",
            )
            false -> localized(
                language,
                de = "Park geschlossen",
                en = "Park closed",
                fr = "Parc fermé",
                nl = "Park gesloten",
            )
            null -> localized(
                language,
                de = "Oeffnungsstatus unbekannt",
                en = "Opening status unknown",
                fr = "Statut d'ouverture inconnu",
                nl = "Openingsstatus onbekend",
            )
        }
        val crowdText = crowdValue?.let {
            localized(
                language,
                de = ", Auslastung ${it.formatPercent()}%",
                en = ", crowd level ${it.formatPercent()}%",
                fr = ", fréquentation ${it.formatPercent()}%",
                nl = ", drukte ${it.formatPercent()}%",
            )
        }.orEmpty()
        val attractionText = if (totalAttractions > 0) {
            localized(
                language,
                de = ", $openAttractions von $totalAttractions Attraktionen offen",
                en = ", $openAttractions of $totalAttractions attractions open",
                fr = ", $openAttractions sur $totalAttractions attractions ouvertes",
                nl = ", $openAttractions van $totalAttractions attracties open",
            )
        } else {
            ""
        }
        return statusText + crowdText + attractionText
    }

    private fun String.toReadableShortStatus(language: String): String {
        return when (normalizedStatus()) {
            "opened" -> localized(language, de = "offen", en = "open", fr = "ouvert", nl = "open")
            "closed" -> localized(language, de = "geschlossen", en = "closed", fr = "fermé", nl = "gesloten")
            "maintenance" -> localized(
                language,
                de = "Technikpause",
                en = "maintenance break",
                fr = "pause technique",
                nl = "technische pauze",
            )
            else -> ifBlank {
                localized(
                    language,
                    de = "Status unbekannt",
                    en = "status unknown",
                    fr = "statut inconnu",
                    nl = "status onbekend",
                )
            }
        }
    }

    private fun String.toReadableStatus(language: String): String {
        return when (normalizedStatus()) {
            "opened" -> localized(
                language,
                de = "Wieder offen. Wenn sie auf deiner Liste steht: jetzt hin.",
                en = "Open again. If it's on your list: go now.",
                fr = "De nouveau ouvert. Si elle est sur ta liste : vas-y maintenant.",
                nl = "Weer open. Als hij op je lijst staat: ga er nu naartoe.",
            )
            "closed" -> localized(
                language,
                de = "Gerade geschlossen. Spar dir den Weg und nimm eine Alternative.",
                en = "Just closed. Save the trip and pick an alternative.",
                fr = "Vient de fermer. Évite le détour et choisis une alternative.",
                nl = "Net gesloten. Bespaar je de moeite en kies een alternatief.",
            )
            "maintenance" -> localized(
                language,
                de = "Technikpause gemeldet. Plane die Attraktion später nochmal ein.",
                en = "Maintenance reported. Plan to come back to this attraction later.",
                fr = "Pause technique signalée. Prévois de revenir à cette attraction plus tard.",
                nl = "Technische pauze gemeld. Plan deze attractie later opnieuw in.",
            )
            else -> localized(
                language,
                de = "Status geändert: $this",
                en = "Status changed: $this",
                fr = "Statut modifié : $this",
                nl = "Status gewijzigd: $this",
            )
        }
    }

    private fun String.toStatusNotificationTitle(status: String, language: String): String {
        return when (status.normalizedStatus()) {
            "opened" -> localized(
                language,
                de = "Wieder offen: $this",
                en = "Open again: $this",
                fr = "De nouveau ouvert : $this",
                nl = "Weer open: $this",
            )
            "closed" -> localized(
                language,
                de = "Gerade zu: $this",
                en = "Just closed: $this",
                fr = "Vient de fermer : $this",
                nl = "Net gesloten: $this",
            )
            "maintenance" -> localized(
                language,
                de = "Technikpause: $this",
                en = "Maintenance: $this",
                fr = "Pause technique : $this",
                nl = "Technische pauze: $this",
            )
            else -> localized(
                language,
                de = "Status-Radar: $this",
                en = "Status radar: $this",
                fr = "Radar de statut : $this",
                nl = "Statusradar: $this",
            )
        }
    }

    private fun String.normalizedStatus(): String = trim().lowercase(Locale.ROOT)

    private fun Int.toParkCountText(language: String): String {
        return if (this == 1) {
            localized(
                language,
                de = "1 Park mit neuen Alarmen",
                en = "1 park with new alerts",
                fr = "1 parc avec de nouvelles alertes",
                nl = "1 park met nieuwe meldingen",
            )
        } else {
            localized(
                language,
                de = "$this Parks mit neuen Alarmen",
                en = "$this parks with new alerts",
                fr = "$this parcs avec de nouvelles alertes",
                nl = "$this parken met nieuwe meldingen",
            )
        }
    }
}
