package com.example.kanjipractice.domain.recognition

import com.example.kanjipractice.domain.AppScript

/**
 * Decides whether a drawing counts as the target character.
 *
 * How many candidates are accepted is a property of the recogniser rather than of
 * this rule, so it comes from [AppScript.acceptedRanks]. Both apps use top-one:
 * accepting more is what let a character ranked below the model's first choice
 * pass, and a wrong character the model is confident about is exactly what this
 * check exists to catch.
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
