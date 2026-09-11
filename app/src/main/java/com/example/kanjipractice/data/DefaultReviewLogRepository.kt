package com.example.kanjipractice.data

import com.example.kanjipractice.data.db.ReviewLogDao
import com.example.kanjipractice.data.db.ReviewLogEntity
import com.example.kanjipractice.domain.repository.ReviewLogRepository
import com.example.fsrs.Rating
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultReviewLogRepository @Inject constructor(
    private val reviewLogDao: ReviewLogDao,
) : ReviewLogRepository {

    override suspend fun log(
        cardId: Long,
        rating: Rating,
        usedIDontKnow: Boolean,
        retryCount: Int,
        reviewedAt: LocalDateTime,
    ): Long = reviewLogDao.insert(
        ReviewLogEntity(
            cardId = cardId,
            rating = rating.value,
            usedIDontKnow = usedIDontKnow,
            retryCount = retryCount,
            reviewedAt = reviewedAt,
        )
    )

    override suspend fun delete(id: Long) = reviewLogDao.deleteById(id)

    override fun observeRecent(limit: Int): Flow<List<ReviewLogEntity>> =
        reviewLogDao.observeRecent(limit)

    override fun observeIntroducedSince(since: LocalDateTime): Flow<Int> =
        reviewLogDao.observeIntroducedSince(since)
}
