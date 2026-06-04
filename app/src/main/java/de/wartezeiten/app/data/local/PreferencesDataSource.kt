package de.wartezeiten.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val WAITING_TIMES_SORT = stringPreferencesKey("waiting_times_sort")
        val WAITING_TIMES_FILTER = stringPreferencesKey("waiting_times_filter")
        val WAITING_TIMES_MAX_WAIT = intPreferencesKey("waiting_times_max_wait")
        val LANGUAGE = stringPreferencesKey("language")
    }

    val darkMode: Flow<Boolean?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.DARK_MODE]
        }

    val dynamicColors: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.DYNAMIC_COLORS] ?: true
        }

    val waitingTimesSort: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences[PreferencesKeys.WAITING_TIMES_SORT] }

    val waitingTimesFilter: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences[PreferencesKeys.WAITING_TIMES_FILTER] }

    val waitingTimesMaxWait: Flow<Int?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences[PreferencesKeys.WAITING_TIMES_MAX_WAIT] }

    val language: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences[PreferencesKeys.LANGUAGE] ?: DEFAULT_LANGUAGE }

    suspend fun setDarkMode(enabled: Boolean?) {
        context.dataStore.edit { preferences ->
            if (enabled == null) {
                preferences.remove(PreferencesKeys.DARK_MODE)
            } else {
                preferences[PreferencesKeys.DARK_MODE] = enabled
            }
        }
    }

    suspend fun setDynamicColors(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DYNAMIC_COLORS] = enabled
        }
    }

    suspend fun setWaitingTimesSort(value: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WAITING_TIMES_SORT] = value
        }
    }

    suspend fun setWaitingTimesFilter(value: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WAITING_TIMES_FILTER] = value
        }
    }

    suspend fun setWaitingTimesMaxWait(value: Int?) {
        context.dataStore.edit { preferences ->
            if (value == null) {
                preferences.remove(PreferencesKeys.WAITING_TIMES_MAX_WAIT)
            } else {
                preferences[PreferencesKeys.WAITING_TIMES_MAX_WAIT] = value
            }
        }
    }

    suspend fun setLanguage(value: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LANGUAGE] = value.takeIf { it in SUPPORTED_LANGUAGES } ?: DEFAULT_LANGUAGE
        }
    }

    companion object {
        const val DEFAULT_LANGUAGE = "de"
        val SUPPORTED_LANGUAGES = setOf("de", "en")
    }
}
