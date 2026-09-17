package com.example.kanjipractice.domain.recognition

import com.example.kanjipractice.domain.AppScript

/**
 * Decides whether a drawing counts as the target character.
 *
 * How many candidates are accepted is a property of the recogniser rather than of
 * this rule, and the two models differ: see [AppScript.acceptedRanks]. The
 * Japanese model runs on top-one, because a wrong character coming top there is a
 * real error worth catching. The Chinese model is a different model over a much
 * larger, denser character set, and a false reject blocks the card outright,
 * whereas a false accept costs one tap on "Again".
 */
object RecognitionMatcher {

    fun isCorrect(
        target: String,
        candidates: List<String>,
        acceptedRanks: Int = AppScript.acceptedRanks,
    ): Boolean = target in candidates.take(acceptedRanks)

    /** The best candidate, shown to the user so a rejection is explicable. */
    fun bestCandidate(candidates: List<String>): String? = candidates.firstOrNull()
}
