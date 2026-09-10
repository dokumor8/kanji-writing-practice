package com.example.fsrs

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow

/**
 * The four grades a card can be reviewed with. The [value] is both the index the
 * FSRS formulas use (1..4) and the value persisted in the review log.
 */
enum class Rating(val value: Int) {
    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4);

    companion object {
        fun fromValue(value: Int): Rating = entries.first { it.value == value }
    }
}

/** The two numbers FSRS keeps per card. */
data class MemoryState(
    val stability: Double,
    val difficulty: Double,
)

/**
 * FSRS v6 -- a direct port of the reference implementation
 * (open-spaced-repetition/fsrs-rs, src/model_v6.rs, plus the wiki page
 * "The Algorithm"). The formulas are kept in the same shape as the reference so
 * the two can be diffed by eye.
 *
 * The two things FSRS-6 changed that matter here:
 *  - 21 parameters instead of the 17 of FSRS-4.5, with w[20] making the decay of
 *    the forgetting curve trainable rather than fixed at 0.5.
 *  - Same-day ("short term") reviews get their own stability formula using
 *    w[17], w[18] and w[19].
 *
 * @param parameters the 21 FSRS-6 weights; defaults to the published defaults.
 * @param requestRetention the target probability of recall at review time, e.g. 0.9.
 */
class Fsrs(
    val parameters: DoubleArray = DEFAULT_PARAMETERS,
    val requestRetention: Double = 0.9,
) {
    init {
        require(parameters.size == PARAMETER_COUNT) {
            "FSRS-6 needs " + PARAMETER_COUNT + " parameters, got " + parameters.size
        }
    }

    /** Trainable decay of the power forgetting curve: -w[20]. */
    private val decay: Double = -parameters[20]

    /**
     * Chosen so that retrievability at t == S is exactly 90%:
     * factor = 0.9^(1/decay) - 1, which equals the FSRS-4.5 constant 19/81 when
     * w[20] is the default 0.5.
     */
    private val factor: Double = 0.9.pow(1.0 / decay) - 1.0

    // ---------------------------------------------------------------- recall

    /**
     * Probability the card is still remembered after [elapsedDays].
     *
     * Elapsed time is rounded to whole days, matching the reference
     * implementation, which stays day-based.
     */
    fun retrievability(elapsedDays: Double, stability: Double): Double {
        val s = stability.coerceAtLeast(S_MIN)
        val t = roundElapsedDays(elapsedDays)
        return (t / s * factor + 1.0).pow(decay)
    }

    /** Days until retrievability decays to [desiredRetention]. */
    fun intervalDays(stability: Double, desiredRetention: Double = requestRetention): Double {
        val s = stability.coerceAtLeast(S_MIN)
        val r = desiredRetention.coerceIn(0.0001, 0.9999)
        return (s * (r.pow(1.0 / decay) - 1.0) / factor).coerceIn(0.0, S_MAX)
    }

    // ------------------------------------------------------- initial memory

    /** S_0(G) = w[G-1] -- stability of a card's very first review. */
    fun initialStability(rating: Rating): Double =
        parameters[rating.value - 1].coerceIn(S_MIN, S_MAX)

    /** D_0(G) = w[4] - e^(w[5] * (G-1)) + 1, clamped to [1, 10]. */
    fun initialDifficulty(rating: Rating): Double =
        rawInitialDifficulty(rating).coerceIn(D_MIN, D_MAX)

    internal fun rawInitialDifficulty(rating: Rating): Double =
        parameters[4] - exp(parameters[5] * (rating.value - 1)) + 1.0

    // -------------------------------------------------------------- updates

    /**
     * Applies one review and returns the new memory state.
     *
     * @param previous the card's memory state *before* this review, or null for a
     *   card that has never been reviewed.
     * @param elapsedDays days since the previous review; ignored for a new card.
     */
    fun review(previous: MemoryState?, elapsedDays: Double, rating: Rating): MemoryState {
        if (previous == null) {
            return MemoryState(initialStability(rating), initialDifficulty(rating))
        }
        val s = previous.stability.coerceIn(S_MIN, S_MAX)
        val d = previous.difficulty.coerceIn(D_MIN, D_MAX)
        val t = roundElapsedDays(elapsedDays)
        val r = retrievability(t, s)

        // The order matters and mirrors the reference: a second review on the
        // same day uses the short-term formula even when it is a lapse.
        val newStability = when {
            t == 0.0 -> stabilityShortTerm(s, rating)
            rating == Rating.AGAIN -> stabilityAfterFailure(s, d, r)
            else -> stabilityAfterSuccess(s, d, r, rating)
        }
        return MemoryState(newStability, nextDifficulty(d, rating))
    }

    /** S'_r -- stability after a successful recall. */
    internal fun stabilityAfterSuccess(s: Double, d: Double, r: Double, rating: Rating): Double {
        val hardPenalty = if (rating == Rating.HARD) parameters[15] else 1.0
        val easyBonus = if (rating == Rating.EASY) parameters[16] else 1.0
        val increase = exp(parameters[8]) *
            (11.0 - d) *
            s.pow(-parameters[9]) *
            (exp((1.0 - r) * parameters[10]) - 1.0) *
            hardPenalty *
            easyBonus
        return (s * (increase + 1.0)).coerceIn(S_MIN, S_MAX)
    }

    /** S'_f -- the (smaller) stability after a lapse. */
    internal fun stabilityAfterFailure(s: Double, d: Double, r: Double): Double {
        val raw = parameters[11] *
            d.pow(-parameters[12]) *
            ((s + 1.0).pow(parameters[13]) - 1.0) *
            exp((1.0 - r) * parameters[14])
        // FSRS-6 caps post-lapse stability relative to the pre-lapse stability.
        val cap = s / exp(parameters[17] * parameters[18])
        return minOf(raw, cap).coerceIn(S_MIN, S_MAX)
    }

    /** S' -- stability after a second review on the same day. */
    internal fun stabilityShortTerm(s: Double, rating: Rating): Double {
        val increase = exp(parameters[17] * (rating.value - 3 + parameters[18])) *
            s.pow(-parameters[19])
        // A review that is not a lapse must never *decrease* stability.
        val effective = if (rating.value >= 2) maxOf(increase, 1.0) else increase
        return (s * effective).coerceIn(S_MIN, S_MAX)
    }

    /**
     * Linear damping plus mean reversion towards D_0(4), which is what keeps
     * difficulty from ratcheting up forever ("ease hell").
     */
    internal fun nextDifficulty(d: Double, rating: Rating): Double {
        val delta = -parameters[6] * (rating.value - 3)
        val damped = d + (10.0 - d) / 9.0 * delta
        val reverted = parameters[7] * rawInitialDifficulty(Rating.EASY) +
            (1.0 - parameters[7]) * damped
        return reverted.coerceIn(D_MIN, D_MAX)
    }

    private fun roundElapsedDays(days: Double): Double = floor(days.coerceAtLeast(0.0) + 0.5)

    companion object {
        const val PARAMETER_COUNT = 21

        /** The published FSRS-6 default weights. */
        val DEFAULT_PARAMETERS = doubleArrayOf(
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334,
            3.0194, 0.001, 1.8722, 0.1666, 0.796, 1.4835,
            0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425,
            0.0912, 0.0658, 0.1542,
        )

        const val S_MIN = 0.0001
        const val S_MAX = 36500.0
        const val D_MIN = 1.0
        const val D_MAX = 10.0
    }
}
