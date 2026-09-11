package com.example.kanjipractice.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
 * The daily new-card limit lives in DataStore rather than Room: it is a device
 * preference, not deck content, and it must survive a database reset.
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

    private companion object {
        val DAILY_NEW_LIMIT = intPreferencesKey("daily_new_limit")
    }
}
