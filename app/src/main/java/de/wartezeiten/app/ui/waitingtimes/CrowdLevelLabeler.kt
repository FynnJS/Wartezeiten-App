package de.wartezeiten.app.ui.waitingtimes

import androidx.compose.ui.graphics.Color
import de.wartezeiten.app.core.i18n.localized

object CrowdLevelLabeler {
    data class Status(val label: String, val color: Color)

    fun getStatus(crowdLevel: Float?, language: String = "de"): Status {
        return when {
            crowdLevel == null -> Status(localized(language, de = "Unbekannt", en = "Unknown", fr = "Inconnu", nl = "Onbekend"), Color.Gray)
            crowdLevel < 30f -> Status(localized(language, de = "Niedrig", en = "Low", fr = "Faible", nl = "Laag"), Color(0xFF388E3C))
            crowdLevel < 60f -> Status(localized(language, de = "Mittel", en = "Medium", fr = "Moyen", nl = "Gemiddeld"), Color(0xFFFBC02D))
            crowdLevel < 80f -> Status(localized(language, de = "Hoch", en = "High", fr = "Élevé", nl = "Hoog"), Color(0xFFE64A19))
            else -> Status(localized(language, de = "Sehr hoch", en = "Very high", fr = "Très élevé", nl = "Zeer hoog"), Color(0xFFD32F2F))
        }
    }
}
