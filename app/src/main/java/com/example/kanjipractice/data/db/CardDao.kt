package com.example.kanjipractice.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface CardDao {

    /**
     * Cards due before [before], most overdue first.
     *
     * `before` is the end of the current study day rather than the current
     * instant, so everything due today is available in one sitting instead of
     * trickling in by the clock.
     *
     * Cards that have never been reviewed ([newState]) are deliberately excluded:
     * they are not scheduled, they enter a session only through the daily
     * new-card allowance, which is what stops a 612-card deck from arriving all
     * at once.
     */
    @Query(
        "SELECT * FROM cards WHERE state <> :newState AND due < :before " +
            "ORDER BY due ASC, id ASC"
    )
    fun observeDueReviews(before: LocalDateTime, newState: Int): Flow<List<CardEntity>>

    @Query("SELECT COUNT(*) FROM cards WHERE state <> :newState AND due < :before")
    fun observeDueReviewCount(before: LocalDateTime, newState: Int): Flow<Int>

    /** Never-reviewed cards, in deck order: easier JLPT levels first. */
    @Query("SELECT * FROM cards WHERE state = :newState ORDER BY jlpt DESC, id ASC LIMIT :limit")
    suspend fun nextNewCards(limit: Int, newState: Int): List<CardEntity>

    @Query("SELECT COUNT(*) FROM cards WHERE state = :newState")
    fun observeNewCount(newState: Int): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT * FROM cards ORDER BY jlpt DESC, due ASC, id ASC")
    fun observeAll(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun getById(id: Long): CardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<CardEntity>)

    @Update
    suspend fun update(card: CardEntity)

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun count(): Int
}
