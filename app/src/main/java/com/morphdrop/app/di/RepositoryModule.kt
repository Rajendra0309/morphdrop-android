package com.morphdrop.app.di

import com.morphdrop.app.data.repository.FavoriteRepositoryImpl
import com.morphdrop.app.data.repository.HistoryRepositoryImpl
import com.morphdrop.app.domain.repository.FavoriteRepository
import com.morphdrop.app.domain.repository.HistoryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(
        impl: HistoryRepositoryImpl
    ): HistoryRepository

    @Binds
    @Singleton
    abstract fun bindFavoriteRepository(
        impl: FavoriteRepositoryImpl
    ): FavoriteRepository
}
