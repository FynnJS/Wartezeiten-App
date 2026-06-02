package de.wartezeiten.app.ui.waitingtimes

import de.wartezeiten.app.domain.model.AttractionStatus
import de.wartezeiten.app.domain.model.CrowdLevel
import de.wartezeiten.app.domain.model.OpeningTimes
import de.wartezeiten.app.domain.model.WaitingTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime

class ParkOpeningDisplayStateTest {
    @Test
    fun openParkIsGreenAndShowsCrowdLevel() {
        val state = parkOpeningDisplayState(
            openingTimes = OpeningTimes(
                opened = true,
                from = "2026-05-28T09:00:00+02:00",
                to = "2026-05-28T18:00:00+02:00",
            ),
            crowdLevel = CrowdLevel(level = 42f, timestamp = "2026-05-28T12:00:00+02:00"),
            currentTimeMillis = millis("2026-05-28T12:00:00+02:00"),
            localTimeOffsetSeconds = 7200,
        )

        assertEquals(ParkOpeningTone.Open, state.tone)
        assertEquals("Heute ge\u00f6ffnet von 09:00 Uhr bis 18:00 Uhr", state.statusText)
        assertEquals("Auslastung: ca. 40% (Normal)", state.crowdText)
    }

    @Test
    fun openParkUsesWaitTimesForMoreAccurateCrowdLevel() {
        val state = parkOpeningDisplayState(
            openingTimes = OpeningTimes(
                opened = true,
                from = "2026-05-28T09:00:00+02:00",
                to = "2026-05-28T18:00:00+02:00",
            ),
            crowdLevel = CrowdLevel(level = 38f, timestamp = "2026-05-28T12:00:00+02:00"),
            waitingTimes = listOf(
                waitingTime("a", 5),
                waitingTime("b", 10),
                waitingTime("c", 15),
                waitingTime("d", 20),
                waitingTime("e", 25),
                waitingTime("f", 30),
            ),
            currentTimeMillis = millis("2026-05-28T12:00:00+02:00"),
            localTimeOffsetSeconds = 7200,
        )

        assertEquals(ParkOpeningTone.Open, state.tone)
        assertEquals("Auslastung: ca. 25% (Wenig los)", state.crowdText)
    }

    @Test
    fun closedBeforeOpeningIsOrangeAndShowsOpeningWindow() {
        val state = parkOpeningDisplayState(
            openingTimes = OpeningTimes(
                opened = true,
                from = "2026-05-28T09:00:00+02:00",
                to = "2026-05-28T18:00:00+02:00",
            ),
            crowdLevel = CrowdLevel(level = 42f, timestamp = "2026-05-28T08:00:00+02:00"),
            currentTimeMillis = millis("2026-05-28T08:00:00+02:00"),
            localTimeOffsetSeconds = 7200,
        )

        assertEquals(ParkOpeningTone.OpenOtherTimeToday, state.tone)
        assertEquals("Heute ge\u00f6ffnet von 09:00 Uhr bis 18:00 Uhr", state.statusText)
        assertNull(state.crowdText)
    }

    @Test
    fun openAttractionBeforeOfficialOpeningShowsCrowdLevel() {
        val state = parkOpeningDisplayState(
            openingTimes = OpeningTimes(
                opened = true,
                from = "2026-05-28T10:00:00+02:00",
                to = "2026-05-28T18:00:00+02:00",
            ),
            crowdLevel = CrowdLevel(level = 24f, timestamp = "2026-05-28T09:00:00+02:00"),
            waitingTimes = listOf(
                waitingTime("a", 5),
                waitingTime("b", 10),
                waitingTime("c", 15),
            ),
            currentTimeMillis = millis("2026-05-28T09:00:00+02:00"),
            localTimeOffsetSeconds = 7200,
        )

        assertEquals(ParkOpeningTone.OpenOtherTimeToday, state.tone)
        assertEquals("Heute ge\u00f6ffnet von 10:00 Uhr bis 18:00 Uhr", state.statusText)
        assertEquals("Auslastung: ca. 15% (Wenig los)", state.crowdText)
    }

    @Test
    fun closedAfterOpeningIsOrangeAndShowsOpeningWindow() {
        val state = parkOpeningDisplayState(
            openingTimes = OpeningTimes(
                opened = true,
                from = "2026-05-28T09:00:00+02:00",
                to = "2026-05-28T18:00:00+02:00",
            ),
            crowdLevel = CrowdLevel(level = 42f, timestamp = "2026-05-28T19:00:00+02:00"),
            currentTimeMillis = millis("2026-05-28T19:00:00+02:00"),
            localTimeOffsetSeconds = 7200,
        )

        assertEquals(ParkOpeningTone.OpenOtherTimeToday, state.tone)
        assertEquals("Heute ge\u00f6ffnet von 09:00 Uhr bis 18:00 Uhr", state.statusText)
        assertNull(state.crowdText)
    }

    @Test
    fun closedAllDayIsRedAndSuppressesCrowdLevel() {
        val state = parkOpeningDisplayState(
            openingTimes = OpeningTimes(
                opened = false,
                from = null,
                to = null,
            ),
            crowdLevel = CrowdLevel(level = 42f, timestamp = "2026-05-28T12:00:00+02:00"),
            currentTimeMillis = millis("2026-05-28T12:00:00+02:00"),
            localTimeOffsetSeconds = 7200,
        )

        assertEquals(ParkOpeningTone.ClosedToday, state.tone)
        assertEquals("Heute geschlossen", state.statusText)
        assertNull(state.crowdText)
    }

    private fun millis(value: String): Long {
        return OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }

    private fun waitingTime(id: String, minutes: Int): WaitingTime {
        return WaitingTime(
            attractionId = id,
            name = id,
            waitingTime = minutes,
            status = AttractionStatus.Opened,
        )
    }
}
