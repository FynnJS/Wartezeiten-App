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
import java.util.UUID
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
        val PARK_SORT = stringPreferencesKey("park_sort")
        val PARK_SEARCH_QUERY = stringPreferencesKey("park_search_query")
        val PARK_SEARCH_HISTORY = stringPreferencesKey("park_search_history")
        val RECENT_PARK_KEYS = stringPreferencesKey("recent_park_keys")
        val PUSH_INSTALLATION_ID = stringPreferencesKey("push_installation_id")
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

    val parkSort: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences[PreferencesKeys.PARK_SORT] }

    val parkSearchQuery: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences[PreferencesKeys.PARK_SEARCH_QUERY].orEmpty() }

    val parkSearchHistory: Flow<List<String>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.PARK_SEARCH_HISTORY]
                ?.split(SEARCH_HISTORY_SEPARATOR)
                .orEmpty()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_SEARCH_HISTORY_ITEMS)
        }

    val recentParkKeys: Flow<List<String>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.RECENT_PARK_KEYS]
                ?.split(SEARCH_HISTORY_SEPARATOR)
                .orEmpty()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_RECENT_PARK_ITEMS)
        }

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

    suspend fun setParkSort(value: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PARK_SORT] = value
        }
    }

    suspend fun setParkSearchQuery(value: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PARK_SEARCH_QUERY] = value
        }
    }

    suspend fun addParkSearchHistory(value: String) {
        val normalizedValue = value.trim().takeIf { it.length >= 2 } ?: return
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.PARK_SEARCH_HISTORY]
                ?.split(SEARCH_HISTORY_SEPARATOR)
                .orEmpty()
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val updated = (listOf(normalizedValue) + current.filterNot { it.equals(normalizedValue, ignoreCase = true) })
                .take(MAX_SEARCH_HISTORY_ITEMS)
            preferences[PreferencesKeys.PARK_SEARCH_HISTORY] = updated.joinToString(SEARCH_HISTORY_SEPARATOR)
        }
    }

    suspend fun clearParkSearchHistory() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.PARK_SEARCH_HISTORY)
        }
    }

    suspend fun addRecentParkKey(value: String) {
        val normalizedValue = value.trim().takeIf { it.isNotBlank() } ?: return
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.RECENT_PARK_KEYS]
                ?.split(SEARCH_HISTORY_SEPARATOR)
                .orEmpty()
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val updated = (listOf(normalizedValue) + current.filterNot { it == normalizedValue })
                .take(MAX_RECENT_PARK_ITEMS)
            preferences[PreferencesKeys.RECENT_PARK_KEYS] = updated.joinToString(SEARCH_HISTORY_SEPARATOR)
        }
    }

    suspend fun getOrCreatePushInstallationId(): String {
        var installationId: String? = null
        context.dataStore.edit { preferences ->
            installationId = preferences[PreferencesKeys.PUSH_INSTALLATION_ID]
            if (installationId.isNullOrBlank()) {
                installationId = UUID.randomUUID().toString()
                preferences[PreferencesKeys.PUSH_INSTALLATION_ID] = installationId.orEmpty()
            }
        }
        return installationId.orEmpty()
    }

    suspend fun setLanguage(value: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LANGUAGE] = value.takeIf { it in SUPPORTED_LANGUAGES } ?: DEFAULT_LANGUAGE
        }
    }

    companion object {
        const val DEFAULT_LANGUAGE = "de"
        val SUPPORTED_LANGUAGES = setOf("de", "en")
        private const val SEARCH_HISTORY_SEPARATOR = "\u001F"
        private const val MAX_SEARCH_HISTORY_ITEMS = 5
        private const val MAX_RECENT_PARK_ITEMS = 5
    }
}
