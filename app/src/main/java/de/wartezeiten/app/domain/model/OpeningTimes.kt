package de.wartezeiten.app.domain.model

data class OpeningTimes(
    val opened: Boolean,
    val from: String?,
    val to: String?
)
