package de.wartezeiten.app.ui.widget

import de.wartezeiten.app.domain.model.AttractionStatus
import de.wartezeiten.app.domain.model.ParkDetail
import de.wartezeiten.app.domain.model.WaitingTime
import de.wartezeiten.app.domain.model.isParkCurrentlyOpen
import java.time.Instant
import kotlin.math.roundToInt

enum class ParkWidgetOpenStatus {
    Open,
    Closed,
    Unknown,
}

data class ParkWidgetAttraction(
    val id: String,
    val name: String,
    val waitingTimeLabel: String,
)

data class ParkWidgetData(
    val parkName: String,
    val status: ParkWidgetOpenStatus,
    val averageWaitingTimeLabel: String,
    val highestWaitingTimeLabel: String,
    val attractions: List<ParkWidgetAttraction>,
    val dataAgeLabel: String,
)

internal fun buildParkWidgetData(
    detail: ParkDetail,
    selectedAttractionIds: List<String>,
    nowMillis: Long = System.currentTimeMillis(),
): ParkWidgetData {
    val openingTimes = detail.openingTimes
    val isCurrentlyOpen = openingTimes?.let {
        isParkCurrentlyOpen(
            openedToday = it.opened,
            openFrom = it.from,
            closedFrom = it.to,
            now = Instant.ofEpochMilli(nowMillis),
        )
    }
    val status = when (isCurrentlyOpen) {
        true -> ParkWidgetOpenStatus.Open
        false -> ParkWidgetOpenStatus.Closed
        null -> ParkWidgetOpenStatus.Unknown
    }
    val liveWaitingTimes = if (isCurrentlyOpen == true) {
        detail.waitingTimes.filter { it.status == AttractionStatus.Opened && it.waitingTime != null }
    } else {
        emptyList()
    }
    val averageWait = liveWaitingTimes
        .mapNotNull { it.waitingTime }
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.roundToInt()
    val highestWait = liveWaitingTimes.mapNotNull { it.waitingTime }.maxOrNull()
    val selectedAttractions = selectedAttractionIds
        .mapNotNull { attractionId -> detail.waitingTimes.firstOrNull { it.attractionId == attractionId } }
    val fallbackAttractions = detail.waitingTimes
        .sortedWith(
            compareByDescending<WaitingTime> { it.status == AttractionStatus.Opened }
                .thenByDescending { it.waitingTime ?: -1 }
                .thenBy { it.name.lowercase() }
        )
    val attractions = (selectedAttractions + fallbackAttractions)
        .distinctBy { it.attractionId }
        .take(3)
        .map {
            ParkWidgetAttraction(
                id = it.attractionId,
                name = it.name,
                waitingTimeLabel = attractionWaitingTimeLabel(it),
            )
        }
    val latestDataMillis = (detail.waitingTimes.maxOfOrNull { it.updatedAtMillis } ?: 0L)
        .coerceAtLeast(detail.park?.updatedAtMillis ?: 0L)

    return ParkWidgetData(
        parkName = detail.park?.name ?: "Lieblingspark",
        status = status,
        averageWaitingTimeLabel = averageWait?.let { "$it min" } ?: "-",
        highestWaitingTimeLabel = highestWait?.let { "$it min" } ?: "-",
        attractions = attractions,
        dataAgeLabel = formatDataAge(latestDataMillis, nowMillis),
    )
}

private fun attractionWaitingTimeLabel(waitingTime: WaitingTime): String {
    return when {
        waitingTime.status == AttractionStatus.Opened && waitingTime.waitingTime != null -> {
            "${waitingTime.waitingTime} min"
        }
        waitingTime.status == AttractionStatus.Opened -> "offen"
        waitingTime.status == AttractionStatus.Maintenance -> "Wartung"
        waitingTime.status == AttractionStatus.ClosedWeather -> "Wetter"
        waitingTime.status == AttractionStatus.Closed -> "zu"
        else -> "-"
    }
}

private fun formatDataAge(latestDataMillis: Long, nowMillis: Long): String {
    if (latestDataMillis <= 0L) return "keine Daten"
    val ageMinutes = ((nowMillis - latestDataMillis).coerceAtLeast(0L) / 60_000L).toInt()
    return when {
        ageMinutes < 1 -> "gerade eben"
        ageMinutes < 60 -> "vor $ageMinutes min"
        ageMinutes < 24 * 60 -> "vor ${ageMinutes / 60} h"
        else -> "vor ${ageMinutes / (24 * 60)} d"
    }
}
