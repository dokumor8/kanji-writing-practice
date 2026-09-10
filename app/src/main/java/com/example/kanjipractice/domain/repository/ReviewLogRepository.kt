package com.example.kanjipractice.domain.repository

import com.example.kanjipractice.data.db.ReviewLogEntity
import com.example.fsrs.Rating
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

interface ReviewLogRepository {
    suspend fun log(
        cardId: Long,
        rating: Rating,
        usedIDontKnow: Boolean,
        retryCount: Int,
        reviewedAt: LocalDateTime,
    )

    fun observeRecent(limit: Int = 50): Flow<List<ReviewLogEntity>>
}
