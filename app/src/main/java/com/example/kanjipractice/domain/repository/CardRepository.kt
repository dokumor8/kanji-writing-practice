package com.example.kanjipractice.domain.repository

import com.example.kanjipractice.data.db.CardEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/**
 * Card access, always scoped to the sets the user has switched on.
 *
 * An empty [deckIds] means "nothing is selected" and yields empty results rather
 * than an unfiltered query -- `IN ()` is not valid SQL, and silently returning
 * the whole deck would be the wrong answer anyway.
 */
interface CardRepository {

    /**
     * Scheduled cards due before [before]. Never-reviewed cards are not included.
     *
     * Callers pass the end of the current study day, not the current instant: see
     * DayBoundary.
     */
    fun observeDueReviews(before: LocalDateTime, deckIds: Set<String>): Flow<List<CardEntity>>

    fun observeDueReviewCount(before: LocalDateTime, deckIds: Set<String>): Flow<Int>

    /** The next [limit] never-reviewed cards, commonest first. */
    suspend fun nextNewCards(limit: Int, deckIds: Set<String>): List<CardEntity>

    fun observeNewCount(deckIds: Set<String>): Flow<Int>

    fun observeTotalCount(deckIds: Set<String>): Flow<Int>

    fun observeAll(deckIds: Set<String>): Flow<List<CardEntity>>

    /** How many cards each set holds, selected or not. */
    fun observeDeckCounts(): Flow<Map<String, Int>>

    suspend fun getById(id: Long): CardEntity?

    suspend fun update(card: CardEntity)

    suspend fun count(): Int
}
