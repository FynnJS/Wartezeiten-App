package de.wartezeiten.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.wartezeiten.app.ui.components.AttributionBanner

@Composable
fun SettingsRoute(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onBackClick = onBackClick,
        onDarkModeChange = viewModel::setDarkMode,
        onLanguageChange = viewModel::setLanguage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBackClick: () -> Unit,
    onDarkModeChange: (Boolean?) -> Unit,
    onLanguageChange: (String) -> Unit,
) {
    var languageExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.language == "en") "Settings" else "Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (state.language == "en") "Back" else "Zurück",
                        )
                    }
                }
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ListItem(
                headlineContent = { Text("Dark Mode") },
                supportingContent = { 
                    Text(when(state.darkMode) {
                        true -> if (state.language == "en") "On" else "An"
                        false -> if (state.language == "en") "Off" else "Aus"
                        else -> if (state.language == "en") "Follow system" else "System folgen"
                    })
                },
                leadingContent = { Icon(Icons.Default.Brightness4, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = state.darkMode ?: false,
                        onCheckedChange = { onDarkModeChange(it) },
                    )
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(if (state.language == "en") "Language" else "Sprache") },
                supportingContent = { Text(state.language.languageLabel(state.language)) },
                leadingContent = { Icon(Icons.Default.Language, contentDescription = null) },
                trailingContent = {
                    IconButton(onClick = { languageExpanded = true }) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = if (state.language == "en") "Select language" else "Sprache auswählen",
                        )
                    }
                    DropdownMenu(
                        expanded = languageExpanded,
                        onDismissRequest = { languageExpanded = false },
                    ) {
                        listOf("de", "en").forEach { language ->
                            DropdownMenuItem(
                                text = { Text(language.languageLabel(state.language)) },
                                onClick = {
                                    onLanguageChange(language)
                                    languageExpanded = false
                                },
                            )
                        }
                    }
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text(state.version) },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
            )
            HorizontalDivider()
            AttributionBanner(language = state.language)
        }
    }
}

private fun String.languageLabel(currentLanguage: String): String {
    return when (this) {
        "en" -> if (currentLanguage == "en") "English" else "Englisch"
        else -> if (currentLanguage == "en") "German" else "Deutsch"
    }
}
