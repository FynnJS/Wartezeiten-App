package de.wartezeiten.app.data.mapper

import de.wartezeiten.app.data.remote.dto.OpeningTimesDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningTimesMapperTest {
    @Test
    fun openedTodayFalseMapsToClosed() {
        val entity = listOf(
            OpeningTimesDto(
                openedToday = false,
                status = null,
                opening = null,
                closing = null,
            ),
        ).toEntity(parkKey = "bobbejaanland", updatedAtMillis = 123L)

        assertFalse(entity.opened)
        assertEquals("bobbejaanland", entity.parkKey)
    }

    @Test
    fun openedTodayTrueKeepsLocalOpeningTimes() {
        val entity = listOf(
            OpeningTimesDto(
                openedToday = true,
                status = null,
                opening = "2026-05-27T09:00:00+02:00",
                closing = "2026-05-27T18:00:00+02:00",
            ),
        ).toEntity(parkKey = "phantasialand", updatedAtMillis = 123L)

        assertTrue(entity.opened)
        assertEquals("2026-05-27T09:00:00+02:00", entity.from)
        assertEquals("2026-05-27T18:00:00+02:00", entity.to)
    }
}
