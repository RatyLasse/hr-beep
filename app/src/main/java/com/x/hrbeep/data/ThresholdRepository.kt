package com.x.hrbeep.data

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class ThresholdRepository(
    private val context: Context,
) {
    val thresholdFlow: Flow<Int> = context.settingsDataStore.data.map { preferences ->
        preferences[KEY_THRESHOLD] ?: DEFAULT_THRESHOLD_BPM
    }

    suspend fun saveThreshold(value: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_THRESHOLD] = value
        }
    }

    companion object {
        const val DEFAULT_THRESHOLD_BPM = 140
        private val KEY_THRESHOLD = intPreferencesKey("threshold_bpm")
    }
}

