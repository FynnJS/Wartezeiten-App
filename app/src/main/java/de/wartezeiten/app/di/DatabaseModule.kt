package de.wartezeiten.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        )
            .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
            .fallbackToDestructiveMigration(true)
            .build()
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

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE park_snapshots ADD COLUMN source TEXT NOT NULL DEFAULT 'local'")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE watchlist ADD COLUMN notifyOnce INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE watchlist ADD COLUMN onlyWhenParkOpen INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE watchlist ADD COLUMN quietHoursEnabled INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE watchlist ADD COLUMN quietStartMinutes INTEGER NOT NULL DEFAULT 1320")
            db.execSQL("ALTER TABLE watchlist ADD COLUMN quietEndMinutes INTEGER NOT NULL DEFAULT 480")
            db.execSQL("ALTER TABLE watchlist ADD COLUMN cooldownMinutes INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE watchlist ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE alert_history ADD COLUMN lastTriggeredAtMillis INTEGER NOT NULL DEFAULT 0")
        }
    }
}
