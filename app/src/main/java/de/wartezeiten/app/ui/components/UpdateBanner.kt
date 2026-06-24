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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.wartezeiten.app.core.i18n.localized
import de.wartezeiten.app.data.remote.dto.AppUpdateInfo
import de.wartezeiten.app.ui.update.ApkDownloadState
import de.wartezeiten.app.update.ApkInstaller
import kotlin.math.roundToInt

@Composable
fun UpdateBanner(
    releaseInfo: AppUpdateInfo,
    language: String = "de",
    downloadState: ApkDownloadState = ApkDownloadState.Idle,
    onDownloadClick: () -> Unit,
    onReleasePageClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val notes = releaseInfo.releaseNotes
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .take(3)
    val fallbackNote = localized(
        language,
        de = "Installiere die aktuelle APK, um die App weiter zu nutzen.",
        en = "Install the current APK to keep using the app.",
        fr = "Installe l'APK actuelle pour continuer à utiliser l'application.",
        nl = "Installeer de huidige APK om de app te blijven gebruiken.",
    )

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
                        text = localized(
                            language,
                            de = "Update erforderlich",
                            en = "Update required",
                            fr = "Mise \u00e0 jour requise",
                            nl = "Update vereist",
                        ),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = localized(
                            language,
                            de = "Version ${releaseInfo.versionName} ist verf\u00fcgbar. Diese installierte Version wird nicht mehr unterst\u00fctzt.",
                            en = "Version ${releaseInfo.versionName} is available. This installed version is no longer supported.",
                            fr = "La version ${releaseInfo.versionName} est disponible. Cette version install\u00e9e n'est plus prise en charge.",
                            nl = "Versie ${releaseInfo.versionName} is beschikbaar. Deze ge\u00efnstalleerde versie wordt niet meer ondersteund.",
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = localized(
                            language,
                            de = "Installiere die APK aus diesem Update-Hinweis \u00fcber die bestehende App. Paket-ID und Release-Signatur bleiben gleich, damit Android die App ohne App-Konflikt ersetzen kann.",
                            en = "Install the APK from this update prompt over the existing app. The package id and release signing stay the same, so Android can replace the app without an app conflict.",
                            fr = "Installe l'APK depuis cette invite de mise \u00e0 jour par-dessus l'application existante. L'ID du package et la signature restent identiques, Android peut donc remplacer l'application sans conflit.",
                            nl = "Installeer de APK uit deze updatemelding over de bestaande app heen. Package-ID en releasehandtekening blijven gelijk, zodat Android de app zonder conflict kan vervangen.",
                        ),
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
                    if (downloadState is ApkDownloadState.Downloading) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinearProgressIndicator(
                                progress = { downloadState.progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = localized(
                                    language,
                                    de = "Lade Update… ${(downloadState.progress * 100).roundToInt()}%",
                                    en = "Downloading update… ${(downloadState.progress * 100).roundToInt()}%",
                                    fr = "Téléchargement de la mise à jour… ${(downloadState.progress * 100).roundToInt()}%",
                                    nl = "Update wordt gedownload… ${(downloadState.progress * 100).roundToInt()}%",
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (downloadState is ApkDownloadState.Failed) {
                        Text(
                            text = downloadState.message,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {
                                when (downloadState) {
                                    is ApkDownloadState.ReadyToInstall -> {
                                        if (ApkInstaller.canInstallPackages(context)) {
                                            context.startActivity(ApkInstaller.installIntent(downloadState.apkUri))
                                        } else {
                                            context.startActivity(ApkInstaller.unknownSourcesSettingsIntent(context))
                                        }
                                    }
                                    else -> onDownloadClick()
                                }
                            },
                            enabled = downloadState !is ApkDownloadState.Downloading,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (downloadState) {
                                    is ApkDownloadState.ReadyToInstall -> localized(
                                        language,
                                        de = "Jetzt installieren",
                                        en = "Install now",
                                        fr = "Installer maintenant",
                                        nl = "Nu installeren",
                                    )
                                    is ApkDownloadState.Failed -> localized(
                                        language,
                                        de = "Erneut versuchen",
                                        en = "Try again",
                                        fr = "Réessayer",
                                        nl = "Opnieuw proberen",
                                    )
                                    is ApkDownloadState.Downloading -> localized(
                                        language,
                                        de = "Lädt…",
                                        en = "Downloading…",
                                        fr = "Téléchargement…",
                                        nl = "Downloaden…",
                                    )
                                    ApkDownloadState.Idle -> localized(
                                        language,
                                        de = "APK laden",
                                        en = "Download APK",
                                        fr = "Télécharger l'APK",
                                        nl = "APK downloaden",
                                    )
                                },
                            )
                        }
                        if (onReleasePageClick != null) {
                            OutlinedButton(onClick = onReleasePageClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Release")
                            }
                        }
                    }
                }
            }
        }
    }
}
