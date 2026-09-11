package com.example.kanjipractice.domain.repository

import com.example.kanjipractice.data.db.CardEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

interface CardRepository {

    /** Scheduled cards that are due. Never-reviewed cards are not included. */
    fun observeDueReviews(now: LocalDateTime): Flow<List<CardEntity>>

    fun observeDueReviewCount(now: LocalDateTime): Flow<Int>

    /** The next [limit] never-reviewed cards in deck order. */
    suspend fun nextNewCards(limit: Int): List<CardEntity>

    fun observeNewCount(): Flow<Int>

    fun observeTotalCount(): Flow<Int>

    fun observeAll(): Flow<List<CardEntity>>

    suspend fun getById(id: Long): CardEntity?

    suspend fun update(card: CardEntity)

    suspend fun count(): Int
}
