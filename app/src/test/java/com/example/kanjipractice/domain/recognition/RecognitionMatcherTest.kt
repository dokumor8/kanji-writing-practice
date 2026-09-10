package com.example.kanjipractice.domain.recognition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecognitionMatcherTest {

    @Test
    fun theTargetIsAcceptedAnywhereInTheTopThree() {
        // Section 5.3: messy but correct drawings should pass.
        assertTrue(RecognitionMatcher.isCorrect("\u99C5", listOf("\u99C5", "\u9A45", "\u99AC")))
        assertTrue(RecognitionMatcher.isCorrect("\u99C5", listOf("\u9A45", "\u99C5", "\u99AC")))
        assertTrue(RecognitionMatcher.isCorrect("\u99C5", listOf("\u9A45", "\u99AC", "\u99C5")))
    }

    @Test
    fun theTargetIsRejectedBelowTheTopThree() {
        assertFalse(
            RecognitionMatcher.isCorrect("\u99C5", listOf("\u9A45", "\u99AC", "\u9A5A", "\u99C5"))
        )
    }

    @Test
    fun nothingDrawnNeverMatches() {
        assertFalse(RecognitionMatcher.isCorrect("\u99C5", emptyList()))
    }

    @Test
    fun theBestCandidateIsTheFirstOne() {
        assertEquals("\u99C5", RecognitionMatcher.bestCandidate(listOf("\u99C5", "\u9A45")))
        assertNull(RecognitionMatcher.bestCandidate(emptyList()))
    }
}
