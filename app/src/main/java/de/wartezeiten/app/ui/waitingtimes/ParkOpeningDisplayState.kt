package de.wartezeiten.app.ui.waitingtimes

import de.wartezeiten.app.core.i18n.localized
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
    language: String,
): ParkOpeningDisplayState {
    if (openingTimes == null) {
        return ParkOpeningDisplayState(
            tone = ParkOpeningTone.Unknown,
            statusText = localized(
                language,
                de = "Öffnungszeiten nicht verfügbar",
                en = "Opening times not available",
                fr = "Horaires non disponibles",
                nl = "Openingstijden niet beschikbaar",
            ),
        )
    }

    if (!openingTimes.opened) {
        return ParkOpeningDisplayState(
            tone = ParkOpeningTone.ClosedToday,
            statusText = localized(
                language,
                de = "Heute geschlossen",
                en = "Closed today",
                fr = "Fermé aujourd'hui",
                nl = "Vandaag gesloten",
            ),
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
            tone = if (isCurrentlyOpen || hasOpenAttraction) {
                ParkOpeningTone.Open
            } else {
                ParkOpeningTone.OpenOtherTimeToday
            },
            statusText = buildOpeningWindowStatusText(openTime, closeTime, language),
            crowdText = buildCrowdText(level = estimate.level, language = language),
        )
    } else {
        ParkOpeningDisplayState(
            tone = ParkOpeningTone.OpenOtherTimeToday,
            statusText = buildOpeningWindowStatusText(openTime, closeTime, language),
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

private fun buildOpeningWindowStatusText(openTime: LocalTime?, closeTime: LocalTime?, language: String): String {
    return when {
        openTime != null && closeTime != null -> localized(
            language,
            de = "Heute geöffnet von ${openTime.format(displayTimeFormatter)} Uhr bis ${closeTime.format(displayTimeFormatter)} Uhr",
            en = "Open today from ${openTime.format(displayTimeFormatter)} to ${closeTime.format(displayTimeFormatter)}",
            fr = "Ouvert aujourd'hui de ${openTime.format(displayTimeFormatter)} à ${closeTime.format(displayTimeFormatter)}",
            nl = "Vandaag geopend van ${openTime.format(displayTimeFormatter)} tot ${closeTime.format(displayTimeFormatter)}",
        )
        openTime != null -> localized(
            language,
            de = "Heute geöffnet ab: ${openTime.format(displayTimeFormatter)} Uhr",
            en = "Opens today at: ${openTime.format(displayTimeFormatter)}",
            fr = "Ouvert aujourd'hui dès ${openTime.format(displayTimeFormatter)}",
            nl = "Vandaag geopend vanaf: ${openTime.format(displayTimeFormatter)}",
        )
        closeTime != null -> localized(
            language,
            de = "Heute geöffnet bis: ${closeTime.format(displayTimeFormatter)} Uhr",
            en = "Open today until: ${closeTime.format(displayTimeFormatter)}",
            fr = "Ouvert aujourd'hui jusqu'à ${closeTime.format(displayTimeFormatter)}",
            nl = "Vandaag geopend tot: ${closeTime.format(displayTimeFormatter)}",
        )
        else -> localized(
            language,
            de = "Heute geöffnet",
            en = "Open today",
            fr = "Ouvert aujourd'hui",
            nl = "Vandaag geopend",
        )
    }
}

private fun buildCrowdText(level: Float?, language: String): String? {
    if (level == null) return null
    val formattedLevel = String.format(Locale.GERMAN, "%.0f%%", level)
    val description = when {
        level < 30 -> localized(language, de = "Wenig los", en = "Quiet", fr = "Peu de monde", nl = "Weinig drukte")
        level < 60 -> localized(language, de = "Normal", en = "Normal", fr = "Normal", nl = "Normaal")
        level < 80 -> localized(language, de = "Voll", en = "Crowded", fr = "Plein", nl = "Druk")
        else -> localized(language, de = "Sehr voll", en = "Very crowded", fr = "Très plein", nl = "Heel druk")
    }
    return localized(
        language,
        de = "Auslastung: ca. $formattedLevel ($description)",
        en = "Occupancy: about $formattedLevel ($description)",
        fr = "Affluence : environ $formattedLevel ($description)",
        nl = "Bezetting: ongeveer $formattedLevel ($description)",
    )
}

private val displayTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
