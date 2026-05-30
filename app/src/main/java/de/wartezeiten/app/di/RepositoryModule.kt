package de.wartezeiten.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.wartezeiten.app.data.repository.DefaultWartezeitenRepository
import de.wartezeiten.app.domain.repository.WartezeitenRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindWartezeitenRepository(
        repository: DefaultWartezeitenRepository
    ): WartezeitenRepository
}
