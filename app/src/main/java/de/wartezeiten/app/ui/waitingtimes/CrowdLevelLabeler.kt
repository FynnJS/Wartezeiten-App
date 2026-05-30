package de.wartezeiten.app.ui.waitingtimes

import androidx.compose.ui.graphics.Color

object CrowdLevelLabeler {
    data class Status(val label: String, val color: Color)

    fun getStatus(crowdLevel: Float?): Status {
        return when {
            crowdLevel == null -> Status("Unbekannt", Color.Gray)
            crowdLevel < 30f -> Status("Niedrig", Color(0xFF388E3C))
            crowdLevel < 60f -> Status("Mittel", Color(0xFFFBC02D))
            crowdLevel < 80f -> Status("Hoch", Color(0xFFE64A19))
            else -> Status("Sehr hoch", Color(0xFFD32F2F))
        }
    }
}
