package de.wartezeiten.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class ParkOpeningWindowTest {
    @Test
    fun currentlyOpenRepairsClosingDateFromPreviousDay() {
        val isOpen = isParkCurrentlyOpen(
            openedToday = true,
            openFrom = "2026-06-10T09:00:00+02:00",
            closedFrom = "2026-06-09T18:00:00+02:00",
            now = OffsetDateTime.parse("2026-06-10T09:04:00+02:00").toInstant(),
        )

        assertTrue(isOpen)
    }

    @Test
    fun currentlyOpenStillRejectsBeforeOpening() {
        val isOpen = isParkCurrentlyOpen(
            openedToday = true,
            openFrom = "2026-06-10T09:00:00+02:00",
            closedFrom = "2026-06-09T18:00:00+02:00",
            now = OffsetDateTime.parse("2026-06-10T08:59:00+02:00").toInstant(),
        )

        assertFalse(isOpen)
    }

    @Test
    fun missingWaitingTimeDataDetectedWhenOpenLongEnoughWithoutAnyOpenAttraction() {
        val isMissing = isParkOpenWithoutWaitingTimeData(
            openedToday = true,
            openFrom = "2026-06-24T09:00:00+02:00",
            closedFrom = "2026-06-24T18:00:00+02:00",
            hasOpenAttraction = false,
            now = OffsetDateTime.parse("2026-06-24T09:16:00+02:00").toInstant(),
        )

        assertTrue(isMissing)
    }

    @Test
    fun missingWaitingTimeDataNotFlaggedWithinGracePeriod() {
        val isMissing = isParkOpenWithoutWaitingTimeData(
            openedToday = true,
            openFrom = "2026-06-24T09:00:00+02:00",
            closedFrom = "2026-06-24T18:00:00+02:00",
            hasOpenAttraction = false,
            now = OffsetDateTime.parse("2026-06-24T09:10:00+02:00").toInstant(),
        )

        assertFalse(isMissing)
    }

    @Test
    fun missingWaitingTimeDataNotFlaggedWhenAnAttractionIsOpen() {
        val isMissing = isParkOpenWithoutWaitingTimeData(
            openedToday = true,
            openFrom = "2026-06-24T09:00:00+02:00",
            closedFrom = "2026-06-24T18:00:00+02:00",
            hasOpenAttraction = true,
            now = OffsetDateTime.parse("2026-06-24T09:16:00+02:00").toInstant(),
        )

        assertFalse(isMissing)
    }

    @Test
    fun missingWaitingTimeDataNotFlaggedAfterClosing() {
        val isMissing = isParkOpenWithoutWaitingTimeData(
            openedToday = true,
            openFrom = "2026-06-24T09:00:00+02:00",
            closedFrom = "2026-06-24T18:00:00+02:00",
            hasOpenAttraction = false,
            now = OffsetDateTime.parse("2026-06-24T18:30:00+02:00").toInstant(),
        )

        assertFalse(isMissing)
    }

    @Test
    fun missingWaitingTimeDataNotFlaggedWhenParkClosedToday() {
        val isMissing = isParkOpenWithoutWaitingTimeData(
            openedToday = false,
            openFrom = "2026-06-24T09:00:00+02:00",
            closedFrom = "2026-06-24T18:00:00+02:00",
            hasOpenAttraction = false,
            now = OffsetDateTime.parse("2026-06-24T09:16:00+02:00").toInstant(),
        )

        assertFalse(isMissing)
    }
}
