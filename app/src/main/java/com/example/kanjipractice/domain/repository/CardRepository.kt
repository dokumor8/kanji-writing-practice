package com.example.kanjipractice.domain.repository

import com.example.kanjipractice.data.db.CardEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

interface CardRepository {

    /**
     * Scheduled cards due before [before]. Never-reviewed cards are not included.
     *
     * Callers pass the end of the current study day, not the current instant: see
     * DayBoundary.
     */
    fun observeDueReviews(before: LocalDateTime): Flow<List<CardEntity>>

    fun observeDueReviewCount(before: LocalDateTime): Flow<Int>

    /** The next [limit] never-reviewed cards in deck order. */
    suspend fun nextNewCards(limit: Int): List<CardEntity>

    fun observeNewCount(): Flow<Int>

    fun observeTotalCount(): Flow<Int>

    fun observeAll(): Flow<List<CardEntity>>

    suspend fun getById(id: Long): CardEntity?

    suspend fun update(card: CardEntity)

    suspend fun count(): Int
}
