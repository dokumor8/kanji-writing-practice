package com.example.kanjipractice.domain.util

import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Where one study day ends and the next begins.
 *
 * Two things are deliberate here.
 *
 * **The day rolls over at 03:00, not at midnight.** Studying at half past midnight
 * is, to the person doing it, still the previous day; starting a fresh day of new
 * cards and resetting the daily allowance at midnight only makes for a confusing
 * session at 00:05.
 *
 * **A session covers a whole study day, not the current instant.** A card that
 * becomes due at 20:00 belongs to today, so it is available from the moment the
 * user sits down in the morning rather than appearing hours later.
 *
 * Timestamps themselves are stored as UTC wall time (see Converters), so the
 * boundaries are returned in that same representation and can be compared
 * directly in a query.
 */
object DayBoundary {

    /** Hours past midnight at which a new study day starts. */
    const val DAY_ROLLOVER_HOUR = 3L

    /** Start of the current study day, inclusive. */
    fun startOfStudyDay(clock: Clock, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        studyDayStart(clock, zone).atUtc()

    /** Start of the *next* study day, i.e. the exclusive end of the current one. */
    fun endOfStudyDay(clock: Clock, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        studyDayStart(clock, zone).plusDays(1).atUtc()

    /**
     * The instant the current study day began, in the user's zone.
     *
     * Shifting "now" back by the rollover first means that 01:00 still belongs to
     * the previous date, and the boundary is then built from that date.
     */
    private fun studyDayStart(clock: Clock, zone: ZoneId): ZonedDateTime {
        val shifted = ZonedDateTime.now(clock.withZone(zone)).minusHours(DAY_ROLLOVER_HOUR)
        return shifted.toLocalDate()
            .atStartOfDay(zone)
            .plusHours(DAY_ROLLOVER_HOUR)
    }

    private fun ZonedDateTime.atUtc(): LocalDateTime =
        toInstant().atZone(ZoneOffset.UTC).toLocalDateTime()
}
