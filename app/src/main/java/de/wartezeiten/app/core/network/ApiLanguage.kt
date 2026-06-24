package de.wartezeiten.app.core.network

/**
 * The upstream Wartezeiten.app API only accepts "de" or "en" for its `language` header.
 * App display languages beyond those (e.g. "fr", "nl") must fall back to English for API calls.
 */
fun String.toApiLanguage(): String = if (this == "de") "de" else "en"
