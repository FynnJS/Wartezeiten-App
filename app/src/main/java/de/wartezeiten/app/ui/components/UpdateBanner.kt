package de.wartezeiten.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.wartezeiten.app.data.remote.dto.AppUpdateInfo

@Composable
fun UpdateBanner(
    releaseInfo: AppUpdateInfo,
    language: String = "de",
    onInstallClick: () -> Unit,
    onReleasePageClick: (() -> Unit)? = null,
) {
    val isEnglish = language == "en"
    val notes = releaseInfo.releaseNotes
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .take(3)
    val fallbackNote = if (isEnglish) {
        "Install the current APK to keep using the app."
    } else {
        "Installiere die aktuelle APK, um die App weiter zu nutzen."
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 620.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = if (isEnglish) "Update required" else "Update erforderlich",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (isEnglish) {
                            "Version ${releaseInfo.versionName} is available. This installed version is no longer supported."
                        } else {
                            "Version ${releaseInfo.versionName} ist verf\u00fcgbar. Diese installierte Version wird nicht mehr unterst\u00fctzt."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (isEnglish) {
                            "Install the APK from this update prompt over the existing app. The package id and release signing stay the same, so Android can replace the app without an app conflict."
                        } else {
                            "Installiere die APK aus diesem Update-Hinweis \u00fcber die bestehende App. Paket-ID und Release-Signatur bleiben gleich, damit Android die App ohne App-Konflikt ersetzen kann."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (notes.ifEmpty { listOf(fallbackNote) }).forEach { note ->
                            Text(
                                text = "- $note",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = onInstallClick,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = if (isEnglish) "Download APK" else "APK laden")
                        }
                        if (onReleasePageClick != null) {
                            OutlinedButton(onClick = onReleasePageClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = if (isEnglish) "Release" else "Release")
                            }
                        }
                    }
                }
            }
        }
    }
}
