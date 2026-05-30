package de.wartezeiten.app.domain.model

import de.wartezeiten.app.domain.model.CrowdLevel
import de.wartezeiten.app.domain.model.HolidayInfo
import de.wartezeiten.app.domain.model.OpeningTimes
import de.wartezeiten.app.domain.model.Park
import de.wartezeiten.app.domain.model.WaitingTime
import de.wartezeiten.app.domain.model.WeatherInfo

data class ParkDetail(
    val park: Park?,
    val openingTimes: OpeningTimes?,
    val crowdLevel: CrowdLevel?,
    val waitingTimes: List<WaitingTime>,
    val weather: WeatherInfo? = null,
    val holidays: List<HolidayInfo> = emptyList()
)
