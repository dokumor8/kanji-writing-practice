package com.example.kanjipractice.domain.recognition

/**
 * Whether the recogniser put the target among its accepted candidates.
 *
 * How many candidates count is a *setting*, not a constant, because the right
 * answer depends on the recogniser and on how tolerant the user wants to be. It
 * is only half the judgement, though: StrokeSimilarity decides whether the
 * drawing actually reproduces the character, which is not a question a rank can
 * answer.
 */
object RecognitionMatcher {

    fun isCorrect(
        target: String,
        candidates: List<String>,
        acceptedCandidates: Int,
    ): Boolean = target in candidates.take(acceptedCandidates)

    /** The best candidate, shown to the user so a rejection is explicable. */
    fun bestCandidate(candidates: List<String>): String? = candidates.firstOrNull()
}
