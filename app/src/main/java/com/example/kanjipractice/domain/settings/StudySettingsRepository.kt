package com.example.kanjipractice.domain.settings

import kotlinx.coroutines.flow.Flow

interface StudySettingsRepository {
    fun observeDailyNewLimit(): Flow<Int>
    suspend fun setDailyNewLimit(limit: Int)
}
