package de.wartezeiten.app.worker

import de.wartezeiten.app.data.local.entity.AlertHistoryEntity
import de.wartezeiten.app.data.local.entity.WatchlistEntity
import de.wartezeiten.app.data.local.entity.WatchlistType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class WatchlistDeliveryRulesTest {
    @Test
    fun overnightQuietHoursCoverLateEveningAndEarlyMorning() {
        val alert = alert(quietHoursEnabled = true)

        assertTrue(alert.isInQuietHours(epoch(LocalTime.of(23, 0)), "2026-06-15T09:00:00Z"))
        assertTrue(alert.isInQuietHours(epoch(LocalTime.of(7, 59)), "2026-06-15T09:00:00Z"))
        assertFalse(alert.isInQuietHours(epoch(LocalTime.of(8, 0)), "2026-06-15T09:00:00Z"))
    }

    @Test
    fun cooldownUsesLastActualTrigger() {
        val alert = alert(cooldownMinutes = 30)
        val now = epoch(LocalTime.NOON)
        val recent = AlertHistoryEntity(1, "true", now - 5 * 60_000L, now - 5 * 60_000L)
        val old = recent.copy(lastTriggeredAtMillis = now - 31 * 60_000L)

        assertFalse(alert.cooldownElapsed(recent, now))
        assertTrue(alert.cooldownElapsed(old, now))
    }

    @Test
    fun dailySummaryOnlyReturnsAKeyDuringTheLocalEighteenHourWindow() {
        assertTrue(dailySummaryDayKey(epoch(LocalTime.of(18, 15)), "2026-06-15T09:00:00Z") != null)
        assertTrue(dailySummaryDayKey(epoch(LocalTime.of(17, 59)), "2026-06-15T09:00:00Z") == null)
    }

    private fun alert(
        quietHoursEnabled: Boolean = false,
        cooldownMinutes: Int = 30,
    ) = WatchlistEntity(
        id = 1,
        parkKey = "park",
        attractionId = null,
        type = WatchlistType.NOW_OPENED,
        threshold = 0,
        quietHoursEnabled = quietHoursEnabled,
        cooldownMinutes = cooldownMinutes,
    )

    private fun epoch(time: LocalTime): Long = LocalDate.of(2026, 6, 15)
        .atTime(time)
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli()
}
