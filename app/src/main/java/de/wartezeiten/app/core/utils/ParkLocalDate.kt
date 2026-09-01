package de.wartezeiten.app.core.utils

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Derives the park-local calendar date from the offset embedded in an `openFrom` timestamp
 * (e.g. "2026-06-05T09:00:00+02:00"). Falls back to the device date when no offset is available.
 * Prevents "today" mismatches and wrong fallback banners for parks in other time zones.
 */
fun parkLocalToday(openFrom: String?, nowMillis: Long = System.currentTimeMillis()): String {
    val zoneId = openFrom?.let { value ->
        runCatching { OffsetDateTime.parse(value).offset }.getOrNull()
    } ?: return LocalDate.now().toString()
    return Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate().toString()
}
