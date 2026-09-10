package com.example.kanjipractice.ui.review

import com.example.kanjipractice.data.db.CardEntity
import com.example.kanjipractice.domain.model.Stroke
import com.example.kanjipractice.domain.stroke.StrokeDiagram
import com.example.fsrs.Rating

/**
 * The review state machine from plan section 6, as a single sealed type.
 *
 * CHECK is a transition rather than a state: while recognition runs we stay in
 * [Prompt] with [Prompt.busy] set, which is what keeps the drawing on screen.
 */
sealed interface ReviewUiState {

    /** Loading the due queue. */
    data object Loading : ReviewUiState

    /** Nothing is due; the session is over. */
    data object SessionComplete : ReviewUiState

    /**
     * PROMPT: meaning and readings only, blank canvas, no hints of any kind.
     */
    data class Prompt(
        val card: CardEntity,
        val strokes: List<Stroke>,
        val retryCount: Int,
        val message: String?,
        val busy: Boolean,
        val remaining: Int,
    ) : ReviewUiState

    /**
     * SUCCESS: the drawing was recognised as the target. The stroke diagram is
     * shown for self-check and the user rates the recall.
     */
    data class Success(
        val card: CardEntity,
        val recognized: String?,
        val rating: Rating?,
        val diagram: StrokeDiagram,
        val remaining: Int,
        val retryCount: Int,
    ) : ReviewUiState

    /**
     * RELEARN: reached only from "I don't know". The diagram is shown and the
     * user must trace the character before moving on. The rating is locked to
     * Again, so this state has no rating buttons.
     */
    data class Relearn(
        val card: CardEntity,
        val strokes: List<Stroke>,
        val diagram: StrokeDiagram,
        val message: String?,
        val busy: Boolean,
        val remaining: Int,
    ) : ReviewUiState
}
