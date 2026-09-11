package com.example.kanjipractice.data

import com.example.kanjipractice.data.db.CardDao
import com.example.kanjipractice.data.db.CardEntity
import com.example.kanjipractice.domain.model.CardState
import com.example.kanjipractice.domain.repository.CardRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultCardRepository @Inject constructor(
    private val cardDao: CardDao,
) : CardRepository {

    override fun observeDueReviews(before: LocalDateTime): Flow<List<CardEntity>> =
        cardDao.observeDueReviews(before, NEW)

    override fun observeDueReviewCount(before: LocalDateTime): Flow<Int> =
        cardDao.observeDueReviewCount(before, NEW)

    override suspend fun nextNewCards(limit: Int): List<CardEntity> =
        if (limit <= 0) emptyList() else cardDao.nextNewCards(limit, NEW)

    override fun observeNewCount(): Flow<Int> = cardDao.observeNewCount(NEW)

    override fun observeTotalCount(): Flow<Int> = cardDao.observeTotalCount()

    override fun observeAll(): Flow<List<CardEntity>> = cardDao.observeAll()

    override suspend fun getById(id: Long): CardEntity? = cardDao.getById(id)

    override suspend fun update(card: CardEntity) = cardDao.update(card)

    override suspend fun count(): Int = cardDao.count()

    private companion object {
        /** Matches CardState.NEW; pinned by a test so it cannot drift. */
        val NEW = CardState.NEW.value
    }
}
