package com.example.kanjipractice.data

import com.example.kanjipractice.data.db.CardDao
import com.example.kanjipractice.data.db.CardEntity
import com.example.kanjipractice.domain.repository.CardRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultCardRepository @Inject constructor(
    private val cardDao: CardDao,
) : CardRepository {

    override fun observeDue(now: LocalDateTime): Flow<List<CardEntity>> = cardDao.observeDue(now)

    override fun observeDueCount(now: LocalDateTime): Flow<Int> = cardDao.observeDueCount(now)

    override fun observeTotalCount(): Flow<Int> = cardDao.observeTotalCount()

    override fun observeAll(): Flow<List<CardEntity>> = cardDao.observeAll()

    override suspend fun getById(id: Long): CardEntity? = cardDao.getById(id)

    override suspend fun update(card: CardEntity) = cardDao.update(card)

    override suspend fun count(): Int = cardDao.count()
}
