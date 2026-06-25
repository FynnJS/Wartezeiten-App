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
        versionCode = 10109,
        versionName = "1.1.9",
        highlightsDe = listOf(
            "Ladehinweise sind jetzt in der ganzen App klarer, damit leere Listen nicht mehr wie endgültige Ergebnisse wirken.",
            "Der Filter für offene Parks zeigt jetzt ausdrücklich, wenn aktuelle Parkdaten noch geprüft werden.",
            "Parkdetails, Statistik, Watchlist, Wetter und Parkvergleich haben freundlichere Lade- und Leerzustände.",
            "Der Parkvergleich aktualisiert ausgewählte Parks jetzt parallel und fühlt sich dadurch schneller an.",
        ),
        highlightsEn = listOf(
            "Loading feedback is clearer across the app, so empty lists no longer look like final results.",
            "The open-parks filter now clearly shows when current park data is still being checked.",
            "Park details, statistics, Watchlist, weather, and comparison screens now have friendlier loading and empty states.",
            "Park comparison now refreshes selected parks in parallel, making it feel faster.",
        ),
        highlightsFr = listOf(
            "Les indications de chargement sont plus claires dans toute l'app, afin que les listes vides ne ressemblent plus à des résultats définitifs.",
            "Le filtre des parcs ouverts indique maintenant clairement quand les données actuelles sont encore en cours de vérification.",
            "Les détails du parc, les statistiques, la Watchlist, la météo et la comparaison ont des états de chargement et vides plus agréables.",
            "La comparaison des parcs actualise maintenant les parcs sélectionnés en parallèle, ce qui la rend plus rapide.",
        ),
        highlightsNl = listOf(
            "Laadmeldingen zijn in de hele app duidelijker, zodat lege lijsten niet meer als definitieve resultaten aanvoelen.",
            "Het filter voor open parken toont nu duidelijk wanneer actuele parkgegevens nog worden gecontroleerd.",
            "Parkdetails, statistieken, Watchlist, weer en parkvergelijking hebben vriendelijkere laad- en lege toestanden.",
            "De parkvergelijking vernieuwt geselecteerde parken nu parallel en voelt daardoor sneller aan.",
        ),
    ),
    WhatsNewRelease(
        versionCode = 10108,
        versionName = "1.1.8",
        highlightsDe = listOf(
            "Attraktions-Alarme zeigen jetzt keine falschen Wartezeitwerte mehr, wenn die Ziel-Attraktion geschlossen ist.",
            "Parkdetails erkennen jetzt wahrscheinliche Datenquellen-Ausfälle, wenn ein geöffneter Park keine Live-Wartezeiten liefert.",
            "Die Sprachauswahl zeigt jetzt Flaggen und native Sprachnamen.",
            "Attribution-Footer bleiben auf Geräten mit System-Navigationsleiste vollständig sichtbar.",
        ),
        highlightsEn = listOf(
            "Attraction alerts no longer show mismatched wait-time values when the target attraction is closed.",
            "Park details now detect likely data-source outages when an open park provides no live wait times.",
            "The language selector now shows flags and native language names.",
            "Attribution footers now stay fully visible above the Android system navigation bar.",
        ),
        highlightsFr = listOf(
            "Les alertes d'attraction n'affichent plus de temps d'attente incorrects lorsque l'attraction cible est fermée.",
            "Les détails du parc détectent désormais les pannes probables de la source de données lorsqu'un parc ouvert ne fournit aucun temps d'attente en direct.",
            "Le sélecteur de langue affiche désormais les drapeaux et les noms natifs des langues.",
            "Les pieds de page d'attribution restent entièrement visibles au-dessus de la barre de navigation système Android.",
        ),
        highlightsNl = listOf(
            "Attractiealarmen tonen geen verkeerde wachttijdwaarden meer wanneer de doelattractie gesloten is.",
            "Parkdetails herkennen nu waarschijnlijke storingen bij de databron wanneer een geopend park geen live wachttijden levert.",
            "De taalkiezer toont nu vlaggen en native taalnamen.",
            "Attributievoeters blijven volledig zichtbaar boven de Android-systeemnavigatiebalk.",
        ),
    ),
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
