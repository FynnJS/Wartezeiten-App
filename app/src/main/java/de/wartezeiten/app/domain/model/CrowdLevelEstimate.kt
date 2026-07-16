package de.wartezeiten.app.domain.model

import kotlin.math.round

data class CrowdLevelEstimate(
    val level: Float?,
    val source: CrowdLevelSource,
)

enum class CrowdLevelSource {
    WaitingTimes,
    Api,
    None,
}

fun estimateCrowdLevel(
    waitingTimes: List<WaitingTime>,
    apiCrowdLevel: Float?,
): CrowdLevelEstimate {
    val waitBasedLevel = waitingTimes.estimatedCrowdLevelFromWaits()
    return when {
        waitBasedLevel != null -> CrowdLevelEstimate(waitBasedLevel, CrowdLevelSource.WaitingTimes)
        apiCrowdLevel != null -> CrowdLevelEstimate(apiCrowdLevel.roundedToNearestFive(), CrowdLevelSource.Api)
        else -> CrowdLevelEstimate(null, CrowdLevelSource.None)
    }
}

fun List<WaitingTime>.estimatedCrowdLevelFromWaits(): Float? {
    val waits = filter { it.status == AttractionStatus.Opened }
        .mapNotNull { it.waitingTime }
        .filter { it >= 0 }
        .map { it.coerceAtMost(120) }
        .sorted()

    if (waits.size < 3) return null

    val averageWait = waits.average()
    val p75Wait = waits[((waits.size - 1) * 0.75).toInt()]
    val estimated = ((averageWait / 40.0) * 0.6 + (p75Wait / 60.0) * 0.4) * 100.0
    return estimated.toFloat().coerceIn(0f, 100f).roundedToNearestFive()
}

fun Float.roundedToNearestFive(): Float {
    return (round(this.coerceIn(0f, 100f) / 5f) * 5f).coerceIn(0f, 100f)
}
