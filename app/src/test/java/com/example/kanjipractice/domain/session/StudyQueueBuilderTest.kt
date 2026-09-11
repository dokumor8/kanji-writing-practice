package com.example.kanjipractice.domain.session

import com.example.kanjipractice.data.db.CardEntity
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class StudyQueueBuilderTest {

    private val now: LocalDateTime = LocalDateTime.of(2024, 5, 1, 9, 0)

    @Test
    fun dueReviewsComeBeforeNewCards() {
        val review = card(1, "\u65E5")
        val new = card(2, "\u6708")
        assertEquals(
            listOf(review, new),
            StudyQueueBuilder.build(listOf(review), listOf(new), remainingNewAllowance = 5),
        )
    }

    @Test
    fun theNewCardAllowanceIsApplied() {
        val new = (1..10).map { card(it.toLong(), "\u65E5") }
        val queue = StudyQueueBuilder.build(emptyList(), new, remainingNewAllowance = 3)
        assertEquals(3, queue.size)
    }

    @Test
    fun aZeroAllowanceAddsNoNewCards() {
        val new = listOf(card(1, "\u65E5"))
        assertEquals(emptyList(), StudyQueueBuilder.build(emptyList(), new, 0))
    }

    @Test
    fun aNegativeAllowanceIsTreatedAsZero() {
        val new = listOf(card(1, "\u65E5"))
        assertEquals(emptyList(), StudyQueueBuilder.build(emptyList(), new, -3))
    }

    @Test
    fun aShortDeckIsNotPadded() {
        val new = listOf(card(1, "\u65E5"), card(2, "\u6708"))
        assertEquals(2, StudyQueueBuilder.build(emptyList(), new, remainingNewAllowance = 50).size)
    }

    private fun card(id: Long, character: String) = CardEntity(
        id = id,
        character = character,
        meaning = "m",
        onyomi = null,
        kunyomi = null,
        exampleWord = null,
        jlpt = 5,
        due = now,
    )
}
