package de.wartezeiten.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import de.wartezeiten.app.ui.WartezeitenApp
import de.wartezeiten.app.ui.settings.SettingsViewModel
import de.wartezeiten.app.ui.theme.WartezeitenTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var notificationParkKey by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationParkKey = intent.notificationParkKey()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

            WartezeitenTheme(
                darkTheme = settingsState.darkMode ?: isSystemInDarkTheme()
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WartezeitenApp(notificationParkKey = notificationParkKey)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationParkKey = intent.notificationParkKey()
    }

    private fun Intent?.notificationParkKey(): String? {
        val uri = this?.data ?: return null
        return uri.takeIf { it.scheme == "wartezeiten" && it.host == "parks" }
            ?.lastPathSegment
    }
}
