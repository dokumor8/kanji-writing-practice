package com.example.kanjipractice.domain.repository

import com.example.kanjipractice.data.db.ReviewLogEntity
import com.example.fsrs.Rating
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

interface ReviewLogRepository {

    /** @return the new row id, so the review can be undone. */
    suspend fun log(
        cardId: Long,
        rating: Rating,
        usedIDontKnow: Boolean,
        retryCount: Int,
        reviewedAt: LocalDateTime,
    ): Long

    suspend fun delete(id: Long)

    fun observeRecent(limit: Int = 50): Flow<List<ReviewLogEntity>>

    /** Cards whose first ever review happened at or after [since]. */
    fun observeIntroducedSince(since: LocalDateTime): Flow<Int>
}
