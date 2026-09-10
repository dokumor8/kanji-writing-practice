package com.example.kanjipractice.domain.recognition

/**
 * Decides whether a drawing counts as the target character (plan, section 5.3).
 *
 * Accepting the top three candidates rather than only the first is deliberate:
 * a messy but correct drawing should pass. The user is never told which rank
 * matched.
 */
object RecognitionMatcher {

    const val ACCEPTED_RANKS = 3

    fun isCorrect(target: String, candidates: List<String>): Boolean =
        target in candidates.take(ACCEPTED_RANKS)

    /** The best candidate, shown on the success screen for transparency. */
    fun bestCandidate(candidates: List<String>): String? = candidates.firstOrNull()
}
