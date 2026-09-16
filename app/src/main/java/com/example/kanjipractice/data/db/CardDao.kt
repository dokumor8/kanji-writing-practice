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
     * Cards due before [before], most overdue first, restricted to [deckIds].
     *
     * `before` is the end of the current study day rather than the current
     * instant, so everything due today is available in one sitting instead of
     * trickling in by the clock.
     *
     * Cards that have never been reviewed ([newState]) are deliberately excluded:
     * they are not scheduled, they enter a session only through the daily
     * new-card allowance, which is what stops a large deck from arriving all at
     * once.
     */
    @Query(
        "SELECT * FROM cards WHERE state <> :newState AND due < :before " +
            "AND deckId IN (:deckIds) ORDER BY due ASC, id ASC"
    )
    fun observeDueReviews(
        before: LocalDateTime,
        newState: Int,
        deckIds: List<String>,
    ): Flow<List<CardEntity>>

    @Query(
        "SELECT COUNT(*) FROM cards WHERE state <> :newState AND due < :before " +
            "AND deckId IN (:deckIds)"
    )
    fun observeDueReviewCount(
        before: LocalDateTime,
        newState: Int,
        deckIds: List<String>,
    ): Flow<Int>

    /** Never-reviewed cards in set order: commonest kanji first. */
    @Query(
        "SELECT * FROM cards WHERE state = :newState AND deckId IN (:deckIds) " +
            "ORDER BY deckSortKey ASC LIMIT :limit"
    )
    suspend fun nextNewCards(
        limit: Int,
        newState: Int,
        deckIds: List<String>,
    ): List<CardEntity>

    @Query("SELECT COUNT(*) FROM cards WHERE state = :newState AND deckId IN (:deckIds)")
    fun observeNewCount(newState: Int, deckIds: List<String>): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards WHERE deckId IN (:deckIds)")
    fun observeTotalCount(deckIds: List<String>): Flow<Int>

    @Query("SELECT * FROM cards WHERE deckId IN (:deckIds) ORDER BY deckId ASC, deckSortKey ASC")
    fun observeAll(deckIds: List<String>): Flow<List<CardEntity>>

    /** Card counts per set, for the settings screen. */
    @Query("SELECT deckId, COUNT(*) AS count FROM cards WHERE deckId IS NOT NULL GROUP BY deckId")
    fun observeDeckCounts(): Flow<List<DeckCount>>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun getById(id: Long): CardEntity?

    /**
     * Seeds cards that are not present yet and leaves existing rows untouched,
     * so a re-seed can never overwrite somebody's review history.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(cards: List<CardEntity>): List<Long>

    /** Set membership is data, not progress, so it is assigned separately. */
    @Query("UPDATE cards SET deckId = :deckId, deckSortKey = :sortKey WHERE id = :id")
    suspend fun assignDeck(id: Long, deckId: String, sortKey: Int)

    @Query("SELECT COUNT(*) FROM cards WHERE deckId IS NULL")
    suspend fun countUnassigned(): Int

    @Update
    suspend fun update(card: CardEntity)

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun count(): Int
}

/** Row type for the per-set card counts. */
data class DeckCount(val deckId: String, val count: Int)
