package io.github.hhwkart.nami.data.di

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.hhwkart.nami.data.settings.ConfigurationMigrationFactory
import io.github.hhwkart.nami.data.settings.MultiProcessConfigurationStore
import io.github.hhwkart.nami.data.settings.LegacySettingsFallback
import io.github.hhwkart.nami.data.settings.RoomLegacySettingsFallback
import io.github.hhwkart.nami.data.settings.RoomSettingsRepository
import io.github.hhwkart.nami.domain.repository.SettingsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideLegacySettingsFallback(
        implementation: RoomLegacySettingsFallback,
    ): LegacySettingsFallback = implementation

    @Provides
    @Singleton
    fun provideConfigurationMigration(
        @ApplicationContext context: Context,
    ): DataMigration<Preferences> = ConfigurationMigrationFactory.fromLegacyRoom(context)

    @Provides
    @Singleton
    fun provideConfigurationDataStore(
        @ApplicationContext context: Context,
        migration: DataMigration<Preferences>,
    ): DataStore<Preferences> = MultiProcessConfigurationStore.get(
        context = context,
        migrations = listOf(migration),
    )

    @Provides
    @Singleton
    fun provideSettingsRepository(): SettingsRepository = RoomSettingsRepository()
}
