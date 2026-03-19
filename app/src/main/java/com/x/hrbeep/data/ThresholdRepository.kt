package com.x.hrbeep.data

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class ThresholdRepository(
    private val context: Context,
) {
    val thresholdFlow: Flow<Int?> = context.settingsDataStore.data.map { preferences ->
        val v = preferences[KEY_THRESHOLD] ?: DEFAULT_THRESHOLD_BPM
        if (v <= 0) null else v
    }

    val lastConnectedAddressFlow: Flow<String?> = context.settingsDataStore.data.map { preferences ->
        preferences[KEY_LAST_CONNECTED_ADDRESS]
    }

    val soundIntensityFlow: Flow<Int> = context.settingsDataStore.data.map { preferences ->
        preferences[KEY_SOUND_INTENSITY] ?: DEFAULT_SOUND_INTENSITY
    }

    val lowerBoundFlow: Flow<Int?> = context.settingsDataStore.data.map { preferences ->
        val v = preferences[KEY_LOWER_BOUND] ?: 0
        if (v <= 0) null else v
    }

    suspend fun saveThreshold(value: Int?) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_THRESHOLD] = value ?: 0
        }
    }

    suspend fun saveLastConnectedAddress(address: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_LAST_CONNECTED_ADDRESS] = address
        }
    }

    suspend fun saveSoundIntensity(value: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_SOUND_INTENSITY] = value.coerceIn(0, 100)
        }
    }

    suspend fun saveLowerBound(value: Int?) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_LOWER_BOUND] = value ?: 0
        }
    }

    companion object {
        const val DEFAULT_THRESHOLD_BPM = 140
        const val DEFAULT_SOUND_INTENSITY = 80
        private val KEY_THRESHOLD = intPreferencesKey("threshold_bpm")
        private val KEY_SOUND_INTENSITY = intPreferencesKey("sound_intensity")
        private val KEY_LOWER_BOUND = intPreferencesKey("lower_bound_bpm")
        private val KEY_LAST_CONNECTED_ADDRESS = stringPreferencesKey("last_connected_address")
    }
}
