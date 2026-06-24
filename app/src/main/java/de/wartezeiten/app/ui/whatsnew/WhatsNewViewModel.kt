package de.wartezeiten.app.ui.whatsnew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.wartezeiten.app.BuildConfig
import de.wartezeiten.app.data.local.PreferencesDataSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WhatsNewUiState(
    val release: WhatsNewRelease? = null,
    val language: String = PreferencesDataSource.DEFAULT_LANGUAGE,
)

@HiltViewModel
class WhatsNewViewModel @Inject constructor(
    private val preferences: PreferencesDataSource,
) : ViewModel() {

    val uiState: StateFlow<WhatsNewUiState> = combine(
        preferences.lastSeenVersionCode,
        preferences.language,
    ) { lastSeenVersionCode, language ->
        WhatsNewUiState(
            release = latestUnseenRelease(BuildConfig.VERSION_CODE, lastSeenVersionCode),
            language = language,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WhatsNewUiState(),
    )

    fun dismiss() {
        viewModelScope.launch {
            preferences.setLastSeenVersionCode(BuildConfig.VERSION_CODE)
        }
    }
}
