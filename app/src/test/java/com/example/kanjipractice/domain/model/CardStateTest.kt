package com.example.kanjipractice.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The DAO queries compare the state column against a raw Int (Room cannot put a
 * type converter inside `state <> ` in every case), so the numbering is part of
 * the persistence contract and must not drift.
 */
class CardStateTest {

    @Test
    fun storedValuesAreStable() {
        assertEquals(0, CardState.NEW.value)
        assertEquals(1, CardState.LEARNING.value)
        assertEquals(2, CardState.REVIEW.value)
        assertEquals(3, CardState.RELEARNING.value)
    }

    @Test
    fun everyStateHasAUniqueValue() {
        val values = CardState.entries.map { it.value }
        assertEquals(values.size, values.toSet().size)
    }

    @Test
    fun anUnknownValueFallsBackToNewRatherThanCrashing() {
        assertEquals(CardState.NEW, CardState.fromValue(99))
    }

    @Test
    fun roundTripsThroughItsStoredValue() {
        for (state in CardState.entries) {
            assertEquals(state, CardState.fromValue(state.value))
        }
    }
}
