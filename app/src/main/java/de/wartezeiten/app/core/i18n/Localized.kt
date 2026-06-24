package de.wartezeiten.app.core.i18n

/**
 * Resolves UI text for the app's four supported display languages.
 * "de" is the fallback for any unrecognized value, matching [de.wartezeiten.app.data.local.PreferencesDataSource.DEFAULT_LANGUAGE].
 */
fun localized(language: String, de: String, en: String, fr: String, nl: String): String = when (language) {
    "en" -> en
    "fr" -> fr
    "nl" -> nl
    else -> de
}
