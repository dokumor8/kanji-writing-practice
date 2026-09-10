package com.example.kanjipractice.domain.model

/**
 * Where a card sits in the learning lifecycle. Stored as an Int so the database
 * column stays readable and the values are stable across refactors.
 */
enum class CardState(val value: Int) {
    /** Never reviewed. */
    NEW(0),

    /** Failed on its first exposure; the tracing step just taught the form. */
    LEARNING(1),

    /** Being scheduled by FSRS at increasing intervals. */
    REVIEW(2),

    /** A mature card that was just forgotten ("I don't know"). */
    RELEARNING(3);

    companion object {
        fun fromValue(value: Int): CardState = entries.firstOrNull { it.value == value } ?: NEW
    }
}
