package com.example.kanjipractice.domain.util

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Where one study day ends and the next begins.
 *
 * Timestamps are stored as UTC wall time (see Converters), but a "day" for the
 * user is a day on their own clock. These helpers translate between the two so
 * the daily new-card allowance resets at local midnight rather than at an
 * arbitrary hour.
 */
object DayBoundary {

    /**
     * Start of the current day in [zone], expressed the way the database stores
     * timestamps (UTC wall time), so it can be compared directly in a query.
     */
    fun startOfToday(clock: Clock, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime {
        val today: LocalDate = LocalDate.now(clock.withZone(zone))
        return today.atStartOfDay(zone).toInstant().atZone(ZoneOffset.UTC).toLocalDateTime()
    }
}
