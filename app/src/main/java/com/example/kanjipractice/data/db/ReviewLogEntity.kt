package com.example.kanjipractice.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * An append-only record of one completed card review. Kept separate from the card
 * so the review history survives (and can later feed FSRS parameter optimisation).
 */
@Entity(tableName = "review_logs")
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    /** 1 = Again, 2 = Hard, 3 = Good, 4 = Easy. */
    val rating: Int,
    /** True when the rating came from the "I don't know" path. */
    val usedIDontKnow: Boolean,
    /** Number of failed recognition attempts before the card was resolved. */
    val retryCount: Int,
    val reviewedAt: LocalDateTime,
)
