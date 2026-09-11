package com.example.kanjipractice.domain.recognition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecognitionMatcherTest {

    @Test
    fun onlyTheFirstCandidateIsAccepted() {
        assertTrue(RecognitionMatcher.isCorrect("\u99C5", listOf("\u99C5", "\u9A45")))
    }

    @Test
    fun aMatchFurtherDownTheListIsRejected() {
        // 玉 must not pass for 主: the recogniser ranks near-identical characters
        // adjacently, so "top three" accepted visibly wrong drawings.
        assertFalse(RecognitionMatcher.isCorrect("\u4E3B", listOf("\u7389", "\u4E3B")))
        assertEquals(1, RecognitionMatcher.ACCEPTED_RANKS)
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
