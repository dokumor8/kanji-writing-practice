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

    /** Cards that are due at [now], most overdue first. */
    @Query("SELECT * FROM cards WHERE due <= :now ORDER BY due ASC, id ASC")
    fun observeDue(now: LocalDateTime): Flow<List<CardEntity>>

    @Query("SELECT COUNT(*) FROM cards WHERE due <= :now")
    fun observeDueCount(now: LocalDateTime): Flow<Int>

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
