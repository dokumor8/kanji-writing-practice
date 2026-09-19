package com.example.kanjipractice.domain.recognition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecognitionMatcherTest {

    @Test
    fun topOneAcceptsOnlyTheFirstCandidate() {
        assertTrue(RecognitionMatcher.isCorrect("\u99C5", listOf("\u99C5", "\u9A45"), 1))
    }

    @Test
    fun topOneRejectsAMatchFurtherDownTheList() {
        // 玉 must not pass for 主: the recogniser ranks near-identical characters
        // adjacently, so accepting more than one let visibly wrong drawings pass.
        assertFalse(RecognitionMatcher.isCorrect("\u4E3B", listOf("\u7389", "\u4E3B"), 1))
    }

    @Test
    fun aWiderWindowAcceptsTheSameMatch() {
        // How wide the window is comes from settings; this is the rule itself.
        assertTrue(RecognitionMatcher.isCorrect("\u4E3B", listOf("\u7389", "\u4E3B"), 3))
    }

    @Test
    fun aMatchOutsideTheWindowIsStillRejected() {
        assertFalse(
            RecognitionMatcher.isCorrect("\u4E3B", listOf("\u7389", "\u738B", "\u4E3B"), 2)
        )
    }

    @Test
    fun nothingDrawnNeverMatches() {
        assertFalse(RecognitionMatcher.isCorrect("\u99C5", emptyList(), 3))
    }

    @Test
    fun theBestCandidateIsTheFirstOne() {
        assertEquals("\u99C5", RecognitionMatcher.bestCandidate(listOf("\u99C5", "\u9A45")))
        assertNull(RecognitionMatcher.bestCandidate(emptyList()))
    }
}
