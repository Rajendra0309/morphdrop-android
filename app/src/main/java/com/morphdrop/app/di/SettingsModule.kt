package com.morphdrop.app.di

import com.morphdrop.app.data.repository.DataStoreSettingsRepository
import com.morphdrop.app.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: DataStoreSettingsRepository
    ): SettingsRepository
}
