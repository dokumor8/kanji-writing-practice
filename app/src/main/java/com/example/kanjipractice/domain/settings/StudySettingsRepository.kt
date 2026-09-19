package com.example.kanjipractice.domain.settings

import kotlinx.coroutines.flow.Flow

interface StudySettingsRepository {

    fun observeDailyNewLimit(): Flow<Int>
    suspend fun setDailyNewLimit(limit: Int)

    /** How many recogniser candidates may be considered. */
    fun observeAcceptedCandidates(): Flow<Int>
    suspend fun setAcceptedCandidates(count: Int)

    /** How closely the drawing must match the reference, as a percentage. */
    fun observeSimilarityThresholdPercent(): Flow<Int>
    suspend fun setSimilarityThresholdPercent(percent: Int)

    /** Which card sets the user has switched on. */
    fun observeSelectedDeckIds(): Flow<Set<String>>
    suspend fun setSelectedDeckIds(ids: Set<String>)

    /**
     * Which revision of the bundled card data the database has been reconciled
     * with. Bookkeeping for the seeder rather than a user preference, but it
     * belongs with the other small pieces of persisted app state.
     */
    fun observeDeckDataVersion(): Flow<Int>
    suspend fun setDeckDataVersion(version: Int)
}
