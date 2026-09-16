package com.example.kanjipractice.data

import com.example.kanjipractice.data.db.CardDao
import com.example.kanjipractice.data.db.CardEntity
import com.example.kanjipractice.domain.model.CardState
import com.example.kanjipractice.domain.repository.CardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultCardRepository @Inject constructor(
    private val cardDao: CardDao,
) : CardRepository {

    override fun observeDueReviews(
        before: LocalDateTime,
        deckIds: Set<String>,
    ): Flow<List<CardEntity>> =
        if (deckIds.isEmpty()) flowOf(emptyList())
        else cardDao.observeDueReviews(before, NEW, deckIds.toList())

    override fun observeDueReviewCount(
        before: LocalDateTime,
        deckIds: Set<String>,
    ): Flow<Int> =
        if (deckIds.isEmpty()) flowOf(0)
        else cardDao.observeDueReviewCount(before, NEW, deckIds.toList())

    override suspend fun nextNewCards(limit: Int, deckIds: Set<String>): List<CardEntity> =
        if (limit <= 0 || deckIds.isEmpty()) {
            emptyList()
        } else {
            cardDao.nextNewCards(limit, NEW, deckIds.toList())
        }

    override fun observeNewCount(deckIds: Set<String>): Flow<Int> =
        if (deckIds.isEmpty()) flowOf(0) else cardDao.observeNewCount(NEW, deckIds.toList())

    override fun observeTotalCount(deckIds: Set<String>): Flow<Int> =
        if (deckIds.isEmpty()) flowOf(0) else cardDao.observeTotalCount(deckIds.toList())

    override fun observeAll(deckIds: Set<String>): Flow<List<CardEntity>> =
        if (deckIds.isEmpty()) flowOf(emptyList()) else cardDao.observeAll(deckIds.toList())

    override fun observeDeckCounts(): Flow<Map<String, Int>> =
        cardDao.observeDeckCounts().map { rows -> rows.associate { it.deckId to it.count } }

    override suspend fun getById(id: Long): CardEntity? = cardDao.getById(id)

    override suspend fun update(card: CardEntity) = cardDao.update(card)

    override suspend fun count(): Int = cardDao.count()

    private companion object {
        /** Matches CardState.NEW; pinned by a test so it cannot drift. */
        val NEW = CardState.NEW.value
    }
}
