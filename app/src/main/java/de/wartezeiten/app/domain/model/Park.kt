package de.wartezeiten.app.domain.model

data class Park(
    val id: String,
    val uuid: String,
    val name: String,
    val country: String,
    val isFavorite: Boolean = false
)
