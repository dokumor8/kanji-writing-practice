package com.example.kanjipractice.domain.util

import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two subtleties these tests exist for:
 *
 *  - a study day starts at 03:00, not midnight, so a late-night session still
 *    belongs to the day the user thinks it does;
 *  - "now" is UTC while a study day belongs to the user, so boundaries have to be
 *    converted into the UTC wall time the database stores.
 */
class DayBoundaryTest {

    /** 2024-05-01T09:00 in Tokyo, 2024-05-01T00:00 UTC, 2024-04-30T20:00 in New York. */
    private val clock: Clock = Clock.fixed(Instant.parse("2024-05-01T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun justAfterMidnightStillBelongsToThePreviousStudyDay() {
        // 00:00 is before the 03:00 rollover, so the study day began yesterday.
        assertEquals(
            LocalDateTime.of(2024, 4, 30, 3, 0),
            DayBoundary.startOfStudyDay(clock, ZoneOffset.UTC),
        )
        assertEquals(
            LocalDateTime.of(2024, 5, 1, 3, 0),
            DayBoundary.endOfStudyDay(clock, ZoneOffset.UTC),
        )
    }

    @Test
    fun theRolloverMovesAtThreeInTheMorning() {
        val zone = ZoneOffset.UTC
        val before = Clock.fixed(Instant.parse("2024-05-01T02:59:00Z"), zone)
        val after = Clock.fixed(Instant.parse("2024-05-01T03:01:00Z"), zone)

        assertEquals(
            LocalDateTime.of(2024, 4, 30, 3, 0),
            DayBoundary.startOfStudyDay(before, zone),
        )
        assertEquals(
            LocalDateTime.of(2024, 5, 1, 3, 0),
            DayBoundary.startOfStudyDay(after, zone),
        )
    }

    @Test
    fun theBoundaryIsTheStudyDayStartInTheUsersZone() {
        // 03:00 on 1 May in Tokyo is 18:00 UTC on 30 April.
        assertEquals(
            LocalDateTime.of(2024, 4, 30, 18, 0),
            DayBoundary.startOfStudyDay(clock, ZoneId.of("Asia/Tokyo")),
        )
        assertEquals(
            LocalDateTime.of(2024, 5, 1, 18, 0),
            DayBoundary.endOfStudyDay(clock, ZoneId.of("Asia/Tokyo")),
        )
    }

    @Test
    fun aZoneBehindUtcGetsItsOwnBoundary() {
        // 20:00 on 30 April in New York, minus three hours, is still 30 April.
        assertEquals(
            LocalDateTime.of(2024, 4, 30, 7, 0),
            DayBoundary.startOfStudyDay(clock, ZoneId.of("America/New_York")),
        )
    }

    @Test
    fun theEndIsTheStartOfTheNextDayAndTheWindowIsADayLong() {
        for (zone in listOf<ZoneId>(ZoneOffset.UTC, ZoneId.of("Asia/Tokyo"), ZoneId.of("America/New_York"))) {
            val start = DayBoundary.startOfStudyDay(clock, zone)
            val end = DayBoundary.endOfStudyDay(clock, zone)
            assertEquals(24, java.time.Duration.between(start, end).toHours(), "zone $zone")
        }
    }

    @Test
    fun theWindowRunsFromTheStartToTheNextRollover() {
        val start = DayBoundary.startOfStudyDay(clock, ZoneOffset.UTC)
        val end = DayBoundary.endOfStudyDay(clock, ZoneOffset.UTC)

        // 02:59 on 1 May is inside the study day that began on 30 April.
        val lateNight = LocalDateTime.of(2024, 5, 1, 2, 59)
        assertTrue(lateNight >= start)
        assertTrue(lateNight < end)

        // 03:00 on 1 May belongs to the next study day.
        assertFalse(LocalDateTime.of(2024, 5, 1, 3, 0) < end)
    }
}
