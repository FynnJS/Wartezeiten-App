package de.wartezeiten.app.ui.waitingtimes

import de.wartezeiten.app.domain.model.AttractionHistoryDay
import de.wartezeiten.app.domain.model.AttractionHistoryPoint
import de.wartezeiten.app.domain.model.WaitingTime
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.roundToInt

enum class AttractionWaitAdviceType {
    GoNow,
    WaitUntil,
    Typical,
}

data class AttractionWaitAdvice(
    val type: AttractionWaitAdviceType,
    val currentWaitMinutes: Int,
    val typicalWaitMinutes: Int,
    val suggestedLocalTime: LocalTime? = null,
    val expectedWaitMinutes: Int? = null,
    val comparisonDays: Int,
)

internal fun buildAttractionWaitAdvice(
    waitingTimes: List<WaitingTime>,
    historyDays: List<AttractionHistoryDay>,
    currentTimeMillis: Long,
    localTimeOffsetSeconds: Int?,
): Map<String, AttractionWaitAdvice> {
    if (historyDays.size < MIN_COMPARISON_DAYS) return emptyMap()
    val offset = ZoneOffset.ofTotalSeconds(localTimeOffsetSeconds ?: 0)
    val currentTime = Instant.ofEpochMilli(currentTimeMillis).atOffset(offset).toLocalTime()

    return waitingTimes.mapNotNull { waitingTime ->
        val currentWait = waitingTime.waitingTime ?: return@mapNotNull null
        val currentSamples = historyDays.perDayWaits(
            attractionId = waitingTime.attractionId,
            targetTime = currentTime,
            windowMinutes = CURRENT_WINDOW_MINUTES,
            fallbackOffset = offset,
        )
        if (currentSamples.size < MIN_COMPARISON_DAYS) return@mapNotNull null

        val typicalWait = currentSamples.median().roundToInt()
        val futureOptions = FUTURE_OFFSETS_MINUTES.mapNotNull { minutesAhead ->
            val targetTime = currentTime.plusMinutes(minutesAhead.toLong())
            val samples = historyDays.perDayWaits(
                attractionId = waitingTime.attractionId,
                targetTime = targetTime,
                windowMinutes = FUTURE_WINDOW_MINUTES,
                fallbackOffset = offset,
            )
            if (samples.size < MIN_COMPARISON_DAYS) null else FutureWaitOption(
                time = targetTime,
                waitMinutes = samples.median().roundToInt(),
            )
        }
        val bestFuture = futureOptions.minByOrNull { it.waitMinutes }

        val advice = when {
            bestFuture != null &&
                    currentWait - bestFuture.waitMinutes >= MIN_MEANINGFUL_DIFFERENCE_MINUTES &&
                    typicalWait - bestFuture.waitMinutes >= MIN_FUTURE_IMPROVEMENT_MINUTES -> {
                AttractionWaitAdvice(
                    type = AttractionWaitAdviceType.WaitUntil,
                    currentWaitMinutes = currentWait,
                    typicalWaitMinutes = typicalWait,
                    suggestedLocalTime = bestFuture.time,
                    expectedWaitMinutes = bestFuture.waitMinutes,
                    comparisonDays = currentSamples.size,
                )
            }
            typicalWait - currentWait >= MIN_GO_NOW_DIFFERENCE_MINUTES -> {
                AttractionWaitAdvice(
                    type = AttractionWaitAdviceType.GoNow,
                    currentWaitMinutes = currentWait,
                    typicalWaitMinutes = typicalWait,
                    comparisonDays = currentSamples.size,
                )
            }
            else -> AttractionWaitAdvice(
                type = AttractionWaitAdviceType.Typical,
                currentWaitMinutes = currentWait,
                typicalWaitMinutes = typicalWait,
                comparisonDays = currentSamples.size,
            )
        }
        waitingTime.attractionId to advice
    }.toMap()
}

private fun List<AttractionHistoryDay>.perDayWaits(
    attractionId: String,
    targetTime: LocalTime,
    windowMinutes: Int,
    fallbackOffset: ZoneOffset,
): List<Float> = mapNotNull { day ->
    val dayOffset = day.openFrom?.let { runCatching { java.time.OffsetDateTime.parse(it).offset }.getOrNull() }
        ?: fallbackOffset
    val values = day.snapshots.mapNotNull { snapshot ->
        val localTime = Instant.ofEpochMilli(snapshot.capturedAtMillis).atOffset(dayOffset).toLocalTime()
        if (localTime.minuteDistanceTo(targetTime) > windowMinutes) return@mapNotNull null
        snapshot.attractions.firstOrNull { point ->
            point.id == attractionId && point.isOpenMeasurement()
        }?.value?.toFloat()
    }
    values.takeIf { it.isNotEmpty() }?.average()?.toFloat()
}

private fun AttractionHistoryPoint.isOpenMeasurement(): Boolean {
    return value >= 0 && (
            statusCode == 0 ||
                    status.equals("opened", ignoreCase = true) ||
                    status.equals("open", ignoreCase = true)
            )
}

private fun LocalTime.minuteDistanceTo(other: LocalTime): Int {
    val direct = abs(toSecondOfDay() - other.toSecondOfDay()) / 60
    return minOf(direct, MINUTES_PER_DAY - direct)
}

private fun List<Float>.median(): Float {
    val sorted = sorted()
    val middle = size / 2
    return if (size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2f else sorted[middle]
}

private data class FutureWaitOption(
    val time: LocalTime,
    val waitMinutes: Int,
)

private const val MIN_COMPARISON_DAYS = 3
private const val CURRENT_WINDOW_MINUTES = 30
private const val FUTURE_WINDOW_MINUTES = 25
private const val MIN_MEANINGFUL_DIFFERENCE_MINUTES = 10
private const val MIN_FUTURE_IMPROVEMENT_MINUTES = 5
private const val MIN_GO_NOW_DIFFERENCE_MINUTES = 5
private const val MINUTES_PER_DAY = 24 * 60
private val FUTURE_OFFSETS_MINUTES = listOf(30, 60, 90, 120)
