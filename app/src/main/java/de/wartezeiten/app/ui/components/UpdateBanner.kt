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
    val notes = releaseInfo.releaseNotesFor(language)
        .map { it.trim() }
        .filter { it.isNotBlank() }
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
                            fr = "Mise à jour requise",
                            nl = "Update vereist",
                        ),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = localized(
                            language,
                            de = "Version ${releaseInfo.versionName} ist verfügbar. Diese installierte Version wird nicht mehr unterstützt.",
                            en = "Version ${releaseInfo.versionName} is available. This installed version is no longer supported.",
                            fr = "La version ${releaseInfo.versionName} est disponible. Cette version installée n'est plus prise en charge.",
                            nl = "Versie ${releaseInfo.versionName} is beschikbaar. Deze geïnstalleerde versie wordt niet meer ondersteund.",
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = localized(
                            language,
                            de = "Installiere die APK aus diesem Hinweis über die bestehende App. Falls Android trotzdem einen Paketkonflikt meldet, wurde die installierte App mit einem anderen Schlüssel signiert und muss einmalig neu installiert werden.",
                            en = "Install the APK from this prompt over the existing app. If Android still reports a package conflict, the installed app was signed with another key and needs one clean reinstall.",
                            fr = "Installe l'APK depuis cette invite par-dessus l'application existante. Si Android signale malgré tout un conflit de package, l'application installée a été signée avec une autre clé et doit être réinstallée une seule fois.",
                            nl = "Installeer de APK uit deze melding over de bestaande app heen. Als Android toch een pakketconflict meldt, is de geïnstalleerde app met een andere sleutel ondertekend en is een eenmalige herinstallatie nodig.",
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
