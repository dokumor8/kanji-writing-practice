package com.example.kanjipractice.domain.settings

/**
 * Study limits that are the user's to choose rather than the scheduler's.
 *
 * A kanji deck is useless if all 600 cards arrive at once: new cards have to be
 * fed in at a rate the user can actually keep up with, which is why every SRS
 * app has a daily new-card allowance.
 */
data class StudySettings(
    /** How many previously unseen cards may be introduced per day. */
    val dailyNewLimit: Int = DEFAULT_DAILY_NEW_LIMIT,

    /** Cards whose *first* review happened today (counted from the review log). */
    val introducedToday: Int = 0,
) {
    /** How many new cards the user may still start today. */
    val remainingNewAllowance: Int get() = (dailyNewLimit - introducedToday).coerceAtLeast(0)

    companion object {
        const val DEFAULT_DAILY_NEW_LIMIT = 20

        const val MIN_DAILY_NEW_LIMIT = 0
        const val MAX_DAILY_NEW_LIMIT = 200
        const val DAILY_NEW_LIMIT_STEP = 5

        /**
         * Keeps the limit inside its legal range. This lives here rather than in
         * the DataStore implementation so that every caller -- including tests
         * and any future storage backend -- gets the same rules.
         */
        fun coerceDailyNewLimit(limit: Int): Int =
            limit.coerceIn(MIN_DAILY_NEW_LIMIT, MAX_DAILY_NEW_LIMIT)
    }
}
