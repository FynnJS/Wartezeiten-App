package de.wartezeiten.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.wartezeiten.app.data.local.WartezeitenDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): WartezeitenDatabase {
        return Room.databaseBuilder(
            context,
            WartezeitenDatabase::class.java,
            "wartezeiten.db"
        ).fallbackToDestructiveMigration(true).build()
    }

    @Provides
    fun provideParkDao(database: WartezeitenDatabase) = database.parkDao()

    @Provides
    fun provideParkDetailDao(database: WartezeitenDatabase) = database.parkDetailDao()

    @Provides
    fun provideParkSnapshotDao(database: WartezeitenDatabase) = database.parkSnapshotDao()

    @Provides
    fun provideWatchlistDao(database: WartezeitenDatabase) = database.watchlistDao()

    @Provides
    fun provideAlertHistoryDao(database: WartezeitenDatabase) = database.alertHistoryDao()
}
