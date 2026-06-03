package com.novasoftware.hadoorbell.di

import android.content.Context
import com.novasoftware.hadoorbell.data.repository.SettingsRepositoryImpl
import com.novasoftware.hadoorbell.domain.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(appPreferences: SettingsRepositoryImpl): SettingsRepository {
        return appPreferences
    }

    @Provides
    @Singleton
    fun provideSettingsRepositoryImpl(@ApplicationContext context: Context): SettingsRepositoryImpl {
        return SettingsRepositoryImpl(context)
    }

    @Provides
    @Singleton
    fun provideHomeAssistantRepository(impl: com.novasoftware.hadoorbell.data.repository.HomeAssistantRepositoryImpl): com.novasoftware.hadoorbell.domain.repository.HomeAssistantRepository {
        return impl
    }
}
