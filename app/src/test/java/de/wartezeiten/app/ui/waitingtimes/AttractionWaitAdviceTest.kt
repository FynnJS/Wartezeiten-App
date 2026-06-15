package de.wartezeiten.app.ui.waitingtimes

import de.wartezeiten.app.domain.model.AttractionHistoryDay
import de.wartezeiten.app.domain.model.AttractionHistoryPoint
import de.wartezeiten.app.domain.model.AttractionHistorySnapshot
import de.wartezeiten.app.domain.model.AttractionStatus
import de.wartezeiten.app.domain.model.WaitingTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class AttractionWaitAdviceTest {
    @Test
    fun recommendsGoingNowWhenCurrentWaitIsBelowTypical() {
        val advice = advice(currentWait = 15, currentHistoricalWait = 30, futureHistoricalWait = 28)

        assertEquals(AttractionWaitAdviceType.GoNow, advice.type)
        assertEquals(30, advice.typicalWaitMinutes)
    }

    @Test
    fun recommendsLaterWhenHistoricalFutureSlotIsMeaningfullyLower() {
        val advice = advice(currentWait = 45, currentHistoricalWait = 40, futureHistoricalWait = 20)

        assertEquals(AttractionWaitAdviceType.WaitUntil, advice.type)
        assertEquals(LocalTime.of(13, 0), advice.suggestedLocalTime)
        assertEquals(20, advice.expectedWaitMinutes)
    }

    @Test
    fun hidesAdviceWhenFewerThanThreeDaysAreAvailable() {
        val result = buildAttractionWaitAdvice(
            waitingTimes = listOf(waitingTime(20)),
            historyDays = historyDays(30, 20).take(2),
            currentTimeMillis = epochMillis(LocalDate.of(2026, 6, 15), LocalTime.NOON),
            localTimeOffsetSeconds = 0,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun reportsTypicalWhenNeitherNowNorLaterIsMeaningfullyBetter() {
        val advice = advice(currentWait = 31, currentHistoricalWait = 30, futureHistoricalWait = 28)

        assertEquals(AttractionWaitAdviceType.Typical, advice.type)
    }

    @Test
    fun ignoresLiveAttractionsWithoutAWaitValue() {
        val result = buildAttractionWaitAdvice(
            waitingTimes = listOf(WaitingTime(ATTRACTION_ID, "Ride", null, AttractionStatus.Opened)),
            historyDays = historyDays(30, 20),
            currentTimeMillis = epochMillis(LocalDate.of(2026, 6, 15), LocalTime.NOON),
            localTimeOffsetSeconds = 0,
        )

        assertTrue(result.isEmpty())
    }

    private fun advice(
        currentWait: Int,
        currentHistoricalWait: Int,
        futureHistoricalWait: Int,
    ): AttractionWaitAdvice {
        return requireNotNull(
            buildAttractionWaitAdvice(
                waitingTimes = listOf(waitingTime(currentWait)),
                historyDays = historyDays(currentHistoricalWait, futureHistoricalWait),
                currentTimeMillis = epochMillis(LocalDate.of(2026, 6, 15), LocalTime.NOON),
                localTimeOffsetSeconds = 0,
            )[ATTRACTION_ID]
        )
    }

    private fun historyDays(currentWait: Int, futureWait: Int): List<AttractionHistoryDay> {
        return (1..3).map { day ->
            val date = LocalDate.of(2026, 6, day)
            AttractionHistoryDay(
                generatedAtMillis = epochMillis(date, LocalTime.of(14, 0)),
                parkKey = "park",
                date = date.toString(),
                openFrom = "${date}T09:00:00Z",
                snapshots = listOf(
                    snapshot(date, LocalTime.NOON, currentWait),
                    snapshot(date, LocalTime.of(12, 30), currentWait),
                    snapshot(date, LocalTime.of(13, 0), futureWait),
                    snapshot(date, LocalTime.of(13, 30), futureWait + 5),
                ),
                attractions = emptyList(),
            )
        }
    }

    private fun snapshot(date: LocalDate, time: LocalTime, wait: Int) = AttractionHistorySnapshot(
        capturedAtMillis = epochMillis(date, time),
        attractions = listOf(AttractionHistoryPoint(ATTRACTION_ID, "Ride", wait, 0, "opened")),
    )

    private fun waitingTime(wait: Int) = WaitingTime(ATTRACTION_ID, "Ride", wait, AttractionStatus.Opened)

    private fun epochMillis(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).toInstant(ZoneOffset.UTC).toEpochMilli()

    companion object {
        private const val ATTRACTION_ID = "ride-1"
    }
}
