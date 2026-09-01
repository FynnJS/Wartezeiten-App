package de.wartezeiten.app.core.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class ParkLocalDateTest {
    @Test
    fun derivesParkLocalDateFromOpenFromOffset() {
        // 2026-06-05T22:30 UTC == 2026-06-06 00:30 in +02:00 park time
        val millis = OffsetDateTime.parse("2026-06-05T22:30:00Z").toInstant().toEpochMilli()
        assertEquals("2026-06-06", parkLocalToday("2026-06-06T09:00:00+02:00", millis))
    }

    @Test
    fun negativeOffsetCanShiftDateBackwards() {
        // 2026-06-06T02:00 UTC == 2026-06-05 20:00 in -06:00 park time
        val millis = OffsetDateTime.parse("2026-06-06T02:00:00Z").toInstant().toEpochMilli()
        assertEquals("2026-06-05", parkLocalToday("2026-06-05T09:00:00-06:00", millis))
    }

    @Test
    fun nullOpenFromFallsBackToDeviceDate() {
        assertEquals(LocalDate.now().toString(), parkLocalToday(null, System.currentTimeMillis()))
    }
}
