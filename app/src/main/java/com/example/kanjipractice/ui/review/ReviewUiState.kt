package com.example.kanjipractice.ui.review

import com.example.kanjipractice.data.db.CardEntity
import com.example.kanjipractice.domain.model.Stroke
import com.example.kanjipractice.domain.stroke.StrokeDiagram
import com.example.fsrs.Rating

/**
 * The review state machine, as a single sealed type.
 *
 * There are only two resting states now. The RELEARN tracing screen is gone:
 * "I don't know" opens a popup ([Prompt.hintVisible]) and drops the user back on
 * the same drawing surface, so there is one screen to draw on rather than two.
 *
 * CHECK is a transition rather than a state: while recognition runs we stay in
 * [Prompt] with [Prompt.busy] set, which is what keeps the drawing on screen.
 */
sealed interface ReviewUiState {

    /** True while the most recent committed review can still be taken back. */
    val canUndo: Boolean

    /** Loading the study queue. */
    data object Loading : ReviewUiState {
        override val canUndo: Boolean get() = false
    }

    /** Nothing is due and the daily new-card allowance is used up. */
    data class SessionComplete(override val canUndo: Boolean) : ReviewUiState

    /** Meaning and readings only, blank canvas, no hints of any kind. */
    data class Prompt(
        val card: CardEntity,
        /** Held here so the hint popup has it without a second load. */
        val diagram: StrokeDiagram,
        val strokes: List<Stroke>,
        val retryCount: Int,
        /** How many times the hint has been opened for this card. */
        val hintCount: Int,
        val hintVisible: Boolean,
        val message: String?,
        /**
         * True when the recogniser itself failed, as opposed to the drawing being
         * wrong. That is the user's cue that the app is broken rather than they
         * are, and it unlocks manual grading so a broken ML Kit install cannot
         * make the deck unusable.
         */
        val recognitionFailed: Boolean,
        val busy: Boolean,
        val remaining: Int,
        override val canUndo: Boolean,
    ) : ReviewUiState

    /**
     * The drawing was recognised as the target. The stroke diagram is shown for
     * self-check and the user rates the recall - including **Again**, which is
     * the escape hatch when the recogniser accepts a wrong character.
     */
    data class Success(
        val card: CardEntity,
        val diagram: StrokeDiagram,
        /** What the user actually drew, shown beside the target for self-check. */
        val strokes: List<Stroke>,
        val recognized: String?,
        val rating: Rating?,
        val hintCount: Int,
        val retryCount: Int,
        val remaining: Int,
        /** True when the user abandoned the card instead of drawing it. */
        val gaveUp: Boolean = false,
        override val canUndo: Boolean,
    ) : ReviewUiState
}
