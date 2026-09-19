package com.example.kanjipractice.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.kanjipractice.domain.deck.DeckCatalog
import com.example.kanjipractice.domain.settings.StudySettings
import com.example.kanjipractice.domain.settings.StudySettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.studySettingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "study_settings",
)

/**
 * Device preferences rather than deck content: they must survive a database
 * reset, and they are not worth a table.
 */
@Singleton
class DataStoreStudySettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : StudySettingsRepository {

    override fun observeDailyNewLimit(): Flow<Int> =
        context.studySettingsStore.data.map { preferences ->
            preferences[DAILY_NEW_LIMIT] ?: StudySettings.DEFAULT_DAILY_NEW_LIMIT
        }

    override suspend fun setDailyNewLimit(limit: Int) {
        val clamped = StudySettings.coerceDailyNewLimit(limit)
        context.studySettingsStore.edit { preferences ->
            preferences[DAILY_NEW_LIMIT] = clamped
        }
    }

    override fun observeAcceptedCandidates(): Flow<Int> =
        context.studySettingsStore.data.map { preferences ->
            preferences[ACCEPTED_CANDIDATES] ?: StudySettings.DEFAULT_ACCEPTED_CANDIDATES
        }

    override suspend fun setAcceptedCandidates(count: Int) {
        val clamped = StudySettings.coerceAcceptedCandidates(count)
        context.studySettingsStore.edit { preferences ->
            preferences[ACCEPTED_CANDIDATES] = clamped
        }
    }

    override fun observeSimilarityThresholdPercent(): Flow<Int> =
        context.studySettingsStore.data.map { preferences ->
            preferences[SIMILARITY_THRESHOLD] ?: StudySettings.DEFAULT_SIMILARITY_PERCENT
        }

    override suspend fun setSimilarityThresholdPercent(percent: Int) {
        val clamped = StudySettings.coerceSimilarityPercent(percent)
        context.studySettingsStore.edit { preferences ->
            preferences[SIMILARITY_THRESHOLD] = clamped
        }
    }

    override fun observeSelectedDeckIds(): Flow<Set<String>> =
        context.studySettingsStore.data.map { preferences ->
            // An empty set means "nothing chosen", which is a legitimate state to
            // be in; only a preference that was never written falls back to the
            // default.
            preferences[SELECTED_DECKS] ?: DeckCatalog.DEFAULT_SELECTED
        }

    override suspend fun setSelectedDeckIds(ids: Set<String>) {
        val known = ids.filter { it in DeckCatalog.byId }.toSet()
        context.studySettingsStore.edit { preferences ->
            preferences[SELECTED_DECKS] = known
        }
    }

    override fun observeDeckDataVersion(): Flow<Int> =
        context.studySettingsStore.data.map { preferences ->
            preferences[DECK_DATA_VERSION] ?: 0
        }

    override suspend fun setDeckDataVersion(version: Int) {
        context.studySettingsStore.edit { preferences ->
            preferences[DECK_DATA_VERSION] = version
        }
    }

    private companion object {
        val DAILY_NEW_LIMIT = intPreferencesKey("daily_new_limit")
        val ACCEPTED_CANDIDATES = intPreferencesKey("accepted_candidates")
        val SIMILARITY_THRESHOLD = intPreferencesKey("similarity_threshold_percent")
        val SELECTED_DECKS = stringSetPreferencesKey("selected_deck_ids")
        val DECK_DATA_VERSION = intPreferencesKey("deck_data_version")
    }
}
