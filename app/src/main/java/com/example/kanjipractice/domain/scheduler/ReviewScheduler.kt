package com.example.kanjipractice.domain.scheduler

import com.example.fsrs.Fsrs
import com.example.fsrs.MemoryState
import com.example.fsrs.Rating
import com.example.kanjipractice.data.db.CardEntity
import com.example.kanjipractice.data.db.isNew
import com.example.kanjipractice.domain.model.CardState
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

/**
 * Turns "the user rated this card X" into the card's next state (plan, section 7).
 *
 * FSRS itself only produces a new stability/difficulty and an interval. How that
 * interval becomes a due *date* is scheduler policy, and the two policies here
 * are worth calling out because they are not part of FSRS:
 *
 *  - a lapse ("Again") is due **immediately**. It is not deferred by a wall-clock
 *    learning step; the session queue is what decides when it comes back, by
 *    re-appending it, so a failed card returns later in the same sitting rather
 *    than ten minutes into the future. Because the queue covers the whole study
 *    day (see DayBoundary), an abandoned session also still finds the card
 *    waiting.
 *  - anything else gets at least a day, since a sub-day interval would just
 *    re-show the card immediately.
 */
@Singleton
class ReviewScheduler @Inject constructor(
    private val fsrs: Fsrs,
    private val clock: Clock,
) {

    fun schedule(
        card: CardEntity,
        rating: Rating,
        now: LocalDateTime = LocalDateTime.now(clock),
    ): CardEntity {
        val previous = if (card.isNew || card.lastReview == null) {
            null
        } else {
            MemoryState(card.stability, card.difficulty)
        }
        val elapsedDays = if (previous == null) {
            0.0
        } else {
            Duration.between(card.lastReview, now).toMillis() / MILLIS_PER_DAY
        }

        val updated = fsrs.review(previous, elapsedDays, rating)
        val due = when (rating) {
            // Immediately due again: the session queue does the spacing.
            Rating.AGAIN -> now
            else -> {
                val days = fsrs.intervalDays(updated.stability)
                    .roundToLong()
                    .coerceAtLeast(MINIMUM_INTERVAL_DAYS)
                now.plusDays(days)
            }
        }

        return card.copy(
            stability = updated.stability,
            difficulty = updated.difficulty,
            due = due,
            lastReview = now,
            reps = card.reps + 1,
            lapses = if (rating == Rating.AGAIN) card.lapses + 1 else card.lapses,
            state = nextState(card.state, rating),
        )
    }

    /**
     * A successful recall always graduates the card to review. A lapse keeps the
     * card in the phase it was already in, so a card failed during relearning
     * stays there.
     */
    private fun nextState(current: CardState, rating: Rating): CardState = when {
        rating != Rating.AGAIN -> CardState.REVIEW
        current == CardState.NEW -> CardState.LEARNING
        current == CardState.REVIEW -> CardState.RELEARNING
        else -> current
    }

    companion object {
        const val MINIMUM_INTERVAL_DAYS = 1L

        private const val MILLIS_PER_DAY = 86_400_000.0
    }
}
