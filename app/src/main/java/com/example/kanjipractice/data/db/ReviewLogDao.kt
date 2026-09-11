package com.example.kanjipractice.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface ReviewLogDao {

    /** @return the new row id, which is what makes a review undoable. */
    @Insert
    suspend fun insert(log: ReviewLogEntity): Long

    /** Used by "undo review" to erase the review being taken back. */
    @Query("DELETE FROM review_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM review_logs ORDER BY reviewedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ReviewLogEntity>>

    @Query("SELECT COUNT(*) FROM review_logs")
    suspend fun count(): Int

    /**
     * How many cards had their *first ever* review at or after [since].
     *
     * The daily new-card allowance is derived from the log rather than from a
     * counter, which keeps it honest for free: undoing a review deletes its log
     * row, and the card stops counting as introduced.
     */
    @Query(
        "SELECT COUNT(*) FROM cards WHERE (" +
            "SELECT MIN(reviewedAt) FROM review_logs WHERE review_logs.cardId = cards.id" +
            ") >= :since"
    )
    fun observeIntroducedSince(since: LocalDateTime): Flow<Int>
}
