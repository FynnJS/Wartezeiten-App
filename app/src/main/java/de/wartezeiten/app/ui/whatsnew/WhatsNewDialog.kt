package de.wartezeiten.app.ui.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.wartezeiten.app.core.i18n.localized

@Composable
fun WhatsNewRoute(viewModel: WhatsNewViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    state.release?.let { release ->
        WhatsNewDialog(
            release = release,
            language = state.language,
            onDismiss = viewModel::dismiss,
        )
    }
}

@Composable
private fun WhatsNewDialog(
    release: WhatsNewRelease,
    language: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                localized(
                    language,
                    de = "Neu in Version ${release.versionName}",
                    en = "New in version ${release.versionName}",
                    fr = "Nouveautés de la version ${release.versionName}",
                    nl = "Nieuw in versie ${release.versionName}",
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                release.highlights(language).forEach { highlight ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("•")
                        Text(highlight)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(localized(language, de = "Verstanden", en = "Got it", fr = "Compris", nl = "Begrepen"))
            }
        },
    )
}
