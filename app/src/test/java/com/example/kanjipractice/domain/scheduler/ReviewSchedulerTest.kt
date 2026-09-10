package com.example.kanjipractice.domain.scheduler

import com.example.fsrs.Fsrs
import com.example.fsrs.Rating
import com.example.kanjipractice.data.db.CardEntity
import com.example.kanjipractice.domain.model.CardState
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewSchedulerTest {

    private val now: LocalDateTime = LocalDateTime.of(2024, 5, 1, 9, 0)
    private val clock: Clock = Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val scheduler = ReviewScheduler(Fsrs(), clock)

    @Test
    fun aNewCardThatFailsGoesToLearningAndComesBackSoon() {
        val updated = scheduler.schedule(newCard(), Rating.AGAIN, now)

        assertEquals(CardState.LEARNING, updated.state)
        assertEquals(1, updated.reps)
        assertEquals(1, updated.lapses)
        assertEquals(now.plusMinutes(ReviewScheduler.RELEARNING_STEP_MINUTES), updated.due)
        assertEquals(now, updated.lastReview)
    }

    @Test
    fun aNewCardThatSucceedsGraduatesToReview() {
        val updated = scheduler.schedule(newCard(), Rating.GOOD, now)

        assertEquals(CardState.REVIEW, updated.state)
        assertEquals(0, updated.lapses)
        assertTrue(
            !updated.due.isBefore(now.plusDays(ReviewScheduler.MINIMUM_INTERVAL_DAYS)),
            "due ${updated.due} is inside the minimum interval",
        )
    }

    @Test
    fun aNewCardGetsItsInitialMemoryStateFromTheFirstRating() {
        val fsrs = Fsrs()
        val updated = scheduler.schedule(newCard(), Rating.EASY, now)
        assertEquals(fsrs.initialStability(Rating.EASY), updated.stability)
        assertEquals(fsrs.initialDifficulty(Rating.EASY), updated.difficulty)
    }

    @Test
    fun aMatureCardThatFailsGoesToRelearning() {
        val mature = scheduler.schedule(newCard(), Rating.GOOD, now)
            .copy(state = CardState.REVIEW)

        val lapsed = scheduler.schedule(mature, Rating.AGAIN, now.plusDays(30))
        assertEquals(CardState.RELEARNING, lapsed.state)
        assertEquals(1, lapsed.lapses)
        assertEquals(2, lapsed.reps)
    }

    @Test
    fun stabilityGrowsWithSuccessfulReviewsAndFallsWithALapse() {
        var card = newCard()
        val intervals = mutableListOf<Long>()

        repeat(4) { i ->
            card = scheduler.schedule(card, Rating.GOOD, now.plusDays(intervals.sum() + i.toLong()))
            intervals += Duration.between(now, card.due).toDays()
        }
        val before = card.stability

        assertTrue(intervals.zipWithNext().all { (a, b) -> b >= a }, "intervals: $intervals")
        assertTrue(before > 0.0)

        val lapsed = scheduler.schedule(card, Rating.AGAIN, card.due)
        assertTrue(lapsed.stability < before, "a lapse must shrink stability")
    }

    @Test
    fun elapsedTimeIsMeasuredFromTheLastReview() {
        // Reviewing the same card much later on must be worth more stability.
        val base = newCard().copy(
            reps = 1,
            state = CardState.REVIEW,
            stability = 10.0,
            difficulty = 5.0,
        )
        val onTime = scheduler.schedule(base.copy(lastReview = now.minusDays(10)), Rating.GOOD, now)
        val late = scheduler.schedule(base.copy(lastReview = now.minusDays(60)), Rating.GOOD, now)

        assertTrue(late.stability > onTime.stability)
        assertEquals(now, onTime.lastReview)
    }

    @Test
    fun aCardIsNeverScheduledInThePast() {
        val base = newCard().copy(reps = 5, state = CardState.REVIEW, lastReview = now)
        for (rating in Rating.entries) {
            val updated = scheduler.schedule(base, rating, now)
            assertTrue(updated.due.isAfter(now), "rating $rating produced a past due date")
        }
    }

    @Test
    fun difficultyStaysInsideItsLegalRange() {
        var card = newCard()
        var at = now
        repeat(12) {
            card = scheduler.schedule(card, Rating.AGAIN, at)
            at = card.due
        }
        assertTrue(card.difficulty in Fsrs.D_MIN..Fsrs.D_MAX, "difficulty ${card.difficulty}")
    }

    private fun newCard() = CardEntity(
        id = 1L,
        character = "\u99C5",
        meaning = "station",
        onyomi = "\u30A8\u30AD",
        kunyomi = null,
        exampleWord = null,
        jlpt = 4,
        due = now,
    )
}
