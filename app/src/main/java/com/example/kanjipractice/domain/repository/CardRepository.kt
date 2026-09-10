package com.example.kanjipractice.domain.repository

import com.example.kanjipractice.data.db.CardEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

interface CardRepository {
    fun observeDue(now: LocalDateTime): Flow<List<CardEntity>>
    fun observeDueCount(now: LocalDateTime): Flow<Int>
    fun observeTotalCount(): Flow<Int>
    fun observeAll(): Flow<List<CardEntity>>
    suspend fun getById(id: Long): CardEntity?
    suspend fun update(card: CardEntity)
    suspend fun count(): Int
}
