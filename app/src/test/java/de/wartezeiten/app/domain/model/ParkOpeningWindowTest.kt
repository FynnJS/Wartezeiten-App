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
}
