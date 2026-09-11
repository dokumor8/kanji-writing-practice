package com.example.kanjipractice.domain.session

import com.example.kanjipractice.data.db.CardEntity

/**
 * Decides what a study session contains.
 *
 * Two rules, both of them product decisions rather than algorithm:
 *
 *  - Due reviews come first. They are the cards the scheduler says are about to
 *    be forgotten, so they are the ones worth spending attention on.
 *  - New cards are capped by the daily allowance, and are only added on top of
 *    whatever is already due. Without the cap a 612-card deck would present all
 *    612 on day one, which is exactly the failure this is here to prevent.
 */
object StudyQueueBuilder {

    fun build(
        dueReviews: List<CardEntity>,
        newCards: List<CardEntity>,
        remainingNewAllowance: Int,
    ): List<CardEntity> =
        dueReviews + newCards.take(remainingNewAllowance.coerceAtLeast(0))
}
