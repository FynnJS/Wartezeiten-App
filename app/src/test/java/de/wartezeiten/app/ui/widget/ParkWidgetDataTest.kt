package de.wartezeiten.app.ui.widget

import de.wartezeiten.app.domain.model.AttractionStatus
import de.wartezeiten.app.domain.model.OpeningTimes
import de.wartezeiten.app.domain.model.Park
import de.wartezeiten.app.domain.model.ParkDetail
import de.wartezeiten.app.domain.model.WaitingTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ParkWidgetDataTest {
    @Test
    fun `open park shows live average highest waits and selected attractions`() {
        val nowMillis = 1_781_596_800_000L
        val detail = ParkDetail(
            park = Park(id = "ep", uuid = "uuid", name = "Europa-Park", country = "Deutschland"),
            openingTimes = OpeningTimes(
                opened = true,
                from = "2026-06-16T09:00:00+02:00",
                to = "2026-06-16T18:00:00+02:00",
            ),
            crowdLevel = null,
            waitingTimes = listOf(
                waitingTime("a", "Blue Fire", 20, nowMillis - 5 * 60_000L),
                waitingTime("b", "Arthur", 40, nowMillis - 5 * 60_000L),
                waitingTime("c", "Silver Star", 30, nowMillis - 5 * 60_000L),
                waitingTime("d", "Geschlossen", 60, nowMillis - 5 * 60_000L, AttractionStatus.Closed),
            ),
        )

        val data = buildParkWidgetData(detail, listOf("c", "a", "b"), nowMillis)

        assertEquals("Europa-Park", data.parkName)
        assertEquals(ParkWidgetOpenStatus.Open, data.status)
        assertEquals("30 min", data.averageWaitingTimeLabel)
        assertEquals("40 min", data.highestWaitingTimeLabel)
        assertEquals(listOf("c", "a", "b"), data.attractions.map { it.id })
        assertEquals("vor 5 min", data.dataAgeLabel)
    }

    @Test
    fun `closed park does not expose stale live wait metrics`() {
        val nowMillis = 1_781_629_200_000L
        val detail = ParkDetail(
            park = Park(id = "ep", uuid = "uuid", name = "Europa-Park", country = "Deutschland"),
            openingTimes = OpeningTimes(
                opened = true,
                from = "2026-06-16T09:00:00+02:00",
                to = "2026-06-16T18:00:00+02:00",
            ),
            crowdLevel = null,
            waitingTimes = listOf(
                waitingTime("a", "Blue Fire", 20, nowMillis - 65 * 60_000L),
                waitingTime("b", "Arthur", 40, nowMillis - 65 * 60_000L),
            ),
        )

        val data = buildParkWidgetData(detail, listOf("a", "b"), nowMillis)

        assertEquals(ParkWidgetOpenStatus.Closed, data.status)
        assertEquals("-", data.averageWaitingTimeLabel)
        assertEquals("-", data.highestWaitingTimeLabel)
        assertEquals(listOf("a", "b"), data.attractions.map { it.id })
        assertEquals("vor 1 h", data.dataAgeLabel)
    }

    private fun waitingTime(
        id: String,
        name: String,
        minutes: Int?,
        updatedAtMillis: Long,
        status: AttractionStatus = AttractionStatus.Opened,
    ): WaitingTime {
        return WaitingTime(
            attractionId = id,
            name = name,
            waitingTime = minutes,
            status = status,
            updatedAtMillis = updatedAtMillis,
        )
    }
}
