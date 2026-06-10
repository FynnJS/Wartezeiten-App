package de.wartezeiten.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.wartezeiten.app.BuildConfig
import de.wartezeiten.app.data.local.PreferencesDataSource
import de.wartezeiten.app.domain.repository.WartezeitenRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val darkMode: Boolean? = null,
    val dynamicColors: Boolean = true,
    val language: String = PreferencesDataSource.DEFAULT_LANGUAGE,
    val version: String = BuildConfig.VERSION_NAME,
    val cacheClearMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesDataSource: PreferencesDataSource,
    private val repository: WartezeitenRepository,
) : ViewModel() {
    private val cacheClearMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesDataSource.darkMode,
        preferencesDataSource.language,
        cacheClearMessage,
    ) { darkMode, language, message ->
            SettingsUiState(
                darkMode = darkMode,
                dynamicColors = false, // We'll simplify for now or add more flows
                language = language,
                version = BuildConfig.VERSION_NAME,
                cacheClearMessage = message,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState()
        )

    fun setDarkMode(enabled: Boolean?) {
        viewModelScope.launch {
            preferencesDataSource.setDarkMode(enabled)
        }
    }

    fun setLanguage(value: String) {
        viewModelScope.launch {
            preferencesDataSource.setLanguage(value)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearCachedData()
            cacheClearMessage.value = "done"
        }
    }

    fun dismissCacheClearMessage() {
        cacheClearMessage.value = null
    }
}
