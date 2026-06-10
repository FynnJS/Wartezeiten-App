package de.wartezeiten.app.ui.waitingtimes

import de.wartezeiten.app.domain.model.CrowdLevel
import de.wartezeiten.app.domain.model.AttractionStatus
import de.wartezeiten.app.domain.model.OpeningTimes
import de.wartezeiten.app.domain.model.WaitingTime
import de.wartezeiten.app.domain.model.estimateCrowdLevel
import de.wartezeiten.app.domain.model.parkOpeningWindow
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class ParkOpeningTone {
    Open,
    OpenOtherTimeToday,
    ClosedToday,
    Unknown,
}

internal data class ParkOpeningDisplayState(
    val tone: ParkOpeningTone,
    val statusText: String,
    val crowdText: String? = null,
)

internal fun parkOpeningDisplayState(
    openingTimes: OpeningTimes?,
    crowdLevel: CrowdLevel?,
    waitingTimes: List<WaitingTime> = emptyList(),
    currentTimeMillis: Long,
    localTimeOffsetSeconds: Int?,
): ParkOpeningDisplayState {
    if (openingTimes == null) {
        return ParkOpeningDisplayState(
            tone = ParkOpeningTone.Unknown,
            statusText = "\u00d6ffnungszeiten nicht verf\u00fcgbar",
        )
    }

    if (!openingTimes.opened) {
        return ParkOpeningDisplayState(
            tone = ParkOpeningTone.ClosedToday,
            statusText = "Heute geschlossen",
        )
    }

    val zoneId = localTimeOffsetSeconds?.let { ZoneOffset.ofTotalSeconds(it) } ?: ZoneId.systemDefault()
    val now = Instant.ofEpochMilli(currentTimeMillis).atZone(zoneId)
    val window = parkOpeningWindow(openingTimes.from, openingTimes.to, zoneId)
    val openAt = window.opensAt
    val closeAt = window.closesAt
    val openTime = window.openTime
    val closeTime = window.closeTime

    val isCurrentlyOpen = when {
        openAt != null && closeAt != null -> !now.isBefore(openAt) && now.isBefore(closeAt)
        openTime != null && closeTime != null -> isWithinOpeningHours(
            currentTime = now.toLocalTime(),
            openTime = openTime,
            closeTime = closeTime,
        )
        else -> true
    }
    val hasOpenAttraction = waitingTimes.any { it.status == AttractionStatus.Opened }
    val canShowCrowdLevel = isCurrentlyOpen || hasOpenAttraction

    return if (canShowCrowdLevel) {
        val estimate = estimateCrowdLevel(
            waitingTimes = waitingTimes,
            apiCrowdLevel = crowdLevel?.level,
        )
        ParkOpeningDisplayState(
            tone = if (isCurrentlyOpen) ParkOpeningTone.Open else ParkOpeningTone.OpenOtherTimeToday,
            statusText = buildOpeningWindowStatusText(openTime, closeTime),
            crowdText = buildCrowdText(level = estimate.level),
        )
    } else {
        ParkOpeningDisplayState(
            tone = ParkOpeningTone.OpenOtherTimeToday,
            statusText = buildOpeningWindowStatusText(openTime, closeTime),
        )
    }
}

private fun isWithinOpeningHours(
    currentTime: LocalTime,
    openTime: LocalTime,
    closeTime: LocalTime,
): Boolean {
    return if (closeTime.isAfter(openTime)) {
        !currentTime.isBefore(openTime) && currentTime.isBefore(closeTime)
    } else {
        !currentTime.isBefore(openTime) || currentTime.isBefore(closeTime)
    }
}

private fun buildOpeningWindowStatusText(openTime: LocalTime?, closeTime: LocalTime?): String {
    return when {
        openTime != null && closeTime != null ->
            "Heute ge\u00f6ffnet von ${openTime.format(displayTimeFormatter)} Uhr bis ${closeTime.format(displayTimeFormatter)} Uhr"
        openTime != null -> "Heute ge\u00f6ffnet ab: ${openTime.format(displayTimeFormatter)} Uhr"
        closeTime != null -> "Heute ge\u00f6ffnet bis: ${closeTime.format(displayTimeFormatter)} Uhr"
        else -> "Heute ge\u00f6ffnet"
    }
}

private fun buildCrowdText(level: Float?): String? {
    if (level == null) return null
    val formattedLevel = String.format(Locale.GERMAN, "%.0f%%", level)
    val description = when {
        level < 30 -> "Wenig los"
        level < 60 -> "Normal"
        level < 80 -> "Voll"
        else -> "Sehr voll"
    }
    return "Auslastung: ca. $formattedLevel ($description)"
}

private val displayTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
