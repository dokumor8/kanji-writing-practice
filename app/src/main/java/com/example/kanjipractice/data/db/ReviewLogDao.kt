package com.example.kanjipractice.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewLogDao {

    @Insert
    suspend fun insert(log: ReviewLogEntity)

    @Query("SELECT * FROM review_logs ORDER BY reviewedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<ReviewLogEntity>>

    @Query("SELECT COUNT(*) FROM review_logs")
    suspend fun count(): Int
}
