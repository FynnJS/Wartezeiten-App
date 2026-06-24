package de.wartezeiten.app.ui.whatsnew

/**
 * "What's new" highlights shown once after an app update.
 *
 * Convention: whenever a release bumps `versionCode`/`versionName` in `app/build.gradle.kts`
 * (see the "Prepare release" workflow in Agents.md), add a matching entry here with that
 * exact `versionCode` so returning users see what changed.
 */
data class WhatsNewRelease(
    val versionCode: Int,
    val versionName: String,
    private val highlightsDe: List<String>,
    private val highlightsEn: List<String>,
    private val highlightsFr: List<String>,
    private val highlightsNl: List<String>,
) {
    fun highlights(language: String): List<String> = when (language) {
        "en" -> highlightsEn
        "fr" -> highlightsFr
        "nl" -> highlightsNl
        else -> highlightsDe
    }
}

val WHATS_NEW_RELEASES = listOf(
    WhatsNewRelease(
        versionCode = 10107,
        versionName = "1.1.7",
        highlightsDe = listOf(
            "Neue Sprachen: Die App ist jetzt auch auf Französisch und Niederländisch verfügbar.",
            "Übersichtlicheres Favoriten-Dashboard mit Status-Badges, sortiert nach geöffneten Parks.",
            "Push-Benachrichtigungen kommen jetzt zuverlässig in deiner eingestellten Sprache an.",
            "Updates lassen sich jetzt direkt in der App herunterladen und installieren, ganz ohne Umweg über GitHub.",
        ),
        highlightsEn = listOf(
            "New languages: the app is now also available in French and Dutch.",
            "Clearer favorites dashboard with status badges, sorted with open parks first.",
            "Push notifications now reliably arrive in your selected language.",
            "Updates can now be downloaded and installed directly in the app, no more manual GitHub detour.",
        ),
        highlightsFr = listOf(
            "Nouvelles langues : l'application est désormais aussi disponible en français et en néerlandais.",
            "Tableau de bord des favoris plus clair avec badges de statut, parcs ouverts affichés en premier.",
            "Les notifications push arrivent désormais de manière fiable dans la langue choisie.",
            "Les mises à jour peuvent désormais être téléchargées et installées directement dans l'application, sans détour par GitHub.",
        ),
        highlightsNl = listOf(
            "Nieuwe talen: de app is nu ook beschikbaar in het Frans en Nederlands.",
            "Overzichtelijker favorietendashboard met statusbadges, geopende parken eerst gesorteerd.",
            "Pushmeldingen komen nu betrouwbaar aan in de door jou ingestelde taal.",
            "Updates kunnen nu direct in de app gedownload en geïnstalleerd worden, zonder omweg via GitHub.",
        ),
    ),
)

fun latestUnseenRelease(currentVersionCode: Int, lastSeenVersionCode: Int): WhatsNewRelease? {
    return WHATS_NEW_RELEASES
        .filter { it.versionCode <= currentVersionCode && it.versionCode > lastSeenVersionCode }
        .maxByOrNull { it.versionCode }
}
