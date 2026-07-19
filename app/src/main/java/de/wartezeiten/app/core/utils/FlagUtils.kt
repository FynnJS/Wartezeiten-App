package de.wartezeiten.app.core.utils

fun countryToFlag(country: String): String {
    return flagEmojiForCountryCode(countryToIsoCode(country) ?: "")
}

fun countryToIsoCode(country: String): String? {
    return when (country.lowercase().trim()) {
        "deutschland", "germany", "de" -> "DE"
        "österreich", "austria", "at" -> "AT"
        "schweiz", "switzerland", "ch" -> "CH"
        "frankreich", "france", "fr" -> "FR"
        "niederlande", "netherlands", "nl" -> "NL"
        "belgien", "belgium", "be" -> "BE"
        "vereinigtes königreich", "united kingdom", "uk", "gb", "great britain", "großbritannien" -> "GB"
        "usa", "us", "u.s.a.", "united states", "united states of america", "vereinigte staaten", "vereinigte staaten von amerika" -> "US"
        "spanien", "spain", "es" -> "ES"
        "italien", "italy", "it" -> "IT"
        "dänemark", "denmark", "dk" -> "DK"
        "schweden", "sweden", "se" -> "SE"
        "norwegen", "norway", "no" -> "NO"
        "finnland", "finland", "fi" -> "FI"
        "japan", "jp" -> "JP"
        "tschechien", "czech republic", "cz" -> "CZ"
        "polen", "poland", "pl" -> "PL"
        "portugal", "pt" -> "PT"
        "luxemburg", "luxembourg", "lu" -> "LU"
        else -> null
    }
}

fun flagEmojiForCountryCode(countryCode: String): String {
    if (countryCode.length != 2) return ""
    val firstLetter = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6
    val secondLetter = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
}
