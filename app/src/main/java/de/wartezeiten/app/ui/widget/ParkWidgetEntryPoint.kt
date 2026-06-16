package de.wartezeiten.app.ui.widget

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.wartezeiten.app.data.local.PreferencesDataSource
import de.wartezeiten.app.domain.repository.WartezeitenRepository

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ParkWidgetEntryPoint {
    fun repository(): WartezeitenRepository
    fun preferencesDataSource(): PreferencesDataSource
}
