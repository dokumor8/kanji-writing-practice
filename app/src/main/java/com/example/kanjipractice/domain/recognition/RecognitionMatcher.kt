package com.example.kanjipractice.domain.recognition

/**
 * Decides whether a drawing counts as the target character (plan, section 5.3).
 *
 * **Only the model's first choice counts.** An earlier version accepted the top
 * three candidates, which turned out to be far too forgiving: 玉 passed for 主,
 * because the two are near-identical and the recogniser ranks them adjacently.
 * Accepting a wrong character as correct is worse than occasionally making the
 * user draw again -- and the "Again" button on the result screen is there for the
 * false positives that still slip through.
 */
object RecognitionMatcher {

    const val ACCEPTED_RANKS = 1

    fun isCorrect(target: String, candidates: List<String>): Boolean =
        target in candidates.take(ACCEPTED_RANKS)

    /** The best candidate, shown on the success screen for transparency. */
    fun bestCandidate(candidates: List<String>): String? = candidates.firstOrNull()
}
