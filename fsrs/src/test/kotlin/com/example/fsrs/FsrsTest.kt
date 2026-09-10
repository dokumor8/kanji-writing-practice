package com.example.fsrs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the FSRS-6 port.
 *
 * The first group replays numeric vectors taken from the reference
 * implementation's own test-suite (open-spaced-repetition/fsrs-rs,
 * src/model_v6.rs). If a formula is transcribed wrongly, these fail with a
 * concrete expected value instead of a vague "scheduling looks odd".
 *
 * The second group asserts the invariants the algorithm is *supposed* to have
 * (retention at t == S is 90%, ratings order intervals correctly, and so on),
 * which is what actually protects the study experience.
 */
class FsrsTest {

    private val fsrs = Fsrs()

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 1e-6) {
        assertTrue(
            kotlin.math.abs(expected - actual) <= tolerance,
            "expected " + expected + " but was " + actual,
        )
    }

    /**
     * The reference vectors come from a single-precision (f32) implementation, so
     * they only agree with this double-precision port to about seven significant
     * digits. Compare relatively.
     */
    private fun assertCloseToReference(expected: Double, actual: Double) {
        assertClose(expected, actual, tolerance = 1e-5 * maxOf(1.0, kotlin.math.abs(expected)))
    }

    // ------------------------------------------------ reference test vectors

    @Test
    fun retrievabilityMatchesReferenceImplementation() {
        val elapsed = doubleArrayOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0)
        val stability = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 4.0, 2.0)
        val expected = doubleArrayOf(1.0, 0.9403443, 0.9253786, 0.9185229, 0.9, 0.8261359)
        for (i in elapsed.indices) {
            assertClose(expected[i], fsrs.retrievability(elapsed[i], stability[i]), 1e-5)
        }
    }

    @Test
    fun intervalIsFractionalAndInvertsTheForgettingCurve() {
        assertClose(121.01551, fsrs.intervalDays(121.01552, 0.9), 1e-4)
    }

    @Test
    fun initialStabilityIsTheFirstFourWeights() {
        val w = Fsrs.DEFAULT_PARAMETERS
        assertClose(w[0], fsrs.initialStability(Rating.AGAIN))
        assertClose(w[1], fsrs.initialStability(Rating.HARD))
        assertClose(w[2], fsrs.initialStability(Rating.GOOD))
        assertClose(w[3], fsrs.initialStability(Rating.EASY))
    }

    @Test
    fun initialDifficultyMatchesReferenceImplementation() {
        val w = Fsrs.DEFAULT_PARAMETERS
        val expected = doubleArrayOf(
            w[4],
            w[4] - kotlin.math.exp(w[5]) + 1.0,
            w[4] - kotlin.math.exp(2.0 * w[5]) + 1.0,
            w[4] - kotlin.math.exp(3.0 * w[5]) + 1.0,
        )
        for (rating in Rating.entries) {
            assertClose(expected[rating.value - 1], fsrs.rawInitialDifficulty(rating))
        }
        // ...and the value handed out for a real card is clamped into [1, 10];
        // the Easy default weight is far outside that range.
        for (rating in Rating.entries) {
            assertTrue(fsrs.initialDifficulty(rating) in Fsrs.D_MIN..Fsrs.D_MAX)
        }
        assertClose(Fsrs.D_MIN, fsrs.initialDifficulty(Rating.EASY))
    }

    @Test
    fun nextDifficultyMatchesReferenceImplementationIncludingMeanReversion() {
        // difficulty 5.0 reviewed with Again / Hard / Good / Easy
        val expected = doubleArrayOf(8.341763, 6.6659956, 4.990228, 3.3144615)
        for (rating in Rating.entries) {
            assertCloseToReference(expected[rating.value - 1], fsrs.nextDifficulty(5.0, rating))
        }
    }

    @Test
    fun stabilityUpdateMatchesReferenceImplementation() {
        val stability = 5.0
        val difficulty = doubleArrayOf(1.0, 2.0, 3.0, 4.0)
        val retrievability = doubleArrayOf(0.9, 0.8, 0.7, 0.6)
        val ratings = Rating.entries

        val success = doubleArrayOf(25.602541, 28.226582, 58.656002, 127.226685)
        val failure = doubleArrayOf(1.0525396, 1.1894329, 1.3680838, 1.584989)
        for (i in ratings.indices) {
            assertCloseToReference(
                success[i],
                fsrs.stabilityAfterSuccess(stability, difficulty[i], retrievability[i], ratings[i]),
            )
            assertCloseToReference(
                failure[i],
                fsrs.stabilityAfterFailure(stability, difficulty[i], retrievability[i]),
            )
        }

        val shortTerm = doubleArrayOf(1.596818, 5.0, 5.0, 8.12961)
        for (rating in ratings) {
            assertCloseToReference(shortTerm[rating.value - 1], fsrs.stabilityShortTerm(stability, rating))
        }
    }

    // ----------------------------------------------------------- invariants

    @Test
    fun stabilityMeansNinetyPercentRecall() {
        // stability is "the interval at which recall is still 90%", so this is
        // the defining property of the whole model.
        for (s in doubleArrayOf(1.0, 10.0, 100.0, 1000.0, 36500.0)) {
            assertClose(0.9, fsrs.retrievability(s, s), 1e-9)
            assertClose(s, fsrs.intervalDays(s, 0.9), 1e-9)
        }
    }

    @Test
    fun elapsedDaysAreRoundedToWholeDays() {
        // Sub-day stability is a legal input, but retrievability is day-based, so
        // 0.5 days must read the same as 1 day (round half up), not as 0 days.
        assertClose(1.0, fsrs.retrievability(0.0, 1.0))
        assertClose(fsrs.retrievability(1.0, 1.0), fsrs.retrievability(0.5, 1.0))
        assertClose(fsrs.retrievability(4.0, 10.0), fsrs.retrievability(4.4, 10.0))
        assertClose(fsrs.retrievability(5.0, 10.0), fsrs.retrievability(4.6, 10.0))
    }

    @Test
    fun retrievabilityFallsWithTimeAndIntervalGrowsWithStability() {
        assertTrue(fsrs.retrievability(0.0, 10.0) > fsrs.retrievability(5.0, 10.0))
        assertTrue(fsrs.retrievability(5.0, 10.0) > fsrs.retrievability(50.0, 10.0))
        assertTrue(fsrs.intervalDays(1.0) < fsrs.intervalDays(10.0))
        assertTrue(fsrs.intervalDays(10.0) < fsrs.intervalDays(100.0))
    }

    @Test
    fun aNewCardGetsTheInitialStateForItsRating() {
        for (rating in Rating.entries) {
            val state = fsrs.review(previous = null, elapsedDays = 0.0, rating = rating)
            assertClose(fsrs.initialStability(rating), state.stability)
            assertClose(fsrs.initialDifficulty(rating), state.difficulty)
        }
    }

    @Test
    fun higherRatingsProduceLongerIntervals() {
        val state = MemoryState(stability = 20.0, difficulty = 5.0)
        val again = fsrs.review(state, 20.0, Rating.AGAIN)
        val hard = fsrs.review(state, 20.0, Rating.HARD)
        val good = fsrs.review(state, 20.0, Rating.GOOD)
        val easy = fsrs.review(state, 20.0, Rating.EASY)

        assertTrue(again.stability < hard.stability, "lapses must shrink stability")
        assertTrue(hard.stability < good.stability)
        assertTrue(good.stability < easy.stability)

        val intervals = listOf(again, hard, good, easy).map { fsrs.intervalDays(it.stability) }
        assertTrue(intervals.zipWithNext().all { (a, b) -> a < b }, "intervals: " + intervals)
    }

    @Test
    fun reviewingLateIsRewardedWithMoreStability() {
        val state = MemoryState(stability = 10.0, difficulty = 5.0)
        val onTime = fsrs.review(state, 10.0, Rating.GOOD)
        val veryLate = fsrs.review(state, 60.0, Rating.GOOD)
        assertTrue(veryLate.stability > onTime.stability)
    }

    @Test
    fun sameDayReviewUsesTheShortTermFormula() {
        val state = MemoryState(stability = 5.0, difficulty = 5.0)
        // A lapse on the same day still goes through the short-term branch.
        val sameDay = fsrs.review(state, 0.0, Rating.AGAIN)
        assertClose(fsrs.stabilityShortTerm(5.0, Rating.AGAIN), sameDay.stability)

        val laterDay = fsrs.review(state, 1.0, Rating.AGAIN)
        assertClose(fsrs.stabilityAfterFailure(5.0, 5.0, fsrs.retrievability(1.0, 5.0)), laterDay.stability)
    }

    @Test
    fun nonLapseSameDayReviewNeverShrinksStability() {
        val state = MemoryState(stability = 500.0, difficulty = 5.0)
        for (rating in listOf(Rating.HARD, Rating.GOOD, Rating.EASY)) {
            assertTrue(fsrs.review(state, 0.0, rating).stability >= 500.0)
        }
    }

    @Test
    fun everyStateStaysInsideTheLegalRanges() {
        var state: MemoryState? = null
        var days = 0.0
        val ratings = listOf(
            Rating.AGAIN, Rating.GOOD, Rating.AGAIN, Rating.HARD,
            Rating.EASY, Rating.GOOD, Rating.GOOD, Rating.AGAIN, Rating.EASY,
        )
        for (rating in ratings) {
            val next = fsrs.review(state, days, rating)
            assertTrue(next.stability >= Fsrs.S_MIN && next.stability <= Fsrs.S_MAX)
            assertTrue(next.difficulty in Fsrs.D_MIN..Fsrs.D_MAX)
            state = next
            days = fsrs.intervalDays(next.stability)
        }
    }

    @Test
    fun parametersMustBeTwentyOneLong() {
        val failure = runCatching { Fsrs(doubleArrayOf(1.0, 2.0)) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun defaultParametersMatchThePublishedValues() {
        val expected = doubleArrayOf(
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334,
            3.0194, 0.001, 1.8722, 0.1666, 0.796, 1.4835,
            0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425,
            0.0912, 0.0658, 0.1542,
        )
        assertEquals(expected.toList(), Fsrs.DEFAULT_PARAMETERS.toList())
    }
}
