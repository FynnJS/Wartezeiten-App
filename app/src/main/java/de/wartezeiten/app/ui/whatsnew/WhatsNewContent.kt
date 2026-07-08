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
        versionCode = 10202,
        versionName = "1.2.2",
        highlightsDe = listOf(
            "Statistik-Graphen und Tagesdaten verwenden jetzt die Ortszeit des Parks (z. B. korrekte 'heutige' Statistiken für USA-Parks auch bei Betrachtung aus Deutschland).",
            "Zahlreiche Stabilitätsverbesserungen: robusterer APK-Download, bessere Fehlerbehandlung bei Push, sichere Typ-Umwandlungen und verbesserte Benachrichtigungs-IDs.",
            "Korrigierte Perzentil-Berechnung für Crowd-Level-Schätzungen und verbesserte Eingabevalidierung.",
        ),
        highlightsEn = listOf(
            "Statistics graphs and daily data now respect the park's local timezone (e.g. correct 'today' stats for US parks even when viewed from Germany).",
            "Numerous stability improvements: more robust APK download, better push error handling, safe type casts, and improved notification IDs.",
            "Fixed percentile calculation for crowd level estimates and improved input sanitization.",
        ),
        highlightsFr = listOf(
            "Les graphiques de statistiques et les données quotidiennes respectent désormais le fuseau horaire local du parc (ex. : stats 'aujourd'hui' correctes pour les parcs US même vues depuis l'Allemagne).",
            "Nombreuses améliorations de stabilité : téléchargement APK plus robuste, meilleure gestion des erreurs push, casts de type sécurisés et IDs de notification améliorés.",
            "Calcul de percentile corrigé pour les estimations de niveau d'affluence et validation d'entrée améliorée.",
        ),
        highlightsNl = listOf(
            "Statistiekdiagrammen en daggegevens gebruiken nu de lokale tijdzone van het park (bijv. correcte 'vandaag'-statistieken voor Amerikaanse parken, ook bij bekijken vanuit Duitsland).",
            "Talrijke stabiliteitsverbeteringen: robuustere APK-download, betere push-foutafhandeling, veilige typecasts en verbeterde notificatie-ID's.",
            "Gecorrigeerde percentielberekening voor crowd-level schattingen en verbeterde inputvalidatie.",
        ),
    ),
    WhatsNewRelease(
        versionCode = 10201,
        versionName = "1.2.1",
        highlightsDe = listOf(
            "Live-Wartezeiten bleiben bei Serverfehlern haeufig weiter verfuegbar, weil die App und Website dann auf queue-times.com ausweichen.",
            "Fallback-Daten werden jetzt als Ausweichquelle erklaert, ohne irrefuehrende rote Serverfehler fuer nutzbare Daten.",
            "Die alten Wartezeit-Empfehlungslabels wie JETZT, SPAETER und UEBLICH wurden aus der Attraktionsliste entfernt.",
            "Watchlist-Alarme sind waehrend Fallback-Zeitraeumen robuster gegen falsche Attraktions-Treffer.",
        ),
        highlightsEn = listOf(
            "Live wait times often remain available during server errors because the app and website can fall back to queue-times.com.",
            "Fallback data is now explained as an alternate source instead of showing misleading red server errors for usable data.",
            "The old wait-time advice labels like NOW, LATER, and TYPICAL were removed from the attraction list.",
            "Watchlist alerts are more robust against mismatched attraction hits during fallback periods.",
        ),
        highlightsFr = listOf(
            "Les temps d'attente en direct restent souvent disponibles lors d'erreurs serveur grace au repli vers queue-times.com.",
            "Les donnees de secours sont maintenant expliquees comme source alternative au lieu d'afficher une erreur serveur trompeuse.",
            "Les anciens labels de conseil comme MAINTENANT, PLUS TARD et HABITUEL ont ete retires de la liste des attractions.",
            "Les alertes Watchlist evitent mieux les mauvaises correspondances d'attractions pendant les periodes de secours.",
        ),
        highlightsNl = listOf(
            "Live wachttijden blijven bij serverfouten vaak beschikbaar doordat app en website kunnen uitwijken naar queue-times.com.",
            "Fallbackgegevens worden nu uitgelegd als alternatieve bron, zonder misleidende rode serverfouten voor bruikbare data.",
            "De oude wachttijdadviezen zoals NU, LATER en GEBRUIKELIJK zijn uit de attractielijst verwijderd.",
            "Watchlist-meldingen zijn robuuster tegen verkeerde attractietreffers tijdens fallbackperiodes.",
        ),
    ),
    WhatsNewRelease(
        versionCode = 10200,
        versionName = "1.2.0",
        highlightsDe = listOf(
            "Aktuelle Wartezeiten, Auslastung und Statistiken sind jetzt auch ohne App direkt im Browser abrufbar.",
            "Zentrale Wartezeit-Messpunkte für Parks und Attraktionen werden wieder zuverlässig erfasst.",
            "Historische Tagesdaten auf der Statistik-Detailseite laden jetzt deutlich schneller.",
            "Attraktionssuche und Statistik-Index im Hintergrund sind spürbar schneller geworden.",
        ),
        highlightsEn = listOf(
            "Live wait times, crowd levels, and statistics are now available directly in the browser — no app needed.",
            "Central wait-time measurement points for parks and attractions are being tracked reliably again.",
            "Historical daily data on the statistics detail page now loads significantly faster.",
            "Attraction search and the statistics index in the background are noticeably faster.",
        ),
        highlightsFr = listOf(
            "Les temps d'attente en direct, les niveaux d'affluence et les statistiques sont désormais accessibles directement dans le navigateur, sans application.",
            "Les points de mesure centraux des temps d'attente pour les parcs et les attractions sont à nouveau enregistrés de manière fiable.",
            "Les données historiques journalières sur la page de détail des statistiques se chargent désormais bien plus rapidement.",
            "La recherche d'attractions et l'index des statistiques en arrière-plan sont sensiblement plus rapides.",
        ),
        highlightsNl = listOf(
            "Live wachttijden, drukte en statistieken zijn nu rechtstreeks in de browser beschikbaar, zonder app.",
            "Centrale meetslagen voor wachttijden van parken en attracties worden weer betrouwbaar bijgehouden.",
            "Historische daggegevens op de statistiekendetailpagina laden nu aanzienlijk sneller.",
            "Attractiezoeken en de statistiekenindex op de achtergrond zijn merkbaar sneller geworden.",
        ),
    ),
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
