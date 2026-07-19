// Kuratierte Zuordnung wartezeiten.app-Park-Slug -> queue-times.com-Park-ID.
// Wird verwendet, wenn die wartezeiten.app-API fuer die Live-Anzeige ausfaellt (siehe index.js).
// Manuell gepflegt und hand-verifiziert gegen https://queue-times.com/parks.json - NICHT automatisch
// generieren/syncen. Bei Aenderungen an wartezeiten- oder queue-times-Park-Slugs muss diese Liste UND
// die Android-Entsprechung (QueueTimesParkMapping.kt) gemeinsam aktualisiert werden.
// Parks ohne eindeutige queue-times.com-Entsprechung (z.B. caribeaquaticpark, traumatica) sind bewusst
// ausgelassen statt geraten.
export const FALLBACK_PARKS = [
  { parkKey: "altontowers", name: "Alton Towers", land: "Großbritannien", queueTimesId: 1 },
  { parkKey: "thorpepark", name: "Thorpe Park", land: "Großbritannien", queueTimesId: 2 },
  { parkKey: "chessingtonworld", name: "Chessington World of Adventures", land: "Großbritannien", queueTimesId: 3 },
  { parkKey: "disneylandparis", name: "Disneyland Park Paris", land: "Frankreich", queueTimesId: 4 },
  { parkKey: "epcot", name: "Epcot", land: "USA", queueTimesId: 5 },
  { parkKey: "magickingdompark", name: "Disney Magic Kingdom", land: "USA", queueTimesId: 6 },
  { parkKey: "disneyshollywoodstudios", name: "Disney Hollywood Studios", land: "USA", queueTimesId: 7 },
  { parkKey: "disneysanimalkingdomthemepark", name: "Animal Kingdom", land: "USA", queueTimesId: 8 },
  { parkKey: "parcasterix", name: "Parc Astérix", land: "Frankreich", queueTimesId: 9 },
  { parkKey: "liseberg", name: "Liseberg", land: "Schweden", queueTimesId: 11 },
  { parkKey: "gardaland", name: "Gardaland", land: "Italien", queueTimesId: 12 },
  { parkKey: "walibibelgium", name: "Walibi Belgium", land: "Belgien", queueTimesId: 14 },
  { parkKey: "disneylandpark", name: "Disneyland", land: "USA", queueTimesId: 16 },
  { parkKey: "disneycaliforniaadventurepark", name: "Disney California Adventure", land: "USA", queueTimesId: 17 },
  { parkKey: "portaventurapark", name: "PortAventura Park", land: "Spanien", queueTimesId: 19 },
  { parkKey: "heidepark", name: "Heide Park", land: "Deutschland", queueTimesId: 25 },
  { parkKey: "legolandwindsor", name: "Legoland Windsor", land: "Großbritannien", queueTimesId: 27 },
  { parkKey: "disneyadventureworld", name: "Disney Adventure World Paris", land: "Frankreich", queueTimesId: 28 },
  { parkKey: "europapark", name: "Europa Park", land: "Deutschland", queueTimesId: 51 },
  { parkKey: "legolandbillund", name: "Legoland Billund", land: "Dänemark", queueTimesId: 52 },
  { parkKey: "walibiholland", name: "Walibi Holland", land: "Niederlande", queueTimesId: 53 },
  { parkKey: "plopsalandbelgium", name: "Plopsaland Belgium", land: "Belgien", queueTimesId: 54 },
  { parkKey: "phantasialand", name: "Phantasialand", land: "Deutschland", queueTimesId: 56 },
  { parkKey: "universalislandsofadventure", name: "Islands Of Adventure At Universal Orlando", land: "USA", queueTimesId: 64 },
  { parkKey: "universalstudiosflorida", name: "Universal Studios At Universal Orlando", land: "USA", queueTimesId: 65 },
  { parkKey: "universalvolcanobay", name: "Universal Volcano Bay", land: "USA", queueTimesId: 67 },
  { parkKey: "efteling", name: "Efteling", land: "Niederlande", queueTimesId: 160 },
  { parkKey: "ferrariland", name: "Ferrari Land", land: "Spanien", queueTimesId: 277 },
  { parkKey: "legoland", name: "Legoland Deutschland", land: "Deutschland", queueTimesId: 278 },
  { parkKey: "legolandcalifornia", name: "Legoland California", land: "USA", queueTimesId: 279 },
  { parkKey: "legolandflorida", name: "Legoland Florida", land: "USA", queueTimesId: 280 },
  { parkKey: "djurssommerland", name: "Djurs Sommerland", land: "Dänemark", queueTimesId: 290 },
  { parkKey: "futuroscope", name: "Futuroscope", land: "Frankreich", queueTimesId: 291 },
  { parkKey: "legolandnewyork", name: "Legoland New York", land: "USA", queueTimesId: 299 },
  { parkKey: "plopsalanddeutschland", name: "Plopsaland Deutschland", land: "Deutschland", queueTimesId: 302 },
  { parkKey: "toverland", name: "Toverland", land: "Niederlande", queueTimesId: 305 },
  { parkKey: "rulantica", name: "Rulantica", land: "Deutschland", queueTimesId: 309 },
  { parkKey: "movieparkgermany", name: "Movie Park Germany", land: "Deutschland", queueTimesId: 310 },
  { parkKey: "bobbejaanland", name: "Bobbejaanland", land: "Belgien", queueTimesId: 311 },
  { parkKey: "energylandia", name: "Energylandia", land: "Polen", queueTimesId: 317 },
  { parkKey: "familypark", name: "Familypark", land: "Österreich", queueTimesId: 322 },
  { parkKey: "universalepicuniverse", name: "Epic Universe", land: "USA", queueTimesId: 334 },
  { parkKey: "nigloland", name: "Nigloland", land: "Frankreich", queueTimesId: 336 },
];

export const FALLBACK_PARK_BY_KEY = new Map(FALLBACK_PARKS.map((park) => [park.parkKey, park]));
