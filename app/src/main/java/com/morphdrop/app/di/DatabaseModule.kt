package com.morphdrop.app.di

import android.content.Context
import androidx.room.Room
import com.morphdrop.app.data.local.MorphDropDatabase
import com.morphdrop.app.data.local.dao.FavoriteDao
import com.morphdrop.app.data.local.dao.HistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMorphDropDatabase(
        @ApplicationContext context: Context
    ): MorphDropDatabase {
        return Room.databaseBuilder(
            context,
            MorphDropDatabase::class.java,
            "morphdrop.db"
        ).fallbackToDestructiveMigration(dropAllTables = true).build()
    }

    @Provides
    fun provideHistoryDao(database: MorphDropDatabase): HistoryDao {
        return database.historyDao()
    }

    @Provides
    fun provideFavoriteDao(database: MorphDropDatabase): FavoriteDao {
        return database.favoriteDao()
    }
}
