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
 * The subtlety these tests exist for: "now" is UTC, but a study *day* belongs to
 * the user, so the day boundary has to be converted into the UTC wall time the
 * database stores.
 */
class DayBoundaryTest {

    /** 2024-05-01T09:00 in Tokyo, which is 2024-05-01T00:00 UTC. */
    private val clock: Clock = Clock.fixed(Instant.parse("2024-05-01T00:00:00Z"), ZoneOffset.UTC)

    private val tokyo = ZoneId.of("Asia/Tokyo")

    @Test
    fun theBoundaryIsTheLocalDayStartExpressedAsStoredUtcTime() {
        // Midnight on 1 May in Tokyo is 15:00 UTC the previous afternoon.
        assertEquals(
            LocalDateTime.of(2024, 4, 30, 15, 0),
            DayBoundary.startOfToday(clock, tokyo),
        )
    }

    @Test
    fun reviewsOnEitherSideOfLocalMidnightFallOnTheRightDay() {
        val boundary = DayBoundary.startOfToday(clock, tokyo)

        // 23:59 JST on 30 April and 00:00 JST on 1 May, as stored.
        assertFalse(LocalDateTime.of(2024, 4, 30, 14, 59) >= boundary)
        assertTrue(LocalDateTime.of(2024, 4, 30, 15, 0) >= boundary)
        assertTrue(LocalDateTime.of(2024, 5, 1, 3, 0) >= boundary)
    }

    @Test
    fun utcIsTheIdentityCase() {
        assertEquals(
            LocalDateTime.of(2024, 5, 1, 0, 0),
            DayBoundary.startOfToday(clock, ZoneOffset.UTC),
        )
    }

    @Test
    fun aZoneBehindUtcGetsItsOwnMidnight() {
        // 2024-05-01T00:00Z is still 30 April in New York.
        assertEquals(
            LocalDateTime.of(2024, 4, 30, 4, 0),
            DayBoundary.startOfToday(clock, ZoneId.of("America/New_York")),
        )
    }
}
