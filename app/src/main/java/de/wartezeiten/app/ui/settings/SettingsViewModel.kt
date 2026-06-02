package de.wartezeiten.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.wartezeiten.app.BuildConfig
import de.wartezeiten.app.data.local.PreferencesDataSource
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
    val version: String = BuildConfig.VERSION_NAME
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesDataSource: PreferencesDataSource
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesDataSource.darkMode,
        preferencesDataSource.language,
    ) { darkMode, language ->
            SettingsUiState(
                darkMode = darkMode,
                dynamicColors = false, // We'll simplify for now or add more flows
                language = language,
                version = BuildConfig.VERSION_NAME
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
}
