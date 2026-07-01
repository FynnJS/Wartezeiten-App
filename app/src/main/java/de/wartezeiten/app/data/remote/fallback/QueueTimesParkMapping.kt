package de.wartezeiten.app.data.remote.fallback

/**
 * Kuratierte Zuordnung wartezeiten.app-Park-Slug -> queue-times.com-Park-ID.
 * Wird verwendet, wenn die wartezeiten.app-API fuer die Live-Anzeige ausfaellt (Parkliste und/oder
 * Wartezeiten). Manuell gepflegt und hand-verifiziert gegen https://queue-times.com/parks.json -
 * NICHT automatisch generieren/syncen. Bei Aenderungen an wartezeiten- oder queue-times-Park-Slugs
 * muss diese Liste UND die Worker-Entsprechung (worker/src/fallbackParks.js) gemeinsam aktualisiert
 * werden. Parks ohne eindeutige queue-times.com-Entsprechung (z.B. caribeaquaticpark, traumatica)
 * sind bewusst ausgelassen statt geraten.
 */
object QueueTimesParkMapping {
    data class FallbackParkInfo(
        val parkKey: String,
        val name: String,
        val country: String,
        val queueTimesId: Int,
    )

    val ENTRIES: List<FallbackParkInfo> = listOf(
        FallbackParkInfo("altontowers", "Alton Towers", "Großbritannien", 1),
        FallbackParkInfo("thorpepark", "Thorpe Park", "Großbritannien", 2),
        FallbackParkInfo("chessingtonworld", "Chessington World of Adventures", "Großbritannien", 3),
        FallbackParkInfo("disneylandparis", "Disneyland Park Paris", "Frankreich", 4),
        FallbackParkInfo("epcot", "Epcot", "USA", 5),
        FallbackParkInfo("magickingdompark", "Disney Magic Kingdom", "USA", 6),
        FallbackParkInfo("disneyshollywoodstudios", "Disney Hollywood Studios", "USA", 7),
        FallbackParkInfo("disneysanimalkingdomthemepark", "Animal Kingdom", "USA", 8),
        FallbackParkInfo("parcasterix", "Parc Astérix", "Frankreich", 9),
        FallbackParkInfo("liseberg", "Liseberg", "Schweden", 11),
        FallbackParkInfo("gardaland", "Gardaland", "Italien", 12),
        FallbackParkInfo("walibibelgium", "Walibi Belgium", "Belgien", 14),
        FallbackParkInfo("disneylandpark", "Disneyland", "USA", 16),
        FallbackParkInfo("disneycaliforniaadventurepark", "Disney California Adventure", "USA", 17),
        FallbackParkInfo("portaventurapark", "PortAventura Park", "Spanien", 19),
        FallbackParkInfo("heidepark", "Heide Park", "Deutschland", 25),
        FallbackParkInfo("legolandwindsor", "Legoland Windsor", "Großbritannien", 27),
        FallbackParkInfo("disneyadventureworld", "Disney Adventure World Paris", "Frankreich", 28),
        FallbackParkInfo("europapark", "Europa Park", "Deutschland", 51),
        FallbackParkInfo("legolandbillund", "Legoland Billund", "Dänemark", 52),
        FallbackParkInfo("walibiholland", "Walibi Holland", "Niederlande", 53),
        FallbackParkInfo("plopsalandbelgium", "Plopsaland Belgium", "Belgien", 54),
        FallbackParkInfo("phantasialand", "Phantasialand", "Deutschland", 56),
        FallbackParkInfo("universalislandsofadventure", "Islands Of Adventure At Universal Orlando", "USA", 64),
        FallbackParkInfo("universalstudiosflorida", "Universal Studios At Universal Orlando", "USA", 65),
        FallbackParkInfo("universalvolcanobay", "Universal Volcano Bay", "USA", 67),
        FallbackParkInfo("efteling", "Efteling", "Niederlande", 160),
        FallbackParkInfo("ferrariland", "Ferrari Land", "Spanien", 277),
        FallbackParkInfo("legoland", "Legoland Deutschland", "Deutschland", 278),
        FallbackParkInfo("legolandcalifornia", "Legoland California", "USA", 279),
        FallbackParkInfo("legolandflorida", "Legoland Florida", "USA", 280),
        FallbackParkInfo("hansapark", "Hansa Park", "Deutschland", 286),
        FallbackParkInfo("djurssommerland", "Djurs Sommerland", "Dänemark", 290),
        FallbackParkInfo("futuroscope", "Futuroscope", "Frankreich", 291),
        FallbackParkInfo("legolandnewyork", "Legoland New York", "USA", 299),
        FallbackParkInfo("plopsalanddeutschland", "Plopsaland Deutschland", "Deutschland", 302),
        FallbackParkInfo("toverland", "Toverland", "Niederlande", 305),
        FallbackParkInfo("rulantica", "Rulantica", "Deutschland", 309),
        FallbackParkInfo("movieparkgermany", "Movie Park Germany", "Deutschland", 310),
        FallbackParkInfo("bobbejaanland", "Bobbejaanland", "Belgien", 311),
        FallbackParkInfo("energylandia", "Energylandia", "Polen", 317),
        FallbackParkInfo("familypark", "Familypark", "Österreich", 322),
        FallbackParkInfo("universalepicuniverse", "Epic Universe", "USA", 334),
        FallbackParkInfo("nigloland", "Nigloland", "Frankreich", 336),
    )

    private val byParkKey: Map<String, FallbackParkInfo> = ENTRIES.associateBy { it.parkKey }

    fun findByParkKey(parkKey: String): FallbackParkInfo? = byParkKey[parkKey]
}
