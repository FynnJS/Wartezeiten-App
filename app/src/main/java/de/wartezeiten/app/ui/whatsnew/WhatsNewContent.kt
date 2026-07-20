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
        versionCode = 10206,
        versionName = "1.2.6",
        highlightsDe = listOf(
            "Optimierte Datumsauswahl in der Statistik durch einen neuen Monats-Grid-Selektor.",
            "Lokalisierte 'Name'-Sortierlabels in der Wartezeit-Liste und im Parkvergleich.",
            "Fehlerbehebung: Ein Absturz beim Öffnen von Alton Towers aufgrund fehlerhafter API-Daten wurde behoben.",
            "Verbesserte Zuverlässigkeit der Park-Scans und Fix für veraltete Statistikdaten bei Parköffnung.",
            "HANSA-Park wurde aus der App entfernt.",
        ),
        highlightsEn = listOf(
            "Optimized date selection in statistics with a new month-based grid selector.",
            "Localized 'Name' sort labels in WaitingTimes and ParkCompare screens.",
            "Bug fix: Resolved a crash when opening Alton Towers due to invalid API data.",
            "Improved park scan reliability and fixed stale statistics data on park opening.",
            "Removed HANSA-Park from the app.",
        ),
        highlightsFr = listOf(
            "Sélection de date optimisée dans les statistiques avec un nouveau sélecteur de grille mensuel.",
            "Labels de tri par 'Nom' localisés dans les écrans WaitingTimes et ParkCompare.",
            "Correction de bug : résolution d'un crash lors de l'ouverture d'Alton Towers dû à des données API invalides.",
            "Amélioration de la fiabilité des scans de parcs et correction des données statistiques obsolètes à l'ouverture des parcs.",
            "HANSA-Park a été retiré de l'application.",
        ),
        highlightsNl = listOf(
            "Geoptimaliseerde datumselectie in statistieken met een nieuwe maandgebaseerde rasterselector.",
            "Gelokaliseerde 'Naam' sorteerlabels in de WaitingTimes en ParkCompare schermen.",
            "Bugfix: Een crash opgelost bij het openen van Alton Towers als gevolg van ongeldige API-gegevens.",
            "Verbeterde betrouwbaarheid van parkscans en oplossing voor verouderde statistiekgegevens bij parkopening.",
            "HANSA-Park is verwijderd uit de app.",
        ),
    ),
    WhatsNewRelease(
        versionCode = 10205,
        versionName = "1.2.5",
        highlightsDe = listOf(
            "Fehlerbehebung bei der Verarbeitung von Navigationsparametern: Ein potenzieller Absturz beim Öffnen von Park-Details und Statistiken wurde behoben.",
            "Allgemeine Stabilitätsverbesserungen und Vorbereitungen für kommende Funktionen.",
        ),
        highlightsEn = listOf(
            "Fixed navigation parameter processing: Resolved a potential crash when opening park details and statistics.",
            "General stability improvements and preparations for upcoming features.",
        ),
        highlightsFr = listOf(
            "Correction du traitement des paramètres de navigation : résolution d'un crash potentiel lors de l'ouverture des détails du parc et des statistiques.",
            "Améliorations générales de la stabilité et préparations pour les fonctionnalités à venir.",
        ),
        highlightsNl = listOf(
            "Navigatieparameterverwerking gerepareerd: een mogelijke crash opgelost bij het openen van parkdetails en statistieken.",
            "Algemene stabiliteitsverbeteringen en voorbereidingen voor aankomende functies.",
        ),
    ),
    WhatsNewRelease(
        versionCode = 10204,
        versionName = "1.2.4",
        highlightsDe = listOf(
            "Die Berechnung der aktuellen Auslastung (Crowd Level) wurde angepasst, um realistischere und präzisere Schätzungen basierend auf Live-Wartezeiten zu liefern.",
            "Die Statistikanzeige wurde korrigiert: Auch Attraktionen, die den ganzen Tag geschlossen waren, zeigen jetzt korrekt ihre historischen Status-Daten an statt einer Fehlermeldung.",
            "Verbesserte Ladezustände in der Parkstatistik: Der Text 'Daten werden geladen...' bleibt nun flackerfrei sichtbar, bis alle Hintergrunddaten vollständig geladen sind.",
        ),
        highlightsEn = listOf(
            "Adjusted the crowd level calculation to provide more realistic and precise estimates based on live wait times.",
            "Fixed statistics charts: Attractions that were closed all day now correctly display their historical status data instead of an error message.",
            "Improved loading states in park statistics: The 'Loading data...' message now remains visible without flickering until all background data is fully fetched.",
        ),
        highlightsFr = listOf(
            "Le calcul du niveau d'affluence a été ajusté pour fournir des estimations plus réalistes et précises basées sur les temps d'attente en direct.",
            "Correction des graphiques statistiques : les attractions fermées toute la journée affichent désormais correctement leurs données d'état historiques au lieu d'un message d'erreur.",
            "Amélioration des états de chargement des statistiques du parc : le message 'Chargement des données...' reste désormais visible sans scintillement jusqu'à la récupération complète des données.",
        ),
        highlightsNl = listOf(
            "De berekening van het drukteniveau is aangepast om realistischere en nauwkeurigere schattingen te geven op basis van live wachttijden.",
            "Statistiekdiagrammen gerepareerd: attracties die de hele dag gesloten waren, tonen nu correct hun historische statusgegevens in plaats van een foutmelding.",
            "Verbeterde laadstatussen in parkstatistieken: het bericht 'Gegevens laden...' blijft nu flikkervrij zichtbaar totdat alle achtergrondgegevens volledig zijn opgehaald.",
        ),
    ),
    WhatsNewRelease(
        versionCode = 10203,
        versionName = "1.2.3",
        highlightsDe = listOf(
            "Wartezeiten-, Parklisten- und Statistik-Ansichten wurden weiter verfeinert: Ladezustände, Fehlerbanner und Pull-to-refresh wirken jetzt konsistenter und verständlicher.",
            "Die Parkliste und die Statistik-Detailansicht profitieren von saubereren Refresh- und Filter-Logiken sowie einer verbesserten Darstellung von Headern und Empty States.",
            "Die Datenzuordnung und Aktualisierungslogik für Trends und Wartezeiten wurde robuster gemacht, damit die UI weniger inkonsistent wirkt.",
        ),
        highlightsEn = listOf(
            "Waiting-times, park list, and statistics views were refined further so loading states, error banners, and pull-to-refresh feel more consistent and clearer.",
            "The park list and statistics detail view benefit from cleaner refresh and filter logic, plus improved headers and empty-state presentation.",
            "Trend and wait-time data mapping and refresh timing were made more robust to reduce UI inconsistencies.",
        ),
        highlightsFr = listOf(
            "Les vues des temps d'attente, de la liste des parcs et des statistiques ont été affinées pour rendre les états de chargement, les bannières d'erreur et le pull-to-refresh plus cohérents et plus clairs.",
            "La liste des parcs et la vue détaillée des statistiques profitent d'une logique de rafraîchissement et de filtre plus propre, ainsi que d'une meilleure présentation des en-têtes et des états vides.",
            "Le mapping des données de tendance et des temps d'attente, ainsi que la logique de rafraîchissement, ont été rendus plus robustes pour limiter les incohérences de l'interface.",
        ),
        highlightsNl = listOf(
            "De weergaven voor wachttijden, parkenlijst en statistieken zijn verder verfijnd, zodat laadstatussen, foutbanners en pull-to-refresh consistenter en duidelijker aanvoelen.",
            "De parkenlijst en de detailweergave van statistieken profiteren van schonere refresh- en filterlogica, plus verbeterde headers en lege-toestanden.",
            "De datamapping en refreshlogica voor trends en wachttijden zijn robuuster gemaakt om inconsistenties in de UI te verminderen.",
        ),
    ),
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
