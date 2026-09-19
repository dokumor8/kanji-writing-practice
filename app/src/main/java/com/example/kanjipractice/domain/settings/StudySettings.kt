package com.example.kanjipractice.domain.settings

/**
 * How a drawing is judged, and how many new cards a day.
 *
 * The defaults for the two judgement settings are calibration results, not
 * taste: see StrokeSimilarityCalibrationTest, which measures a real reference
 * diagram against copies of itself. Correct copies score 1.00, and every way of
 * getting it wrong that was measured -- a missing stroke, a displaced stroke,
 * half a character, the right strokes in the wrong order -- scored 0.57 or less.
 * 70% sits in that gap with room on both sides.
 */
data class StudySettings(
    /** How many previously unseen cards may be introduced per day. */
    val dailyNewLimit: Int = DEFAULT_DAILY_NEW_LIMIT,

    /** Cards whose *first* review happened today (counted from the review log). */
    val introducedToday: Int = 0,

    /**
     * How many of the recogniser's candidates may be considered.
     *
     * Wider than one because the recogniser answers "which character is this?",
     * and a correct drawing can lose to a similar one. The shape check below is
     * what stops that from meaning "anything goes".
     */
    val acceptedCandidates: Int = DEFAULT_ACCEPTED_CANDIDATES,

    /**
     * How closely the drawing must reproduce the reference diagram, as a
     * percentage. A drawing is accepted only if it clears this as well as
     * appearing among the accepted candidates.
     */
    val similarityThresholdPercent: Int = DEFAULT_SIMILARITY_PERCENT,
) {
    /** How many new cards the user may still start today. */
    val remainingNewAllowance: Int get() = (dailyNewLimit - introducedToday).coerceAtLeast(0)

    /** The shape threshold as a fraction, which is what the comparison returns. */
    val similarityThreshold: Double get() = similarityThresholdPercent / 100.0

    companion object {
        const val DEFAULT_DAILY_NEW_LIMIT = 20

        const val MIN_DAILY_NEW_LIMIT = 0
        const val MAX_DAILY_NEW_LIMIT = 200
        const val DAILY_NEW_LIMIT_STEP = 5

        const val DEFAULT_ACCEPTED_CANDIDATES = 5
        const val MIN_ACCEPTED_CANDIDATES = 1
        const val MAX_ACCEPTED_CANDIDATES = 10

        const val DEFAULT_SIMILARITY_PERCENT = 70
        const val MIN_SIMILARITY_PERCENT = 0
        const val MAX_SIMILARITY_PERCENT = 100
        const val SIMILARITY_STEP = 5

        /**
         * Keeps a setting inside its legal range. This lives here rather than in
         * the DataStore implementation so that every caller -- including tests
         * and any future storage backend -- gets the same rules.
         */
        fun coerceDailyNewLimit(limit: Int): Int =
            limit.coerceIn(MIN_DAILY_NEW_LIMIT, MAX_DAILY_NEW_LIMIT)

        fun coerceAcceptedCandidates(count: Int): Int =
            count.coerceIn(MIN_ACCEPTED_CANDIDATES, MAX_ACCEPTED_CANDIDATES)

        fun coerceSimilarityPercent(percent: Int): Int =
            percent.coerceIn(MIN_SIMILARITY_PERCENT, MAX_SIMILARITY_PERCENT)
    }
}
