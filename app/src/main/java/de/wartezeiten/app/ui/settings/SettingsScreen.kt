package de.wartezeiten.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.wartezeiten.app.core.i18n.localized
import de.wartezeiten.app.data.local.PreferencesDataSource
import de.wartezeiten.app.ui.components.AttributionBanner
import de.wartezeiten.app.core.utils.countryToFlag

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
        onClearCache = viewModel::clearCache,
        onDismissCacheMessage = viewModel::dismissCacheClearMessage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBackClick: () -> Unit,
    onDarkModeChange: (Boolean?) -> Unit,
    onLanguageChange: (String) -> Unit,
    onClearCache: () -> Unit,
    onDismissCacheMessage: () -> Unit,
) {
    var languageExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        localized(
                            state.language,
                            de = "Einstellungen",
                            en = "Settings",
                            fr = "Paramètres",
                            nl = "Instellingen",
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = localized(
                                state.language,
                                de = "Zurück",
                                en = "Back",
                                fr = "Retour",
                                nl = "Terug",
                            ),
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
                    Text(
                        when (state.darkMode) {
                            true -> localized(state.language, de = "An", en = "On", fr = "Activé", nl = "Aan")
                            false -> localized(state.language, de = "Aus", en = "Off", fr = "Désactivé", nl = "Uit")
                            else -> localized(
                                state.language,
                                de = "System folgen",
                                en = "Follow system",
                                fr = "Suivre le système",
                                nl = "Systeem volgen",
                            )
                        },
                    )
                },
                leadingContent = { Icon(Icons.Default.Brightness4, contentDescription = null) },
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                val options = listOf(
                    null to localized(state.language, de = "System", en = "System", fr = "Système", nl = "Systeem"),
                    false to localized(state.language, de = "Hell", en = "Light", fr = "Clair", nl = "Licht"),
                    true to localized(state.language, de = "Dunkel", en = "Dark", fr = "Sombre", nl = "Donker"),
                )
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = state.darkMode == option.first,
                        onClick = { onDarkModeChange(option.first) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = options.size,
                        ),
                    ) {
                        Text(option.second)
                    }
                }
            }
            HorizontalDivider()
            ListItem(
                headlineContent = {
                    Text(localized(state.language, de = "Sprache", en = "Language", fr = "Langue", nl = "Taal"))
                },
                supportingContent = { Text(state.language.languageLabel()) },
                leadingContent = { Icon(Icons.Default.Language, contentDescription = null) },
                trailingContent = {
                    IconButton(onClick = { languageExpanded = true }) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = localized(
                                state.language,
                                de = "Sprache auswählen",
                                en = "Select language",
                                fr = "Choisir la langue",
                                nl = "Taal selecteren",
                            ),
                        )
                    }
                    DropdownMenu(
                        expanded = languageExpanded,
                        onDismissRequest = { languageExpanded = false },
                    ) {
                        PreferencesDataSource.SUPPORTED_LANGUAGES.sorted().forEach { language ->
                            DropdownMenuItem(
                                text = { Text(language.languageLabel()) },
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
                headlineContent = {
                    Text(localized(state.language, de = "Lokaler Cache", en = "Local cache", fr = "Cache local", nl = "Lokale cache"))
                },
                supportingContent = {
                    Text(
                        if (state.cacheClearMessage != null) {
                            localized(
                                state.language,
                                de = "Cache-Daten wurden gelöscht. Favoriten und Benachrichtigungen bleiben erhalten.",
                                en = "Cached API data was cleared. Favorites and notifications stay saved.",
                                fr = "Les données en cache ont été supprimées. Favoris et notifications restent conservés.",
                                nl = "Gecachte gegevens zijn gewist. Favorieten en meldingen blijven bewaard.",
                            )
                        } else {
                            localized(
                                state.language,
                                de = "Park-, Attraktions-, Wetter- und Statistikdaten aus dem Cache löschen.",
                                en = "Clear cached park, attraction, weather, and statistics data.",
                                fr = "Supprimer les données en cache des parcs, attractions, météo et statistiques.",
                                nl = "Wis gecachte park-, attractie-, weer- en statistiekgegevens.",
                            )
                        },
                    )
                },
                leadingContent = { Icon(Icons.Default.Delete, contentDescription = null) },
                trailingContent = {
                    TextButton(
                        onClick = {
                            if (state.cacheClearMessage == null) {
                                onClearCache()
                            } else {
                                onDismissCacheMessage()
                            }
                        },
                    ) {
                        Text(
                            if (state.cacheClearMessage == null) {
                                localized(state.language, de = "Leeren", en = "Clear", fr = "Vider", nl = "Wissen")
                            } else {
                                "OK"
                            }
                        )
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

private fun String.languageLabel(): String {
    val flag = when (this) {
        "en" -> countryToFlag("United Kingdom")
        "fr" -> countryToFlag("France")
        "nl" -> countryToFlag("Netherlands")
        else -> countryToFlag("Germany")
    }
    val nativeName = when (this) {
        "en" -> "English"
        "fr" -> "Français"
        "nl" -> "Nederlands"
        else -> "Deutsch"
    }
    return "$flag $nativeName"
}
