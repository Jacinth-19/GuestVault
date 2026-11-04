package com.guestvault.di

import com.guestvault.data.repository.AppCatalogRepositoryImpl
import com.guestvault.data.repository.PinRepositoryImpl
import com.guestvault.data.repository.ProfileRepositoryImpl
import com.guestvault.data.repository.RecoveryCodeRepositoryImpl
import com.guestvault.data.repository.SettingsRepositoryImpl
import com.guestvault.domain.repository.AppCatalogRepository
import com.guestvault.domain.repository.PinRepository
import com.guestvault.domain.repository.ProfileRepository
import com.guestvault.domain.repository.RecoveryCodeRepository
import com.guestvault.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindPinRepository(impl: PinRepositoryImpl): PinRepository

    @Binds
    @Singleton
    abstract fun bindAppCatalogRepository(impl: AppCatalogRepositoryImpl): AppCatalogRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindRecoveryCodeRepository(
        impl: RecoveryCodeRepositoryImpl
    ): RecoveryCodeRepository
}
